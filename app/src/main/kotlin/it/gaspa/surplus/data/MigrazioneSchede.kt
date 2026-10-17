package it.gaspa.surplus.data

/**
 * Passaggio dalle due sessioni fisse (Spinta/Trazione) alle schede personali.
 *
 * Gira una volta sola, all'apertura dei dati. Non cancella niente: le sessioni
 * vecchie prendono il nome della loro scheda, gli esercizi creati a mano entrano
 * nell'archivio insieme a quelli di partenza.
 */
object MigrazioneSchede {

    /** Sessioni fuori da ogni scheda. */
    const val LIBERO_ID = "libero"
    const val LIBERO_NOME = "Libero"

    fun migra(d: AppData): AppData {
        if (d.schedeInizializzate) return d

        val archivio = (d.exercises + SeedExercises.all + d.customExercises).distinctBy { it.id }

        val sessioni = d.workouts.map { s ->
            val t = s.type
            if (s.routineId != null || t == null) s
            else s.copy(routineId = t.name.lowercase(), routineName = t.label)
        }

        val schede = d.routines.ifEmpty {
            listOf(
                schedaIniziale(WorkoutType.SPINTA, archivio, d.workouts),
                schedaIniziale(WorkoutType.TRAZIONE, archivio, d.workouts),
            )
        }

        return d.copy(
            schemaVersion = AppData.SCHEMA_VERSION,
            exercises = archivio,
            customExercises = emptyList(),
            routines = schede,
            workouts = sessioni,
            schedeInizializzate = true,
        )
    }

    /**
     * La scheda di partenza contiene gli esercizi che hai DAVVERO fatto almeno
     * una volta in quel tipo di sessione: quelli mai toccati restano in archivio,
     * a un tocco di distanza, invece di allungare la lista ogni volta.
     * Chi non ha ancora sessioni parte con tutti gli esercizi del tipo.
     */
    internal fun schedaIniziale(
        tipo: WorkoutType,
        archivio: List<Exercise>,
        sessioni: List<WorkoutSession>,
    ): Routine {
        val fatti = sessioni
            .filter { it.type == tipo }
            .flatMap { s -> s.exercises.filter { it.sets.isNotEmpty() }.map { it.exerciseId } }
            .toSet()
        val scelti = if (fatti.isEmpty()) {
            archivio.filter { if (tipo == WorkoutType.SPINTA) it.spinta else it.trazione }
        } else {
            archivio.filter { it.id in fatti }
        }
        return Routine(id = tipo.name.lowercase(), nome = tipo.label, exerciseIds = scelti.map { it.id })
    }
}

/** La sessione così come va salvata: solo gli esercizi in cui c'è almeno una serie. */
fun WorkoutSession.soloEserciziFatti(): WorkoutSession =
    copy(exercises = exercises.filter { it.sets.isNotEmpty() })

/**
 * Riapre una sessione rimettendo in lista gli esercizi della scheda non ancora
 * fatti, nell'ordine della scheda; quelli aggiunti a parte restano in fondo.
 */
fun WorkoutSession.conEserciziDellaScheda(scheda: Routine, archivio: List<Exercise>): WorkoutSession {
    val dellaScheda = scheda.exerciseIds.mapNotNull { id ->
        exercises.find { it.exerciseId == id }
            ?: archivio.find { it.id == id }?.let { ExerciseLog(it.id, it.nome) }
    }
    val extra = exercises.filter { log -> scheda.exerciseIds.none { it == log.exerciseId } }
    return copy(exercises = dellaScheda + extra)
}
