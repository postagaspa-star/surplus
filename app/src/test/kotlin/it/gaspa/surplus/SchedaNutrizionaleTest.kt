package it.gaspa.surplus

import it.gaspa.surplus.data.Food
import it.gaspa.surplus.logic.Livello
import it.gaspa.surplus.logic.SchedaNutrizionale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedaNutrizionaleTest {

    private fun food(
        nome: String = "Test",
        kcal: Double? = null,
        prot: Double? = null,
        grassi: Double? = null,
        carb: Double? = null,
        fibra: Double? = null,
        saturi: Double? = null,
        zuccheri: Double? = null,
        sale: Double? = null,
        nova: Int? = null,
        drink: Boolean = false,
    ) = Food(
        id = "t", nome = nome, kcal100 = kcal, prot100 = prot, grassi100 = grassi,
        carb100 = carb, fibra100 = fibra, grassiSaturi100 = saturi, zuccheri100 = zuccheri,
        sale100 = sale, nova = nova, isDrink = drink,
    )

    @Test
    fun `mostra solo le righe di cui esiste il dato`() {
        val righe = SchedaNutrizionale.righe(food(kcal = 356.0, prot = 14.5))
        assertEquals(listOf("Energia", "Proteine"), righe.map { it.etichetta })
    }

    @Test
    fun `alimento senza valori - nessuna riga, nessuna invenzione`() {
        assertTrue(SchedaNutrizionale.righe(food()).isEmpty())
    }

    /** Il metro è la massa: un cibo calorico è un pregio, non un difetto. */
    @Test
    fun `cibo calorico giudicato positivamente`() {
        val riga = SchedaNutrizionale.righe(food(kcal = 600.0)).single()
        assertEquals(Livello.BUONO, riga.livello)
        assertTrue(riga.giudizio.contains("surplus"))
    }

    @Test
    fun `cibo poco calorico - neutro, mai colpevolizzato`() {
        val riga = SchedaNutrizionale.righe(food(kcal = 30.0)).single()
        assertEquals(Livello.NEUTRO, riga.livello)
    }

    @Test
    fun `proteine alte sono verdi`() {
        val righe = SchedaNutrizionale.righe(food(prot = 25.0))
        assertEquals(Livello.BUONO, righe.single().livello)
        assertTrue(righe.single().giudizio.contains("Ottima"))
    }

    @Test
    fun `prodotto industriale - nessun giudizio negativo`() {
        val riga = SchedaNutrizionale.righe(food(nova = 4)).single()
        assertEquals("NOVA 4", riga.valore)
        assertEquals(Livello.NEUTRO, riga.livello)
    }

    /** Stessa regola dei consigli: nessuna parola che colpevolizza. */
    @Test
    fun `nessuna riga usa parole colpevolizzanti`() {
        val proibite = listOf("cattiv", "sbagliat", "male", "schifezz", "evita", "dannos", "nocivo")
        val completo = food(
            kcal = 539.0, prot = 6.0, grassi = 30.5, carb = 57.5,
            fibra = 3.4, saturi = 10.6, zuccheri = 56.3, sale = 0.107, nova = 4,
        )
        SchedaNutrizionale.righe(completo).forEach { r ->
            val testo = (r.etichetta + " " + r.giudizio).lowercase()
            proibite.forEach { p -> assertFalse("«$p» in: ${r.giudizio}", testo.contains(p)) }
        }
    }

    @Test
    fun `contributo - riporta la quantita reale della voce`() {
        val c = SchedaNutrizionale.contributo(food(kcal = 356.0, prot = 14.5), 130.0)
        assertTrue(c.contains("130 g"))
        assertTrue(c.contains("462,8 kcal"))
        // 14,5 × 1,3 = 18,85 → una cifra decimale, come ovunque nell'app.
        assertTrue("trovato invece: $c", c.contains("18,8 g di proteine"))
    }

    @Test
    fun `contributo di una bevanda - in millilitri`() {
        val c = SchedaNutrizionale.contributo(food(kcal = 64.0, drink = true), 300.0)
        assertTrue(c.contains("300 ml"))
    }

    @Test
    fun `scheda completa - ordine stabile e tutte le voci presenti`() {
        val completo = food(
            kcal = 356.0, prot = 14.5, grassi = 1.5, carb = 70.0,
            fibra = 2.9, saturi = 0.3, zuccheri = 3.1, sale = 0.0, nova = 1,
        )
        assertEquals(
            listOf("Energia", "Proteine", "Carboidrati", "Fibre", "Grassi", "Grassi saturi", "Zuccheri", "Sale", "Lavorazione"),
            SchedaNutrizionale.righe(completo).map { it.etichetta },
        )
    }
}
