package it.gaspa.surplus

import it.gaspa.surplus.data.AppData
import it.gaspa.surplus.data.ExSet
import it.gaspa.surplus.data.Exercise
import it.gaspa.surplus.data.ExerciseLog
import it.gaspa.surplus.data.MigrazioneSchede
import it.gaspa.surplus.data.Routine
import it.gaspa.surplus.data.SeedExercises
import it.gaspa.surplus.data.WorkoutSession
import it.gaspa.surplus.data.WorkoutType
import it.gaspa.surplus.data.conEserciziDellaScheda
import it.gaspa.surplus.data.soloEserciziFatti
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il passaggio alle schede personali tocca dati veri già sul telefono:
 * qui si verifica che nessuna sessione, serie o esercizio vada perso.
 */
class MigrazioneSchedeTest {

    private fun log(id: String, vararg sets: ExSet) = ExerciseLog(id, id, sets.toList())

    private val mioEsercizio = Exercise("custom-ex-1", "Dip alla sedia", "Tricipiti", spinta = true, custom = true)

    /** Dati come li salvava la 2.4.0: sessioni con tipo fisso, esercizi personali a parte. */
    private val vecchi = AppData(
        schemaVersion = 3,
        workouts = listOf(
            WorkoutSession(
                "w1", "2026-07-20", WorkoutType.SPINTA,
                listOf(log("shoulder-press", ExSet(12.0, 10)), log("custom-ex-1", ExSet(0.0, 12)), log("floor-press")),
                startedAt = 1,
            ),
            WorkoutSession(
                "w2", "2026-07-24", WorkoutType.SPINTA,
                listOf(log("alzate-laterali", ExSet(5.0, 15))),
                startedAt = 2,
            ),
        ),
        customExercises = listOf(mioEsercizio),
    )

    @Test
    fun `le sessioni vecchie prendono il nome della loro scheda, serie intatte`() {
        val m = MigrazioneSchede.migra(vecchi)
        assertEquals(listOf("Spinta", "Spinta"), m.workouts.map { it.etichetta })
        assertEquals(listOf("spinta", "spinta"), m.workouts.map { it.routineId })
        assertEquals(vecchi.workouts.map { it.exercises }, m.workouts.map { it.exercises })
    }

    @Test
    fun `gli esercizi creati a mano entrano nell'archivio insieme a quelli di partenza`() {
        val m = MigrazioneSchede.migra(vecchi)
        assertTrue(m.exercises.any { it.id == "custom-ex-1" })
        assertTrue(SeedExercises.all.all { s -> m.exercises.any { it.id == s.id } })
        assertTrue(m.customExercises.isEmpty())
        assertEquals(m.exercises.size, m.exercises.map { it.id }.distinct().size)
    }

    @Test
    fun `la scheda iniziale contiene solo gli esercizi fatti davvero almeno una volta`() {
        val spinta = MigrazioneSchede.migra(vecchi).routines.first { it.id == "spinta" }
        // floor-press era in lista ma senza serie: resta in archivio, non nella scheda.
        assertEquals(
            setOf("shoulder-press", "alzate-laterali", "custom-ex-1"),
            spinta.exerciseIds.toSet(),
        )
    }

    @Test
    fun `senza sessioni di quel tipo la scheda parte con tutti i suoi esercizi`() {
        val trazione = MigrazioneSchede.migra(vecchi).routines.first { it.id == "trazione" }
        assertEquals(SeedExercises.all.filter { it.trazione }.map { it.id }, trazione.exerciseIds)
    }

    @Test
    fun `nuovo utente - due schede pronte con tutti gli esercizi`() {
        val m = MigrazioneSchede.migra(AppData())
        assertEquals(listOf("Spinta", "Trazione"), m.routines.map { it.nome })
        assertEquals(SeedExercises.all.filter { it.spinta }.map { it.id }, m.routines[0].exerciseIds)
        assertTrue(m.schedeInizializzate)
        assertEquals(AppData.SCHEMA_VERSION, m.schemaVersion)
    }

