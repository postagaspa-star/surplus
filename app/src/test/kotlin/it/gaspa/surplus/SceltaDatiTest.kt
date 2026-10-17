package it.gaspa.surplus

import it.gaspa.surplus.data.AppData
import it.gaspa.surplus.data.SceltaDati
import it.gaspa.surplus.data.SceltaDati.Fonte
import it.gaspa.surplus.data.UserProfile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Quale copia dei dati si usa all'avvio. È il punto in cui il diario si salva
 * o si perde: il caso che l'ha fatto nascere è un file rimasto VUOTO (0 byte)
 * dopo uno spegnimento improvviso, visto davvero sull'emulatore.
 */
class SceltaDatiTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun testo(nome: String) =
        json.encodeToString(AppData.serializer(), AppData(profile = UserProfile(nome = nome, isConfigured = true)))

    private fun scegli(principale: String?, copia: String?, vecchio: String? = null) = SceltaDati.scegli(
        principale = principale,
        copiaPrecedente = copia,
        vecchioFormato = vecchio,
        decodifica = { json.decodeFromString(AppData.serializer(), it) },
        nuovi = { AppData() },
    )

    @Test
    fun `di norma si usa il file principale`() {
        val e = scegli(testo("oggi"), testo("ieri"))
        assertEquals(Fonte.PRINCIPALE, e.fonte)
        assertEquals("oggi", e.dati.profile.nome)
        assertFalse(e.principaleRovinato)
    }

    @Test
    fun `file principale vuoto dopo uno spegnimento - si riparte dall'ultima copia buona`() {
        val e = scegli("", testo("ieri"))
        assertEquals(Fonte.COPIA_PRECEDENTE, e.fonte)
        assertEquals("ieri", e.dati.profile.nome)
        assertTrue(e.principaleRovinato)
    }

    @Test
    fun `file principale troncato a meta - si riparte dall'ultima copia buona`() {
        val intero = testo("oggi")
        val e = scegli(intero.take(intero.length / 2), testo("ieri"))
        assertEquals(Fonte.COPIA_PRECEDENTE, e.fonte)
        assertTrue(e.principaleRovinato)
    }

    @Test
    fun `principale sparito - si usa la copia`() {
        val e = scegli(null, testo("ieri"))
        assertEquals(Fonte.COPIA_PRECEDENTE, e.fonte)
        assertFalse(e.principaleRovinato)
    }

    @Test
    fun `primo avvio in assoluto - dati nuovi`() {
        val e = scegli(null, null)
        assertEquals(Fonte.NUOVI, e.fonte)
        assertFalse(e.dati.profile.isConfigured)
    }

    @Test
    fun `tutto rovinato - si riparte da zero ma il principale va messo da parte`() {
        val e = scegli("{rotto", "")
        assertEquals(Fonte.NUOVI, e.fonte)
        assertTrue(e.principaleRovinato)
    }

    @Test
    fun `senza principale ne copia si recupera il vecchio file a utente unico`() {
        assertEquals(Fonte.VECCHIO_FORMATO, scegli(null, null, testo("vecchio")).fonte)
        // Anche col principale rovinato: un diario indietro nel tempo è meglio di uno vuoto.
        val e = scegli("", null, testo("vecchio"))
        assertEquals(Fonte.VECCHIO_FORMATO, e.fonte)
        assertTrue(e.principaleRovinato)
    }
}
