package it.gaspa.surplus.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Identità dell'utente corrente. Oggi è sempre quella locale; con Firebase Auth
 * `uid` diventerà l'UID Firebase e `email` l'account collegato.
 */
data class UserIdentity(
    val userId: String = UserProfile.LOCAL_USER,
    val email: String? = null,
    val isRemote: Boolean = false,
)

sealed interface SyncState {
    /** Nessun account: tutto in locale. È lo stato di default e resterà sempre possibile. */
    data object LocalOnly : SyncState
    data class Synced(val lastSyncAt: Long) : SyncState
    data class Error(val message: String) : SyncState
}

/**
 * Contratto per la sincronizzazione cloud. Oggi esiste solo l'implementazione
 * locale (no-op): l'app resta 100% offline e senza account.
 *
 * Quando/se arriverà Firebase, l'unica cosa da fare è una FirebaseSyncGateway
 * (Auth per l'identità + Firestore `users/<uid>/appData` per i dati) e passarla
 * ad AppStore: nessun'altra parte dell'app cambia, perché tutto passa da qui.
 *
 * Scelta di fondo: local-first. La sorgente di verità resta il file su disco,
 * il cloud è una copia. Così chi non fa login non perde nulla e chi lo fa non
 * dipende dalla rete per usare l'app.
 */
interface SyncGateway {
    val identity: Flow<UserIdentity>
    val state: Flow<SyncState>

    /** Carica i dati locali sul cloud (chiamata dopo ogni mutazione, se attivo). */
    suspend fun push(userId: String, data: AppData)

    /** Scarica i dati del cloud, o null se non ce ne sono / non è attivo. */
    suspend fun pull(userId: String): AppData?

    /**
     * Manda il diario e restituisce quello unito, o null se non c'è niente da
     * fare (nessun abbinamento) o se la sincronizzazione non è riuscita.
     *
     * È diverso da [push]: col NAS di casa non si "carica" e basta, si uniscono
     * le due versioni, e quello che torna può contenere cose scritte sull'altro
     * apparecchio. Chi chiama decide se tenerle (AppStore, che controlla anche
     * che l'unione non svuoti il diario).
     */
    suspend fun sincronizza(userId: String, data: AppData): AppData? = null

    /**
     * L'ultimo diario restituito da [sincronizza] è stato tenuto e scritto sul
     * disco. Solo da qui in poi vale come base: una risposta scartata (arrivata
     * mentre scrivevi un pasto, o mai arrivata) non deve contare, altrimenti
     * tutto quello che contiene sembrerebbe cancellato alla volta dopo.
     */
    suspend fun tenuto() = Unit

    fun isActive(): Boolean
}

/** Implementazione di default: non fa nulla, l'app vive sul telefono. */
class LocalOnlyGateway : SyncGateway {
    private val _identity = MutableStateFlow(UserIdentity())
    private val _state = MutableStateFlow<SyncState>(SyncState.LocalOnly)

    override val identity = _identity.asStateFlow()
    override val state = _state.asStateFlow()

    override suspend fun push(userId: String, data: AppData) = Unit
    override suspend fun pull(userId: String): AppData? = null
    override fun isActive(): Boolean = false
}
