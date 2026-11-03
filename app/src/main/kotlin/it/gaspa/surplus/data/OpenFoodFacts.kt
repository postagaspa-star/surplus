package it.gaspa.surplus.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

/**
 * Open Food Facts: database aperto, gratuito, ricco di prodotti italiani.
 * È il primo dei due archivi usati dall'app, sia per la ricerca per nome sia
 * per il codice a barre.
 *
 * Nessuna chiave API, nessun account, nessun dato dell'utente inviato: parte
 * solo il testo cercato o il codice del prodotto. Un prodotto scelto viene
 * copiato nella dispensa locale, quindi dopo la prima volta funziona offline.
 */
object OpenFoodFacts {

    // Il motore di ricerca nuovo di Open Food Facts: veloce e regge le ricerche di fila.
    // Quello vecchio (search.pl) risponde "occupato" a metà delle richieste: resta solo
    // come riserva, se il nuovo non risponde.
    private const val SEARCH = "https://search.openfoodfacts.org/search"
    private const val SEARCH_OLD = "https://world.openfoodfacts.org/cgi/search.pl"
    private const val PRODUCT = "https://world.openfoodfacts.org/api/v2/product"
    private const val FIELDS =
        "product_name,product_name_it,brands,quantity,nutriments,categories_tags,nova_group,additives_n"
    private const val UA = "Surplus/2.1 (app personale; kotlin)"
    private val json = Json { ignoreUnknownKeys = true }

    /** Cerca per nome. Lancia se nessuno dei due motori risponde: il chiamante mostra l'errore. */
    suspend fun search(query: String, limit: Int = 15): List<FoodInfo> {
        val nuovo = runCatching {
            Http.get(
                "$SEARCH?q=${URLEncoder.encode(searchQuery(query), "UTF-8")}" +
                    "&langs=it&page_size=$limit&fields=$FIELDS",
                UA,
            )
        }
        val body = nuovo.getOrNull() ?: Http.get(
            "$SEARCH_OLD?search_terms=${URLEncoder.encode(query, "UTF-8")}" +
                "&search_simple=1&action=process&json=1&page_size=$limit" +
                "&fields=$FIELDS&countries_tags_en=italy",
            UA,
        )
        return parseSearch(body)
    }

    /**
     * Il motore nuovo legge la ricerca come una piccola formula: due punti, virgolette
     * e parentesi scritti dall'utente la romperebbero. Restano lettere, cifre, spazi e
     * apostrofi, più il filtro sui prodotti venduti in Italia.
     */
    internal fun searchQuery(query: String): String {
        val pulita = query.map { if (it.isLetterOrDigit() || it == '\'') it else ' ' }
            .joinToString("").split(' ').filter { it.isNotBlank() }.joinToString(" ")
        return "$pulita countries_tags:\"en:italy\""
    }

    /** Cerca per codice a barre. null = archivio raggiunto ma prodotto assente. */
    suspend fun byBarcode(code: String): FoodInfo? {
        val body = Http.get("$PRODUCT/$code.json?fields=$FIELDS", UA)
        return parseProduct(body, code)
    }

    internal fun parseSearch(body: String): List<FoodInfo> {
        // "hits" nel motore nuovo, "products" in quello vecchio.
        val root = json.parseToJsonElement(body).jsonObject
        val products = (root["hits"] ?: root["products"])?.jsonArray ?: return emptyList()
        return products.mapNotNull { toInfo(it.jsonObject, null) }
            .filter { it.kcal100 != null } // senza calorie il prodotto non serve a nulla
    }

    internal fun parseProduct(body: String, code: String): FoodInfo? {
        val root = json.parseToJsonElement(body).jsonObject
        val status = root["status"]?.jsonPrimitive?.safeContent()
        if (status == "0") return null
        val p = root["product"]?.jsonObject ?: return null
        return toInfo(p, code)?.takeIf { it.kcal100 != null }
    }

    private fun toInfo(p: JsonObject, code: String?): FoodInfo? {
        val nome = (p["product_name_it"] ?: p["product_name"])
            ?.jsonPrimitive?.safeContent()?.trim()
            ?.takeIf { it.isNotBlank() } ?: return null
        val n = p["nutriments"]?.jsonObject
        val kcal = n?.get("energy-kcal_100g")?.jsonPrimitive?.safeDouble()
            ?: n?.get("energy_100g")?.jsonPrimitive?.safeDouble()?.let { it / 4.184 }
        val cats = p["categories_tags"]?.jsonArray?.joinToString(" ") {
            it.jsonPrimitive.safeContent().orEmpty()
        }.orEmpty()
        return FoodInfo(
            nome = nome,
            marca = firstBrand(p["brands"]),
            kcal100 = kcal?.takeIf { it > 0 }?.let { kotlin.math.round(it * 10) / 10 },
            prot100 = n?.get("proteins_100g")?.jsonPrimitive?.safeDouble(),
            grassi100 = n?.get("fat_100g")?.jsonPrimitive?.safeDouble(),
            carb100 = n?.get("carbohydrates_100g")?.jsonPrimitive?.safeDouble(),
            fibra100 = n?.get("fiber_100g")?.jsonPrimitive?.safeDouble(),
            grassiSaturi100 = n?.get("saturated-fat_100g")?.jsonPrimitive?.safeDouble(),
            zuccheri100 = n?.get("sugars_100g")?.jsonPrimitive?.safeDouble(),
            sale100 = n?.get("salt_100g")?.jsonPrimitive?.safeDouble(),
            nova = p["nova_group"]?.jsonPrimitive?.safeDouble()?.toInt()?.takeIf { it in 1..4 },
            gruppo = FoodTagger.groupFromText(cats),
            isDrink = FoodTagger.isDrink(cats),
            barcode = code,
            fonte = "Open Food Facts",
        )
    }
}

/** La marca arriva come "A, B" dal motore vecchio e come ["A", "B"] da quello nuovo. */
private fun firstBrand(e: kotlinx.serialization.json.JsonElement?): String? {
    val testo = when (e) {
        is kotlinx.serialization.json.JsonNull, null -> null
        is kotlinx.serialization.json.JsonArray -> e.firstOrNull()
            ?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.safeContent()
        is kotlinx.serialization.json.JsonPrimitive -> e.safeContent()
        else -> null
    }
    return testo?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
}

// I primitivi JSON di Open Food Facts sono a volte stringa, a volte numero:
// questi helper evitano di far esplodere il parsing su un campo sporco.
private fun kotlinx.serialization.json.JsonPrimitive.safeContent(): String? =
    runCatching { content }.getOrNull()

private fun kotlinx.serialization.json.JsonPrimitive.safeDouble(): Double? =
    runCatching { content.replace(',', '.').toDoubleOrNull() }.getOrNull()
