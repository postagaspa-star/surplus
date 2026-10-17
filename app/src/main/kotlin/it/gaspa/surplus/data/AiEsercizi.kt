package it.gaspa.surplus.data

import it.gaspa.surplus.logic.CompilaEsercizio
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Compila un esercizio dal nome con l'AI, usando la chiave dell'utente.
 *
 * Entra in gioco solo quando le regole dell'app non riconoscono il nome
 * (vedi [CompilaEsercizio]): parte solo il nome dell'esercizio, niente del diario.
 */
object AiEsercizi {

    private val json = Json { ignoreUnknownKeys = true }

    internal val GRUPPI = listOf(
        "Petto", "Schiena", "Spalle", "Salute spalle", "Bicipiti", "Tricipiti", "Avambracci",
        "Trapezio", "Gambe", "Glutei", "Polpacci", "Addominali", "Altro",
    )

    private const val NON_RICONOSCIUTO = "nome non riconosciuto"

    internal fun prompt(nome: String): String = """
Sei un allenatore di palestra italiano. Compila la scheda dell'esercizio «${nome.trim()}» come la scriveresti per un ragazzo che si allena per crescere di massa muscolare.
- gruppo: il muscolo principale, esattamente uno tra: ${GRUPPI.joinToString(", ")}
- serie: il numero di serie, come "3" oppure "3-4"
- repMin e repMax: il range di ripetizioni per l'ipertrofia (per un esercizio a tempo, come il plank, i secondi)
- incrementoKg: di quanto sale il peso con un disco in più con l'attrezzo tipico di questo esercizio (bilanciere 2.5, macchina o cavi 5, manubri 1 o 2); 0 se si fa a corpo libero
- nota: un consiglio di tecnica brevissimo, massimo 8 parole, oppure stringa vuota
Se il nome non è un esercizio che riconosci, metti gruppo "Altro" e nota "$NON_RICONOSCIUTO".
Rispondi in italiano.
""".trim()

    private val CAMPI = listOf("gruppo", "serie", "repMin", "repMax", "incrementoKg", "nota")

    private fun schemaClaude(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("gruppo") {
                put("type", "string")
                putJsonArray("enum") { GRUPPI.forEach { add(it) } }
            }
            putJsonObject("serie") { put("type", "string") }
            putJsonObject("repMin") { put("type", "integer") }
            putJsonObject("repMax") { put("type", "integer") }
            putJsonObject("incrementoKg") { put("type", "number") }
            putJsonObject("nota") { put("type", "string") }
        }
        putJsonArray("required") { CAMPI.forEach { add(it) } }
        put("additionalProperties", false)
    }

    private fun schemaGemini(): JsonObject = buildJsonObject {
        put("type", "OBJECT")
        putJsonObject("properties") {
            putJsonObject("gruppo") {
                put("type", "STRING")
                putJsonArray("enum") { GRUPPI.forEach { add(it) } }
            }
            putJsonObject("serie") { put("type", "STRING") }
            putJsonObject("repMin") { put("type", "INTEGER") }
            putJsonObject("repMax") { put("type", "INTEGER") }
            putJsonObject("incrementoKg") { put("type", "NUMBER") }
            putJsonObject("nota") { put("type", "STRING") }
        }
        putJsonArray("required") { CAMPI.forEach { add(it) } }
    }

    /** La proposta dell'AI, oppure null se nemmeno l'AI riconosce il nome. */
    suspend fun compila(
        provider: AiProvider,
        apiKey: String,
        model: String,
        nome: String,
    ): CompilaEsercizio.Proposta? {
        val grezzo = when (provider) {
            AiProvider.CLAUDE -> chiamaClaude(apiKey, model, prompt(nome))
            AiProvider.GEMINI -> chiamaGemini(apiKey, model, prompt(nome))
        }
        return parse(grezzo)
    }

    private suspend fun chiamaClaude(apiKey: String, model: String, testo: String): String {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", 2000)
            putJsonObject("output_config") {
                // Un nome da classificare, non un problema da ragionare: sforzo basso.
                put("effort", "low")
                putJsonObject("format") {
                    put("type", "json_schema")
                    put("schema", schemaClaude())
                }
            }
            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", testo)
                })
            }
        }.toString()
        val risposta = Http.postJson(
            "https://api.anthropic.com/v1/messages",
            body,
            mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01"),
            timeoutMs = 30_000,
        )
        val root = json.parseToJsonElement(risposta).jsonObject
        return root["content"]?.jsonArray
            ?.map { it.jsonObject }
            ?.firstOrNull { it["type"]?.jsonPrimitive?.content == "text" }
            ?.get("text")?.jsonPrimitive?.content
            ?: throw IllegalStateException("Risposta senza testo")
    }

    private suspend fun chiamaGemini(apiKey: String, model: String, testo: String): String {
        val body = buildJsonObject {
            putJsonArray("contents") {
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("parts") { add(buildJsonObject { put("text", testo) }) }
                })
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("responseSchema", schemaGemini())
            }
        }.toString()
        val risposta = Http.postJson(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
            body,
            mapOf("x-goog-api-key" to apiKey),
            timeoutMs = 30_000,
        )
        val root = json.parseToJsonElement(risposta).jsonObject
        return root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("text")?.jsonPrimitive?.content
            ?: throw IllegalStateException("Risposta senza testo")
    }

    /** Legge la risposta difendendosi da valori strani: meglio un campo vuoto che uno assurdo. */
    internal fun parse(grezzo: String): CompilaEsercizio.Proposta? {
        val o = json.parseToJsonElement(AiFoodVision.estraiJson(grezzo)).jsonObject
        fun testo(k: String) = runCatching { o[k]?.jsonPrimitive?.content }.getOrNull()?.trim().orEmpty()
        fun numero(k: String) = testo(k).replace(',', '.').toDoubleOrNull()

        val nota = testo("nota")
        if (nota.contains(NON_RICONOSCIUTO, ignoreCase = true)) return null

        val gruppo = GRUPPI.firstOrNull { it.equals(testo("gruppo"), ignoreCase = true) } ?: "Altro"
        val serie = testo("serie").takeIf { Regex("^\\d{1,2}(-\\d{1,2})?$").matches(it) } ?: "3"
        val a = numero("repMin")?.toInt()?.takeIf { it in 1..120 }
        val b = numero("repMax")?.toInt()?.takeIf { it in 1..120 }
        val (repMin, repMax) = when {
            a != null && b != null -> if (a <= b) a to b else b to a
            a != null -> a to a
            b != null -> b to b
            else -> 8 to 12
        }
        return CompilaEsercizio.Proposta(
            gruppo = gruppo,
            serie = serie,
            repMin = repMin,
            repMax = repMax,
            incrementoKg = numero("incrementoKg")?.takeIf { it > 0 && it <= 20 },
            nota = nota.takeIf { it.isNotBlank() }?.take(80),
        )
    }
}
