package it.gaspa.surplus.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * USDA FoodData Central: secondo archivio, usato quando Open Food Facts non
 * conosce il codice a barre. Copre bene ciò che a OFF spesso manca — proteine
 * in polvere, barrette, marche estere.
 *
 * Usa DEMO_KEY, la chiave pubblica documentata da api.data.gov: non è un
 * segreto (quindi non è un problema che stia dentro l'APK), ma è limitata a
 * poche richieste all'ora. Se il limite scatta, la ricerca fallisce in modo
 * pulito e resta l'inserimento manuale.
 */
object UsdaFoodData {

    private const val ENDPOINT = "https://api.nal.usda.gov/fdc/v1/foods/search"
    private const val API_KEY = "DEMO_KEY"
    private const val UA = "Surplus/2.1 (app personale; kotlin)"
    private val json = Json { ignoreUnknownKeys = true }

    // Codici nutriente USDA (stabili, indipendenti dalla lingua).
    private const val N_KCAL = "208"
    private const val N_PROT = "203"
    private const val N_FAT = "204"
    private const val N_CARB = "205"
    private const val N_FIBER = "291"
    private const val N_SATFAT = "606"
    private const val N_SUGAR = "269"
    private const val N_SODIUM = "307" // mg, va convertito in sale

    suspend fun byBarcode(code: String): FoodInfo? {
        val body = Http.get(
            "$ENDPOINT?api_key=$API_KEY&query=$code&dataType=Branded&pageSize=5",
            UA,
        )
        return parse(body, code)
    }

    internal fun parse(body: String, code: String): FoodInfo? {
        val foods = json.parseToJsonElement(body).jsonObject["foods"]?.jsonArray ?: return null
        // Il match deve essere sul codice: la ricerca testuale può restituire altro.
        val p = foods.map { it.jsonObject }.firstOrNull { f ->
            val gtin = f["gtinUpc"]?.jsonPrimitive?.safeContent()?.trimStart('0')
            gtin != null && gtin == code.trimStart('0')
        } ?: return null

        val nutrients = p["foodNutrients"]?.jsonArray.orEmpty().associate { el ->
            val o = el.jsonObject
            val num = o["nutrientNumber"]?.jsonPrimitive?.safeContent().orEmpty()
            num to o["value"]?.jsonPrimitive?.safeDouble()
        }
        val kcal = nutrients[N_KCAL]?.takeIf { it > 0 } ?: return null
        val nome = p["description"]?.jsonPrimitive?.safeContent()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.lowercase()
            ?.replaceFirstChar { it.uppercase() }
            ?: return null
        val categoria = p["foodCategory"]?.jsonPrimitive?.safeContent().orEmpty()

        return FoodInfo(
            nome = nome,
            marca = p["brandOwner"]?.jsonPrimitive?.safeContent()?.trim()?.takeIf { it.isNotBlank() },
            kcal100 = kotlin.math.round(kcal * 10) / 10,
            prot100 = nutrients[N_PROT],
            grassi100 = nutrients[N_FAT],
            carb100 = nutrients[N_CARB],
            fibra100 = nutrients[N_FIBER],
            grassiSaturi100 = nutrients[N_SATFAT],
            zuccheri100 = nutrients[N_SUGAR],
            // Il sodio è in mg: sale (g) = sodio × 2,5 / 1000.
            sale100 = nutrients[N_SODIUM]?.let { kotlin.math.round(it * 2.5 / 1000 * 100) / 100 },
            // USDA non pubblica il NOVA: un prodotto confezionato di marca è
            // però quasi sempre lavorato, quindi 4 è la stima onesta.
            nova = 4,
            gruppo = FoodTagger.groupFromText(categoria),
            isDrink = FoodTagger.isDrink(categoria),
            barcode = code,
            fonte = "USDA FoodData Central",
        )
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.safeContent(): String? =
    runCatching { content }.getOrNull()

private fun kotlinx.serialization.json.JsonPrimitive.safeDouble(): Double? =
    runCatching { content.replace(',', '.').toDoubleOrNull() }.getOrNull()
