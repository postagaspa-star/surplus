package it.gaspa.surplus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.gaspa.surplus.data.ExSet
import it.gaspa.surplus.logic.AdvisorEngine
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

val ColorOk = Color(0xFF2E7D32)
val ColorOver = Color(0xFFEF6C00)

fun fmtInt(n: Int): String = AdvisorEngine.grp(n)

fun fmtKcal(v: Double): String = fmtInt(v.roundToInt())

/** Una cifra decimale, virgola italiana, niente ",0" inutile. */
fun fmt1(v: Double): String {
    val r = kotlin.math.round(v * 10.0) / 10.0
    return if (r == floor(r)) r.toLong().toString()
    else r.toString().replace('.', ',')
}

fun fmtLiters(ml: Int): String = AdvisorEngine.lt(ml / 1000.0)

private val fullDate = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)
private val shortDate = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)

fun dateLabel(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) {
        today -> "Oggi"
        today.minusDays(1) -> "Ieri"
        else -> fullDate.format(d).replaceFirstChar { it.uppercase() }
    }
}

fun dateLabelShort(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) {
        today -> "Oggi"
        today.minusDays(1) -> "Ieri"
        else -> shortDate.format(d).replaceFirstChar { it.uppercase() }
    }
}

fun dateLabelShortIso(iso: String): String =
    runCatching { dateLabelShort(LocalDate.parse(iso)) }.getOrDefault(iso)

/** Serie compatta: "10 kg×8", oppure "corpo×12" per esercizi a corpo libero. */
fun fmtSet(s: ExSet): String =
    if (s.weightKg > 0) "${fmt1(s.weightKg)} kg×${s.reps}" else "corpo×${s.reps}"

/** Ora locale "HH:mm" da epoch millis. */
fun fmtClock(epochMs: Long): String {
    val t = java.time.Instant.ofEpochMilli(epochMs).atZone(java.time.ZoneId.systemDefault())
    return String.format(Locale.ROOT, "%02d:%02d", t.hour, t.minute)
}

/** Barra di avanzamento verso un range min–max (kcal o proteine). */
@Composable
fun TargetBar(
    label: String,
    value: Double,
    min: Int,
    max: Int,
    unit: String,
    modifier: Modifier = Modifier,
) {
    val fraction = (value / max.toDouble()).toFloat().coerceIn(0f, 1f)
    val inRange = value >= min && value <= max
    val over = value > max + max * 0.08
    val barColor = when {
        over -> ColorOver
        inRange -> ColorOk
        else -> MaterialTheme.colorScheme.primary
    }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                "${fmtKcal(value)} / ${fmtInt(min)}–${fmtInt(max)} $unit" + if (inRange) "  ✓" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = if (inRange) ColorOk else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { fraction },
            color = barColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp)),
        )
    }
}
