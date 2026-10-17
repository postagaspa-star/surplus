package it.gaspa.surplus

import it.gaspa.surplus.data.AiFoodVision
import it.gaspa.surplus.data.AiProvider
import it.gaspa.surplus.data.FoodGroup
import it.gaspa.surplus.data.HttpFailure
import it.gaspa.surplus.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiFoodVisionTest {

    private val panino = """
        {
          "alimenti": [
            {"nome":"Pane","grammi":90,"kcal100":268,"prot100":8,"grassi100":0.5,
             "carb100":50,"fibra100":3.5,"nova":3,"gruppo":"CEREALE","certezza":"media"},
            {"nome":"Prosciutto cotto","grammi":50,"kcal100":165,"prot100":19,"grassi100":9,
             "carb100":1,"fibra100":0,"nova":4,"gruppo":"PROTEICO","certezza":"alta"}
          ],
          "domanda": "C'è maionese o altra salsa nel panino?",
          "note": "Stima dalle proporzioni nella foto."
        }
    """.trimIndent()

    @Test
    fun `legge piu alimenti da un piatto composto`() {
        val esito = AiFoodVision.parse(panino)
        assertEquals(2, esito.alimenti.size)
        assertEquals("Pane", esito.alimenti[0].nome)
        assertEquals(90.0, esito.alimenti[0].grammi, 0.001)
        assertEquals(268.0, esito.alimenti[0].kcal100!!, 0.001)
        assertEquals(FoodGroup.CEREALE, esito.alimenti[0].gruppo)
    }

    /** Il grado di lavorazione deve arrivare anche da qui, come dagli altri archivi. */
    @Test
    fun `ogni alimento porta il suo grado di lavorazione`() {
        val esito = AiFoodVision.parse(panino)
        assertEquals(3, esito.alimenti[0].nova)
        assertEquals(4, esito.alimenti[1].nova)
    }

    @Test
    fun `la domanda di chiarimento viene riportata`() {
        assertEquals("C'è maionese o altra salsa nel panino?", AiFoodVision.parse(panino).domanda)
    }

    @Test
    fun `nessun dubbio - domanda vuota diventa null`() {
        val body = """
            {"alimenti":[{"nome":"Mela","grammi":180,"kcal100":52,"prot100":0.3,"grassi100":0.2,
             "carb100":14,"fibra100":2.4,"nova":1,"gruppo":"FRUTTA","certezza":"alta"}],
             "domanda":"","note":""}
        """.trimIndent()
        val esito = AiFoodVision.parse(body)
        assertNull(esito.domanda)
        assertNull(esito.note)
    }

    @Test
    fun `nova fuori scala viene scartato invece di essere inventato`() {
        val body = """
            {"alimenti":[{"nome":"X","grammi":100,"kcal100":100,"prot100":1,"grassi100":1,
             "carb100":1,"fibra100":1,"nova":9,"gruppo":"ALTRO","certezza":"bassa"}],
             "domanda":"","note":""}
        """.trimIndent()
        assertNull(AiFoodVision.parse(body).alimenti.single().nova)
    }

    @Test
    fun `gruppo sconosciuto ricade su ALTRO senza rompere`() {
        val body = """
            {"alimenti":[{"nome":"X","grammi":100,"kcal100":100,"prot100":1,"grassi100":1,
             "carb100":1,"fibra100":1,"nova":1,"gruppo":"INVENTATO","certezza":"media"}],
             "domanda":"","note":""}
        """.trimIndent()
        assertEquals(FoodGroup.ALTRO, AiFoodVision.parse(body).alimenti.single().gruppo)
    }

    @Test
    fun `alimento senza nome viene ignorato`() {
        val body = """
            {"alimenti":[
              {"nome":"","grammi":100,"kcal100":100,"prot100":1,"grassi100":1,"carb100":1,
               "fibra100":1,"nova":1,"gruppo":"ALTRO","certezza":"media"},
              {"nome":"Riso","grammi":80,"kcal100":360,"prot100":7,"grassi100":1,"carb100":80,
               "fibra100":1.4,"nova":1,"gruppo":"CEREALE","certezza":"alta"}
            ],"domanda":"","note":""}
        """.trimIndent()
        assertEquals(listOf("Riso"), AiFoodVision.parse(body).alimenti.map { it.nome })
    }

    @Test
    fun `grammi assurdi ricadono su 100 invece di registrare zero`() {
        val body = """
            {"alimenti":[{"nome":"X","grammi":0,"kcal100":100,"prot100":1,"grassi100":1,
             "carb100":1,"fibra100":1,"nova":1,"gruppo":"ALTRO","certezza":"bassa"}],
             "domanda":"","note":""}
        """.trimIndent()
        assertEquals(100.0, AiFoodVision.parse(body).alimenti.single().grammi, 0.001)
    }

    @Test
    fun `JSON incorniciato da testo viene comunque letto`() {
        val sporco = "Ecco il risultato:\n```json\n$panino\n```\nSpero sia utile."
        assertEquals(2, AiFoodVision.parse(sporco).alimenti.size)
        assertTrue(AiFoodVision.estraiJson(sporco).startsWith("{"))
        assertTrue(AiFoodVision.estraiJson(sporco).endsWith("}"))
    }

    @Test
    fun `l alimento riconosciuto diventa una voce di dispensa completa`() {
        val food = AiFoodVision.parse(panino).alimenti[1].toFood("ai-1", "Claude")
        assertEquals("Prosciutto cotto", food.nome)
        assertEquals(4, food.nova)
        assertEquals(FoodGroup.PROTEICO, food.gruppo)
        assertTrue(food.custom)
        assertTrue(food.hint!!.contains("certezza alta"))
    }

    // ---- messaggi d'errore: devono dire cosa fare, non solo che è andata male ----

    @Test
    fun `chiave sbagliata - il messaggio manda alle impostazioni`() {
        val msg = AiFoodVision.messaggioErrore(HttpFailure(401, "invalid x-api-key"))
        assertTrue(msg.contains("Chiave non valida"))
        assertTrue(msg.contains("impostazioni"))
    }

    @Test
    fun `credito finito - il messaggio lo dice chiaramente`() {
        assertTrue(AiFoodVision.messaggioErrore(HttpFailure(429, "")).contains("credito"))
    }

    @Test
    fun `rete assente - nessun dettaglio tecnico incomprensibile`() {
        val msg = AiFoodVision.messaggioErrore(java.net.UnknownHostException("api.example.com"))
        assertTrue(msg.contains("connessione"))
    }

    // ---- istruzioni in base a quante foto e a come sono raggruppate ----

    @Test
    fun `una foto sola - nessuna istruzione di unione`() {
        val t = AiFoodVision.istruzioni(nFoto = 1, unico = false)
        assertFalse(t.contains("foto mostrano"))
        assertFalse(t.contains("UN SOLO alimento"))
    }

    @Test
    fun `piu foto - dice che sono lo stesso alimento e da dove prendere i valori`() {
        val t = AiFoodVision.istruzioni(nFoto = 3, unico = false)
        assertTrue(t.contains("Le 3 foto mostrano LO STESSO alimento"))
        assertTrue(t.contains("etichetta"))
    }

    @Test
    fun `un solo alimento - chiede di non scomporlo in ingredienti`() {
        assertTrue(AiFoodVision.istruzioni(nFoto = 1, unico = true).contains("UN SOLO alimento"))
    }

    @Test
    fun `le istruzioni contengono sempre la scala di lavorazione`() {
        listOf(true, false).forEach { unico ->
            val t = AiFoodVision.istruzioni(nFoto = 2, unico = unico)
            assertTrue(t.contains("nova"))
            assertTrue(t.contains("industriale"))
        }
    }

    // ---- impostazioni ----

    @Test
    fun `senza chiave la funzione foto resta spenta`() {
        assertFalse(Settings().aiAttiva)
        assertFalse(Settings(aiApiKey = "   ").aiAttiva)
        assertTrue(Settings(aiApiKey = "sk-test").aiAttiva)
    }

    @Test
    fun `modello vuoto usa il predefinito del fornitore scelto`() {
        assertEquals(
            AiProvider.GEMINI.modelDefault,
            Settings(aiProvider = AiProvider.GEMINI).aiModelEffettivo,
        )
        assertEquals(
            "mio-modello",
            Settings(aiProvider = AiProvider.CLAUDE, aiModel = "mio-modello").aiModelEffettivo,
        )
    }
}