    @Test
    fun `gira una volta sola - la seconda volta restituisce gli stessi dati`() {
        val una = MigrazioneSchede.migra(vecchi)
        assertSame(una, MigrazioneSchede.migra(una))
    }

    @Test
    fun `schede svuotate dall'utente non vengono ricreate`() {
        val svuotate = MigrazioneSchede.migra(vecchi).copy(routines = emptyList())
        assertTrue(MigrazioneSchede.migra(svuotate).routines.isEmpty())
    }

    /** Il file vero sul telefono è JSON: va riletto con le stesse opzioni dell'app. */
    @Test
    fun `un file salvato dalla 2_4_0 si rilegge e si converte`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val file240 = """
            {"schemaVersion":3,
             "workouts":[{"id":"w1","date":"2026-07-20","type":"TRAZIONE",
               "exercises":[{"exerciseId":"curl-manubri","nome":"Curl con manubri",
                 "sets":[{"weightKg":8.0,"reps":10}]}],
               "startedAt":1,"firstSetAt":5,"lastSetAt":9}],
             "customExercises":[{"id":"custom-ex-9","nome":"Pull-over","gruppo":"Schiena",
               "spinta":false,"trazione":true}]}
        """.trimIndent()
        val letti = json.decodeFromString(AppData.serializer(), file240)
        assertEquals(WorkoutType.TRAZIONE, letti.workouts.single().type)

        val m = MigrazioneSchede.migra(letti)
        val s = m.workouts.single()
        assertEquals("Trazione", s.etichetta)
        assertEquals(5L, s.firstSetAt)
        assertEquals(9L, s.lastSetAt)
        assertEquals(listOf(ExSet(8.0, 10)), s.exercises.single().sets)
        assertEquals(listOf("curl-manubri"), m.routines.first { it.id == "trazione" }.exerciseIds)
        assertTrue(m.exercises.any { it.nome == "Pull-over" })

        // E il risultato si salva e si rilegge uguale.
        val riletti = json.decodeFromString(AppData.serializer(), json.encodeToString(AppData.serializer(), m))
        assertEquals(m, riletti)
    }

    // ---- chiusura e ripresa della sessione ----

    @Test
    fun `chiudendo restano solo gli esercizi con almeno una serie`() {
        val s = WorkoutSession("s", "2026-08-01", exercises = listOf(log("a", ExSet(10.0, 8)), log("b")))
        assertEquals(listOf("a"), s.soloEserciziFatti().exercises.map { it.exerciseId })
    }

    @Test
    fun `riprendendo tornano gli esercizi della scheda non ancora fatti, in ordine`() {
        val archivio = listOf("a", "b", "c", "x").map { Exercise(it, it.uppercase(), "G") }
        val scheda = Routine("r", "R", listOf("a", "b", "c"))
        val s = WorkoutSession(
            "s", "2026-08-01",
            exercises = listOf(log("c", ExSet(10.0, 8)), log("x", ExSet(5.0, 10))),
        )
        val ripresa = s.conEserciziDellaScheda(scheda, archivio)
        assertEquals(listOf("a", "b", "c", "x"), ripresa.exercises.map { it.exerciseId })
        // Le serie già fatte non si toccano.
        assertEquals(listOf(ExSet(10.0, 8)), ripresa.exercises.first { it.exerciseId == "c" }.sets)
    }

    @Test
    fun `un esercizio cancellato dall'archivio non ricompare riprendendo`() {
        val archivio = listOf(Exercise("a", "A", "G"))
        val scheda = Routine("r", "R", listOf("a", "cancellato"))
        val ripresa = WorkoutSession("s", "2026-08-01").conEserciziDellaScheda(scheda, archivio)
        assertEquals(listOf("a"), ripresa.exercises.map { it.exerciseId })
    }

    @Test
    fun `sessione libera - etichetta leggibile anche senza scheda`() {
        val s = WorkoutSession("s", "2026-08-01", routineId = MigrazioneSchede.LIBERO_ID, routineName = MigrazioneSchede.LIBERO_NOME)
        assertEquals("Libero", s.etichetta)
        assertNull(s.type)
        assertFalse(s.etichetta.isBlank())
    }
}
