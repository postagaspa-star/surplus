package it.gaspa.surplus.logic

import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Prossima occorrenza di un promemoria dato orario e giorni attivi.
 * daysMask: bit 0 = lunedì … bit 6 = domenica (DayOfWeek.value - 1).
 */
object NextOccurrence {

    fun compute(now: LocalDateTime, hour: Int, minute: Int, daysMask: Int): LocalDateTime? {
        if (daysMask and 0b1111111 == 0) return null
        for (d in 0..7) {
            val date = now.toLocalDate().plusDays(d.toLong())
            val candidate = LocalDateTime.of(date, LocalTime.of(hour, minute))
            val bit = 1 shl (date.dayOfWeek.value - 1)
            if (candidate.isAfter(now) && (daysMask and bit) != 0) return candidate
        }
        return null
    }
}
