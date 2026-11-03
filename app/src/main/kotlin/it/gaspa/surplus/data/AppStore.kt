package it.gaspa.surplus.data

import android.content.Context
import androidx.core.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Persistenza locale PER UTENTE: un file JSON `data-<userId>.json` in filesDir,
 * scritto in modo atomico (tmp + rename). Nessun database: pochi KB al giorno.
 *
 * Multi-utente: oggi l'unico utente è quello locale, ma i dati sono già scopati
 * per `userId`, quindi un secondo account (Firebase Auth) userebbe un file suo
 * senza toccare questo. Le mutazioni passano tutte da `mutate`, che notifica
 * anche il [SyncGateway]: quando esisterà l'impl Firebase, il push parte da lì.
 */
class AppStore private constructor(
    private val appContext: Context,
    private val sync: SyncGateway = LocalOnlyGateway(),
) {

    @Volatile private var userId: String = UserProfile.LOCAL_USER
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private val sincronizzazione = Mutex()

    /**
     * Quante volte è cambiato il diario in memoria. Serve alla sincronizzazione
     * per accorgersi se hai scritto qualcosa mentre aspettava il NAS.
     */
    private val serratura = Any()
    private var versioneLocale = 0L

    private fun fileFor(uid: String) = File(appContext.filesDir, "data-$uid.json")

    /** Ultima copia buona, presa a ogni avvio: il paracadute se il principale si rovina. */
    private fun copiaFor(uid: String) = File(appContext.filesDir, "data-$uid.prev.json")

    private val _state = MutableStateFlow(load(userId))
    val state: StateFlow<AppData> = _state.asStateFlow()

    private fun load(uid: String): AppData {
        val principale = fileFor(uid)
        val testoPrincipale = leggi(principale)
        val esito = SceltaDati.scegli(
            principale = testoPrincipale,
            copiaPrecedente = leggi(copiaFor(uid)),
            // File unico di prima del multiutente: vale solo per l'utente locale.
            vecchioFormato = if (uid == UserProfile.LOCAL_USER) leggi(legacyFile) else null,
            decodifica = { json.decodeFromString(AppData.serializer(), it) },
            nuovi = { AppData(profile = UserProfile(userId = uid)) },
        )
        // Un principale rovinato si mette da parte intero, mai sovrascritto alla cieca
        // (se è vuoto non c'è niente da conservare).
        if (esito.principaleRovinato && principale.length() > 0) {
            runCatching {
                principale.copyTo(File(appContext.filesDir, "data-$uid.illeggibile-${System.currentTimeMillis()}.json"))
            }
        }
        // Il file letto bene diventa la copia di sicurezza per il prossimo avvio.
        // In sottofondo, per non rallentare l'apertura dell'app.
        if (esito.fonte == SceltaDati.Fonte.PRINCIPALE && testoPrincipale != null) {
            scope.launch {
                writeMutex.withLock { runCatching { scriviSulDisco(copiaFor(uid), testoPrincipale) } }
            }
        }
        // Schede personali (2.5.0) o recupero da copia: si riscrive subito il principale.
        val migrati = MigrazioneSchede.migra(esito.dati)
        if (migrati !== esito.dati || esito.fonte != SceltaDati.Fonte.PRINCIPALE) scrivi(uid, migrati)
        return migrati
    }

    /** Testo del file, o null se non esiste o non si legge. */
    private fun leggi(file: File): String? =
        if (!file.exists()) null
        else runCatching { AtomicFile(file).readFully().toString(Charsets.UTF_8) }.getOrElse { "" }

    /**
     * Scrittura sicura: il testo va in un file a parte, viene forzato sul disco
     * (senza, il sistema lo tiene in memoria qualche secondo e uno spegnimento
     * improvviso lascia un file vuoto) e solo dopo prende il posto del vecchio,
     * in un colpo solo. In nessun momento il diario resta senza file.
     */
    private fun scriviSulDisco(file: File, testo: String) {
        val af = AtomicFile(file)
        val out = af.startWrite()
        try {
            out.write(testo.toByteArray(Charsets.UTF_8))
            af.finishWrite(out)
        } catch (e: Exception) {
            af.failWrite(out)
            throw e
        }
    }

    private val legacyFile get() = File(appContext.filesDir, "data.json")

    /** Cambia utente: salva implicitamente (già su disco) e carica i dati dell'altro. */
    fun switchUser(uid: String) {
        if (uid == userId) return
        userId = uid
        synchronized(serratura) {
            _state.value = load(uid)
            versioneLocale++
        }
    }

    private fun mutate(f: (AppData) -> AppData) {
        val uid = userId
        val next = synchronized(serratura) {
            versioneLocale++
            _state.updateAndGet(f)
        }
        scrivi(uid, next)
    }

    private fun scrivi(uid: String, next: AppData, poiSincronizza: Boolean = true) {
        scope.launch {
            writeMutex.withLock {
                try {
                    // Sempre il diario di adesso, non quello del momento della modifica: le
                    // scritture possono arrivare in disordine, e una vecchia arrivata per
                    // ultima riporterebbe il file indietro.
                    val daScrivere = if (uid == userId) _state.value else next
                    scriviSulDisco(fileFor(uid), json.encodeToString(AppData.serializer(), daScrivere))
                } catch (_: Exception) {
                    // Il flusso in memoria resta valido; ritenterà alla prossima mutazione.
                }
            }
            if (poiSincronizza) pianificaSincronizzazione()
        }
    }

    // ---- NAS di casa ----

    private var lavoroSincronizzazione: Job? = null

    /**
     * Dopo una modifica si aspetta qualche secondo prima di parlare col NAS: chi
     * scrive un pasto ne scrive spesso tre di fila, e tre sincronizzazioni di
     * fila sarebbero tre volte lo stesso lavoro.
     */
    private fun pianificaSincronizzazione() {
        if (!sync.isActive()) return
        lavoroSincronizzazione?.cancel()
        lavoroSincronizzazione = scope.launch {
            delay(ATTESA_SINCRONIZZAZIONE_MS)
            sincronizzaOra()
        }
    }

    /**
     * Manda il diario al NAS e tiene quello unito. Restituisce true se da fuori
     * è arrivato qualcosa di nuovo.
     *
     * Il controllo prima di accettare non è formalità: se un giorno il NAS
     * rispondesse con un diario vuoto (un errore suo, un ripristino andato
     * male), senza quel controllo l'app cancellerebbe mesi di pasti da sola.
     */
    suspend fun sincronizzaOra(): Result<Boolean> = sincronizzazione.withLock {
        if (!sync.isActive()) return@withLock Result.success(false)
        val uid = userId
        val (mio, versione) = synchronized(serratura) { _state.value to versioneLocale }
        val unito = sync.sincronizza(uid, mio)
            ?: return@withLock Result.failure(IOException("sincronizzazione non riuscita"))
        val cambiato = unito != mio
        if (cambiato) {
            if (!unioneAccettabile(mio, unito)) {
                return@withLock Result.failure(IOException("Il NAS ha mandato un diario che toglierebbe quasi tutte le voci: non l'ho accettato."))
            }
            val tenuto = synchronized(serratura) {
                if (versioneLocale != versione) {
                    false
                } else {
                    _state.value = unito
                    versioneLocale++
                    true
                }
            }
            if (!tenuto) {
                // Hai scritto qualcosa mentre il NAS rispondeva: il diario unito non
                // lo contiene, e tenerlo lo cancellerebbe. Non si tiene: la base resta
                // quella di prima e fra poco si rimanda tutto, modifica compresa.
                pianificaSincronizzazione()
                return@withLock Result.success(false)
            }
        }
        // Prima sul disco, poi la base: se il telefono si spegnesse in mezzo, al
        // riavvio la base direbbe "hai già tutto" mentre il file è quello vecchio.
        if (!scriviAdesso(uid)) {
            return@withLock Result.failure(IOException("il diario unito non si è salvato sul telefono"))
        }
        sync.tenuto()
        Result.success(cambiato)
    }

    private suspend fun scriviAdesso(uid: String): Boolean = writeMutex.withLock {
        runCatching {
            scriviSulDisco(fileFor(uid), json.encodeToString(AppData.serializer(), _state.value))
        }.isSuccess
    }

    /**
     * Mentre l'app è aperta resta in ascolto del NAS: appena il computer scrive un
     * pasto, il NAS lo dice e qui si sincronizza subito, così le due app mostrano
     * sempre lo stesso diario. Gira finché chi l'ha lanciata non la ferma (l'app
     * che va in secondo piano). Se il NAS non risponde (fuori casa, NAS spento)
     * si riprova sempre più di rado, fino a quindici secondi.
     */
    suspend fun restaInAscolto() {
        var pausa = 0L
        while (true) {
            val novita = try {
                sync.attendiNovita()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                pausa = (pausa * 2).coerceIn(PAUSA_MINIMA_ASCOLTO_MS, PAUSA_MASSIMA_ASCOLTO_MS)
                delay(pausa)
                continue
            }
            // Il NAS è appena tornato (rientro a casa, NAS riacceso): quello che hai
            // scritto nel frattempo parte subito, senza aspettare la prossima modifica.
            val eraGiu = pausa > 0L
            pausa = 0L
            if (novita || eraGiu) sincronizzaOra()
            // Un respiro fra una domanda e l'altra: se qualcosa non torna (una
            // sincronizzazione che non riesce a tenere il diario) non si gira a vuoto.
            delay(500)
        }
    }

    /** Una perdita di oltre metà delle voci in un colpo solo non è mai voluta. */
    internal fun unioneAccettabile(prima: AppData, dopo: AppData): Boolean =
        prima.entries.size < 10 || dopo.entries.size * 2 >= prima.entries.size

    // ---- Profilo utente ----

    fun saveProfile(p: UserProfile) = mutate { it.copy(profile = p.copy(userId = userId)) }

    // ---- Backup ----

    /** Serializza tutti i dati dell'utente corrente (formato del file di backup). */
    fun exportJson(): String = json.encodeToString(AppData.serializer(), _state.value)

    /**
     * Importa un backup. Rifiuta file di uno schema più recente (app vecchia,
     * backup nuovo) invece di caricarli a metà. Il profilo viene ri-agganciato
     * all'utente corrente: un backup di un altro dispositivo resta usabile.
     */
    fun importJson(text: String): Result<Int> = runCatching {
        val parsed = json.decodeFromString(AppData.serializer(), text)
        require(parsed.schemaVersion <= AppData.SCHEMA_VERSION) {
            "Backup creato con una versione più recente dell'app: aggiorna Surplus e riprova."
        }
        // Un backup di prima della 2.5.0 viene convertito alle schede come i dati sul telefono.
        mutate { MigrazioneSchede.migra(parsed.copy(profile = parsed.profile.copy(userId = userId))) }
        parsed.entries.size + parsed.workouts.size + parsed.weights.size
    }

    fun allFoods(data: AppData): List<Food> = SeedFoods.all + data.customFoods

    /**
     * Corregge l'unità di misura di un alimento in dispensa (g ↔ ml).
     * Serve quando un archivio sbaglia a classificarlo: la correzione resta,
     * così l'errore non si ripresenta. I cibi di serie non sono modificabili.
     */
    fun setFoodIsDrink(foodId: String, isDrink: Boolean) = mutate { d ->
        d.copy(customFoods = d.customFoods.map { if (it.id == foodId) it.copy(isDrink = isDrink) else it })
    }

    /** Ritrova un prodotto già scansionato: la seconda volta non serve la rete. */
    fun findByBarcode(data: AppData, code: String): Food? =
        allFoods(data).find { it.barcode != null && it.barcode == code }

    fun addEntry(
        date: String, moment: Moment, nome: String, grams: Double,
        kcal100: Double?, prot100: Double?, isDrink: Boolean = false,
        fibra100: Double? = null, nova: Int? = null, gruppo: FoodGroup = FoodGroup.ALTRO,
    ) {
        val entry = Entry(
            id = UUID.randomUUID().toString(),
            date = date,
            moment = moment,
            nome = nome.trim(),
            grams = grams,
            kcal = FoodMath.forGrams(grams, kcal100),
            prot = FoodMath.forGrams(grams, prot100),
            fibra = FoodMath.forGrams(grams, fibra100),
            nova = nova,
            gruppo = gruppo,
            isDrink = isDrink,
            createdAt = System.currentTimeMillis(),
        )
        mutate { d ->
            val next = d.copy(entries = d.entries + entry)
            // Ogni bevanda conta anche come liquidi: niente doppio inserimento a mano.
            if (entry.isDrink) {
                val current = next.waterByDate[date] ?: 0
                next.copy(waterByDate = next.waterByDate + (date to current + grams.roundToInt()))
            } else next
        }
    }

    fun removeEntry(id: String) = mutate { d ->
        val entry = d.entries.find { it.id == id }
        val next = d.copy(entries = d.entries.filterNot { it.id == id })
        // Simmetria: cancellare una bevanda toglie anche i suoi ml dal contatore.
        if (entry?.isDrink == true) {
            val current = next.waterByDate[entry.date] ?: 0
            next.copy(
                waterByDate = next.waterByDate +
                    (entry.date to (current - entry.grams.roundToInt()).coerceAtLeast(0))
            )
        } else next
    }

    fun addWater(date: String, deltaMl: Int) = mutate { d ->
        val current = d.waterByDate[date] ?: 0
        val next = (current + deltaMl).coerceAtLeast(0)
        d.copy(waterByDate = d.waterByDate + (date to next))
    }

    fun setWeight(date: String, kg: Double) = mutate { d ->
        d.copy(weights = d.weights.filterNot { it.date == date } + WeightEntry(date, kg))
    }

    fun removeWeight(date: String) = mutate { d ->
        d.copy(weights = d.weights.filterNot { it.date == date })
    }

    fun addCustomFood(food: Food) = mutate { d ->
        if (allFoods(d).any { it.nome.equals(food.nome, ignoreCase = true) }) d
        else d.copy(customFoods = d.customFoods + food.copy(custom = true))
    }

    // ---- Allenamento ----

    fun allExercises(data: AppData): List<Exercise> = data.exercises

    /**
     * Avvia una sessione con gli esercizi della scheda, nel suo ordine. Nessuno
     * è obbligatorio: quelli senza serie spariscono quando la sessione si chiude.
     * Senza scheda è un allenamento libero, con gli esercizi aggiunti al volo.
     */
    fun startWorkout(date: String, scheda: Routine?): String {
        val id = "w-${UUID.randomUUID()}"
        mutate { d ->
            val esercizi = scheda?.exerciseIds.orEmpty()
                .mapNotNull { exId -> d.exercises.find { it.id == exId } }
                .map { ExerciseLog(it.id, it.nome) }
            val session = WorkoutSession(
                id = id,
                date = date,
                exercises = esercizi,
                startedAt = System.currentTimeMillis(),
                routineId = scheda?.id ?: MigrazioneSchede.LIBERO_ID,
                routineName = scheda?.nome ?: MigrazioneSchede.LIBERO_NOME,
            )
            d.copy(workouts = d.workouts + session)
        }
        return id
    }

    /** Riprende la sessione di oggi rimettendo in lista gli esercizi della scheda non ancora fatti. */
    fun riapriConScheda(sessionId: String, scheda: Routine) = mutate { d ->
        d.copy(workouts = d.workouts.map { s ->
            if (s.id != sessionId) s else s.conEserciziDellaScheda(scheda, d.exercises)
        })
    }

    /** Chiude la sessione: restano solo gli esercizi fatti; se non c'è nessuna serie, sparisce. */
    fun chiudiSessione(sessionId: String) = mutate { d ->
        val s = d.workouts.find { it.id == sessionId }
        when {
            s == null -> d
            s.exercises.none { it.sets.isNotEmpty() } ->
                d.copy(workouts = d.workouts.filterNot { it.id == sessionId })
            else -> d.copy(workouts = d.workouts.map { if (it.id == sessionId) it.soloEserciziFatti() else it })
        }
    }

    fun deleteWorkout(sessionId: String) = mutate { d ->
        d.copy(workouts = d.workouts.filterNot { it.id == sessionId })
    }

    // ---- Schede ----

    fun salvaScheda(scheda: Routine) = mutate { d ->
        // Solo esercizi ancora in archivio: uno cancellato nel frattempo non ricompare.
        val pulita = scheda.copy(
            nome = scheda.nome.trim(),
            exerciseIds = scheda.exerciseIds.distinct().filter { id -> d.exercises.any { it.id == id } },
        )
        if (d.routines.any { it.id == pulita.id }) d.copy(routines = d.routines.map { if (it.id == pulita.id) pulita else it })
        else d.copy(routines = d.routines + pulita)
    }

    fun eliminaScheda(schedaId: String) = mutate { d ->
        d.copy(routines = d.routines.filterNot { it.id == schedaId })
    }

    fun aggiungiAScheda(schedaId: String, exerciseId: String) = mutate { d ->
        d.copy(routines = d.routines.map { r ->
            if (r.id != schedaId || exerciseId in r.exerciseIds) r
            else r.copy(exerciseIds = r.exerciseIds + exerciseId)
        })
    }

    // ---- Archivio esercizi ----

    /** Crea un esercizio; se ne esiste già uno con lo stesso nome usa quello, senza doppioni. */
    fun creaEsercizio(ex: Exercise): Exercise {
        val nome = ex.nome.trim()
        _state.value.exercises.find { it.nome.equals(nome, ignoreCase = true) }?.let { return it }
        val nuovo = ex.copy(nome = nome, custom = true)
        mutate { d -> d.copy(exercises = d.exercises + nuovo) }
        return nuovo
    }

    /** Lo storico conserva il nome di allora: rinominare non riscrive le sessioni passate. */
    fun salvaEsercizio(ex: Exercise) = mutate { d ->
        d.copy(exercises = d.exercises.map { if (it.id == ex.id) ex.copy(nome = ex.nome.trim()) else it })
    }

    /** Toglie l'esercizio dall'archivio e dalle schede; le serie già fatte restano nello storico. */
    fun eliminaEsercizio(exerciseId: String) = mutate { d ->
        d.copy(
            exercises = d.exercises.filterNot { it.id == exerciseId },
            routines = d.routines.map { r -> r.copy(exerciseIds = r.exerciseIds - exerciseId) },
        )
    }

    fun addExerciseToWorkout(sessionId: String, exerciseId: String, nome: String) = mutate { d ->
        d.copy(workouts = d.workouts.map { s ->
            if (s.id != sessionId || s.exercises.any { it.exerciseId == exerciseId }) s
            else s.copy(exercises = s.exercises + ExerciseLog(exerciseId, nome))
        })
    }

    fun removeExerciseFromWorkout(sessionId: String, exerciseId: String) = mutate { d ->
        d.copy(workouts = d.workouts.map { s ->
            if (s.id != sessionId) s
            else s.copy(exercises = s.exercises.filterNot { it.exerciseId == exerciseId })
        })
    }

    fun addSet(sessionId: String, exerciseId: String, weightKg: Double, reps: Int) = mutate { d ->
        val now = System.currentTimeMillis()
        d.copy(workouts = d.workouts.map { s ->
            if (s.id != sessionId) s
            else s.copy(
                firstSetAt = if (s.firstSetAt > 0) s.firstSetAt else now,
                lastSetAt = now,
                exercises = s.exercises.map { log ->
                    if (log.exerciseId != exerciseId) log
                    else log.copy(sets = log.sets + ExSet(weightKg, reps))
                },
            )
        })
    }

    fun removeSet(sessionId: String, exerciseId: String, index: Int) = mutate { d ->
        d.copy(workouts = d.workouts.map { s ->
            if (s.id != sessionId) s
            else s.copy(exercises = s.exercises.map { log ->
                if (log.exerciseId != exerciseId) log
                else log.copy(sets = log.sets.filterIndexed { i, _ -> i != index })
            })
        })
    }

    companion object {
        @Volatile private var instance: AppStore? = null

        /** Quanto si aspetta, dopo una modifica, prima di mandarla al NAS. */
        const val ATTESA_SINCRONIZZAZIONE_MS = 1_500L
        const val PAUSA_MINIMA_ASCOLTO_MS = 5_000L
        const val PAUSA_MASSIMA_ASCOLTO_MS = 15_000L

        /**
         * [sync] è iniettabile: di serie c'è il NAS di casa, ma passando un
         * LocalOnlyGateway l'app torna a vivere solo sul telefono.
         */
        fun get(context: Context, sync: SyncGateway = NasGateway.get(context)): AppStore =
            instance ?: synchronized(this) {
                instance ?: AppStore(context.applicationContext, sync).also { instance = it }
            }
    }
}
