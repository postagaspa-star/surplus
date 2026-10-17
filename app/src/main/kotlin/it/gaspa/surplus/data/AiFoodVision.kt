package it.gaspa.surplus.data

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
 * Riconoscimento del cibo da una foto, con la chiave dell'utente.
 *
 * Regola di progetto: **nessuna chiave è dentro l'app**. Chi vuole questa
 * funzione incolla la propria nelle impostazioni; senza chiave la funzione non
 * compare e tutto il resto continua a lavorare offline come sempre. Così
 * nessuno può estrarre una chiave dall'APK e spendere soldi altrui.
 */
enum class AiProvider(val label: String, val modelDefault: String, val comeOttenerla: String) {
    CLAUDE(
        "Claude (Anthropic)",
        "claude-opus-5",
        "console.anthropic.com → API keys (a consumo, serve credito)",
    ),
    GEMINI(
        "Google Gemini",
        "gemini-2.5-flash",
        "aistudio.google.com → Get API key (ha un piano gratuito con limiti giornalieri)",
    ),
}

/** Un alimento riconosciuto nella foto. Valori per 100 g, come nel resto dell'app. */
data class AiFoodItem(
    val nome: String,
    val grammi: Double,
    val kcal100: Double?,
    val prot100: Double?,
    val grassi100: Double?,
    val carb100: Double?,
    val fibra100: Double?,
    val nova: Int?,
    val gruppo: FoodGroup,
    val certezza: String,
) {
    fun toFood(id: String, fonte: String): Food = Food(
        id = id,
        nome = nome,
        kcal100 = kcal100,
        prot100 = prot100,
        grassi100 = grassi100,
        carb100 = carb100,
        fibra100 = fibra100,
        nova = nova,
        gruppo = gruppo,
        isDrink = false,
        hint = "riconosciuto da $fonte · certezza $certezza",
        custom = true,
    )
}

/** Esito di un'analisi: gli alimenti trovati e, se serve, UNA domanda di chiarimento. */
data class AiAnalysis(
    val alimenti: List<AiFoodItem>,
    val domanda: String?,
    val note: String?,
)

/** Un turno della conversazione: serve per rispondere ai dubbi senza rimandare la foto. */
data class AiTurn(val fromUser: Boolean, val text: String)

object AiFoodVision {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private const val PROMPT = """
Sei l'assistente di un diario alimentare italiano. Guarda la foto e dimmi cosa c'è da mangiare.

DUE CASI DIVERSI:
1. ETICHETTA o CONFEZIONE di un prodotto: LEGGI i valori dichiarati sulla tabella nutrizionale, non stimarli. Usa il nome del prodotto e la marca. La quantità in grammi è quella della porzione che la persona sta per mangiare: se non è deducibile, usa 100.
2. CIBO VERO (un piatto, un panino, un pasto): identifica ogni componente separatamente (pane, affettato, formaggio, salsa...) e stima i grammi guardando le proporzioni nella foto.

PER OGNI ALIMENTO:
- nome: breve, in italiano
- grammi: la quantità che vedi nella foto
- kcal100, prot100, grassi100, carb100, fibra100: valori PER 100 g, MAI per porzione
- nova: grado di lavorazione, scala NOVA
    1 = non lavorato o poco lavorato (frutta, verdura, uova, carne e pesce freschi, riso, pasta secca, latte)
    2 = ingrediente di cucina (olio, burro, zucchero, sale)
    3 = lavorato (pane, formaggi, prosciutto crudo, conserve, cibo in scatola)
    4 = prodotto industriale (merendine, bibite, snack confezionati, salse pronte, affettati con additivi, cereali da colazione)
- gruppo: esattamente uno tra VERDURA, FRUTTA, PROTEICO, CEREALE, LATTICINO, GRASSI, DOLCE, ALTRO
- certezza: alta se leggi un'etichetta o riconosci senza dubbi, media se stimi, bassa se stai tirando a indovinare

DOMANDA: se un ingrediente che non riesci a stabilire cambierebbe molto i valori (il condimento di un panino, se la carne è magra o grassa, se c'è olio o maionese, il tipo di pane), scrivi UNA domanda breve e specifica nel campo "domanda". Se non hai dubbi importanti, lascia "domanda" come stringa vuota.

Non inventare precisione che non hai: se stimi a occhio, la certezza è media o bassa.
Rispondi in italiano.
"""

