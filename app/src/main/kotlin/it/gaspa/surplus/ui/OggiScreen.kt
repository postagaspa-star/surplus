package it.gaspa.surplus.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.gaspa.surplus.R
import it.gaspa.surplus.data.Entry
import it.gaspa.surplus.data.Food
import it.gaspa.surplus.data.Moment
import it.gaspa.surplus.data.Settings
import it.gaspa.surplus.data.DayTotals
import it.gaspa.surplus.data.computeTotals
import it.gaspa.surplus.logic.Advice
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun OggiScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val data by vm.data.collectAsState()
    val settings by vm.settings.collectAsState()
    val advice by vm.advice.collectAsState()
    val selectedDate by vm.selectedDate.collectAsState()
    val isToday = selectedDate == LocalDate.now()
    val dayEntries = data.entries
        .filter { it.date == selectedDate.toString() }
        .sortedBy { it.createdAt }
    val totals = computeTotals(dayEntries)
    val water = data.waterByDate[selectedDate.toString()] ?: 0
    var addFor by remember { mutableStateOf<Moment?>(null) }
    var fotoFor by remember { mutableStateOf<Moment?>(null) }
    var schedaFor by remember { mutableStateOf<Pair<Food, Double>?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            DateNav(
                date = selectedDate,
                isToday = isToday,
                onPrev = { vm.shiftDay(-1) },
                onNext = { vm.shiftDay(1) },
                onToday = { vm.goToday() },
            )
        }
        item { TotalsCard(totals, settings) }
        if (isToday && advice.isNotEmpty()) {
            item { AdviceCard(advice) }
        }
        item {
            WaterCard(
                waterMl = water,
                targetMl = settings.waterTargetMl,
                daBevande = dayEntries.filter { it.isDrink }.sumOf { it.grams }.roundToInt(),
                onAdd = { vm.addWater(it) },
            )
        }
        items(Moment.entries.toList()) { m ->
            MomentCard(
                moment = m,
                entries = dayEntries.filter { it.moment == m },
                onAdd = { addFor = m },
                onFoto = { fotoFor = m },
                onDelete = { vm.deleteEntry(it) },
            )
        }
    }

    addFor?.let { m ->
        AddEntryDialog(
            vm = vm,
            initialMoment = m,
            foods = vm.allFoods(data),
            recents = vm.recentFoods(data),
            onConfirm = { e ->
                val f = e.food
                vm.addEntry(
                    e.moment, f.nome, e.grams, f.kcal100, f.prot100,
                    e.saveFood, f.isDrink, f.fibra100, f.nova, f.gruppo, f.barcode,
                )
                addFor = null
                vm.clearOnlineSearch()
                // Scheda del prodotto: 5 secondi, saltabile, disattivabile.
                if (settings.mostraScheda) schedaFor = f to e.grams
            },
            onFoto = { fotoFor = m; addFor = null; vm.clearOnlineSearch() },
            onDismiss = { addFor = null; vm.clearOnlineSearch(); vm.clearScan() },
        )
    }

    fotoFor?.let { m ->
        FotoAnalisiDialog(
            vm = vm,
            moment = m,
            onDismiss = { fotoFor = null },
            onAggiunti = { fotoFor = null },
        )
    }

    schedaFor?.let { (food, grams) ->
        SchedaProdottoDialog(
            food = food,
            grams = grams,
            mostraTutorial = !settings.schedaTutorialVisto,
            onTutorialVisto = { vm.markSchedaTutorialVisto() },
            onDismiss = { schedaFor = null },
        )
    }
}

@Composable
private fun DateNav(
    date: LocalDate,
    isToday: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Giorno precedente") }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(enabled = !isToday) { onToday() },
        ) {
            Text(dateLabel(date), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (!isToday) {
                Text(
                    "tocca per tornare a oggi",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onNext, enabled = !isToday) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Giorno successivo") }
    }
}

@Composable
private fun TotalsCard(totals: DayTotals, settings: Settings) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TargetBar("Calorie", totals.kcal, settings.kcalMin, settings.kcalMax, "kcal")
            TargetBar("Proteine", totals.prot, settings.protMin, settings.protMax, "g")
        }
    }
}

@Composable
private fun AdviceCard(advice: List<Advice>) {
    val shown = advice // tutti, già ordinati per priorità
    var explain by remember { mutableStateOf<Advice?>(null) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(vertical = 10.dp)) {
            Text(
                "Consigli",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
            shown.forEachIndexed { i, a ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { explain = a }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("• ${a.text}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Default.Info, "Perché questo consiglio",
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    explain?.let { a ->
        AlertDialog(
            onDismissRequest = { explain = null },
            title = { Text("Perché questo consiglio") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(a.text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        a.why.ifBlank { "Consiglio generato dalle regole del piano." },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { explain = null }) { Text("Ok") } },
        )
    }
}

@Composable
private fun WaterCard(waterMl: Int, targetMl: Int, daBevande: Int, onAdd: (Int) -> Unit) {
    val done = waterMl >= targetMl
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Acqua", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "${fmtLiters(waterMl)} / ${fmtLiters(targetMl)} L" + if (done) "  ✓" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (done) ColorOk else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { (waterMl.toFloat() / targetMl).coerceIn(0f, 1f) },
                color = Color(0xFF0277BD),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onAdd(250) }) { Text("+250 ml") }
                OutlinedButton(onClick = { onAdd(500) }) { Text("+500 ml") }
                TextButton(onClick = { onAdd(-250) }, enabled = waterMl > 0) { Text("−250") }
            }
            Text(
                if (daBevande > 0)
                    "Di cui ${fmtInt(daBevande)} ml da bevande registrate nel diario: contano tutte come liquidi. I tasti servono per l'acqua pura."
                else
                    "Le bevande che registri nel diario si aggiungono qui da sole: usa i tasti solo per l'acqua pura.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MomentCard(
    moment: Moment,
    entries: List<Entry>,
    onAdd: () -> Unit,
    onFoto: () -> Unit,
    onDelete: (String) -> Unit,
) {
    val kcal = entries.mapNotNull { it.kcal }.sum()
    val prot = entries.mapNotNull { it.prot }.sum()
    val hasUnknown = entries.any { it.kcal == null }
    Card {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(moment.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (entries.isNotEmpty()) {
                    Text(
                        buildString {
                            append("${fmtKcal(kcal)} kcal")
                            if (prot > 0) append(" · ${fmt1(prot)} g prot")
                            if (hasUnknown) append(" +?")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            entries.forEach { e -> EntryRow(e, onDelete) }
            if (entries.isEmpty()) {
                Text(
                    if (moment == Moment.BEVANDE) "Nessuna bevanda registrata" else "Vuoto",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            // Due strade visibili fin da subito: scrivere o fotografare.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Aggiungi")
                }
                TextButton(onClick = onFoto) {
                    Icon(painterResource(R.drawable.ic_camera), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Foto")
                }
            }
        }
    }
}

@Composable
private fun EntryRow(e: Entry, onDelete: (String) -> Unit) {
    val unit = if (e.isDrink) "ml" else "g"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(e.nome, style = MaterialTheme.typography.bodyMedium)
            val values = if (e.kcal != null) {
                "≈ ${fmtKcal(e.kcal)} kcal" + (e.prot?.let { ", ${fmt1(it)} g prot" } ?: "")
            } else "valori non noti (?)"
            val time = if (e.createdAt > 0) "${fmtClock(e.createdAt)} · " else ""
            Text(
                "$time${fmt1(e.grams)} $unit · $values",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onDelete(e.id) }) {
            Icon(
                Icons.Default.Close, "Elimina",
                Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
