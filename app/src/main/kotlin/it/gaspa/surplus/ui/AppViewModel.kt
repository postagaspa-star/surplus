package it.gaspa.surplus.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.gaspa.surplus.data.AiAnalysis
import it.gaspa.surplus.data.AiEsercizi
import it.gaspa.surplus.data.AiFoodItem
import it.gaspa.surplus.data.AiFoodVision
import it.gaspa.surplus.data.AiTurn
import it.gaspa.surplus.data.AppData
import it.gaspa.surplus.data.AppStore
import it.gaspa.surplus.data.ImageForAi
import it.gaspa.surplus.data.Exercise
import it.gaspa.surplus.data.Food
import it.gaspa.surplus.data.FoodGroup
import it.gaspa.surplus.data.FoodInfo
import it.gaspa.surplus.data.FoodLookup
import it.gaspa.surplus.data.MigrazioneSchede
import it.gaspa.surplus.data.Moment
import it.gaspa.surplus.data.OpenFoodFacts
import it.gaspa.surplus.data.ReminderKey
import it.gaspa.surplus.data.Routine
import it.gaspa.surplus.data.Settings
import it.gaspa.surplus.data.SettingsRepo
import it.gaspa.surplus.data.TargetCalculator
import it.gaspa.surplus.data.UserProfile
import it.gaspa.surplus.data.computeTotals
import it.gaspa.surplus.logic.Advice
import it.gaspa.surplus.logic.AdvisorEngine
import it.gaspa.surplus.logic.CompilaEsercizio
import it.gaspa.surplus.logic.DayStatus
import it.gaspa.surplus.logic.DietQuality
import it.gaspa.surplus.logic.Targets
import it.gaspa.surplus.logic.WorkoutQueries
import it.gaspa.surplus.notify.ReminderScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = AppStore.get(app)
    private val settingsRepo = SettingsRepo(app)

    val data: StateFlow<AppData> = store.state

    val settings: StateFlow<Settings> = settingsRepo.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    // Orologio: i consigli si aggiornano col passare del tempo, non solo coi dati.
    private val tick: StateFlow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(30_000)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LocalDateTime.now())

    val advice: StateFlow<List<Advice>> = combine(data, settings, tick) { d, s, now ->
        val today = now.toLocalDate()
        val yesterday = today.minusDays(1)
        val yesterdayStatus =
            if (d.entries.any { it.date == yesterday.toString() }) dayStatus(d, yesterday) else null
        AdvisorEngine.advise(now, dayStatus(d, today), yesterdayStatus, targets(s))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun dayStatus(d: AppData, date: LocalDate): DayStatus {
        val dayEntries = d.entries.filter { it.date == date.toString() }
        val t = computeTotals(dayEntries)
        return DayStatus(
            t.kcal, t.prot, t.unknownCount, t.countByMoment,
            d.waterByDate[date.toString()] ?: 0,
            trained = WorkoutQueries.trainedOn(d.workouts, date.toString()),
            quality = DietQuality.of(dayEntries),
        )
    }

    fun targets(s: Settings) = Targets(s.kcalMin, s.kcalMax, s.protMin, s.protMax, s.waterTargetMl)

    fun shiftDay(delta: Long) {
        val next = _selectedDate.value.plusDays(delta)
        _selectedDate.value = if (next.isAfter(LocalDate.now())) LocalDate.now() else next
    }

    fun goToday() {
        _selectedDate.value = LocalDate.now()
    }

    fun addEntry(
        moment: Moment, nome: String, grams: Double,
        kcal100: Double?, prot100: Double?,
        saveFood: Boolean, isDrink: Boolean,
        fibra100: Double? = null, nova: Int? = null,
        gruppo: FoodGroup = FoodGroup.ALTRO, barcode: String? = null,
    ) {
        store.addEntry(
            _selectedDate.value.toString(), moment, nome, grams,
            kcal100, prot100, isDrink, fibra100, nova, gruppo,
        )
        if (saveFood) {
            store.addCustomFood(
                Food(
                    id = "custom-${UUID.randomUUID()}",
                    nome = nome.trim(),
                    kcal100 = kcal100,
                    prot100 = prot100,
                    fibra100 = fibra100,
                    nova = nova,
                    gruppo = gruppo,
                    isDrink = isDrink,
                    // Col codice a barre, la prossima scansione lo trova offline.
                    barcode = barcode,
                    custom = true,
                )
            )
        }
    }

    fun deleteEntry(id: String) = store.removeEntry(id)

    fun addWater(deltaMl: Int) = store.addWater(_selectedDate.value.toString(), deltaMl)

    fun setWeight(kg: Double) = store.setWeight(LocalDate.now().toString(), kg)

    fun removeWeight(date: String) = store.removeWeight(date)

    fun allFoods(d: AppData): List<Food> = store.allFoods(d)

    /** Correzione g↔ml di un alimento: resta salvata se il cibo è in dispensa. */
    fun setFoodUnit(food: Food, isDrink: Boolean) {
        if (food.custom) store.setFoodIsDrink(food.id, isDrink)
    }

    /** Alimenti usati di recente, per l'inserimento rapido. */
    fun recentFoods(d: AppData): List<Food> {
        val foods = allFoods(d)
        return d.entries
            .sortedByDescending { it.createdAt }
            .map { it.nome.lowercase() }
            .distinct()
            .mapNotNull { nomeLc -> foods.find { it.nome.lowercase() == nomeLc } }
            .take(8)
    }

    /** Il velo di spiegazione della scheda si mostra una volta sola. */
    fun markSchedaTutorialVisto() {
        val s = settings.value
        if (s.schedaTutorialVisto) return
        viewModelScope.launch { settingsRepo.save(s.copy(schedaTutorialVisto = true)) }
    }

    fun saveSettings(s: Settings) {
        viewModelScope.launch {
            settingsRepo.save(s)
            ReminderScheduler.rescheduleAll(getApplication(), s)
        }
    }

    // ---- Allenamento ----

    private val _openWorkoutId = MutableStateFlow<String?>(null)
    val openWorkoutId: StateFlow<String?> = _openWorkoutId.asStateFlow()

    fun allExercises(d: AppData): List<Exercise> = store.allExercises(d)

    /**
     * Un tocco sulla scheda e si parte. Se oggi quella scheda è già stata aperta,
     * la riprende invece di crearne un doppione (capita chiudendo per sbaglio).
     */
    fun avviaScheda(scheda: Routine?) {
        val oggi = LocalDate.now().toString()
        val rid = scheda?.id ?: MigrazioneSchede.LIBERO_ID
        val diOggi = data.value.workouts
            .filter { it.date == oggi && it.routineId == rid }
            .maxByOrNull { it.startedAt }
        _openWorkoutId.value = if (diOggi != null) {
            if (scheda != null) store.riapriConScheda(diOggi.id, scheda)
            diOggi.id
        } else {
            store.startWorkout(oggi, scheda)
        }
    }

    fun openWorkout(id: String) {
        _openWorkoutId.value = id
    }

    /** Chiude l'editor: restano solo gli esercizi fatti, una sessione vuota sparisce. */
    fun closeWorkout() {
        _openWorkoutId.value?.let { store.chiudiSessione(it) }
        _openWorkoutId.value = null
    }

    fun deleteWorkout(id: String) {
        store.deleteWorkout(id)
        if (_openWorkoutId.value == id) _openWorkoutId.value = null
    }

    fun addExerciseToWorkout(sessionId: String, exerciseId: String, nome: String) =
        store.addExerciseToWorkout(sessionId, exerciseId, nome)

    fun removeExerciseFromWorkout(sessionId: String, exerciseId: String) =
        store.removeExerciseFromWorkout(sessionId, exerciseId)

    fun salvaScheda(scheda: Routine) = store.salvaScheda(scheda)

    fun eliminaScheda(id: String) = store.eliminaScheda(id)

    fun aggiungiAScheda(schedaId: String, exerciseId: String) = store.aggiungiAScheda(schedaId, exerciseId)

    fun creaEsercizio(ex: Exercise): Exercise = store.creaEsercizio(ex)

    sealed interface EsitoCompila {
        data class DalleRegole(val p: CompilaEsercizio.Proposta) : EsitoCompila
        data class DallAi(val p: CompilaEsercizio.Proposta) : EsitoCompila
        data class NonRiconosciuto(val conChiave: Boolean) : EsitoCompila
        data class Errore(val messaggio: String) : EsitoCompila
    }

    /**
     * Compila un esercizio dal nome: prima le regole dell'app (gratis, senza
     * internet), l'AI con la chiave dell'utente solo se il nome non si riconosce.
     */
    suspend fun compilaEsercizio(nome: String): EsitoCompila {
        val salto = CompilaEsercizio.saltoAbituale(data.value.exercises.map { it.incrementoKg })
        CompilaEsercizio.daRegole(nome, salto)?.let { return EsitoCompila.DalleRegole(it) }
        val s = settings.value
        if (!s.aiAttiva) return EsitoCompila.NonRiconosciuto(conChiave = false)
        return runCatching { AiEsercizi.compila(s.aiProvider, s.aiApiKey, s.aiModelEffettivo, nome) }
            .fold(
                onSuccess = { p -> p?.let { EsitoCompila.DallAi(it) } ?: EsitoCompila.NonRiconosciuto(conChiave = true) },
                onFailure = { EsitoCompila.Errore(AiFoodVision.messaggioErrore(it)) },
            )
    }

    fun salvaEsercizio(ex: Exercise) = store.salvaEsercizio(ex)

    fun eliminaEsercizio(id: String) = store.eliminaEsercizio(id)

    fun addSet(sessionId: String, exerciseId: String, weightKg: Double, reps: Int) {
        store.addSet(sessionId, exerciseId, weightKg, reps)
        // Promemoria dinamico: ogni serie di una sessione di OGGI fa scorrere il timer.
        val s = settings.value
        val postEnabled = s.reminders.find { it.key == ReminderKey.POST_ALLENAMENTO }?.enabled == true
        val session = data.value.workouts.find { it.id == sessionId }
        if (postEnabled && s.postWorkoutDynamic && session?.date == LocalDate.now().toString()) {
            ReminderScheduler.armPostWorkoutIn(getApplication(), s.postWorkoutDelayMin)
        }
    }

    fun removeSet(sessionId: String, exerciseId: String, index: Int) =
        store.removeSet(sessionId, exerciseId, index)

    // ---- Profilo / onboarding ----

    val profile: StateFlow<UserProfile> = data
        .map { it.profile }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserProfile())

    /**
     * Chiude l'onboarding: salva il profilo, accetta il disclaimer e scrive
     * i target calcolati nelle impostazioni (poi restano modificabili a mano).
     */
    fun completeOnboarding(p: UserProfile, applyTargets: Boolean) {
        val anno = LocalDate.now().year
        val minor = p.annoNascita > 0 && (anno - p.annoNascita) < 18
        store.saveProfile(
            p.copy(
                isConfigured = true,
                isMinor = minor,
                disclaimerAcceptedAt = System.currentTimeMillis(),
            )
        )
        if (applyTargets) {
            val t = TargetCalculator.compute(p, anno)
            viewModelScope.launch {
                val s = settings.value.copy(
                    kcalMin = t.kcalMin, kcalMax = t.kcalMax,
                    protMin = t.protMin, protMax = t.protMax,
                    waterTargetMl = t.waterMl,
                )
                settingsRepo.save(s)
                ReminderScheduler.rescheduleAll(getApplication(), s)
            }
        }
    }

    fun saveProfile(p: UserProfile) = store.saveProfile(p)

    // ---- Backup ----

    fun exportJson(): String = store.exportJson()

    fun importJson(text: String): Result<Int> = store.importJson(text)

    // ---- Ricerca Open Food Facts (rete, opzionale) ----

    sealed interface OffState {
        data object Idle : OffState
        data object Loading : OffState
        data class Ready(val results: List<FoodInfo>) : OffState
        data class Failed(val message: String) : OffState
    }

    private val _offState = MutableStateFlow<OffState>(OffState.Idle)
    val offState: StateFlow<OffState> = _offState.asStateFlow()

    fun searchOnline(query: String) {
        if (query.isBlank()) return
        _offState.value = OffState.Loading
        viewModelScope.launch {
            runCatching { OpenFoodFacts.search(query) }
                .onSuccess { list ->
                    _offState.value =
                        if (list.isEmpty()) OffState.Failed("Nessun prodotto trovato per «$query».")
                        else OffState.Ready(list)
                }
                .onFailure {
                    _offState.value =
                        OffState.Failed("Ricerca non riuscita: controlla la connessione. Puoi comunque creare l'alimento a mano.")
                }
        }
    }

    fun clearOnlineSearch() {
        _offState.value = OffState.Idle
    }

    /** Salva un risultato online nella dispensa locale: da lì in poi è offline. */
    fun saveOnlineFood(r: FoodInfo): Food {
        val food = r.toFood("off-${UUID.randomUUID()}")
        store.addCustomFood(food)
        return food
    }

    // ---- Codice a barre ----

    sealed interface ScanState {
        data object Idle : ScanState
        data class Loading(val code: String) : ScanState
        /** Trovato: `local` = era già in dispensa, nessuna rete usata. */
        data class Found(val food: Food, val fonte: String, val local: Boolean) : ScanState
        /** Nessun archivio lo conosce: si passa all'inserimento manuale. */
        data class Unknown(val code: String, val message: String) : ScanState
    }

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    /**
     * Cerca un codice a barre: prima nella dispensa locale (istantaneo e
     * offline), poi nei due archivi pubblici in cascata.
     */
    fun lookupBarcode(code: String) {
        val clean = code.trim()
        val locale = store.findByBarcode(data.value, clean)
        if (locale != null) {
            _scanState.value = ScanState.Found(locale, "dispensa", local = true)
            return
        }
        _scanState.value = ScanState.Loading(clean)
        viewModelScope.launch {
            when (val outcome = FoodLookup.byBarcode(clean)) {
                is FoodLookup.Outcome.Found -> {
                    val food = outcome.info.toFood("bar-${UUID.randomUUID()}")
                    store.addCustomFood(food)
                    _scanState.value = ScanState.Found(food, outcome.info.fonte, local = false)
                }
                is FoodLookup.Outcome.NotFound -> _scanState.value = ScanState.Unknown(
                    clean,
                    "Prodotto non presente né in Open Food Facts né in USDA. Inseriscilo a mano: resterà legato a questo codice e la prossima volta sarà immediato.",
                )
                is FoodLookup.Outcome.Error -> _scanState.value = ScanState.Unknown(clean, outcome.message)
            }
        }
    }

    fun clearScan() {
        _scanState.value = ScanState.Idle
    }

    // ---- Riconoscimento dalle foto (solo con la chiave dell'utente) ----

    /**
     * Uno scatto raccolto. `gruppo` dice a quale alimento appartiene: due foto
     * con lo stesso numero (per esempio il piatto e la sua etichetta) vengono
     * analizzate insieme come un'unica cosa.
     */
    data class Scatto(
        val id: Long,
        val preparata: ImageForAi.Preparata,
        val miniatura: Bitmap?,
        val gruppo: Int,
    )

    /** Esito di un gruppo di foto, con la sua conversazione sui dubbi. */
    data class GruppoRisultato(
        val gruppo: Int,
        val esito: AiAnalysis,
        val risposteDate: List<String>,
    )

    sealed interface FotoState {
        /** Raccolta scatti: si aggiungono foto e si decide cosa sta con cosa. */
        data object Raccolta : FotoState
        data object Preparo : FotoState
        data class Analizzo(val fatti: Int, val totali: Int) : FotoState
        data class Pronto(val risultati: List<GruppoRisultato>) : FotoState
        data class Fallito(val messaggio: String) : FotoState
    }

    private val _fotoState = MutableStateFlow<FotoState>(FotoState.Raccolta)
    val fotoState: StateFlow<FotoState> = _fotoState.asStateFlow()

    private val _scatti = MutableStateFlow<List<Scatto>>(emptyList())
    val scatti: StateFlow<List<Scatto>> = _scatti.asStateFlow()

    /** true = tutte le foto sono lo stesso alimento; false = alimenti distinti da raggruppare. */
    private val _unicoAlimento = MutableStateFlow(true)
    val unicoAlimento: StateFlow<Boolean> = _unicoAlimento.asStateFlow()

    /** Conversazione per gruppo: le risposte già date ai dubbi. */
    private val turniPerGruppo = mutableMapOf<Int, MutableList<AiTurn>>()
    private var prossimoId = 1L

    fun aggiungiScatto(uri: Uri) {
        _fotoState.value = FotoState.Preparo
        viewModelScope.launch {
            val app = getApplication<Application>()
            val preparata = ImageForAi.prepara(app, uri)
            if (preparata == null) {
                _fotoState.value = FotoState.Fallito("Non sono riuscito a leggere questa immagine.")
                return@launch
            }
            val mini = ImageForAi.miniatura(app, uri)
            // Con "un solo alimento" tutto finisce nel gruppo 1; altrimenti ogni
            // foto parte come alimento a sé e l'utente unisce quelle che vuole.
            val gruppo = if (_unicoAlimento.value) 1 else (_scatti.value.maxOfOrNull { it.gruppo } ?: 0) + 1
            _scatti.value = _scatti.value + Scatto(prossimoId++, preparata, mini, gruppo)
            _fotoState.value = FotoState.Raccolta
        }
    }

    fun rimuoviScatto(id: Long) {
        _scatti.value = _scatti.value.filterNot { it.id == id }
    }

    /** Sposta uno scatto sull'alimento successivo, o lo stacca come alimento nuovo. */
    fun cambiaGruppo(id: Long) {
        val lista = _scatti.value
        val massimo = maxOf(lista.size, lista.maxOfOrNull { it.gruppo } ?: 1)
        _scatti.value = lista.map {
            if (it.id != id) it else it.copy(gruppo = if (it.gruppo >= massimo) 1 else it.gruppo + 1)
        }
    }

    fun impostaUnicoAlimento(unico: Boolean) {
        _unicoAlimento.value = unico
        // Passando a "un solo alimento" tutte le foto si uniscono; tornando indietro
        // si ri-separano, così la scelta è sempre reversibile senza rifare le foto.
        _scatti.value = if (unico) {
            _scatti.value.map { it.copy(gruppo = 1) }
        } else {
            _scatti.value.mapIndexed { i, s -> s.copy(gruppo = i + 1) }
        }
    }

    fun avviaAnalisi() {
        val s = settings.value
        if (!s.aiAttiva) {
            _fotoState.value = FotoState.Fallito(
                "Per usare le foto serve la tua chiave: mettila in Impostazioni → Riconoscimento foto."
            )
            return
        }
        if (_scatti.value.isEmpty()) return
        turniPerGruppo.clear()
        viewModelScope.launch { analizzaGruppi(_scatti.value.map { it.gruppo }.distinct().sorted()) }
    }

    /** Risponde al dubbio di UN gruppo e rianalizza solo quello. */
    fun rispondiAlDubbio(gruppo: Int, risposta: String) {
        val testo = risposta.trim()
        if (testo.isBlank()) return
        val corrente = (_fotoState.value as? FotoState.Pronto)?.risultati
            ?.firstOrNull { it.gruppo == gruppo } ?: return
        val turni = turniPerGruppo.getOrPut(gruppo) { mutableListOf() }
        corrente.esito.domanda?.let { turni += AiTurn(fromUser = false, text = it) }
        turni += AiTurn(fromUser = true, text = testo)
        viewModelScope.launch { analizzaGruppi(listOf(gruppo), mantieni = corrente.gruppo) }
    }

    /** Analizza i gruppi indicati, tenendo i risultati degli altri già pronti. */
    private suspend fun analizzaGruppi(gruppi: List<Int>, mantieni: Int? = null) {
        val s = settings.value
        val giaPronti = (_fotoState.value as? FotoState.Pronto)?.risultati.orEmpty()
            .filterNot { it.gruppo in gruppi || it.gruppo == mantieni }
        val risultati = giaPronti.toMutableList()
        var errore: String? = null

        gruppi.forEachIndexed { i, g ->
            _fotoState.value = FotoState.Analizzo(i, gruppi.size)
            val immagini = _scatti.value.filter { it.gruppo == g }.map { it.preparata }
            if (immagini.isEmpty()) return@forEachIndexed
            val turni = turniPerGruppo[g].orEmpty()
            runCatching {
                AiFoodVision.analizza(
                    provider = s.aiProvider,
                    apiKey = s.aiApiKey,
                    model = s.aiModelEffettivo,
                    immagini = immagini,
                    // Più foto nello stesso gruppo = viste diverse della stessa cosa.
                    unico = _unicoAlimento.value || immagini.size > 1,
                    turni = turni,
                )
            }.onSuccess { esito ->
                if (esito.alimenti.isNotEmpty() || esito.domanda != null) {
                    risultati += GruppoRisultato(
                        gruppo = g,
                        esito = esito,
                        risposteDate = turni.filter { it.fromUser }.map { it.text },
                    )
                }
            }.onFailure { e -> errore = AiFoodVision.messaggioErrore(e) }
        }

        _fotoState.value = when {
            risultati.isNotEmpty() -> FotoState.Pronto(risultati.sortedBy { it.gruppo })
            errore != null -> FotoState.Fallito(errore!!)
            else -> FotoState.Fallito("Non ho riconosciuto cibo nelle foto. Prova a inquadrare meglio.")
        }
    }

    /**
     * Registra nel diario gli alimenti confermati, col nome scelto dall'utente,
     * e li mette in dispensa: la prossima volta si ritrovano con la ricerca, senza
     * rifare la foto. Solo quelli spuntati: le proposte scartate non lasciano traccia.
     */
    fun aggiungiDaFoto(moment: Moment, scelti: List<AiFoodItem>) {
        val fonte = settings.value.aiProvider.label
        scelti.forEach { item ->
            val food = item.toFood("ai-${UUID.randomUUID()}", fonte)
            store.addEntry(
                _selectedDate.value.toString(), moment, food.nome, item.grammi,
                food.kcal100, food.prot100, false, food.fibra100, food.nova, food.gruppo,
            )
            // Se in dispensa c'è già un alimento con lo stesso nome, resta quello.
            store.addCustomFood(food)
        }
    }

    fun chiudiFoto() {
        _scatti.value.forEach { it.miniatura?.recycle() }
        _scatti.value = emptyList()
        turniPerGruppo.clear()
        _unicoAlimento.value = true
        _fotoState.value = FotoState.Raccolta
    }

    /** Torna alla raccolta senza perdere le foto già scattate. */
    fun tornaAllaRaccolta() {
        _fotoState.value = FotoState.Raccolta
    }

    /** Token immagine stimati per gli scatti raccolti: serve a mostrare il costo. */
    fun tokenFoto(): Int =
        _scatti.value.sumOf { ImageForAi.tokenStimati(it.preparata.larghezza, it.preparata.altezza) }
}
