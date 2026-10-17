package it.gaspa.surplus

import it.gaspa.surplus.data.AiEsercizi
import it.gaspa.surplus.data.SeedExercises
import it.gaspa.surplus.logic.CompilaEsercizio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CompilaEsercizioTest {

    private fun r(nome: String, salto: Double? = null) = CompilaEsercizio.daRegole(nome, salto)

    /** Le regole devono ridare gli stessi valori della scheda di partenza. */
    @Test
    fun `gli esercizi della scheda escono come nella scheda`() {
        val attesi = mapOf(
            "Shoulder Press" to Triple("Spalle", "3-4", 8 to 12),
            "Alzate Laterali" to Triple("Spalle", "3", 12 to 20),
            "French Press" to Triple("Tricipiti", "3", 10 to 15),
            "Curl con manubri" to Triple("Bicipiti", "3", 8 to 12),
            "Reverse Fly" to Triple("Salute spalle", "2-3", 12 to 15),
            "Face Pull" to Triple("Salute spalle", "2-3", 12 to 15),
            "Leg Raises a terra" to Triple("Addominali", "3", 12 to 20),
            "Alzate polpacci a una gamba" to Triple("Polpacci", "3", 15 to 25),
        )
        attesi.forEach { (nome, atteso) ->
            val p = r(nome)
            assertNotNull("non riconosciuto: $nome", p)
            assertEquals(nome, atteso.first, p!!.gruppo)
            assertEquals(nome, atteso.second, p.serie)
            assertEquals(nome, atteso.third, p.repMin to p.repMax)
        }
    }

    @Test
    fun `tutti gli esercizi di partenza vengono riconosciuti`() {
        SeedExercises.all.forEach { assertNotNull("non riconosciuto: ${it.nome}", r(it.nome)) }
    }

    @Test
    fun `nomi italiani comuni in palestra`() {
        assertEquals("Gambe", r("Squat con manubri")!!.gruppo)
        assertEquals("Gambe", r("Stacco da terra")!!.gruppo)
        assertEquals("Petto", r("Panca piana")!!.gruppo)
        assertEquals("Petto", r("Croci ai cavi")!!.gruppo)
        assertEquals("Petto", r("Piegamenti")!!.gruppo)
        assertEquals("Schiena", r("Trazioni alla sbarra")!!.gruppo)
        assertEquals("Schiena", r("Lat machine")!!.gruppo)
        assertEquals("Schiena", r("Pulley basso")!!.gruppo)
        assertEquals("Spalle", r("Military press")!!.gruppo)
        assertEquals("Tricipiti", r("Push down al cavo")!!.gruppo)
        assertEquals("Glutei", r("Hip thrust")!!.gruppo)
        assertEquals("Addominali", r("Crunch inverso")!!.gruppo)
    }

    @Test
    fun `quelli specifici prima dei generici`() {
        assertEquals("Gambe", r("Leg curl")!!.gruppo) // non Bicipiti per via di "curl"
        assertEquals("Tricipiti", r("Diamond push-up")!!.gruppo) // non Petto per via di "push-up"
        assertEquals("Salute spalle", r("Alzate posteriori")!!.gruppo) // non Spalle generico
        assertEquals("Addominali", r("Alzate le gambe")!!.gruppo) // non Spalle per via di "alzate"
        assertEquals("Spalle", r("Alzate laterali")!!.gruppo)
    }

    @Test
    fun `maiuscole, accenti e trattini non contano`() {
        assertEquals(r("push up")!!.gruppo, r("PUSH-UP")!!.gruppo)
        assertEquals(r("military press")!!.gruppo, r("  Military   Press ")!!.gruppo)
        assertEquals("Gambe", r("Affondi all'indietro")!!.gruppo)
    }

    @Test
    fun `lat non scatta dentro laterali`() {
        assertEquals("Spalle", r("Alzate laterali con elastico")!!.gruppo)
    }

    // ---- salto di peso: dipende dall'attrezzo, non dall'esercizio ----

    @Test
    fun `bilanciere 2,5 kg, macchine e cavi 5 kg`() {
        assertEquals(2.5, r("Panca piana con bilanciere")!!.incrementoKg!!, 0.0)
        assertEquals(5.0, r("Lat machine")!!.incrementoKg!!, 0.0)
        assertEquals(5.0, r("Croci ai cavi")!!.incrementoKg!!, 0.0)
    }

    @Test
    fun `manubri - il salto che l'utente usa gia`() {
        assertEquals(2.0, r("Curl con manubri", salto = 2.0)!!.incrementoKg!!, 0.0)
        assertNull(r("Curl con manubri", salto = null)!!.incrementoKg) // vuoto = 1 kg di serie
    }

    @Test
    fun `corpo libero - nessun salto, a meno che sia zavorrato`() {
        assertNull(r("Trazioni", salto = 2.0)!!.incrementoKg)
        assertNull(r("Piegamenti", salto = 2.0)!!.incrementoKg)
        assertEquals(2.0, r("Trazioni zavorrate", salto = 2.0)!!.incrementoKg!!, 0.0)
    }

    @Test
    fun `note utili - per lato e plank a tempo`() {
        assertEquals("per lato", r("Rematore a un braccio")!!.nota)
        assertEquals("per lato", r("Affondi con manubri")!!.nota)
        assertEquals("a tempo: segna i secondi come ripetizioni", r("Plank")!!.nota)
        assertNull(r("Panca piana")!!.nota)
    }

    @Test
    fun `nomi sconosciuti - nessuna invenzione, passa all'AI`() {
        assertNull(r("Farmer walk"))
        assertNull(r("Burpees"))
        assertNull(r(""))
    }

    @Test
    fun `salto abituale - il piu usato tra quelli impostati`() {
        assertEquals(2.0, CompilaEsercizio.saltoAbituale(listOf(2.0, null, 2.0, 1.0))!!, 0.0)
        assertNull(CompilaEsercizio.saltoAbituale(listOf(null, null)))
    }

    // ---- risposta dell'AI ----

    @Test
    fun `AI - risposta normale`() {
        val p = AiEsercizi.parse(
            """{"gruppo":"Gambe","serie":"3","repMin":10,"repMax":15,"incrementoKg":2,"nota":"schiena dritta"}"""
        )!!
        assertEquals("Gambe", p.gruppo)
        assertEquals(10 to 15, p.repMin to p.repMax)
        assertEquals(2.0, p.incrementoKg!!, 0.0)
        assertEquals("schiena dritta", p.nota)
    }

    @Test
    fun `AI - corpo libero e range scritto al contrario`() {
        val p = AiEsercizi.parse(
            """{"gruppo":"Petto","serie":"3","repMin":15,"repMax":8,"incrementoKg":0,"nota":""}"""
        )!!
        assertEquals(8 to 15, p.repMin to p.repMax)
        assertNull(p.incrementoKg)
        assertNull(p.nota)
    }

    @Test
    fun `AI - valori assurdi vengono scartati invece di finire nella scheda`() {
        val p = AiEsercizi.parse(
            """{"gruppo":"Muscoli vari","serie":"tante","repMin":500,"repMax":12,"incrementoKg":80,"nota":"ok"}"""
        )!!
        assertEquals("Altro", p.gruppo)
        assertEquals("3", p.serie)
        assertEquals(12 to 12, p.repMin to p.repMax)
        assertNull(p.incrementoKg)
    }

    @Test
    fun `AI - nome non riconosciuto`() {
        assertNull(
            AiEsercizi.parse(
                """{"gruppo":"Altro","serie":"3","repMin":8,"repMax":12,"incrementoKg":0,"nota":"nome non riconosciuto"}"""
            )
        )
    }
}
