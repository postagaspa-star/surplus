package it.gaspa.surplus

import it.gaspa.surplus.aggiornamento.coincide
import it.gaspa.surplus.aggiornamento.impronta
import it.gaspa.surplus.data.Nas
import it.gaspa.surplus.data.VersioneDaScaricare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Prove della parte "pura" del NAS: l'indirizzo scritto a mano e il controllo
 * del file scaricato. Sono i due punti dove un errore fa danno davvero: un
 * indirizzo finto manderebbe il diario altrove, e un controllo debole
 * lascerebbe installare un'app che non è la nostra.
 */
class NasTest {

    @Test
    fun `l'indirizzo scritto a mano diventa un indirizzo completo`() {
        assertEquals("http://192.168.1.20:8600", Nas.normalizza("192.168.1.20:8600"))
        assertEquals("http://192.168.1.20:8600", Nas.normalizza("  192.168.1.20:8600/  "))
        assertEquals("http://nas.casa:8600", Nas.normalizza("http://nas.casa:8600"))
    }

    @Test
    fun `i nomi di tailscale vanno in https`() {
        assertEquals("https://surplus.esempio.ts.net", Nas.normalizza("surplus.esempio.ts.net"))
        assertEquals("https://surplus.esempio.ts.net", Nas.normalizza("https://surplus.esempio.ts.net"))
    }

    @Test
    fun `un indirizzo che non va bene torna null`() {
        assertNull(Nas.normalizza(""))
        assertNull(Nas.normalizza("   "))
        assertNull(Nas.normalizza(null))
        assertNull(Nas.normalizza("ftp://nas"))
        assertNull(Nas.normalizza("http://"))
    }

    @Test
    fun `il file scaricato vale solo se impronta e dimensione coincidono`() {
        val file = File.createTempFile("surplus-prova", ".apk")
        try {
            file.writeBytes(byteArrayOf(1, 2, 3, 4))
            val vera = impronta(file)
            assertEquals(64, vera.length)
            assertTrue(coincide(file, VersioneDaScaricare(15, "2.7.0", vera, 4L)))
            assertFalse(coincide(file, VersioneDaScaricare(15, "2.7.0", vera, 5L)))
            assertFalse(coincide(file, VersioneDaScaricare(15, "2.7.0", "a".repeat(64), 4L)))
            // Senza impronta non si installa niente.
            assertFalse(coincide(file, VersioneDaScaricare(15, "2.7.0", null, 4L)))
            assertFalse(coincide(file, VersioneDaScaricare(15, "2.7.0", "troppo corta", 4L)))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `un file che non c'e non coincide`() {
        val file = File("non-esiste-davvero.apk")
        assertFalse(coincide(file, VersioneDaScaricare(15, "2.7.0", "a".repeat(64), null)))
    }
}
