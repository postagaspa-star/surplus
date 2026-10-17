package it.gaspa.surplus.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import it.gaspa.surplus.data.AiFoodItem
import it.gaspa.surplus.data.Moment
import java.io.File
import kotlin.math.roundToInt

/**
 * Riconoscimento del cibo da una o più foto.
 *
 * Il flusso è in due tempi: prima si raccolgono gli scatti e si dice cosa sta
 * con cosa (il piatto e la sua etichetta sono lo stesso alimento, due piatti
 * diversi no), poi si analizza. Niente entra nel diario senza conferma: nome,
 * quantità e selezione restano modificabili fino all'ultimo.
 */
@Composable
fun FotoAnalisiDialog(
    vm: AppViewModel,
    moment: Moment,
    onDismiss: () -> Unit,
    onAggiunti: () -> Unit,
) {
    val context = LocalContext.current
    val stato by vm.fotoState.collectAsState()
    val scatti by vm.scatti.collectAsState()
    val unico by vm.unicoAlimento.collectAsState()
    val settings by vm.settings.collectAsState()

    // Modifiche dell'utente sui risultati, per chiave "gruppo-indice".
    val scelti = remember { mutableStateMapOf<String, Boolean>() }
    val nomi = remember { mutableStateMapOf<String, String>() }
    val grammi = remember { mutableStateMapOf<String, String>() }
    val risposte = remember { mutableStateMapOf<Int, String>() }
    var fotoUri by remember { mutableStateOf<Uri?>(null) }

    val galleria = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { vm.aggiungiScatto(it) } }

    val fotocamera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok -> if (ok) fotoUri?.let { vm.aggiungiScatto(it) } }

    fun scatta() {
        val uri = nuovoFileFoto(context)
        fotoUri = uri
        fotocamera.launch(uri)
    }

    val permesso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concesso -> if (concesso) scatta() }

    fun scattaConPermesso() {
        val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (ok) scatta() else permesso.launch(Manifest.permission.CAMERA)
    }

    // A ogni nuovo esito si riparte da tutto selezionato coi nomi proposti:
    // togliere e correggere è più veloce che riscrivere.
    LaunchedEffect(stato) {
        (stato as? AppViewModel.FotoState.Pronto)?.risultati?.forEach { g ->
            g.esito.alimenti.forEachIndexed { i, a ->
                val k = "${g.gruppo}-$i"
                if (k !in scelti) {
                    scelti[k] = true
                    nomi[k] = a.nome
                    grammi[k] = a.grammi.roundToInt().toString()
                }
            }
        }
    }

    val pronto = stato as? AppViewModel.FotoState.Pronto
    val selezionati = pronto?.risultati.orEmpty().sumOf { g ->
        g.esito.alimenti.indices.count { scelti["${g.gruppo}-$it"] == true }
    }

    AlertDialog(
        onDismissRequest = { vm.chiudiFoto(); onDismiss() },
        title = {
            Text(
                when (stato) {
                    is AppViewModel.FotoState.Pronto -> "Cosa ho riconosciuto"
                    else -> "Foto · ${moment.label}"
                }
            )
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 470.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (val s = stato) {
                    is AppViewModel.FotoState.Raccolta -> {
                        if (!settings.aiAttiva) {
                            Text(
                                "Questa funzione ha bisogno di una tua chiave: la metti una volta " +
                                    "in Impostazioni → Riconoscimento foto. Google Gemini ha un " +
                                    "piano gratuito. Senza chiave, tutto il resto dell'app " +
                                    "continua a funzionare normalmente.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            return@Column
                        }

                        Text(
                            "Fotografa il piatto. Puoi aggiungere più scatti dello stesso " +
                                "alimento — per esempio la confezione e la sua tabella " +
                                "nutrizionale — oppure di alimenti diversi.",
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { scattaConPermesso() },
                                modifier = Modifier.weight(1f),
                            ) { Text("Scatta") }
                            OutlinedButton(
                                onClick = {
                                    galleria.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("Galleria") }
                        }

                        if (scatti.isNotEmpty()) {
                            HorizontalDivider()
                            Text(
                                "Le foto mostrano…",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = unico,
                                    onClick = { vm.impostaUnicoAlimento(true) },
                                    label = { Text("Un solo alimento") },
                                )
                                FilterChip(
                                    selected = !unico,
                                    onClick = { vm.impostaUnicoAlimento(false) },
                                    label = { Text("Alimenti diversi") },
                                )
                            }
                            if (!unico) {
                                Text(
                                    "Tocca il numero sotto una foto per spostarla su un altro " +
                                        "alimento: due foto con lo stesso numero vengono lette insieme.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                scatti.forEach { sc ->
                                    Miniatura(
                                        scatto = sc,
                                        mostraGruppo = !unico,
                                        onGruppo = { vm.cambiaGruppo(sc.id) },
                                        onRimuovi = { vm.rimuoviScatto(sc.id) },
                                    )
                                }
                            }
                            Text(
                                "${scatti.size} foto · ~${vm.tokenFoto()} token immagine da inviare",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    is AppViewModel.FotoState.Preparo -> Attesa("Preparo l'immagine…")

                    is AppViewModel.FotoState.Analizzo -> Attesa(
                        if (s.totali > 1) "Sto guardando le foto… (${s.fatti + 1} di ${s.totali})"
                        else "Sto guardando le foto…"
                    )

                    is AppViewModel.FotoState.Fallito -> {
                        Text(
                            s.messaggio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        OutlinedButton(
                            onClick = { vm.tornaAllaRaccolta() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Torna alle foto") }
                    }

                    is AppViewModel.FotoState.Pronto -> {
                        s.risultati.forEach { g ->
                            if (s.risultati.size > 1) {
                                Text(
                                    "Alimento ${g.gruppo}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            g.risposteDate.forEach {
                                Text(
                                    "Hai detto: $it",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            g.esito.alimenti.forEachIndexed { i, a ->
                                val k = "${g.gruppo}-$i"
                                RigaAlimento(
                                    item = a,
                                    selezionato = scelti[k] == true,
                                    nome = nomi[k] ?: a.nome,
                                    grammiTesto = grammi[k].orEmpty(),
                                    onSelezione = { scelti[k] = it },
                                    onNome = { nomi[k] = it },
                                    onGrammi = { grammi[k] = it },
                                )
                            }
                            g.esito.note?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            g.esito.domanda?.let { domanda ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    ),
                                ) {
                                    Column(
                                        Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            domanda,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        OutlinedTextField(
                                            value = risposte[g.gruppo].orEmpty(),
                                            onValueChange = { risposte[g.gruppo] = it },
                                            label = { Text("La tua risposta") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        TextButton(
                                            enabled = !risposte[g.gruppo].isNullOrBlank(),
                                            onClick = {
                                                g.esito.alimenti.indices.forEach {
                                                    scelti.remove("${g.gruppo}-$it")
                                                    nomi.remove("${g.gruppo}-$it")
                                                    grammi.remove("${g.gruppo}-$it")
                                                }
                                                vm.rispondiAlDubbio(g.gruppo, risposte[g.gruppo].orEmpty())
                                                risposte.remove(g.gruppo)
                                            },
                                        ) { Text("Rispondi e ricalcola") }
                                        Text(
                                            "Puoi anche ignorare la domanda e tenere la stima così com'è.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                        TextButton(onClick = { vm.tornaAllaRaccolta() }) {
                            Text("Aggiungi altre foto")
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                pronto != null -> TextButton(
                    enabled = selezionati > 0,
                    onClick = {
                        val finali = pronto.risultati.flatMap { g ->
                            g.esito.alimenti.mapIndexedNotNull { i, a ->
                                val k = "${g.gruppo}-$i"
                                if (scelti[k] != true) null
                                else a.copy(
                                    nome = nomi[k]?.trim()?.takeIf { it.isNotBlank() } ?: a.nome,
                                    grammi = grammi[k]?.replace(',', '.')?.toDoubleOrNull()
                                        ?.takeIf { it > 0 } ?: a.grammi,
                                )
                            }
                        }
                        vm.aggiungiDaFoto(moment, finali)
                        vm.chiudiFoto()
                        onAggiunti()
                    },
                ) { Text("Aggiungi $selezionati") }

                stato is AppViewModel.FotoState.Raccolta && scatti.isNotEmpty() ->
                    TextButton(onClick = { vm.avviaAnalisi() }) { Text("Analizza") }

                else -> Unit
            }
        },
        dismissButton = {
            TextButton(onClick = { vm.chiudiFoto(); onDismiss() }) { Text("Chiudi") }
        },
    )
}

@Composable
private fun Attesa(testo: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(testo, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Miniatura(
    scatto: AppViewModel.Scatto,
    mostraGruppo: Boolean,
    onGruppo: () -> Unit,
    onRimuovi: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            scatto.miniatura?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(88.dp),
                )
            }
        }
        if (mostraGruppo) {
            TextButton(onClick = onGruppo) { Text("Alimento ${scatto.gruppo}") }
        }
        TextButton(onClick = onRimuovi) {
            Text("Togli", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun RigaAlimento(
    item: AiFoodItem,
    selezionato: Boolean,
    nome: String,
    grammiTesto: String,
    onSelezione: (Boolean) -> Unit,
    onNome: (String) -> Unit,
    onGrammi: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = selezionato, onCheckedChange = onSelezione)
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onSelezione(!selezionato) },
                ) {
                    Text(
                        buildString {
                            item.kcal100?.let { append("${it.roundToInt()} kcal") }
                            item.prot100?.let { append(" · ${fmt1(it)} g prot") }
                            append(" /100 g")
                            item.nova?.let { append(" · NOVA $it") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                EtichettaCertezza(item.certezza)
            }
            // Il nome è dell'utente: quello proposto è solo un punto di partenza.
            OutlinedTextField(
                value = nome,
                onValueChange = onNome,
                label = { Text("Nome") },
                singleLine = true,
                enabled = selezionato,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = grammiTesto,
                onValueChange = onGrammi,
                label = { Text("grammi") },
                singleLine = true,
                enabled = selezionato,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** La certezza è dichiarata apposta: una stima a occhio non deve sembrare una misura. */
@Composable
private fun EtichettaCertezza(certezza: String) {
    val (testo, colore) = when (certezza) {
        "alta" -> "sicuro" to ColorOk
        "bassa" -> "incerto" to ColorOver
        else -> "stimato" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(testo, style = MaterialTheme.typography.labelSmall, color = colore)
}

/** Scatto temporaneo nella cache privata dell'app. */
private fun nuovoFileFoto(context: Context): Uri {
    val dir = File(context.cacheDir, "foto").apply { mkdirs() }
    val file = File(dir, "scatto_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
