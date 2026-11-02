package it.gaspa.surplus.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * La sincronizzazione col NAS di casa, al posto del vecchio innesto cloud.
 *
 * Scelta di fondo, invariata: local-first. Il file sul telefono resta la verità
 * per il telefono, si usa l'app anche col NAS spento, e quello che hai scritto
 * parte alla prima occasione. Il NAS non comanda: unisce.
 *
 * Qui non si applica niente ai dati: si parla col NAS e si restituisce il
 * diario unito. A tenerlo o no ci pensa [AppStore], che prima controlla che
 * l'unione non stia cancellando mezzo diario.
 */
class NasGateway(
    context: Context,
    private val impostazioni: SettingsRepo = SettingsRepo(context),
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : SyncGateway {

    private val _identity = MutableStateFlow(UserIdentity())
    private val _state = MutableStateFlow<SyncState>(SyncState.LocalOnly)

    override val identity: Flow<UserIdentity> = _identity.asStateFlow()
    override val state: Flow<SyncState> = _state.asStateFlow()

    /** L'ultima copia conosciuta: [isActive] viene chiamata senza poter aspettare. */
    @Volatile
    private var ultime: Settings? = null

    init {
        scope.launch {
            impostazioni.flow.collect { s ->
                ultime = s
                if (!s.nasAbbinato) _state.value = SyncState.LocalOnly
            }
        }
    }

    override fun isActive(): Boolean = ultime?.nasAbbinato == true

    override suspend fun push(userId: String, data: AppData) {
        // Col NAS non esiste un "carica e basta": si passa sempre dall'unione.
        sincronizza(userId, data)
    }

    override suspend fun pull(userId: String): AppData? = null

    override suspend fun sincronizza(userId: String, data: AppData): AppData? {
        val s = ultime ?: impostazioni.current().also { ultime = it }
        if (!s.nasAbbinato) return null
        val esito = Nas.sincronizza(s.nasIndirizzo, s.nasToken, data)
        esito.onSuccess {
            val adesso = System.currentTimeMillis()
            _state.value = SyncState.Synced(adesso)
            impostazioni.salvaNas(ultima = adesso)
        }
        esito.onFailure { e ->
            _state.value = SyncState.Error(Nas.messaggio(e))
            if (e is HttpFailure && e.code == 401) {
                // Il NAS non ci conosce più: si butta il permesso, così l'app
                // chiede un codice nuovo invece di riprovare a vuoto per sempre.
                impostazioni.salvaNas(token = "")
            }
        }
        return esito.getOrNull()
    }

    /** Abbina questo telefono con un codice preso dal NAS. */
    suspend fun abbina(indirizzoScritto: String, codice: String): Result<Unit> {
        val indirizzo = Nas.normalizza(indirizzoScritto)
            ?: return Result.failure(IllegalArgumentException("indirizzo"))
        return Nas.abbina(indirizzo, codice).map { token ->
            impostazioni.salvaNas(indirizzo = indirizzo, token = token)
            ultime = impostazioni.current()
            _state.value = SyncState.Synced(System.currentTimeMillis())
        }
    }

    /** Stacca il telefono: il diario resta da tutte e due le parti, ma non si aggiornano più. */
    suspend fun stacca() {
        impostazioni.salvaNas(token = "", ultima = 0L)
        ultime = impostazioni.current()
        _state.value = SyncState.LocalOnly
    }

    companion object {
        @Volatile private var istanza: NasGateway? = null

        /**
         * Uno solo per tutta l'app: lo usano sia AppStore (che sincronizza) sia
         * le impostazioni (che abbinano e mostrano com'è andata). Con due
         * oggetti diversi la schermata direbbe "mai sincronizzato" anche subito
         * dopo una sincronizzazione riuscita.
         */
        fun get(context: Context): NasGateway =
            istanza ?: synchronized(this) {
                istanza ?: NasGateway(context.applicationContext).also { istanza = it }
            }
    }
}
