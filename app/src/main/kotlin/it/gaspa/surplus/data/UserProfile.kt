package it.gaspa.surplus.data

import kotlinx.serialization.Serializable

enum class Sesso(val label: String) { MASCHIO("Maschio"), FEMMINA("Femmina") }

enum class Attivita(val label: String, val fattore: Double, val descr: String) {
    SEDENTARIA("Sedentaria", 1.2, "ufficio/studio, poco movimento"),
    LEGGERA("Leggera", 1.375, "1-3 allenamenti a settimana"),
    MODERATA("Moderata", 1.55, "3-5 allenamenti a settimana"),
    ALTA("Alta", 1.725, "6-7 allenamenti a settimana"),
    MOLTO_ALTA("Molto alta", 1.9, "lavoro fisico + allenamenti"),
}

enum class Obiettivo(val label: String, val deltaKcal: Int, val protPerKg: Double, val descr: String) {
    MASSA("Massa", 400, 1.8, "surplus per crescita muscolare"),
    RICOMPOSIZIONE("Ricomposizione", 0, 2.0, "mantieni il peso, cambia la composizione"),
    DEFINIZIONE("Definizione", -400, 2.2, "deficit per perdere grasso"),
}

/**
 * Profilo dell'utente. Nasce vuoto (isConfigured = false) e viene compilato
 * dall'onboarding: da qui si calcolano i target, che restano comunque
 * modificabili a mano dalle impostazioni.
 */
@Serializable
data class UserProfile(
    val userId: String = LOCAL_USER,
    val nome: String = "",
    val annoNascita: Int = 0,
    val sesso: Sesso = Sesso.MASCHIO,
    val altezzaCm: Int = 0,
    val pesoKg: Double = 0.0,
    val attivita: Attivita = Attivita.MODERATA,
    val obiettivo: Obiettivo = Obiettivo.MASSA,
    val isConfigured: Boolean = false,
    val disclaimerAcceptedAt: Long = 0L,
    val isMinor: Boolean = false,
) {
    fun eta(annoCorrente: Int): Int = if (annoNascita > 0) annoCorrente - annoNascita else 0

    companion object {
        /** Utente locale predefinito: l'app funziona senza account, come sempre. */
        const val LOCAL_USER = "local"
    }
}

/** Target calcolati dal profilo. Nessuna magia: Mifflin-St Jeor, standard di settore. */
data class ComputedTargets(
    val bmr: Int,
    val tdee: Int,
    val kcalMin: Int,
    val kcalMax: Int,
    val protMin: Int,
    val protMax: Int,
    val waterMl: Int,
)

object TargetCalculator {

    /** Metabolismo basale, formula di Mifflin-St Jeor. */
    fun bmr(p: UserProfile, annoCorrente: Int): Double {
        val eta = p.eta(annoCorrente)
        val base = 10 * p.pesoKg + 6.25 * p.altezzaCm - 5 * eta
        return if (p.sesso == Sesso.MASCHIO) base + 5 else base - 161
    }

    fun compute(p: UserProfile, annoCorrente: Int): ComputedTargets {
        val bmr = bmr(p, annoCorrente)
        val tdee = bmr * p.attivita.fattore
        val center = tdee + p.obiettivo.deltaKcal
        // Range di ±200 kcal attorno al centro, arrotondato a 50.
        val kcalMin = round50(center - 200)
        val kcalMax = round50(center + 200)
        val protMin = kotlin.math.round(p.pesoKg * p.obiettivo.protPerKg).toInt()
        val protMax = protMin + 20
        // ~40 ml per kg di peso, arrotondati a 250 ml.
        val water = (kotlin.math.round(p.pesoKg * 40 / 250) * 250).toInt().coerceIn(1500, 5000)
        return ComputedTargets(
            bmr = kotlin.math.round(bmr).toInt(),
            tdee = kotlin.math.round(tdee).toInt(),
            kcalMin = kcalMin,
            kcalMax = kcalMax,
            protMin = protMin,
            protMax = protMax,
            waterMl = water,
        )
    }

    private fun round50(v: Double): Int = (kotlin.math.round(v / 50.0) * 50).toInt()
}
