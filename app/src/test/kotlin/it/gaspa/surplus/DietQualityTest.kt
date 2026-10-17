package it.gaspa.surplus

import it.gaspa.surplus.data.Entry
import it.gaspa.surplus.data.FoodGroup
import it.gaspa.surplus.data.Moment
import it.gaspa.surplus.logic.DietQuality
import it.gaspa.surplus.logic.QualityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DietQualityTest {

    private var seq = 0

    private fun e(
        nome: String,
        grams: Double,
        kcal: Double?,
        fibra: Double? = null,
        nova: Int? = null,
        gruppo: FoodGroup = FoodGroup.ALTRO,
    ) = Entry(
        id = "e${seq++}",
        date = "2026-08-01",
        moment = Moment.PRANZO,
        nome = nome,
        grams = grams,
        kcal = kcal,
        prot = null,
        fibra = fibra,
        nova = nova,
        gruppo = gruppo,
    )

    @Test
    fun `porzioni frutta e verdura - 100 g = 1 porzione`() {
        val q = DietQuality.of(
            listOf(
                e("Mela", 180.0, 94.0, gruppo = FoodGroup.FRUTTA),
                e("Insalata", 120.0, 24.0, gruppo = FoodGroup.VERDURA),
                e("Pasta", 130.0, 455.0, gruppo = FoodGroup.CEREALE),
            )
        )
        assertEquals(3.0, q.porzioniFruttaVerdura, 0.001)
    }

    @Test
    fun `frutta secca non conta come porzione di frutta`() {
        val q = DietQuality.of(listOf(e("Mandorle", 300.0, 1800.0, gruppo = FoodGroup.GRASSI)))
        assertEquals(0.0, q.porzioniFruttaVerdura, 0.001)
    }

    @Test
    fun `quota ultra-processati calcolata solo sulle voci con grado noto`() {
        val q = DietQuality.of(
            listOf(
                e("Cereali", 100.0, 400.0, nova = 4),
                e("Pasta", 130.0, 400.0, nova = 1),
                e("Roba senza dato", 100.0, 1000.0), // nova null: fuori dal calcolo
            )
        )
        assertEquals(800.0, q.kcalConGrado, 0.001)
        assertEquals(0.5, q.quotaUltraProcessati!!, 0.001)
        // Copertura bassa: 800 kcal su 1800 totali.
        assertTrue(q.copertura < 0.5)
    }

    @Test
    fun `campione troppo piccolo - nessuna conclusione sul grado`() {
        val q = DietQuality.of(listOf(e("Merendina", 50.0, 200.0, nova = 4)))
        assertTrue(q.kcalConGrado < QualityStatus.MIN_KCAL_GRADO)
        assertNull(q.quotaUltraProcessati)
    }

    @Test
    fun `fibra sommata solo dove il dato esiste`() {
        val q = DietQuality.of(
            listOf(
                e("Pane", 100.0, 268.0, fibra = 3.5),
                e("Verdura", 200.0, 60.0, fibra = 5.0),
                e("Formaggio", 100.0, 370.0), // niente fibra dichiarata
            )
        )
        assertEquals(8.5, q.fibraG, 0.001)
    }

    @Test
    fun `giornata vuota - tutto a zero, nessuna divisione per zero`() {
        val q = DietQuality.of(emptyList())
        assertEquals(0.0, q.kcalTotali, 0.001)
        assertEquals(0.0, q.copertura, 0.001)
        assertNull(q.quotaUltraProcessati)
    }

    @Test
    fun `media settimanale - ignora i giorni senza dati`() {
        val pieno = DietQuality.of(listOf(e("Mela", 200.0, 100.0, fibra = 4.0, gruppo = FoodGroup.FRUTTA)))
        val vuoto = DietQuality.of(emptyList())
        val media = DietQuality.average(listOf(pieno, vuoto, vuoto))!!
        assertEquals(2.0, media.porzioniFruttaVerdura, 0.001)
        assertEquals(4.0, media.fibraG, 0.001)
    }

    @Test
    fun `media di zero giorni utili - null`() {
        assertNull(DietQuality.average(listOf(DietQuality.of(emptyList()))))
    }
}
