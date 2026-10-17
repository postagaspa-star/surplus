package it.gaspa.surplus

import it.gaspa.surplus.data.Moment
import it.gaspa.surplus.logic.AdvisorEngine
import it.gaspa.surplus.logic.DayStatus
import it.gaspa.surplus.logic.QualityStatus
import it.gaspa.surplus.logic.Targets
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class AdvisorEngineTest {

    private val targets = Targets(3300, 3700, 130, 150, 3250)

    private fun day(
        kcal: Double = 0.0,
        prot: Double = 0.0,
        unknown: Int = 0,
        counts: Map<Moment, Int> = emptyMap(),
        water: Int = 2000,
        trained: Boolean = false,
        quality: QualityStatus? = null,
    ) = DayStatus(kcal, prot, unknown, counts, water, trained, quality)

    private fun quality(
        porzioni: Double = 0.0,
        fibra: Double = 0.0,
        kcalTot: Double = 3000.0,
        kcalConGrado: Double = 0.0,
        kcalUltra: Double = 0.0,
    ) = QualityStatus(kcalTot, kcalConGrado, kcalUltra, fibra, porzioni)

    private fun at(hour: Int, minute: Int = 0): LocalDateTime =
        LocalDateTime.of(2026, 7, 30, hour, minute)

    @Test
    fun `mattina presto senza dati - nessun allarme`() {
        val advice = AdvisorEngine.advise(at(8), day(), null, targets)
        assertTrue("attesa lista vuota, trovato: $advice", advice.isEmpty())
    }

    @Test
    fun `pomeriggio indietro - consiglio concreto con kcal`() {
        val d = day(kcal = 700.0, prot = 25.0, counts = mapOf(Moment.COLAZIONE to 1))
        val advice = AdvisorEngine.advise(at(16), d, null, targets)
        assertTrue(advice.any { it.text.contains("sotto il ritmo") })
        assertTrue(advice.any { it.text.contains("kcal") })
    }

    @Test
    fun `pasto saltato a finestra chiusa - consiglio per domani`() {
        val d = day(kcal = 1500.0, prot = 60.0, counts = mapOf(Moment.COLAZIONE to 1, Moment.PRANZO to 1))
        val advice = AdvisorEngine.advise(at(13), d, null, targets)
        assertTrue(
            "atteso consiglio per domani sullo spuntino saltato: $advice",
            advice.any { it.text.contains("domani prova") && it.text.contains("metà mattina") },
        )
    }

    @Test
    fun `sera sotto il minimo - proposta prima di dormire`() {
        val d = day(kcal = 2400.0, prot = 100.0, water = 3000)
        val advice = AdvisorEngine.advise(at(21, 30), d, null, targets)
        val top = advice.first()
        assertTrue(top.text.contains("Ti mancano"))
        assertTrue(top.text.contains("prima di dormire"))
    }

    @Test
    fun `sera a target - conferma senza allarmi`() {
        val d = day(kcal = 3450.0, prot = 140.0, water = 3300)
        val advice = AdvisorEngine.advise(at(22), d, null, targets)
        assertTrue(advice.first().text.contains("a target"))
    }

    @Test
    fun `sera calorie ok ma proteine basse - spinta proteica`() {
        val d = day(kcal = 3400.0, prot = 110.0, water = 3300)
        val advice = AdvisorEngine.advise(at(22), d, null, targets)
        assertTrue(advice.first().text.contains("yogurt greco"))
    }

    @Test
    fun `sera sopra il range - tono tranquillo`() {
        val d = day(kcal = 4300.0, prot = 150.0, water = 3300)
        val advice = AdvisorEngine.advise(at(22), d, null, targets)
        assertTrue(advice.first().text.contains("sopra il range"))
    }

    @Test
    fun `mattina dopo giornata scarsa - recupero da ieri`() {
        val yesterday = day(kcal = 2500.0, prot = 90.0)
        val advice = AdvisorEngine.advise(at(8, 30), day(), yesterday, targets)
        assertTrue(advice.any { it.text.contains("Ieri") })
    }

    @Test
    fun `voci senza valori - nota di sottostima`() {
        val d = day(kcal = 2000.0, prot = 80.0, unknown = 2, counts = mapOf(Moment.PRANZO to 3))
        val advice = AdvisorEngine.advise(at(14), d, null, targets)
        assertTrue(advice.any { it.text.contains("senza valori") })
    }

    @Test
    fun `acqua indietro nel pomeriggio - promemoria`() {
        val d = day(kcal = 2200.0, prot = 90.0, water = 1000, counts = mapOf(Moment.PRANZO to 1))
        val advice = AdvisorEngine.advise(at(16), d, null, targets)
        assertTrue(advice.any { it.text.contains("Acqua") })
    }

    @Test
    fun `acqua a posto - nessun promemoria acqua`() {
        val d = day(kcal = 2200.0, prot = 90.0, water = 2500, counts = mapOf(Moment.PRANZO to 1))
        val advice = AdvisorEngine.advise(at(16), d, null, targets)
        assertFalse(advice.any { it.text.contains("Acqua") })
    }

    @Test
    fun `sera giorno di allenamento - soglia alzata e dichiarata`() {
        val d = day(kcal = 3100.0, prot = 135.0, water = 3300, trained = true)
        val top = AdvisorEngine.advise(at(22), d, null, targets).first()
        assertTrue(top.text.contains("3.450"))
        assertTrue(top.text.contains("allenamento"))
    }

    @Test
    fun `sera stesso introito senza allenamento - soglia normale`() {
        val d = day(kcal = 3100.0, prot = 135.0, water = 3300)
        val top = AdvisorEngine.advise(at(22), d, null, targets).first()
        assertTrue(top.text.contains("3.300"))
        assertFalse(top.text.contains("allenamento"))
    }

    @Test
    fun `pomeriggio - ritmo piu esigente nei giorni di allenamento`() {
        val base = day(kcal = 1800.0, prot = 70.0, counts = mapOf(Moment.COLAZIONE to 1, Moment.PRANZO to 1))
        val untrained = AdvisorEngine.advise(at(16), base, null, targets)
        val trained = AdvisorEngine.advise(at(16), base.copy(trained = true), null, targets)
        assertFalse(untrained.any { it.text.contains("sotto il ritmo") })
        assertTrue(trained.any { it.text.contains("sotto il ritmo") })
    }

    @Test
    fun `ogni consiglio porta la sua spiegazione`() {
        val scenarios = listOf(
            Triple(at(16), day(kcal = 700.0, prot = 25.0, counts = mapOf(Moment.COLAZIONE to 1), water = 1000), null),
            Triple(at(22), day(kcal = 2400.0, prot = 100.0, unknown = 1, counts = mapOf(Moment.PRANZO to 2), water = 2000), null),
            Triple(at(8, 30), day(), day(kcal = 2500.0, prot = 90.0)),
        )
        scenarios.forEach { (now, d, yesterday) ->
            val advice = AdvisorEngine.advise(now, d, yesterday, targets)
            assertTrue("scenario senza consigli: $now", advice.isNotEmpty())
            advice.forEach { a ->
                assertTrue("spiegazione vuota per: ${a.text}", a.why.isNotBlank())
            }
        }
    }

    @Test
    fun `ritmo ok - conferma esplicita invece del silenzio`() {
        val d = day(kcal = 2200.0, prot = 90.0, water = 2500, counts = mapOf(Moment.PRANZO to 1))
        val advice = AdvisorEngine.advise(at(16), d, null, targets)
        assertTrue(advice.any { it.text.contains("In linea col ritmo") })
    }

    // ---- qualità del cibo (v2.1.0) ----

    @Test
    fun `poca frutta e verdura - suggerisce di aggiungerne`() {
        val d = day(kcal = 2000.0, prot = 90.0, counts = mapOf(Moment.PRANZO to 1), quality = quality(porzioni = 1.0))
        val advice = AdvisorEngine.advise(at(16), d, null, targets)
        val a = advice.first { it.text.contains("Frutta e verdura") }
        assertTrue(a.text.contains("Aggiungine"))
        assertTrue(a.why.contains("100 g"))
    }

    @Test
    fun `qualita buona - riconoscimento esplicito, non silenzio`() {
        val d = day(
            kcal = 3400.0, prot = 140.0, water = 3300,
            quality = quality(porzioni = 4.0, fibra = 32.0, kcalConGrado = 3000.0, kcalUltra = 300.0),
        )
        val advice = AdvisorEngine.advise(at(22), d, null, targets)
        assertTrue(advice.any { it.text.contains("Qualità della giornata buona") })
    }

    @Test
    fun `grado di lavorazione - avviso solo con campione sufficiente`() {
        val piccolo = day(
            kcal = 3400.0, prot = 140.0, water = 3300,
            quality = quality(porzioni = 4.0, fibra = 32.0, kcalConGrado = 300.0, kcalUltra = 300.0),
        )
        assertFalse(
            AdvisorEngine.advise(at(22), piccolo, null, targets)
                .any { it.text.contains("prodotti industriali") }
        )

        val ampio = day(
            kcal = 3400.0, prot = 140.0, water = 3300,
            quality = quality(porzioni = 4.0, fibra = 32.0, kcalConGrado = 3000.0, kcalUltra = 2400.0),
        )
        assertTrue(
            AdvisorEngine.advise(at(22), ampio, null, targets)
                .any { it.text.contains("prodotti industriali") }
        )
    }

    @Test
    fun `nessun consiglio sulla qualita senza dati`() {
        val d = day(kcal = 3400.0, prot = 140.0, water = 3300, quality = null)
        val advice = AdvisorEngine.advise(at(22), d, null, targets)
        assertFalse(advice.any { it.text.contains("Frutta e verdura") || it.text.contains("Fibra") })
    }

    /** Regola di progetto: si suggerisce cosa aggiungere, mai si colpevolizza. */
    @Test
    fun `i consigli non colpevolizzano mai`() {
        val proibite = listOf("cattiv", "sbagliat", "male", "schifezz", "colpa", "evita", "smetti", "non mangiare")
        val scenari = listOf(
            at(16) to day(kcal = 2000.0, prot = 90.0, counts = mapOf(Moment.PRANZO to 1), quality = quality(porzioni = 0.0)),
            at(22) to day(
                kcal = 3400.0, prot = 140.0, water = 3300,
                quality = quality(porzioni = 0.0, fibra = 8.0, kcalConGrado = 3000.0, kcalUltra = 2700.0),
            ),
            at(22) to day(kcal = 4500.0, prot = 150.0, water = 3300, quality = quality(porzioni = 1.0)),
        )
        scenari.forEach { (now, d) ->
            AdvisorEngine.advise(now, d, null, targets).forEach { a ->
                val testo = (a.text + " " + a.why).lowercase()
                proibite.forEach { parola ->
                    assertFalse("«$parola» in: ${a.text}", testo.contains(parola))
                }
            }
        }
    }

    @Test
    fun `spiegazione serale - contiene i numeri del calcolo`() {
        val d = day(kcal = 3100.0, prot = 135.0, water = 3300, trained = true)
        val top = AdvisorEngine.advise(at(22), d, null, targets).first()
        assertTrue(top.why.contains("3.450"))
        assertTrue(top.why.contains("allenamento"))
        assertTrue(top.why.contains("3.100"))
    }
}
