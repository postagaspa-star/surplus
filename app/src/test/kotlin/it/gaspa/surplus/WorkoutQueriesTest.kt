package it.gaspa.surplus

import it.gaspa.surplus.data.ExSet
import it.gaspa.surplus.data.Exercise
import it.gaspa.surplus.data.ExerciseLog
import it.gaspa.surplus.data.WorkoutSession
import it.gaspa.surplus.data.WorkoutType
import it.gaspa.surplus.logic.WorkoutQueries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutQueriesTest {

    private fun session(id: String, date: String, startedAt: Long, vararg logs: ExerciseLog) =
        WorkoutSession(id, date, WorkoutType.SPINTA, logs.toList(), startedAt, 0L)

    private fun log(exId: String, vararg sets: ExSet) = ExerciseLog(exId, exId, sets.toList())

    private val s1 = session("a", "2026-07-20", 1, log("sp", ExSet(10.0, 8), ExSet(10.0, 8)))
    private val s2 = session("b", "2026-07-24", 2, log("sp", ExSet(10.0, 10), ExSet(10.0, 9)))
    private val s3 = session("c", "2026-07-28", 3, log("sp")) // sessione senza serie
    private val current = session("d", "2026-07-31", 4, log("sp"))
    private val all = listOf(s1, s2, s3, current)

    @Test
    fun `ultima volta - sessione precedente piu recente con serie`() {
        val prev = WorkoutQueries.previousSets(all, "sp", "d")
        assertNotNull(prev)
        assertEquals("2026-07-24", prev!!.first)
        assertEquals(listOf(ExSet(10.0, 10), ExSet(10.0, 9)), prev.second)
    }

    @Test
    fun `ultima volta - stesso giorno ordina per orario di inizio`() {
        val m1 = session("m1", "2026-07-30", 5, log("sp", ExSet(8.0, 8)))
        val m2 = session("m2", "2026-07-30", 9, log("sp", ExSet(9.0, 8)))
        val cur = session("cur", "2026-07-31", 1, log("sp"))
        val prev = WorkoutQueries.previousSets(listOf(m1, m2, cur), "sp", "cur")
        assertEquals(listOf(ExSet(9.0, 8)), prev!!.second)
    }

    @Test
    fun `ultima volta - null se mai fatto prima`() {
        assertNull(WorkoutQueries.previousSets(all, "mai-fatto", "d"))
    }

    @Test
    fun `storico - dal piu recente, senza sessioni vuote`() {
        val h = WorkoutQueries.history(all, "sp")
        assertEquals(listOf("2026-07-24", "2026-07-20"), h.map { it.date })
    }

    @Test
    fun `giorno di allenamento - richiede almeno una serie`() {
        assertTrue(WorkoutQueries.trainedOn(all, "2026-07-24"))
        assertFalse(WorkoutQueries.trainedOn(all, "2026-07-28")) // sessione vuota
        assertFalse(WorkoutQueries.trainedOn(all, "2026-07-25")) // giorno senza sessioni
    }

    @Test
    fun `progressione - tutte le serie al massimo del range`() {
        assertTrue(WorkoutQueries.allAtTopOfRange(listOf(ExSet(12.0, 12), ExSet(12.0, 13)), 12))
        assertFalse(WorkoutQueries.allAtTopOfRange(listOf(ExSet(12.0, 12), ExSet(12.0, 10)), 12))
        assertFalse(WorkoutQueries.allAtTopOfRange(listOf(ExSet(12.0, 12)), null))
        assertFalse(WorkoutQueries.allAtTopOfRange(emptyList(), 12))
    }

    @Test
    fun `storico - ogni riga porta il nome della scheda di allora`() {
        val vecchia = session("v", "2026-07-20", 1, log("sp", ExSet(10.0, 8)))
        val nuova = WorkoutSession(
            "n", "2026-08-10", exercises = listOf(log("sp", ExSet(11.0, 8))),
            startedAt = 2, routineId = "r-1", routineName = "Upper",
        )
        val h = WorkoutQueries.history(listOf(vecchia, nuova), "sp")
        assertEquals(listOf("Upper", "Spinta"), h.map { it.etichetta })
    }

    // ---- proposta della serie: un tocco su "Segna" nel caso normale ----

    private val press = Exercise("sp", "Shoulder Press", "Spalle", repMin = 8, repMax = 12)

    @Test
    fun `proposta - di norma l'ultima serie della volta scorsa`() {
        val p = WorkoutQueries.proposta(listOf(ExSet(12.0, 10), ExSet(12.0, 9)), press)!!
        assertEquals(ExSet(12.0, 9), p.set)
        assertFalse(p.progressione)
    }

    @Test
    fun `proposta - tutte al massimo del range, un disco in piu ripartendo dal minimo`() {
        val p = WorkoutQueries.proposta(listOf(ExSet(12.0, 12), ExSet(12.0, 12), ExSet(12.0, 13)), press)!!
        assertEquals(ExSet(13.0, 8), p.set)
        assertTrue(p.progressione)
    }

    @Test
    fun `proposta - il salto di peso e quello dell'esercizio`() {
        val conDischi = press.copy(incrementoKg = 2.5)
        val p = WorkoutQueries.proposta(listOf(ExSet(10.0, 12)), conDischi)!!
        assertEquals(12.5, p.set.weightKg, 0.001)
    }

    @Test
    fun `proposta - a corpo libero non c'e disco da aggiungere`() {
        val pushup = Exercise("pu", "Push-up", "Petto", repMin = 10, repMax = 15)
        val p = WorkoutQueries.proposta(listOf(ExSet(0.0, 15), ExSet(0.0, 15)), pushup)!!
        assertEquals(ExSet(0.0, 15), p.set)
        assertFalse(p.progressione)
    }

    @Test
    fun `proposta - senza range niente progressione automatica`() {
        val libero = Exercise("x", "X", "Altro")
        assertFalse(WorkoutQueries.proposta(listOf(ExSet(10.0, 30)), libero)!!.progressione)
    }

    @Test
    fun `proposta - mai fatto, nessuna proposta`() {
        assertNull(WorkoutQueries.proposta(null, press))
        assertNull(WorkoutQueries.proposta(emptyList(), press))
    }

    @Test
    fun `arrotonda - niente pesi come 12,500000001`() {
        var peso = 10.0
        repeat(5) { peso = WorkoutQueries.arrotonda(peso + 0.5) }
        assertEquals(12.5, peso, 0.0)
    }

    @Test
    fun `ultima volta di una scheda - solo sessioni con serie`() {
        val a = WorkoutSession("a", "2026-08-01", exercises = listOf(log("sp", ExSet(10.0, 8))), routineId = "r1")
        val b = WorkoutSession("b", "2026-08-05", exercises = listOf(log("sp")), routineId = "r1") // vuota
        val c = WorkoutSession("c", "2026-08-07", exercises = listOf(log("sp", ExSet(10.0, 8))), routineId = "r2")
        assertEquals("2026-08-01", WorkoutQueries.ultimaVolta(listOf(a, b, c), "r1"))
        assertNull(WorkoutQueries.ultimaVolta(listOf(a, b, c), "r3"))
    }
}
