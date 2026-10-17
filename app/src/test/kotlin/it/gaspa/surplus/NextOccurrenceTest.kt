package it.gaspa.surplus

import it.gaspa.surplus.logic.NextOccurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class NextOccurrenceTest {

    // 2026-07-29 è un mercoledì.
    private val wednesday10 = LocalDateTime.of(2026, 7, 29, 10, 0)
    private val allDays = 0b1111111

    @Test
    fun `stesso giorno se l'orario deve ancora arrivare`() {
        val next = NextOccurrence.compute(wednesday10, 10, 30, allDays)
        assertEquals(LocalDateTime.of(2026, 7, 29, 10, 30), next)
    }

    @Test
    fun `giorno dopo se l'orario e gia passato`() {
        val next = NextOccurrence.compute(wednesday10, 9, 0, allDays)
        assertEquals(LocalDateTime.of(2026, 7, 30, 9, 0), next)
    }

    @Test
    fun `orario identico ad adesso - va a domani`() {
        val next = NextOccurrence.compute(wednesday10, 10, 0, allDays)
        assertEquals(LocalDateTime.of(2026, 7, 30, 10, 0), next)
    }

    @Test
    fun `solo domenica - salta alla prossima domenica`() {
        val onlySunday = 1 shl (DayOfWeek.SUNDAY.value - 1)
        val next = NextOccurrence.compute(wednesday10, 17, 0, onlySunday)
        assertEquals(LocalDateTime.of(2026, 8, 2, 17, 0), next)
        assertEquals(DayOfWeek.SUNDAY, next!!.dayOfWeek)
    }

    @Test
    fun `nessun giorno attivo - null`() {
        assertNull(NextOccurrence.compute(wednesday10, 10, 30, 0))
    }
}
