package it.gaspa.surplus

import it.gaspa.surplus.data.Entry
import it.gaspa.surplus.data.FoodMath
import it.gaspa.surplus.data.Moment
import it.gaspa.surplus.data.computeTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FoodMathTest {

    @Test
    fun `pasta 130 g a 350 kcal per 100 - 455 kcal`() {
        assertEquals(455.0, FoodMath.forGrams(130.0, 350.0)!!, 0.001)
    }

    @Test
    fun `valori nulli restano nulli`() {
        assertNull(FoodMath.forGrams(130.0, null))
    }

    @Test
    fun `arrotondamento a una cifra decimale`() {
        assertEquals(1.2, FoodMath.forGrams(150.0, 0.8)!!, 0.001)
    }

    @Test
    fun `totali giornata - kcal sommate e voci sconosciute contate`() {
        fun e(moment: Moment, kcal: Double?, prot: Double?) = Entry(
            id = kcal.toString() + prot.toString() + moment.name,
            date = "2026-07-30",
            moment = moment,
            nome = "test",
            grams = 100.0,
            kcal = kcal,
            prot = prot,
        )

        val totals = computeTotals(
            listOf(
                e(Moment.COLAZIONE, 500.0, 20.0),
                e(Moment.PRANZO, 900.0, 40.0),
                e(Moment.PRANZO, null, null),
                e(Moment.BEVANDE, 130.0, 6.6),
            )
        )
        assertEquals(1530.0, totals.kcal, 0.001)
        assertEquals(66.6, totals.prot, 0.001)
        assertEquals(1, totals.unknownCount)
        assertEquals(2, totals.countByMoment[Moment.PRANZO])
    }
}
