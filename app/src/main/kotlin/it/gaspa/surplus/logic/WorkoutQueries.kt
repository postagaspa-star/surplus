package it.gaspa.surplus.logic

import it.gaspa.surplus.data.ExSet
import it.gaspa.surplus.data.Exercise
import it.gaspa.surplus.data.WorkoutSession

/** Query pure sui dati allenamento: testabili senza Android. */
object WorkoutQueries {

    data class HistoryRow(val date: String, val etichetta: String, val sets: List<ExSet>)

    /** Serie proposta per la prossima serie, e se è un passo avanti rispetto all'ultima volta. */
    data class Proposta(val set: ExSet, val progressione: Boolean)

    // Le date sono ISO yyyy-MM-dd: l'ordinamento lessicografico è cronologico.
    private val sessionOrder =
        compareByDescending<WorkoutSession> { it.date }.thenByDescending { it.startedAt }

    /** Serie dell'ultima sessione precedente (con serie registrate) per un esercizio. */
    fun previousSets(
        workouts: List<WorkoutSession>,
        exerciseId: String,
        currentSessionId: String?,
    ): Pair<String, List<ExSet>>? {
        val current = workouts.find { it.id == currentSessionId }
        return workouts.asSequence()
            .filter { it.id != currentSessionId }
            .filter {
                current == null || it.date < current.date ||
                    (it.date == current.date && it.startedAt < current.startedAt)
            }
            .sortedWith(sessionOrder)
            .mapNotNull { s ->
                s.exercises.find { it.exerciseId == exerciseId }
                    ?.takeIf { it.sets.isNotEmpty() }
                    ?.let { s.date to it.sets }
            }
            .firstOrNull()
    }

    /** Storico completo di un esercizio, dal più recente. */
    fun history(workouts: List<WorkoutSession>, exerciseId: String): List<HistoryRow> =
        workouts
            .sortedWith(sessionOrder)
            .mapNotNull { s ->
                s.exercises.find { it.exerciseId == exerciseId }
                    ?.takeIf { it.sets.isNotEmpty() }
                    ?.let { HistoryRow(s.date, s.etichetta, it.sets) }
            }

    /** Giorno "di allenamento" = almeno una serie registrata quel giorno. */
    fun trainedOn(workouts: List<WorkoutSession>, date: String): Boolean =
        workouts.any { it.date == date && it.exercises.any { e -> e.sets.isNotEmpty() } }

    /** Regola di progressione della scheda: tutte le serie al massimo del range → aggiungi un disco. */
    fun allAtTopOfRange(sets: List<ExSet>, repMax: Int?): Boolean =
        repMax != null && sets.isNotEmpty() && sets.all { it.reps >= repMax }

    /**
     * Cosa proporre per la prima serie di oggi, così nella maggior parte dei casi
     * basta un tocco su "Segna":
     * - di norma, l'ultima serie della volta scorsa;
     * - se l'ultima volta TUTTE le serie erano al massimo del range, un disco in
     *   più ripartendo dal minimo delle ripetizioni: è la regola della scheda,
     *   fatta dall'app invece che a mente;
     * - a corpo libero non c'è disco da aggiungere, quindi resta com'era.
     */
    fun proposta(prev: List<ExSet>?, ex: Exercise?): Proposta? {
        val ultima = prev?.lastOrNull() ?: return null
        val pesoMax = prev.maxOf { it.weightKg }
        if (ex != null && pesoMax > 0 && allAtTopOfRange(prev, ex.repMax)) {
            val peso = arrotonda(pesoMax + ex.passoKg)
            return Proposta(ExSet(peso, ex.repMin ?: ultima.reps), progressione = true)
        }
        return Proposta(ultima, progressione = false)
    }

    /** Evita pesi come 12.500000001 dopo qualche passo da 0,5 kg. */
    fun arrotonda(kg: Double): Double = kotlin.math.round(kg * 100.0) / 100.0

    /** Ultima data in cui è stata fatta una sessione di questa scheda (con serie), o null. */
    fun ultimaVolta(workouts: List<WorkoutSession>, schedaId: String): String? =
        workouts
            .filter { it.routineId == schedaId && it.exercises.any { e -> e.sets.isNotEmpty() } }
            .maxOfOrNull { it.date }
}
