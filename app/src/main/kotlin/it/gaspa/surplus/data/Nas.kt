package it.gaspa.surplus.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Il server di Surplus sul NAS di casa: tiene il diario e lo unisce fra telefono
 * e computer, così quello che scrivi non vive su un solo apparecchio.
 *
 * Fuori da qui non esce nient'altro: niente account, niente servizi esterni, e
 * il diario va soltanto al NAS indicato da chi usa l'app.
 *
 * Il telefono non manda "la verità": manda il suo diario e riceve quello unito.
 * Chi decide come unire è il NAS (server/app/unione.py), con una regola sola:
 * nessuna delle due parti sovrascrive l'altra, e una voce cancellata resta
 * cancellata.
 */
object Nas {

    private const val UA = "Surplus/2.7 (telefono)"

    /** Il nome con cui questo telefono si presenta al NAS. */
    fun nomeDispositivo(): String = (android.os.Build.MODEL ?: "Telefono").take(40)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Da quello che uno scrive ("192.168.1.20:8600") all'indirizzo completo.
     * Senza http davanti si mette http, tranne i nomi di Tailscale che vogliono https.
     */
    fun normalizza(scritto: String?): String? {
        val s = (scritto ?: "").trim()
        if (s.isEmpty()) return null
        val completo = if (s.contains("://")) s else (if (s.contains(".ts.net", true)) "https://" else "http://") + s
        return try {
            val u = URL(completo)
            if (u.protocol != "http" && u.protocol != "https") return null
            if (u.host.isNullOrBlank()) return null
            u.protocol + "://" + u.authority
        } catch (_: Exception) {
            null
        }
    }

    /** Chiede al NAS il permesso per questo telefono, con un codice di 8 cifre. */
    suspend fun abbina(indirizzo: String, codice: String, nome: String = nomeDispositivo()): Result<String> =
        runCatching {
            val radice = normalizza(indirizzo) ?: throw IOException("indirizzo")
            val corpo = buildJsonObject {
                put("codice", codice.trim())
                put("nome", nome)
                put("tipo", "telefono")
            }
            val risposta = Http.postJson("$radice/api/abbina", corpo.toString(), mapOf("User-Agent" to UA), timeoutMs = 20_000)
            Json.parseToJsonElement(risposta).jsonObject["token"]?.jsonPrimitive?.content
                ?: throw IOException("il NAS non ha dato il permesso")
        }

    /**
     * Manda il diario e riceve quello unito, con la sua revisione. [base] è la
     * revisione dell'ultimo diario unito che il telefono ha tenuto: null se non
     * si sa (appena abbinato, o app aggiornata da una versione che non la segnava).
     */
    suspend fun sincronizza(indirizzo: String, token: String, dati: AppData, base: Long? = null): Result<Unione> = runCatching {
        val radice = normalizza(indirizzo) ?: throw IOException("indirizzo")
        val corpo = buildJsonObject {
            put("dati", json.encodeToJsonElement(AppData.serializer(), dati))
            if (base != null && base >= 0) put("base", base)
        }
        val risposta = Http.postJson(
            "$radice/api/sincronizza",
            corpo.toString(),
            mapOf("User-Agent" to UA, "Authorization" to "Bearer $token"),
            timeoutMs = 60_000,
        )
        val oggetto = Json.parseToJsonElement(risposta).jsonObject
        val unito = oggetto["dati"] as? JsonObject
            ?: throw IOException("il NAS non ha mandato il diario")
        val revisione = oggetto["revisione"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: throw IOException("il NAS non ha detto la revisione")
        Unione(json.decodeFromJsonElement(AppData.serializer(), unito), revisione)
    }

    /** La versione dell'app pubblicata sul NAS, se è più nuova di quella installata. */
    suspend fun versioneAndroid(indirizzo: String, codiceMio: Int): Result<VersioneDaScaricare?> = runCatching {
        val radice = normalizza(indirizzo) ?: throw IOException("indirizzo")
        val risposta = Http.get("$radice/api/versione", UA, timeoutMs = 12_000)
        val a = Json.parseToJsonElement(risposta).jsonObject["android"] as? JsonObject ?: return@runCatching null
        val codice = a["codice"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@runCatching null
        if (codice <= codiceMio) return@runCatching null
        VersioneDaScaricare(
            codice = codice,
            nome = a["nome"]?.jsonPrimitive?.content ?: codice.toString(),
            sha256 = a["sha256"]?.jsonPrimitive?.content,
            byte = a["byte"]?.jsonPrimitive?.content?.toLongOrNull(),
        )
    }

    /** Scarica l'app nuova dal NAS, senza tenerla in memoria. */
    suspend fun scaricaApk(indirizzo: String, destinazione: File): Boolean = withContext(Dispatchers.IO) {
        val radice = normalizza(indirizzo) ?: return@withContext false
        val conn = (URL("$radice/scarica/surplus.apk").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 120_000
            setRequestProperty("User-Agent", UA)
        }
        try {
            if (conn.responseCode !in 200..299) return@withContext false
            destinazione.parentFile?.mkdirs()
            conn.inputStream.use { ingresso -> destinazione.outputStream().use { ingresso.copyTo(it) } }
            true
        } catch (_: IOException) {
            false
        } finally {
            conn.disconnect()
        }
    }

    /** Una frase da mostrare, al posto dell'errore tecnico. */
    fun messaggio(e: Throwable): String = when {
        e is HttpFailure && e.code == 401 ->
            "Questo telefono non è più abbinato al NAS. Abbinalo di nuovo con un codice."
        e is HttpFailure && e.code == 429 ->
            "Troppi codici sbagliati. Aspetta un quarto d'ora e chiedine uno nuovo."
        e is HttpFailure && e.code == 404 -> "A questo indirizzo il NAS non ha Surplus."
        e is HttpFailure -> "Il NAS ha risposto male (${e.code})."
        e.message == "indirizzo" -> "L'indirizzo del NAS non è scritto bene."
        else -> "Il NAS non risponde. Controlla che sia acceso e di essere sulla rete di casa."
    }
}

/** Una versione dell'app pubblicata sul NAS. */
/** Il diario unito dal NAS e il numero che il NAS gli ha dato. */
data class Unione(val dati: AppData, val revisione: Long)

data class VersioneDaScaricare(
    val codice: Int,
    val nome: String,
    val sha256: String?,
    val byte: Long?,
)
