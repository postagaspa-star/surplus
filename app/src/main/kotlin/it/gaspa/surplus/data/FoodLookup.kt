package it.gaspa.surplus.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Prodotto trovato in un archivio pubblico, indipendente dalla fonte.
 * Stesso formato per Open Food Facts e per USDA, così la UI non deve sapere
 * da dove arriva il dato (lo mostra e basta).
 */
data class FoodInfo(
    val nome: String,
    val marca: String?,
    val kcal100: Double?,
    val prot100: Double?,
    val grassi100: Double?,
    val carb100: Double?,
    val fibra100: Double? = null,
    val grassiSaturi100: Double? = null,
    val zuccheri100: Double? = null,
    val sale100: Double? = null,
    val nova: Int? = null,
    val gruppo: FoodGroup = FoodGroup.ALTRO,
    val isDrink: Boolean = false,
    val barcode: String? = null,
    val fonte: String = "",
) {
    val nomeCompleto: String get() = if (marca != null) "$nome ($marca)" else nome

    fun toFood(id: String): Food = Food(
        id = id,
        nome = nomeCompleto,
        kcal100 = kcal100,
        prot100 = prot100,
        grassi100 = grassi100,
        carb100 = carb100,
        fibra100 = fibra100,
        grassiSaturi100 = grassiSaturi100,
        zuccheri100 = zuccheri100,
        sale100 = sale100,
        nova = nova,
        gruppo = gruppo,
        isDrink = isDrink,
        hint = "da $fonte",
        barcode = barcode,
        custom = true,
    )
}

/** Errore di rete con il messaggio del servizio: senza, una chiave sbagliata darebbe solo "errore". */
class HttpFailure(val code: Int, val body: String) : Exception("HTTP $code")

/** GET/POST minimali condivisi: nessuna libreria di rete in più. */
internal object Http {
    suspend fun get(url: String, userAgent: String, timeoutMs: Int = 8000): String =
        withContext(Dispatchers.IO) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("User-Agent", userAgent)
            }
            try {
                conn.inputStream.bufferedReader().use(BufferedReader::readText)
            } finally {
                conn.disconnect()
            }
        }

    /**
     * GET con intestazioni (per esempio il permesso del NAS). Come [postJson], in
     * caso di errore lancia [HttpFailure] col codice: chi chiama deve poter
     * distinguere "non abbinato" da "NAS spento".
     */
    suspend fun getJson(
        url: String,
        headers: Map<String, String>,
        timeoutMs: Int,
    ): String = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = timeoutMs
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.bufferedReader().use(BufferedReader::readText)
            } else {
                val err = conn.errorStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
                throw HttpFailure(code, err)
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * POST JSON. In caso di errore legge anche il corpo della risposta: è lì che
     * i servizi scrivono "chiave non valida" o "credito esaurito", e senza quel
     * testo l'utente non saprebbe cosa ha sbagliato.
     */
    suspend fun postJson(
        url: String,
        body: String,
        headers: Map<String, String>,
        timeoutMs: Int = 90_000,
    ): String = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = timeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.bufferedReader().use(BufferedReader::readText)
            } else {
                val err = conn.errorStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
                throw HttpFailure(code, err)
            }
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * Ricerca di un prodotto dal codice a barre, con due archivi in cascata:
 * Open Food Facts (ottima copertura italiana) e, se lì non c'è, USDA FoodData
 * Central (forte su integratori, barrette e marche estere).
 *
 * Nessun dato dell'utente esce dal telefono: parte solo il codice del prodotto.
 * Se nessuno dei due lo conosce, il chiamante apre l'inserimento manuale.
 */
object FoodLookup {

    sealed interface Outcome {
        data class Found(val info: FoodInfo) : Outcome
        data object NotFound : Outcome
        data class Error(val message: String) : Outcome
    }

    /** Un codice a barre è fatto solo di cifre (EAN-8/13, UPC-A/E). */
    fun isBarcode(code: String): Boolean =
        code.length in 6..14 && code.all { it.isDigit() }

    /** In ordine: chi ha più probabilità di conoscere il prodotto viene prima. */
    private val providers: List<suspend (String) -> FoodInfo?> = listOf(
        OpenFoodFacts::byBarcode,
        UsdaFoodData::byBarcode,
    )

    suspend fun byBarcode(code: String): Outcome {
        val clean = code.trim()
        if (!isBarcode(clean)) {
            return Outcome.Error("«$clean» non sembra il codice a barre di un prodotto.")
        }
        var failed = 0
        for (lookup in providers) {
            runCatching { lookup(clean) }
                .onSuccess { info -> if (info != null) return Outcome.Found(info) }
                .onFailure { failed++ }
        }
        // Tutti irraggiungibili = problema di rete; altrimenti il prodotto non c'è.
        return if (failed == providers.size) Outcome.Error(NO_NET) else Outcome.NotFound
    }

    private const val NO_NET =
        "Archivi non raggiungibili: controlla la connessione. Puoi comunque inserire il prodotto a mano."
}
