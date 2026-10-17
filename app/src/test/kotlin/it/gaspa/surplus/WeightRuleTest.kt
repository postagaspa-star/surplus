package it.gaspa.surplus

import it.gaspa.surplus.data.WeightEntry
import it.gaspa.surplus.logic.WeightRule
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeightRuleTest {

    private val today = LocalDate.of(2026, 7, 30)

    private fun w(daysAgo: Long, kg: Double) =
        WeightEntry(today.minusDays(daysAgo).toString(), kg)

    @Test
    fun `peso fermo da piu di 3 settimane - consiglio di aumentare`() {
        val advice = WeightRule.check(listOf(w(28, 70.0), w(14, 70.2), w(0, 70.1)), today)
        assertNotNull(advice)
        assertTrue(advice!!.contains("aumentare le porzioni"))
    }

    @Test
    fun `peso in salita - nessun avviso`() {
        assertNull(WeightRule.check(listOf(w(28, 70.0), w(0, 71.5)), today))
    }

    @Test
    fun `dati per meno di 3 settimane - nessun avviso`() {
        assertNull(WeightRule.check(listOf(w(10, 70.0), w(0, 70.0)), today))
    }

    @Test
    fun `pesate troppo vecchie escluse dal calcolo`() {
        assertNull(WeightRule.check(listOf(w(60, 65.0), w(3, 70.0)), today))
    }

    @Test
    fun `nessuna pesata - nessun avviso`() {
        assertNull(WeightRule.check(emptyList(), today))
    }
}
