package it.gaspa.surplus.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.gaspa.surplus.data.AiProvider
import it.gaspa.surplus.data.ReminderCfg
import it.gaspa.surplus.data.ReminderKey
import it.gaspa.surplus.data.Settings
import it.gaspa.surplus.data.SyncState
import java.util.Locale

private val dayLetters = listOf("L", "M", "M", "G", "V", "S", "D")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImpostazioniScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val profile by vm.profile.collectAsState()

    var kcalMin by remember(settings) { mutableStateOf(settings.kcalMin.toString()) }
    var kcalMax by remember(settings) { mutableStateOf(settings.kcalMax.toString()) }
    var protMin by remember(settings) { mutableStateOf(settings.protMin.toString()) }
    var protMax by remember(settings) { mutableStateOf(settings.protMax.toString()) }
    var waterMl by remember(settings) { mutableStateOf(settings.waterTargetMl.toString()) }
    var reminders by remember(settings) { mutableStateOf(settings.reminders) }
    var postDynamic by remember(settings) { mutableStateOf(settings.postWorkoutDynamic) }
    var postDelay by remember(settings) { mutableStateOf(settings.postWorkoutDelayMin.toString()) }
    var mostraScheda by remember(settings) { mutableStateOf(settings.mostraScheda) }
    var aiProvider by remember(settings) { mutableStateOf(settings.aiProvider) }
    var aiKey by remember(settings) { mutableStateOf(settings.aiApiKey) }
    var aiModel by remember(settings) { mutableStateOf(settings.aiModel) }
    var chiaveVisibile by remember { mutableStateOf(false) }
    var pickerFor by remember { mutableStateOf<ReminderKey?>(null) }
    var nasIndirizzo by remember(settings) { mutableStateOf(settings.nasIndirizzo) }
    var nasCodice by remember { mutableStateOf("") }
    var nasStacca by remember { mutableStateOf(false) }
    val statoNas by vm.statoNas.collectAsState()
    val nasInCorso by vm.nasInCorso.collectAsState()

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // Backup: Storage Access Framework, così il file finisce dove vuole l'utente
    // (Drive, Download, chiavetta) senza permessi extra né FileProvider.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportJson().toByteArray()) }
            }.onSuccess {
                Toast.makeText(context, "Backup salvato", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "Backup non riuscito", Toast.LENGTH_LONG).show()
            }
        }
    }
    var importUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) importUri = uri }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Target giornalieri", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(kcalMin, { kcalMin = it }, "kcal min", Modifier.weight(1f))
            NumField(kcalMax, { kcalMax = it }, "kcal max", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(protMin, { protMin = it }, "proteine min (g)", Modifier.weight(1f))
            NumField(protMax, { protMax = it }, "proteine max (g)", Modifier.weight(1f))
        }
        NumField(waterMl, { waterMl = it }, "acqua al giorno (ml)", Modifier.fillMaxWidth())

        HorizontalDivider()

        Text("Promemoria spuntini", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Solo spuntini e merende: colazione, pranzo e cena non hanno mai promemoria. Orario e giorni li scegli tu.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        reminders.forEach { r ->
            val isPost = r.key == ReminderKey.POST_ALLENAMENTO
            ReminderRow(
                cfg = r,
                onToggle = { on ->
                    reminders = reminders.map { if (it.key == r.key) it.copy(enabled = on) else it }
                    if (on && Build.VERSION.SDK_INT >= 33) {
                        permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onPickTime = { pickerFor = r.key },
                onToggleDay = { bit ->
                    reminders = reminders.map {
                        if (it.key == r.key) it.copy(daysMask = it.daysMask xor (1 shl bit)) else it
                    }
                },
                showTimeAndDays = !(isPost && postDynamic),
                extra = if (isPost) {
                    {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !postDynamic,
                                onClick = { postDynamic = false },
                                label = { Text("Orario fisso") },
                            )
                            FilterChip(
                                selected = postDynamic,
                                onClick = { postDynamic = true },
                                label = { Text("Dopo l'ultima serie") },
                            )
                        }
                        if (postDynamic) {
                            NumField(postDelay, { postDelay = it }, "minuti dopo l'ultima serie", Modifier.fillMaxWidth())
                            Text(
                                "Suona N minuti dopo l'ultima serie registrata, solo per sessioni di oggi. " +
                                    "Nei giorni senza allenamento non suona.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else null,
            )
        }

        val am = context.getSystemService(AlarmManager::class.java)
        val needExactPerm = Build.VERSION.SDK_INT >= 31 && am != null &&
            !am.canScheduleExactAlarms() && reminders.any { it.enabled }
        if (needExactPerm) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Per promemoria precisi al minuto attiva «Sveglie e promemoria» per Surplus.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(
                                android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:${context.packageName}"),
                            )
                        )
                    }) { Text("Apri impostazioni di sistema") }
                }
            }
        }

        HorizontalDivider()

        Text("Diario sul NAS di casa", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (!settings.nasAbbinato) {
            Text(
                "Il diario può stare anche sul NAS: così il telefono e il computer vedono le stesse cose, " +
                    "e quello che scrivi non vive su un solo apparecchio. Niente account e niente internet: " +
                    "i dati restano in casa.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = nasIndirizzo,
                onValueChange = { nasIndirizzo = it },
                label = { Text("Indirizzo del NAS") },
                placeholder = { Text("nome-del-nas:8600") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = nasCodice,
                onValueChange = { nasCodice = it.filter { c -> c.isDigit() }.take(8) },
                label = { Text("Codice del NAS") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Il codice lo dà il NAS e vale un quarto d'ora.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = {
                    vm.abbinaNas(nasIndirizzo, nasCodice) { errore ->
                        Toast.makeText(context, errore ?: "Telefono abbinato al NAS", Toast.LENGTH_LONG).show()
                        if (errore == null) nasCodice = ""
                    }
                },
                enabled = !nasInCorso && nasCodice.length == 8,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (nasInCorso) "Sto abbinando…" else "Abbina questo telefono") }
        } else {
            Text(
                "Abbinato a ${settings.nasIndirizzo}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                when (val st = statoNas) {
                    is SyncState.Error -> st.message
                    else -> if (settings.nasUltima > 0) "Ultima sincronizzazione " + oraDi(settings.nasUltima) + "."
                    else "Non si è ancora sincronizzato."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (statoNas is SyncState.Error) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Si sincronizza da solo: quello che scrivi sul computer compare qui in un paio di secondi, " +
                    "e viceversa. Quello che scrivi senza NAS acceso parte appena torna.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        vm.sincronizzaNas { errore ->
                            Toast.makeText(context, errore ?: "Sincronizzato col NAS", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !nasInCorso,
                    modifier = Modifier.weight(1f),
                ) { Text(if (nasInCorso) "Attendi…" else "Sincronizza adesso") }
                TextButton(
                    onClick = { nasStacca = true },
                    modifier = Modifier.weight(1f),
                ) { Text("Stacca") }
            }
        }

        HorizontalDivider()

        Text("Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            if (settings.nasAbbinato)
                "Il diario sta su questo telefono e sul NAS di casa. Il file del backup contiene " +
                    "diario, allenamenti, peso e dispensa, ed è lo stesso del programma per il computer."
            else
                "I dati vivono solo su questo telefono: senza backup, se lo perdi perdi tutto. " +
                    "Il file contiene diario, allenamenti, peso e dispensa.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val d = java.time.LocalDate.now()
                    exportLauncher.launch("surplus-backup-$d.json")
                },
                modifier = Modifier.weight(1f),
            ) { Text("Esporta") }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                modifier = Modifier.weight(1f),
            ) { Text("Ripristina") }
        }

        HorizontalDivider()

        Text("Profilo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        val p = profile
        Text(
            if (p.isConfigured && p.altezzaCm > 0)
                "${p.nome.ifBlank { "Utente" }} · ${p.altezzaCm} cm · ${fmt1(p.pesoKg)} kg · obiettivo ${p.obiettivo.label.lowercase()}"
            else "Profilo non compilato: i target sono quelli di default.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { vm.saveProfile(p.copy(isConfigured = false)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Rifai il calcolo dei target") }

        DisclaimerBox(minorenne = profile.isMinor, modifier = Modifier.fillMaxWidth())

        HorizontalDivider()

        HorizontalDivider()

        Text(
            "Riconoscimento foto",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Facoltativo. Con una tua chiave puoi fotografare un piatto o un'etichetta e farti " +
                "proporre gli alimenti. L'app non contiene nessuna chiave: paghi solo il tuo uso, " +
                "e chi scarica l'app senza chiave semplicemente non vede questa funzione.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiProvider.entries.forEach { p ->
                FilterChip(
                    selected = aiProvider == p,
                    onClick = { aiProvider = p },
                    label = { Text(p.label) },
                )
            }
        }
        Text(
            "Dove prenderla: ${aiProvider.comeOttenerla}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = aiKey,
            onValueChange = { aiKey = it.trim() },
            label = { Text("La tua chiave") },
            singleLine = true,
            visualTransformation =
                if (chiaveVisibile) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { chiaveVisibile = !chiaveVisibile }) {
                    Text(if (chiaveVisibile) "Nascondi" else "Mostra")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = aiModel,
            onValueChange = { aiModel = it.trim() },
            label = { Text("Modello (vuoto = ${aiProvider.modelDefault})") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "La chiave resta nella memoria privata di questo telefono e viene usata solo quando " +
                "lo chiedi tu: per le foto che scegli e per compilare un esercizio dal nome, se " +
                "l'app non lo riconosce da sola. Non esce nient'altro del diario. Il campo modello " +
                "serve se un domani il nome cambia, così non devi aspettare un aggiornamento dell'app.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()

        Card {
            Row(
                Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Scheda del prodotto",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Dopo ogni inserimento mostra per 5 secondi i valori dell'alimento. " +
                            "Tocca la scheda per mettere in pausa.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = mostraScheda, onCheckedChange = { mostraScheda = it })
            }
        }

        Button(
            onClick = {
                val s = Settings(
                    kcalMin = kcalMin.toIntOrNull() ?: settings.kcalMin,
                    kcalMax = kcalMax.toIntOrNull() ?: settings.kcalMax,
                    protMin = protMin.toIntOrNull() ?: settings.protMin,
                    protMax = protMax.toIntOrNull() ?: settings.protMax,
                    waterTargetMl = waterMl.toIntOrNull() ?: settings.waterTargetMl,
                    reminders = reminders,
                    postWorkoutDynamic = postDynamic,
                    postWorkoutDelayMin = postDelay.toIntOrNull()?.coerceIn(5, 180)
                        ?: settings.postWorkoutDelayMin,
                    mostraScheda = mostraScheda,
                    // Da non azzerare qui: la spiegazione della scheda si mostra una volta sola.
                    schedaTutorialVisto = settings.schedaTutorialVisto,
                    aiProvider = aiProvider,
                    aiApiKey = aiKey,
                    aiModel = aiModel,
                )
                vm.saveSettings(s)
                Toast.makeText(context, "Impostazioni salvate", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Salva impostazioni") }

        Text(
            "Surplus v" + versioneApp(context) +
                if (settings.nasAbbinato) " — diario su questo telefono e sul NAS"
                else " — dati salvati solo sul telefono",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (nasStacca) {
        AlertDialog(
            onDismissRequest = { nasStacca = false },
            title = { Text("Staccare questo telefono?") },
            text = {
                Text(
                    "Il diario resta sul telefono e resta anche sul NAS, ma non si aggiornano " +
                        "più a vicenda. Per rimetterli insieme serve un codice nuovo."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.staccaNas()
                    nasStacca = false
                    Toast.makeText(context, "Telefono staccato dal NAS", Toast.LENGTH_SHORT).show()
                }) { Text("Stacca") }
            },
            dismissButton = { TextButton(onClick = { nasStacca = false }) { Text("Annulla") } },
        )
    }

    // Il ripristino sovrascrive tutto: conferma esplicita prima di procedere.
    importUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { importUri = null },
            title = { Text("Ripristinare il backup?") },
            text = {
                Text(
                    "Tutti i dati attuali (diario, allenamenti, peso, dispensa) verranno " +
                        "sostituiti con quelli del file. L'operazione non è annullabile."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val text = runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                    val msg = if (text == null) "File non leggibile"
                    else vm.importJson(text).fold(
                        onSuccess = { "Ripristinati $it elementi" },
                        onFailure = { it.message ?: "File non valido" },
                    )
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    importUri = null
                }) { Text("Ripristina") }
            },
            dismissButton = { TextButton(onClick = { importUri = null }) { Text("Annulla") } },
        )
    }

    pickerFor?.let { k ->
        key(k) {
            val r = reminders.first { it.key == k }
            val state = rememberTimePickerState(initialHour = r.hour, initialMinute = r.minute, is24Hour = true)
            AlertDialog(
                onDismissRequest = { pickerFor = null },
                text = { TimePicker(state) },
                confirmButton = {
                    TextButton(onClick = {
                        reminders = reminders.map {
                            if (it.key == k) it.copy(hour = state.hour, minute = state.minute) else it
                        }
                        pickerFor = null
                    }) { Text("Ok") }
                },
                dismissButton = { TextButton(onClick = { pickerFor = null }) { Text("Annulla") } },
            )
        }
    }
}

@Composable
private fun NumField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun ReminderRow(
    cfg: ReminderCfg,
    onToggle: (Boolean) -> Unit,
    onPickTime: () -> Unit,
    onToggleDay: (Int) -> Unit,
    showTimeAndDays: Boolean = true,
    extra: (@Composable () -> Unit)? = null,
) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(cfg.key.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Switch(checked = cfg.enabled, onCheckedChange = onToggle)
            }
            if (cfg.enabled) {
                extra?.invoke()
                if (showTimeAndDays) {
                    OutlinedButton(onClick = onPickTime) {
                        Text(String.format(Locale.ROOT, "Orario: %02d:%02d", cfg.hour, cfg.minute))
                    }
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        dayLetters.forEachIndexed { i, letter ->
                            FilterChip(
                                selected = cfg.daysMask and (1 shl i) != 0,
                                onClick = { onToggleDay(i) },
                                label = { Text(letter) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "alle 20:31" se è di oggi, altrimenti "il 02/11 alle 20:31". */
private fun oraDi(quando: Long): String {
    val d = java.util.Calendar.getInstance().apply { timeInMillis = quando }
    val oggi = java.util.Calendar.getInstance()
    val ora = String.format(
        Locale.ROOT, "%02d:%02d",
        d.get(java.util.Calendar.HOUR_OF_DAY), d.get(java.util.Calendar.MINUTE),
    )
    val stessoGiorno = d.get(java.util.Calendar.YEAR) == oggi.get(java.util.Calendar.YEAR) &&
        d.get(java.util.Calendar.DAY_OF_YEAR) == oggi.get(java.util.Calendar.DAY_OF_YEAR)
    if (stessoGiorno) return "alle $ora"
    val giorno = String.format(
        Locale.ROOT, "%02d/%02d",
        d.get(java.util.Calendar.DAY_OF_MONTH), d.get(java.util.Calendar.MONTH) + 1,
    )
    return "il $giorno alle $ora"
}

/** La versione vera, chiesta ad Android: scritta a mano finiva per restare indietro. */
private fun versioneApp(context: android.content.Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
}.getOrDefault("")
