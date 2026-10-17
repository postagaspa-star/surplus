package it.gaspa.surplus.logic

import it.gaspa.surplus.data.WeightEntry
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Regola del piano: se dopo 3-4 settimane il peso non si muove,
 * aumenta le porzioni. Guarda le pesate degli ultimi 35 giorni.
 */
object WeightRule {

    fun check(weights: List<WeightEntry>, today: LocalDate): String? {
        val recent = weights
            .mapNotNull { w -> runCatching { LocalDate.parse(w.date) to w.kg }.getOrNull() }
            .filter { (d, _) -> !d.isAfter(today) && d >= today.minusDays(35) }
            .sortedBy { it.first }
        if (recent.size < 2) return null
        val (firstDate, firstKg) = recent.first()
        val (lastDate, lastKg) = recent.last()
        val span = ChronoUnit.DAYS.between(firstDate, lastDate)
        if (span < 21) return null
        val delta = lastKg - firstKg
        if (delta >= 0.4) return null
        val deltaTxt = String.format("%+.1f", delta).replace('.', ',')
        return "Peso fermo da $span giorni ($deltaTxt kg): il piano dice di aumentare le porzioni. Parti da +50 g di pasta o +1 fetta di pane al giorno."
    }
}
