package it.gaspa.surplus.logic

import it.gaspa.surplus.data.Entry
import it.gaspa.surplus.data.FoodGroup

/**
 * Qualità del cibo di una giornata, su tre assi positivi:
 * frutta/verdura, fibra e grado di lavorazione.
 *
 * Non esiste un "punteggio salute": nessun cibo viene bollato come cattivo.
 * Dove il dato manca l'app tace, esattamente come fa con le kcal sconosciute.
 */
data class QualityStatus(
    val kcalTotali: Double,
    val kcalConGrado: Double,        // kcal delle voci di cui conosciamo il NOVA
    val kcalUltraProcessati: Double, // NOVA 4
    val fibraG: Double,
    val porzioniFruttaVerdura: Double,
) {
    /** Quota di ultra-processati, calcolata SOLO sulle voci di grado noto. */
    val quotaUltraProcessati: Double?
        get() = if (kcalConGrado >= MIN_KCAL_GRADO) kcalUltraProcessati / kcalConGrado else null

    /** Quanta parte della giornata è coperta dal dato: sotto il 50% non si conclude nulla. */
    val copertura: Double
        get() = if (kcalTotali > 0) kcalConGrado / kcalTotali else 0.0

    companion object {
        /** Sotto questa soglia il campione è troppo piccolo per dire qualcosa. */
        const val MIN_KCAL_GRADO = 500.0
    }
}

object DietQuality {

    /** Una porzione di frutta o verdura = 100 g: soglia semplice e verificabile. */
    const val PORZIONE_G = 100.0

    /** Obiettivo giornaliero di fibra per una dieta ipercalorica. */
    const val FIBRA_TARGET = 30.0

    fun of(entries: List<Entry>): QualityStatus {
        var kcalTot = 0.0
        var kcalConGrado = 0.0
        var kcalUltra = 0.0
        var fibra = 0.0
        var grammiFruttaVerdura = 0.0

        for (e in entries) {
            val kcal = e.kcal ?: 0.0
            kcalTot += kcal
            if (e.nova != null) {
                kcalConGrado += kcal
                if (e.nova >= 4) kcalUltra += kcal
            }
            fibra += e.fibra ?: 0.0
            if (e.gruppo == FoodGroup.FRUTTA || e.gruppo == FoodGroup.VERDURA) {
                grammiFruttaVerdura += e.grams
            }
        }

        return QualityStatus(
            kcalTotali = kcalTot,
            kcalConGrado = kcalConGrado,
            kcalUltraProcessati = kcalUltra,
            fibraG = kotlin.math.round(fibra * 10) / 10,
            porzioniFruttaVerdura = kotlin.math.round(grammiFruttaVerdura / PORZIONE_G * 10) / 10,
        )
    }

    /** Media giornaliera su più giorni: per il riquadro settimanale. */
    fun average(perDay: List<QualityStatus>): QualityStatus? {
        val giorni = perDay.filter { it.kcalTotali > 0 }
        if (giorni.isEmpty()) return null
        val n = giorni.size
        return QualityStatus(
            kcalTotali = giorni.sumOf { it.kcalTotali } / n,
            kcalConGrado = giorni.sumOf { it.kcalConGrado } / n,
            kcalUltraProcessati = giorni.sumOf { it.kcalUltraProcessati } / n,
            fibraG = kotlin.math.round(giorni.sumOf { it.fibraG } / n * 10) / 10,
            porzioniFruttaVerdura =
                kotlin.math.round(giorni.sumOf { it.porzioniFruttaVerdura } / n * 10) / 10,
        )
    }
}
