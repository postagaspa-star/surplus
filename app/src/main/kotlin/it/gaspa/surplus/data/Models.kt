package it.gaspa.surplus.data

import kotlinx.serialization.Serializable

/** Momenti della giornata, come da piano. BEVANDE è il contenitore senza orario. */
enum class Moment(val label: String) {
    COLAZIONE("Colazione"),
    META_MATTINA("Metà mattina"),
    PRANZO("Pranzo"),
    POST_ALLENAMENTO("Dopo allenamento"),
    CENA("Cena"),
    PRE_NANNA("Prima di dormire"),
    BEVANDE("Bevande");
}

/**
 * Gruppo alimentare: serve solo ai segnali di qualità (frutta/verdura, grassi
 * buoni…). Non giudica nulla da solo, è una classificazione.
 */
enum class FoodGroup(val label: String) {
    VERDURA("Verdura"),
    FRUTTA("Frutta"),
    PROTEICO("Proteico"),
    CEREALE("Cereali"),
    LATTICINO("Latticini"),
    GRASSI("Grassi"),
    DOLCE("Dolci"),
    ALTRO("Altro"),
}

/**
 * Alimento in dispensa: valori per 100 g (o 100 ml se bevanda), tutti facoltativi
 * tranne il nome. `nova` è il grado di lavorazione (1 = cibo vero, 4 = ultra-processato)
 * e `barcode` permette di ritrovare il prodotto scansionato senza rete.
 */
@Serializable
data class Food(
    val id: String,
    val nome: String,
    val kcal100: Double? = null,
    val prot100: Double? = null,
    val grassi100: Double? = null,
    val carb100: Double? = null,
    val fibra100: Double? = null,
    val grassiSaturi100: Double? = null,
    val zuccheri100: Double? = null,
    val sale100: Double? = null,
    val nova: Int? = null,
    val gruppo: FoodGroup = FoodGroup.ALTRO,
    val isDrink: Boolean = false,
    val hint: String? = null,
    val barcode: String? = null,
    val custom: Boolean = false,
)

/**
 * Voce del diario. I grammi (ml per le bevande) sono obbligatori;
 * kcal/prot sono i TOTALI della voce, null = valori non noti.
 */
@Serializable
data class Entry(
    val id: String,
    val date: String, // ISO yyyy-MM-dd
    val moment: Moment,
    val nome: String,
    val grams: Double,
    val kcal: Double? = null,
    val prot: Double? = null,
    val fibra: Double? = null,
    val nova: Int? = null,
    val gruppo: FoodGroup = FoodGroup.ALTRO,
    val isDrink: Boolean = false,
    val createdAt: Long = 0L,
)

@Serializable
data class WeightEntry(val date: String, val kg: Double)

/**
 * Tipi di sessione delle prime versioni. Oggi le sessioni nascono dalle schede
 * dell'utente: l'enum resta solo per rileggere i dati salvati prima della 2.5.0.
 */
enum class WorkoutType(val label: String) { SPINTA("Spinta"), TRAZIONE("Trazione") }

/**
 * Esercizio in archivio. `spinta`/`trazione` servono solo a costruire le due
 * schede iniziali dai dati vecchi; da lì in poi decide la composizione delle schede.
 */
@Serializable
data class Exercise(
    val id: String,
    val nome: String,
    val gruppo: String,
    val spinta: Boolean = false,
    val trazione: Boolean = false,
    val serie: String? = null, // es. "3-4", com'è scritto in scheda
    val repMin: Int? = null,
    val repMax: Int? = null,
    val nota: String? = null,
    val custom: Boolean = false,
    /** Di quanto sale il peso con un disco in più: il passo dei tasti − e +. */
    val incrementoKg: Double? = null,
) {
    val passoKg: Double get() = incrementoKg?.takeIf { it > 0 } ?: 1.0
}

/** Scheda personale: un nome e gli esercizi nell'ordine in cui li fai. */
@Serializable
data class Routine(
    val id: String,
    val nome: String,
    val exerciseIds: List<String> = emptyList(),
)

/** Una serie registrata: peso (0 = a corpo libero) e ripetizioni. */
@Serializable
data class ExSet(val weightKg: Double, val reps: Int)

@Serializable
data class ExerciseLog(
    val exerciseId: String,
    val nome: String,
    val sets: List<ExSet> = emptyList(),
)

/**
 * Sessione di allenamento. La data è ISO yyyy-MM-dd come il diario alimentare.
 * `routineName` è una copia del nome della scheda al momento dell'allenamento:
 * se poi la scheda viene rinominata o cancellata, lo storico resta leggibile.
 */
@Serializable
data class WorkoutSession(
    val id: String,
    val date: String,
    val type: WorkoutType? = null, // solo sessioni salvate prima della 2.5.0
    val exercises: List<ExerciseLog> = emptyList(),
    val startedAt: Long = 0L,  // creazione della sessione
    val firstSetAt: Long = 0L, // registrazione della PRIMA serie della sessione
    val lastSetAt: Long = 0L,  // registrazione dell'ULTIMA serie della sessione
    val routineId: String? = null,
    val routineName: String = "",
) {
    val etichetta: String get() = routineName.ifBlank { type?.label ?: "Allenamento" }
}

/**
 * Tutto lo stato persistito di UN utente, un solo file JSON locale.
 * `schemaVersion` serve al backup: un file più recente dell'app viene rifiutato
 * invece di essere caricato a metà.
 */
@Serializable
data class AppData(
    val schemaVersion: Int = SCHEMA_VERSION,
    val profile: UserProfile = UserProfile(),
    val entries: List<Entry> = emptyList(),
    val waterByDate: Map<String, Int> = emptyMap(), // ml per giorno
    val weights: List<WeightEntry> = emptyList(),
    val customFoods: List<Food> = emptyList(),
    val workouts: List<WorkoutSession> = emptyList(),
    /** Solo nei dati vecchi: dalla 2.5.0 confluiscono in [exercises]. */
    val customExercises: List<Exercise> = emptyList(),
    /** Archivio esercizi dell'utente: tutti modificabili, anche quelli di partenza. */
    val exercises: List<Exercise> = emptyList(),
    val routines: List<Routine> = emptyList(),
    /** true dopo la creazione delle schede: evita di ricrearle se l'utente le svuota. */
    val schedeInizializzate: Boolean = false,
) {
    companion object {
        const val SCHEMA_VERSION = 4
    }
}

data class DayTotals(
    val kcal: Double,
    val prot: Double,
    val unknownCount: Int,
    val kcalByMoment: Map<Moment, Double>,
    val countByMoment: Map<Moment, Int>,
)

fun computeTotals(entries: List<Entry>): DayTotals {
    var kcal = 0.0
    var prot = 0.0
    var unknown = 0
    val kcalByMoment = mutableMapOf<Moment, Double>()
    val countByMoment = mutableMapOf<Moment, Int>()
    for (e in entries) {
        countByMoment[e.moment] = (countByMoment[e.moment] ?: 0) + 1
        if (e.kcal == null) unknown++ else {
            kcal += e.kcal
            kcalByMoment[e.moment] = (kcalByMoment[e.moment] ?: 0.0) + e.kcal
        }
        if (e.prot != null) prot += e.prot
    }
    return DayTotals(kcal, prot, unknown, kcalByMoment, countByMoment)
}

object FoodMath {
    /** kcal o proteine totali per una quantità in grammi, dato il valore per 100 g. */
    fun forGrams(grams: Double, per100: Double?): Double? =
        per100?.let { round1(grams * it / 100.0) }

    fun round1(v: Double): Double = kotlin.math.round(v * 10.0) / 10.0
}
