package it.gaspa.surplus

import it.gaspa.surplus.data.Attivita
import it.gaspa.surplus.data.Obiettivo
import it.gaspa.surplus.data.Sesso
import it.gaspa.surplus.data.TargetCalculator
import it.gaspa.surplus.data.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetCalculatorTest {

    private val anno = 2026

    private fun profilo(
        peso: Double = 70.0,
        altezza: Int = 178,
        nascita: Int = 2010,
        sesso: Sesso = Sesso.MASCHIO,
        attivita: Attivita = Attivita.MODERATA,
        obiettivo: Obiettivo = Obiettivo.MASSA,
    ) = UserProfile(
        annoNascita = nascita, sesso = sesso, altezzaCm = altezza,
        pesoKg = peso, attivita = attivita, obiettivo = obiettivo,
    )

    @Test
    fun `bmr maschio - formula di Mifflin-St Jeor`() {
        // 10*70 + 6.25*178 - 5*16 + 5 = 700 + 1112.5 - 80 + 5 = 1737.5
        assertEquals(1737.5, TargetCalculator.bmr(profilo(), anno), 0.01)
    }

    @Test
    fun `bmr femmina - 166 kcal sotto il maschio equivalente`() {
        val m = TargetCalculator.bmr(profilo(sesso = Sesso.MASCHIO), anno)
        val f = TargetCalculator.bmr(profilo(sesso = Sesso.FEMMINA), anno)
        assertEquals(166.0, m - f, 0.01)
    }

    // Gli estremi sono arrotondati a 50, quindi il centro può scostarsi fino a 25 kcal.
    @Test
    fun `massa - surplus sopra il consumo stimato`() {
        val t = TargetCalculator.compute(profilo(), anno)
        val centro = (t.kcalMin + t.kcalMax) / 2
        assertTrue("centro $centro non è in surplus su TDEE ${t.tdee}", centro > t.tdee)
        assertEquals(400.0, (centro - t.tdee).toDouble(), 25.0)
    }

    @Test
    fun `definizione - deficit sotto il consumo stimato`() {
        val t = TargetCalculator.compute(profilo(obiettivo = Obiettivo.DEFINIZIONE), anno)
        val centro = (t.kcalMin + t.kcalMax) / 2
        assertTrue("centro $centro non è in deficit su TDEE ${t.tdee}", centro < t.tdee)
        assertEquals(-400.0, (centro - t.tdee).toDouble(), 25.0)
    }

    @Test
    fun `proteine - proporzionali al peso e all'obiettivo`() {
        assertEquals(126, TargetCalculator.compute(profilo(peso = 70.0), anno).protMin) // 70 × 1.8
        assertEquals(154, TargetCalculator.compute(profilo(peso = 70.0, obiettivo = Obiettivo.DEFINIZIONE), anno).protMin) // 70 × 2.2
    }

    @Test
    fun `attivita piu alta - piu calorie`() {
        val sed = TargetCalculator.compute(profilo(attivita = Attivita.SEDENTARIA), anno).kcalMin
        val alta = TargetCalculator.compute(profilo(attivita = Attivita.ALTA), anno).kcalMin
        assertTrue("sedentaria $sed dovrebbe essere < alta $alta", sed < alta)
    }

    @Test
    fun `acqua - proporzionale al peso, arrotondata e limitata`() {
        assertEquals(2750, TargetCalculator.compute(profilo(peso = 70.0), anno).waterMl)
        assertEquals(5000, TargetCalculator.compute(profilo(peso = 200.0), anno).waterMl) // tetto
    }

    @Test
    fun `range calorico - ampio 400 kcal e arrotondato a 50`() {
        val t = TargetCalculator.compute(profilo(), anno)
        assertEquals(400, t.kcalMax - t.kcalMin)
        assertEquals(0, t.kcalMin % 50)
        assertEquals(0, t.kcalMax % 50)
    }

    @Test
    fun `eta - zero se l'anno di nascita non e stato inserito`() {
        assertEquals(0, UserProfile().eta(anno))
        assertEquals(16, profilo(nascita = 2010).eta(anno))
    }
}