    // ---- Schema della risposta: uguale per i due fornitori, dialetti diversi ----

    private val GRUPPI = listOf(
        "VERDURA", "FRUTTA", "PROTEICO", "CEREALE", "LATTICINO", "GRASSI", "DOLCE", "ALTRO",
    )
    private val CAMPI_ALIMENTO = listOf(
        "nome", "grammi", "kcal100", "prot100", "grassi100", "carb100",
        "fibra100", "nova", "gruppo", "certezza",
    )

    /** Schema JSON per Claude (minuscolo, additionalProperties obbligatorio). */
    private fun schemaClaude(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("alimenti") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("nome") { put("type", "string") }
                        putJsonObject("grammi") { put("type", "number") }
                        putJsonObject("kcal100") { put("type", "number") }
                        putJsonObject("prot100") { put("type", "number") }
                        putJsonObject("grassi100") { put("type", "number") }
                        putJsonObject("carb100") { put("type", "number") }
                        putJsonObject("fibra100") { put("type", "number") }
                        putJsonObject("nova") { put("type", "integer") }
                        putJsonObject("gruppo") {
                            put("type", "string")
                            putJsonArray("enum") { GRUPPI.forEach { add(it) } }
                        }
                        putJsonObject("certezza") {
                            put("type", "string")
                            putJsonArray("enum") { listOf("alta", "media", "bassa").forEach { add(it) } }
                        }
                    }
                    putJsonArray("required") { CAMPI_ALIMENTO.forEach { add(it) } }
                    put("additionalProperties", false)
                }
            }
            putJsonObject("domanda") { put("type", "string") }
            putJsonObject("note") { put("type", "string") }
        }
        putJsonArray("required") { listOf("alimenti", "domanda", "note").forEach { add(it) } }
        put("additionalProperties", false)
    }

    /** Schema per Gemini: stessi campi, tipi in maiuscolo. */
    private fun schemaGemini(): JsonObject = buildJsonObject {
        put("type", "OBJECT")
        putJsonObject("properties") {
            putJsonObject("alimenti") {
                put("type", "ARRAY")
                putJsonObject("items") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("nome") { put("type", "STRING") }
                        putJsonObject("grammi") { put("type", "NUMBER") }
                        putJsonObject("kcal100") { put("type", "NUMBER") }
                        putJsonObject("prot100") { put("type", "NUMBER") }
                        putJsonObject("grassi100") { put("type", "NUMBER") }
                        putJsonObject("carb100") { put("type", "NUMBER") }
                        putJsonObject("fibra100") { put("type", "NUMBER") }
                        putJsonObject("nova") { put("type", "INTEGER") }
                        putJsonObject("gruppo") {
                            put("type", "STRING")
                            putJsonArray("enum") { GRUPPI.forEach { add(it) } }
                        }
                        putJsonObject("certezza") { put("type", "STRING") }
                    }
                    putJsonArray("required") { CAMPI_ALIMENTO.forEach { add(it) } }
                }
            }
            putJsonObject("domanda") { put("type", "STRING") }
            putJsonObject("note") { put("type", "STRING") }
        }
        putJsonArray("required") { listOf("alimenti", "domanda", "note").forEach { add(it) } }
    }

    // ---- Chiamata ----

    /**
     * Istruzione che cambia in base a quante foto arrivano e a cosa mostrano.
     * Più foto dello stesso alimento (per esempio il piatto e la sua etichetta)
     * vanno unite in un risultato solo, non trattate come cibi diversi.
     */
    internal fun istruzioni(nFoto: Int, unico: Boolean): String = buildString {
        if (nFoto > 1) {
            append(
                "Le $nFoto foto mostrano LO STESSO alimento o lo stesso piatto da angolazioni " +
                    "diverse: una può essere la confezione o la tabella nutrizionale. Uniscile in " +
                    "un unico risultato. Se una foto mostra un'etichetta, i valori per 100 g " +
                    "vengono da lì; le altre foto servono a capire la quantità.\n\n",
            )
        }
        if (unico) {
            append(
                "Restituisci UN SOLO alimento: non scomporlo negli ingredienti, " +
                    "trattalo come un'unica voce.\n\n",
            )
        }
        append(PROMPT.trim())
    }

    /**
     * Analizza una o più foto dello stesso alimento. `turni` porta le risposte già
     * date ai dubbi precedenti, così la conversazione continua senza rispedire
     * il ragionamento da capo.
     */
    suspend fun analizza(
        provider: AiProvider,
        apiKey: String,
        model: String,
        immagini: List<ImageForAi.Preparata>,
        unico: Boolean,
        turni: List<AiTurn> = emptyList(),
    ): AiAnalysis {
        require(immagini.isNotEmpty()) { "Serve almeno una foto" }
        val testo = istruzioni(immagini.size, unico)
        val grezzo = when (provider) {
            AiProvider.CLAUDE -> chiamaClaude(apiKey, model, immagini, testo, turni)
            AiProvider.GEMINI -> chiamaGemini(apiKey, model, immagini, testo, turni)
        }
        return parse(grezzo)
    }

    private suspend fun chiamaClaude(
        apiKey: String,
        model: String,
        immagini: List<ImageForAi.Preparata>,
        testo: String,
        turni: List<AiTurn>,
    ): String {
        val messaggi = buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    immagini.forEach { img ->
                        add(buildJsonObject {
                            put("type", "image")
                            putJsonObject("source") {
                                put("type", "base64")
                                put("media_type", img.mediaType)
                                put("data", img.base64)
                            }
                        })
                    }
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", testo)
                    })
                }
            })
            turni.forEach { t ->
                add(buildJsonObject {
                    put("role", if (t.fromUser) "user" else "assistant")
                    put("content", t.text)
                })
            }
        }

        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", 8000)
            putJsonObject("output_config") {
                // L'estrazione da foto non è un compito di ragionamento profondo:
                // sforzo basso costa meno e risponde prima, senza perdere qualità.
                put("effort", "low")
                putJsonObject("format") {
                    put("type", "json_schema")
                    put("schema", schemaClaude())
                }
            }
            put("messages", messaggi)
        }.toString()

        val risposta = Http.postJson(
            "https://api.anthropic.com/v1/messages",
            body,
            mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01"),
        )
        val root = json.parseToJsonElement(risposta).jsonObject
        // Con lo schema attivo il primo blocco di testo è il JSON richiesto.
        val testo = root["content"]?.jsonArray
            ?.map { it.jsonObject }
            ?.firstOrNull { it["type"]?.jsonPrimitive?.content == "text" }
            ?.get("text")?.jsonPrimitive?.content
        return testo ?: throw IllegalStateException("Risposta senza testo")
    }

    private suspend fun chiamaGemini(
        apiKey: String,
        model: String,
        immagini: List<ImageForAi.Preparata>,
        testo: String,
        turni: List<AiTurn>,
    ): String {
        val contents = buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    immagini.forEach { img ->
                        add(buildJsonObject {
                            putJsonObject("inline_data") {
                                put("mime_type", img.mediaType)
                                put("data", img.base64)
                            }
                        })
                    }
                    add(buildJsonObject { put("text", testo) })
                }
            })
            turni.forEach { t ->
                add(buildJsonObject {
                    put("role", if (t.fromUser) "user" else "model")
                    putJsonArray("parts") { add(buildJsonObject { put("text", t.text) }) }
                })
            }
        }

        val body = buildJsonObject {
            put("contents", contents)
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("responseSchema", schemaGemini())
            }
        }.toString()

        val risposta = Http.postJson(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
            body,
            mapOf("x-goog-api-key" to apiKey),
        )
        val root = json.parseToJsonElement(risposta).jsonObject
        val testo = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("text")?.jsonPrimitive?.content
        return testo ?: throw IllegalStateException("Risposta senza testo")
    }

    // ---- Lettura della risposta ----

    internal fun parse(grezzo: String): AiAnalysis {
        val root = json.parseToJsonElement(estraiJson(grezzo)).jsonObject
        val alimenti = root["alimenti"]?.jsonArray.orEmpty().mapNotNull { el ->
            val o = el.jsonObject
            val nome = o["nome"]?.jsonPrimitive?.contentOrNull()?.trim()
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            AiFoodItem(
                nome = nome,
                grammi = o["grammi"]?.jsonPrimitive?.doubleOrNull()?.takeIf { it > 0 } ?: 100.0,
                kcal100 = o["kcal100"]?.jsonPrimitive?.doubleOrNull(),
                prot100 = o["prot100"]?.jsonPrimitive?.doubleOrNull(),
                grassi100 = o["grassi100"]?.jsonPrimitive?.doubleOrNull(),
                carb100 = o["carb100"]?.jsonPrimitive?.doubleOrNull(),
                fibra100 = o["fibra100"]?.jsonPrimitive?.doubleOrNull(),
                nova = o["nova"]?.jsonPrimitive?.doubleOrNull()?.toInt()?.takeIf { it in 1..4 },
                gruppo = runCatching {
                    FoodGroup.valueOf(o["gruppo"]?.jsonPrimitive?.contentOrNull().orEmpty().uppercase())
                }.getOrDefault(FoodGroup.ALTRO),
                certezza = o["certezza"]?.jsonPrimitive?.contentOrNull()?.lowercase() ?: "media",
            )
        }
        return AiAnalysis(
            alimenti = alimenti,
            domanda = root["domanda"]?.jsonPrimitive?.contentOrNull()?.trim()?.takeIf { it.isNotBlank() },
            note = root["note"]?.jsonPrimitive?.contentOrNull()?.trim()?.takeIf { it.isNotBlank() },
        )
    }

    /** Se il modello incornicia il JSON con del testo, tiene solo l'oggetto. */
    internal fun estraiJson(s: String): String {
        val t = s.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val i = t.indexOf('{')
        val j = t.lastIndexOf('}')
        return if (i >= 0 && j > i) t.substring(i, j + 1) else t
    }

    /** Traduce l'errore del servizio in una frase che dice cosa fare. */
    fun messaggioErrore(e: Throwable): String = when {
        e is HttpFailure && e.code in listOf(401, 403) ->
            "Chiave non valida o senza permessi. Controllala nelle impostazioni."
        e is HttpFailure && e.code == 429 ->
            "Hai finito il credito o superato il limite di richieste del tuo piano. Riprova più tardi."
        e is HttpFailure && e.code == 400 ->
            "Richiesta rifiutata: forse il nome del modello nelle impostazioni non è più valido. " +
                "Dettaglio: ${e.body.take(160)}"
        e is HttpFailure -> "Errore del servizio (${e.code}). Dettaglio: ${e.body.take(160)}"
        else -> "Non sono riuscito a contattare il servizio: controlla la connessione."
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
    runCatching { content }.getOrNull()

private fun kotlinx.serialization.json.JsonPrimitive.doubleOrNull(): Double? =
    runCatching { content.replace(',', '.').toDoubleOrNull() }.getOrNull()
