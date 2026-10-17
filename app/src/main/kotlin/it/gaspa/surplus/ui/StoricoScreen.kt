package it.gaspa.surplus.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import it.gaspa.surplus.data.AppData
import it.gaspa.surplus.data.Entry
import it.gaspa.surplus.data.Moment
import it.gaspa.surplus.data.Settings
import it.gaspa.surplus.data.computeTotals
import it.gaspa.surplus.logic.DayStatus
import it.gaspa.surplus.logic.DietQuality
import it.gaspa.surplus.logic.QualityStatus
import it.gaspa.surplus.logic.WeightRule
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun StoricoScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val data by vm.data.collectAsState()
    val settings by vm.settings.collectAsState()
    val today = LocalDate.now()
    // Tutti i giorni in cui c'è qualcosa di registrato, non una finestra fissa.
    val days = remember(data) {
        val conDati = buildSet {
            data.entries.forEach { add(it.date) }
            data.waterByDate.forEach { (d, ml) -> if (ml > 0) add(d) }
            data.weights.forEach { add(it.date) }
            data.workouts.forEach { w -> if (w.exercises.any { it.sets.isNotEmpty() }) add(w.date) }
        }
        (conDati.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() } + today)
            .filterNot { it.isAfter(today) }
            .distinct()
            .sortedDescending()
    }
    val week = (0..6)
        .map { vm.dayStatus(data, today.minusDays(it.toLong())) }
        .filter { it.kcal > 0 }
    // Media su tutta la storia: dice se il piano regge nel tempo, non solo questa settimana.
    val tuttaLaStoria = remember(data, days) {
        days.map { vm.dayStatus(data, it) }.filter { it.kcal > 0 }
    }
    var kgText by remember { mutableStateOf("") }
    var detailFor by remember { mutableStateOf<LocalDate?>(null) }
    val weightAdvice = WeightRule.check(data.weights, today)
    val weekQuality = DietQuality.average(
        (0..6).map { d ->
            val iso = today.minusDays(d.toLong()).toString()
            DietQuality.of(data.entries.filter { it.date == iso })
        }
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Media ultimi 7 giorni", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (week.isEmpty()) {
                        Text("Ancora nessun giorno registrato questa settimana.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        val avgK = week.map { it.kcal }.average()
                        val avgP = week.map { it.prot }.average()
                        TargetBar("Calorie (su ${week.size} gg)", avgK, settings.kcalMin, settings.kcalMax, "kcal")
                        TargetBar("Proteine", avgP, settings.protMin, settings.protMax, "g")
                        Text(
                            "Il piano ragiona sulla media della settimana, non sul singolo giorno.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Media complessiva",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (tuttaLaStoria.isEmpty()) {
                        Text(
                            "Ancora nessun giorno registrato.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val n = tuttaLaStoria.size
                        TargetBar(
                            "Calorie (su $n ${if (n == 1) "giorno" else "giorni"})",
                            tuttaLaStoria.map { it.kcal }.average(),
                            settings.kcalMin, settings.kcalMax, "kcal",
                        )
                        TargetBar(
                            "Proteine",
                            tuttaLaStoria.map { it.prot }.average(),
                            settings.protMin, settings.protMax, "g",
                        )
                        Text(
                            "Tutti i giorni con qualcosa di registrato, dal primo a oggi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item { QualitaCard(weekQuality) }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Peso", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (weightAdvice != null) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                            Text(weightAdvice, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = kgText,
                            onValueChange = { kgText = it },
                            label = { Text("kg") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(120.dp),
                        )
                        Button(
                            enabled = kgText.replace(',', '.').toDoubleOrNull() != null,
                            onClick = {
                                kgText.replace(',', '.').toDoubleOrNull()?.let { vm.setWeight(it) }
                                kgText = ""
                            },
                        ) { Text("Registra oggi") }
                    }
                    val weights = data.weights.sortedByDescending { it.date }.take(10)
                    if (weights.isEmpty()) {
                        Text(
                            "Pesati una volta a settimana, appena sveglio: serve alla regola delle 3-4 settimane del piano.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    weights.forEach { w ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                runCatching { dateLabelShort(LocalDate.parse(w.date)) }.getOrDefault(w.date),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${fmt1(w.kg)} kg", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                IconButton(onClick = { vm.removeWeight(w.date) }) {
                                    Icon(
                                        Icons.Default.Close, "Elimina",
                                        Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Text(
                if (days.size <= 1) "Giorni registrati"
                else "Giorni registrati (${days.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        items(days) { d ->
            DayRow(d, vm.dayStatus(data, d), settings, trainLabelFor(data, d), onClick = { detailFor = d })
        }
    }

    detailFor?.let { d ->
        DayDetailDialog(
            date = d,
            entries = data.entries.filter { it.date == d.toString() },
            waterMl = data.waterByDate[d.toString()] ?: 0,
            weightKg = data.weights.find { it.date == d.toString() }?.kg,
            trainLabel = trainLabelFor(data, d),
            onDismiss = { detailFor = null },
        )
    }
}

/**
 * Qualità del cibo della settimana: tre segnali positivi, nessun punteggio,
 * nessun cibo bollato come cattivo. Dove manca il dato, l'app lo dice.
 */
@Composable
private fun QualitaCard(q: QualityStatus?) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Qualità della settimana",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (q == null) {
                Text(
                    "Ancora nessun giorno registrato.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            QualitaRiga(
                "Frutta e verdura",
                "${fmt1(q.porzioniFruttaVerdura)} porzioni al giorno",
                "obiettivo 3-5",
                ok = q.porzioniFruttaVerdura >= 3,
            )
            QualitaRiga(
                "Fibra",
                "${fmt1(q.fibraG)} g al giorno",
                "obiettivo ${DietQuality.FIBRA_TARGET.toInt()} g",
                ok = q.fibraG >= DietQuality.FIBRA_TARGET,
            )
            val quota = q.quotaUltraProcessati
            if (quota != null) {
                QualitaRiga(
                    "Da cibo poco lavorato",
                    "${((1 - quota) * 100).roundToInt()}% delle calorie",
                    "più è alto, meglio è",
                    ok = quota <= 0.35,
                )
            } else {
                Text(
                    "Grado di lavorazione: non abbastanza voci con questo dato. Compare quando usi la ricerca online o il codice a barre.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Nessun alimento è «cattivo»: questi sono suggerimenti su cosa aggiungere, non voti.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QualitaRiga(titolo: String, valore: String, nota: String, ok: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titolo, style = MaterialTheme.typography.bodyMedium)
            Text(
                nota,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            valore + if (ok) "  ✓" else "",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (ok) ColorOk else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun trainLabelFor(data: AppData, d: LocalDate): String? = data.workouts
    .filter { it.date == d.toString() && it.exercises.any { e -> e.sets.isNotEmpty() } }
    .map { it.etichetta }
    .distinct()
    .joinToString("+")
    .ifEmpty { null }

/** Dettaglio completo della giornata: ogni voce con dosi e valori, per momento. */
@Composable
private fun DayDetailDialog(
    date: LocalDate,
    entries: List<Entry>,
    waterMl: Int,
    weightKg: Double?,
    trainLabel: String?,
    onDismiss: () -> Unit,
) {
    val totals = computeTotals(entries)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dateLabel(date)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (entries.isEmpty()) {
                    Text("Nessun cibo registrato in questa giornata.", style = MaterialTheme.typography.bodyMedium)
                }
                Moment.entries.forEach { m ->
                    val list = entries.filter { it.moment == m }.sortedBy { it.createdAt }
                    if (list.isNotEmpty()) {
                        val mKcal = list.mapNotNull { it.kcal }.sum()
                        Text(
                            m.label + if (mKcal > 0) " — ${fmtKcal(mKcal)} kcal" else "",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        list.forEach { e ->
                            val unit = if (e.isDrink) "ml" else "g"
                            val vals = if (e.kcal != null) {
                                "≈ ${fmtKcal(e.kcal)} kcal" + (e.prot?.let { ", ${fmt1(it)} g prot" } ?: "")
                            } else "valori non noti"
                            val time = if (e.createdAt > 0) "${fmtClock(e.createdAt)} · " else ""
                            Text(
                                "$time${e.nome} — ${fmt1(e.grams)} $unit · $vals",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(
                    "Totale: ${fmtKcal(totals.kcal)} kcal · ${fmt1(totals.prot)} g proteine" +
                        if (totals.unknownCount > 0) " (+${totals.unknownCount} voci senza valori)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text("Acqua: ${fmtLiters(waterMl)} L", style = MaterialTheme.typography.bodySmall)
                weightKg?.let { Text("Peso registrato: ${fmt1(it)} kg", style = MaterialTheme.typography.bodySmall) }
                trainLabel?.let { Text("Allenamento: $it", style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
    )
}

@Composable
private fun DayRow(d: LocalDate, s: DayStatus, settings: Settings, trainLabel: String?, onClick: () -> Unit) {
    val hasData = s.kcal > 0 || s.unknownCount > 0
    val kcalColor = when {
        !hasData -> MaterialTheme.colorScheme.onSurfaceVariant
        s.kcal >= settings.kcalMin && s.kcal <= settings.kcalMax -> ColorOk
        s.kcal > settings.kcalMax -> ColorOver
        else -> MaterialTheme.colorScheme.onSurface
    }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(dateLabelShort(d), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (!hasData) {
                Text(
                    trainLabel?.let { "🏋 $it" } ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${fmtKcal(s.kcal)} kcal" + if (s.unknownCount > 0) " +?" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = kcalColor,
                    )
                    Text(
                        "${s.prot.roundToInt()} g prot · ${fmtLiters(s.waterMl)} L" +
                            (trainLabel?.let { " · 🏋 $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
