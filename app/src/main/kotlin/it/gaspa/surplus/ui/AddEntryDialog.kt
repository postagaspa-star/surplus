package it.gaspa.surplus.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import it.gaspa.surplus.data.Food
import it.gaspa.surplus.data.FoodGroup
import it.gaspa.surplus.data.Moment
import kotlin.math.roundToInt

/**
 * Dati raccolti dal dialogo per una nuova voce del diario. Porta con sé
 * l'alimento intero (valori per 100 g): servono sia a registrare la voce sia
 * a mostrare la scheda del prodotto subito dopo.
 */
data class NewEntryData(
    val moment: Moment,
    val food: Food,
    val grams: Double,
    val saveFood: Boolean,
)

/**
 * Inserimento voce: cerca in dispensa, online, o **scansiona il codice a barre**;
 * in ultima istanza si crea l'alimento a mano. Quantità in g/ml OBBLIGATORIA,
 * valori nutrizionali facoltativi.
 */
@Composable
fun AddEntryDialog(
    vm: AppViewModel,
    initialMoment: Moment,
    foods: List<Food>,
    recents: List<Food>,
    onConfirm: (NewEntryData) -> Unit,
    onFoto: () -> Unit,
    onDismiss: () -> Unit,
) {
    val settings by vm.settings.collectAsState()
    var moment by remember { mutableStateOf(initialMoment) }
    var momentMenu by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Food?>(null) }
    var customMode by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    var gramsText by remember { mutableStateOf("") }
    var kcal100Text by remember { mutableStateOf("") }
    var prot100Text by remember { mutableStateOf("") }
    // Valori completi dell'alimento creato a mano: senza questi, i conti su fibre
    // e grado di lavorazione sarebbero ciechi proprio sui cibi che aggiungi tu.
    var grassi100Text by remember { mutableStateOf("") }
    var carb100Text by remember { mutableStateOf("") }
    var fibra100Text by remember { mutableStateOf("") }
    var saturi100Text by remember { mutableStateOf("") }
    var zuccheri100Text by remember { mutableStateOf("") }
    var sale100Text by remember { mutableStateOf("") }
    var novaManuale by remember { mutableStateOf<Int?>(null) }
    var gruppoManuale by remember { mutableStateOf(FoodGroup.ALTRO) }
    var gruppoMenu by remember { mutableStateOf(false) }
    var altriValori by remember { mutableStateOf(false) }
    var saveInPantry by remember { mutableStateOf(true) }
    var customDrink by remember { mutableStateOf(initialMoment == Moment.BEVANDE) }
    var pendingBarcode by remember { mutableStateOf<String?>(null) }
    var scanNotice by remember { mutableStateOf<String?>(null) }
    // Correzione manuale dell'unità: gli archivi a volte sbagliano a classificare.
    var unitOverride by remember { mutableStateOf<Boolean?>(null) }
    val offState by vm.offState.collectAsState()
    val scanState by vm.scanState.collectAsState()

    // Lo scanner è un'activity a sé: torna il codice letto (o null se annullato).
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { code ->
            scanNotice = null
            vm.lookupBarcode(code)
        }
    }

    // Esito della scansione: prodotto pronto, oppure si passa all'inserimento a mano.
    LaunchedEffect(scanState) {
        when (val s = scanState) {
            is AppViewModel.ScanState.Found -> {
                selected = s.food
                customMode = false
                pendingBarcode = s.food.barcode
                scanNotice =
                    if (s.local) "Già in dispensa: nessuna ricerca necessaria."
                    else "Trovato su ${s.fonte} e salvato in dispensa."
                vm.clearScan()
            }
            is AppViewModel.ScanState.Unknown -> {
                customMode = true
                selected = null
                pendingBarcode = s.code
                scanNotice = s.message
                vm.clearScan()
            }
            else -> Unit
        }
    }

    val grams = gramsText.replace(',', '.').toDoubleOrNull()
    val kcal100 = if (customMode) kcal100Text.replace(',', '.').toDoubleOrNull() else selected?.kcal100
    val prot100 = if (customMode) prot100Text.replace(',', '.').toDoubleOrNull() else selected?.prot100
    val nome = if (customMode) customName.trim() else selected?.nome.orEmpty()
    val isDrink = if (customMode) customDrink else unitOverride ?: (selected?.isDrink == true)
    val unit = if (isDrink) "ml" else "g"
    val picking = selected == null && !customMode
    val canConfirm = !picking && nome.isNotBlank() && grams != null && grams > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (picking) "Aggiungi · ${moment.label}" else nome.ifBlank { "Nuovo alimento" }) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box {
                    OutlinedButton(onClick = { momentMenu = true }) { Text("Momento: ${moment.label}") }
                    DropdownMenu(expanded = momentMenu, onDismissRequest = { momentMenu = false }) {
                        Moment.entries.forEach { m ->
                            DropdownMenuItem(text = { Text(m.label) }, onClick = { moment = m; momentMenu = false })
                        }
                    }
                }

                scanNotice?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }

                if (scanState is AppViewModel.ScanState.Loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp))
                        Text(
                            "  Cerco il codice negli archivi…",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                if (picking) {
                    // Scansione: il modo più veloce e senza errori di battitura.
                    OutlinedButton(
                        onClick = { scanLauncher.launch(scanOptions()) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Scansiona codice a barre") }

                    // Sempre visibile: se manca la chiave lo spiega il dialogo della foto,
                    // altrimenti la funzione resterebbe invisibile a chi non sa che esiste.
                    OutlinedButton(
                        onClick = onFoto,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (settings.aiAttiva) "Riconosci da una foto"
                            else "Riconosci da una foto (serve la tua chiave)"
                        )
                    }

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("oppure cerca per nome") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val results =
                        if (query.isBlank()) recents
                        else foods.filter { it.nome.contains(query.trim(), ignoreCase = true) }.take(12)
                    if (query.isBlank() && recents.isNotEmpty()) {
                        Text("Recenti", style = MaterialTheme.typography.labelSmall)
                    }
                    results.forEach { f ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selected = f }
                                .padding(vertical = 6.dp),
                        ) {
                            Text(f.nome, style = MaterialTheme.typography.bodyMedium)
                            val per = if (f.isDrink) "100 ml" else "100 g"
                            val info = f.kcal100?.let { k ->
                                "${k.roundToInt()} kcal/$per" +
                                    (f.prot100?.let { p -> " · ${fmt1(p)} g prot" } ?: "")
                            } ?: "valori non noti"
                            Text(
                                info + (f.hint?.let { " — $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (query.isNotBlank()) {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        // Ricerca online: additiva e opzionale, l'app resta usabile offline.
                        when (val off = offState) {
                            is AppViewModel.OffState.Loading ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(18.dp))
                                    Text("  Cerco online…", style = MaterialTheme.typography.bodySmall)
                                }
                            is AppViewModel.OffState.Failed ->
                                Text(
                                    off.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            is AppViewModel.OffState.Ready -> {
                                Text(
                                    "Da Open Food Facts",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                off.results.forEach { r ->
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { selected = vm.saveOnlineFood(r) }
                                            .padding(vertical = 6.dp),
                                    ) {
                                        Text(r.nomeCompleto, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "${r.kcal100?.roundToInt()} kcal/100${if (r.isDrink) " ml" else " g"}" +
                                                (r.prot100?.let { " · ${fmt1(it)} g prot" } ?: ""),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            else -> TextButton(onClick = { vm.searchOnline(query.trim()) }) {
                                Text("🌐 Cerca «${query.trim()}» online")
                            }
                        }
                        TextButton(onClick = { customMode = true; customName = query.trim() }) {
                            Text("➕ Crea «${query.trim()}» a mano")
                        }
                    }
                } else {
                    if (customMode) {
                        OutlinedTextField(
                            value = customName,
                            onValueChange = { customName = it },
                            label = { Text("Nome") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        pendingBarcode?.let {
                            Text(
                                "Codice a barre $it: lo lego a questo alimento, la prossima scansione sarà immediata.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(selected!!.nome, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                selected = null; customMode = false
                                pendingBarcode = null; scanNotice = null; unitOverride = null
                            }) { Text("Cambia") }
                        }
                        selected?.hint?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // Se l'archivio ha sbagliato (capita: pasta letta come bevanda),
                        // qui si corregge e la correzione resta per le volte successive.
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Si misura in", style = MaterialTheme.typography.bodySmall)
                            FilterChip(
                                selected = !isDrink,
                                onClick = {
                                    unitOverride = false
                                    selected?.let { vm.setFoodUnit(it, false) }
                                },
                                label = { Text("grammi") },
                            )
                            FilterChip(
                                selected = isDrink,
                                onClick = {
                                    unitOverride = true
                                    selected?.let { vm.setFoodUnit(it, true) }
                                },
                                label = { Text("ml") },
                            )
                        }
                        if (isDrink) {
                            Text(
                                "Come bevanda, i suoi ml contano anche nel totale dei liquidi del giorno.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    OutlinedTextField(
                        value = gramsText,
                        onValueChange = { gramsText = it },
                        label = { Text("Quantità ($unit) — obbligatoria") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (customMode) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = kcal100Text,
                                onValueChange = { kcal100Text = it },
                                label = { Text("kcal/100") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = prot100Text,
                                onValueChange = { prot100Text = it },
                                label = { Text("prot/100") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Text(
                            "Valori facoltativi: senza, la voce conta come «?»",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        // Grado di lavorazione: chiesto anche a mano, altrimenti i cibi
                        // che aggiungi tu resterebbero fuori dal conto della qualità.
                        Text("Quanto è lavorato", style = MaterialTheme.typography.labelMedium)
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            novaScelte.forEach { (livello, etichetta) ->
                                FilterChip(
                                    selected = novaManuale == livello,
                                    onClick = { novaManuale = if (novaManuale == livello) null else livello },
                                    label = { Text(etichetta) },
                                )
                            }
                        }
                        novaManuale?.let {
                            Text(
                                novaSpiegazioni.getValue(it),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Box {
                            OutlinedButton(onClick = { gruppoMenu = true }) {
                                Text("Gruppo: ${gruppoManuale.label}")
                            }
                            DropdownMenu(expanded = gruppoMenu, onDismissRequest = { gruppoMenu = false }) {
                                FoodGroup.entries.forEach { g ->
                                    DropdownMenuItem(
                                        text = { Text(g.label) },
                                        onClick = { gruppoManuale = g; gruppoMenu = false },
                                    )
                                }
                            }
                        }

                        // Il resto dei valori sta chiuso: l'inserimento veloce resta veloce.
                        TextButton(onClick = { altriValori = !altriValori }) {
                            Text(if (altriValori) "Nascondi altri valori" else "Altri valori (fibre, grassi, sale…)")
                        }
                        if (altriValori) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = grassi100Text, onValueChange = { grassi100Text = it },
                                    label = { Text("grassi/100") }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = carb100Text, onValueChange = { carb100Text = it },
                                    label = { Text("carb/100") }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = fibra100Text, onValueChange = { fibra100Text = it },
                                    label = { Text("fibre/100") }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = saturi100Text, onValueChange = { saturi100Text = it },
                                    label = { Text("saturi/100") }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = zuccheri100Text, onValueChange = { zuccheri100Text = it },
                                    label = { Text("zuccheri/100") }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = sale100Text, onValueChange = { sale100Text = it },
                                    label = { Text("sale/100") }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Text(
                                "Trovi tutti questi numeri nella tabella nutrizionale della confezione.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = customDrink, onCheckedChange = { customDrink = it })
                            Text("È una bevanda (valori per 100 ml)", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = saveInPantry, onCheckedChange = { saveInPantry = it })
                            Text("Salva in dispensa per la prossima volta", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (grams != null && kcal100 != null) {
                        Text(
                            "≈ ${fmtKcal(grams * kcal100 / 100)} kcal" +
                                (prot100?.let { ", ${fmt1(grams * it / 100)} g proteine" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = {
                    val food = if (customMode) {
                        fun num(t: String) = t.replace(',', '.').toDoubleOrNull()
                        Food(
                            id = "manuale",
                            nome = nome,
                            kcal100 = kcal100,
                            prot100 = prot100,
                            grassi100 = num(grassi100Text),
                            carb100 = num(carb100Text),
                            fibra100 = num(fibra100Text),
                            grassiSaturi100 = num(saturi100Text),
                            zuccheri100 = num(zuccheri100Text),
                            sale100 = num(sale100Text),
                            // Se non lo dichiari, un prodotto col codice a barre è confezionato.
                            nova = novaManuale ?: pendingBarcode?.let { 4 },
                            gruppo = gruppoManuale,
                            isDrink = isDrink,
                            barcode = pendingBarcode,
                            custom = true,
                        )
                    } else {
                        selected!!.copy(isDrink = isDrink)
                    }
                    onConfirm(
                        NewEntryData(
                            moment = moment,
                            food = food,
                            grams = grams!!,
                            saveFood = customMode && saveInPantry,
                        )
                    )
                },
            ) { Text("Aggiungi") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

/** Le quattro categorie NOVA, con parole comprensibili invece dei numeri. */
private val novaScelte = listOf(
    1 to "Cibo vero",
    2 to "Ingrediente",
    3 to "Lavorato",
    4 to "Industriale",
)

private val novaSpiegazioni = mapOf(
    1 to "Frutta, verdura, uova, carne e pesce freschi, riso, pasta secca, latte.",
    2 to "Usato per cucinare: olio, burro, zucchero, sale.",
    3 to "Pane, formaggi, prosciutto, cibo in scatola, conserve.",
    4 to "Merendine, bibite, snack confezionati, salse pronte, cereali da colazione.",
)

/** Solo formati da prodotto (EAN/UPC): niente falsi positivi da QR a caso. */
private fun scanOptions(): ScanOptions = ScanOptions().apply {
    setDesiredBarcodeFormats(ScanOptions.PRODUCT_CODE_TYPES)
    setPrompt("Inquadra il codice a barre del prodotto")
    setBeepEnabled(false)
    setOrientationLocked(false)
}
