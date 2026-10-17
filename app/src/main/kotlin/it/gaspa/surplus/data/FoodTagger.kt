package it.gaspa.surplus.data

/**
 * Deduce gruppo alimentare e "è una bevanda" dalle categorie testuali degli
 * archivi (OFF e USDA usano parole diverse per le stesse cose). Deterministico,
 * offline, nessun giudizio: serve solo a sapere se una voce è frutta/verdura
 * e se si misura in grammi o millilitri.
 *
 * Due trappole imparate sul campo, entrambe gestite qui:
 * 1. Open Food Facts mette QUASI TUTTO il cibo vegetale sotto la categoria
 *    ombrello "plant-based-foods-and-beverages" — che contiene "beverages"
 *    ma copre anche pasta, pane e verdura. Va tolta prima di decidere.
 * 2. Cercare pezzi di parola dà falsi positivi ("steak" contiene "tea"):
 *    il confronto è per parola intera, non per sottostringa.
 */
object FoodTagger {

    /** Categorie che non dicono nulla e traggono in inganno: via prima di tutto. */
    private val ombrello = listOf(
        "plant based foods and beverages",
        "beverages and beverages preparations",
    )

    /**
     * Cibi solidi la cui categoria può contenere anche una parola da bevanda
     * ("milk chocolate", "yogurt", "milk bread"…): qui il solido vince.
     *
     * Attenzione a cosa NON va messo qui: frutta, verdura, semi e frutta secca
     * devono restarne fuori, altrimenti succhi di frutta, centrifughe di verdura
     * e bevande vegetali verrebbero scambiati per solidi.
     */
    private val solidi = listOf(
        "pasta", "pastas", "pane", "bread", "breads", "pizza", "biscuit", "biscuits",
        "cheese", "cheeses", "formaggio", "formaggi", "yogurt", "yogurts", "yoghurts",
        "meat", "meats", "carne", "carni", "fish", "pesce", "snack", "snacks",
        "cereal", "cereals", "cereali", "rice", "riso", "flour", "flours", "farina", "farine",
        "chocolate", "chocolates", "cioccolato", "candies", "sweets", "dolci",
        "dessert", "desserts", "creams", "sauce", "sauces", "salse", "soup", "soups",
        "spread", "spreads", "eggs", "uova",
    )

    private val bevande = listOf(
        "beverage", "beverages", "drink", "drinks", "bevanda", "bevande", "bibita", "bibite",
        "water", "waters", "acqua", "acque", "juice", "juices", "jus", "succo", "succhi",
        "soda", "sodas", "milk", "milks", "latte", "tea", "teas", "the", "coffee", "coffees",
        "caffe", "caffè", "smoothie", "smoothies", "nectar", "nectars", "nettare",
    )

    private val verdura = listOf("vegetable", "vegetables", "verdura", "verdure", "ortaggi", "legume", "legumes", "legumi", "salad", "salads", "insalata", "insalate", "pomodori", "tomatoes")
    private val frutta = listOf("fruit", "fruits", "frutta", "frutti", "berries", "apples", "bananas")
    private val proteico = listOf("meat", "meats", "carne", "carni", "fish", "pesce", "poultry", "egg", "eggs", "uova", "seafood", "salumi", "prosciutto", "hams", "sausages")
    private val cereale = listOf("cereal", "cereals", "cereali", "pasta", "pastas", "bread", "breads", "pane", "rice", "riso", "biscuit", "biscuits", "flour", "flours", "farina", "farine")
    private val latticino = listOf("dairy", "dairies", "cheese", "cheeses", "formaggio", "formaggi", "yogurt", "yogurts", "yoghurts", "milk", "milks", "latte")
    private val grassi = listOf("oil", "oils", "olio", "nut", "nuts", "noci", "mandorle", "seeds", "semi", "butter", "burro")
    private val dolce = listOf("sweet", "sweets", "dolci", "sugar", "zucchero", "chocolate", "chocolates", "cioccolato", "dessert", "desserts", "candy", "candies", "confiture", "marmellata", "jams", "biscuits")

    /**
     * Minuscolo, senza prefissi di lingua ("en:", "it:"), trattini in spazi,
     * categorie ombrello rimosse, con spazi ai bordi per il confronto per parola.
     */
    internal fun normalize(categories: String): String {
        var t = categories.lowercase()
            .replace(Regex("\\b[a-z]{2}:"), " ")
            .replace('-', ' ')
            .replace(',', ' ')
        ombrello.forEach { t = t.replace(it, " ") }
        return " " + t.replace(Regex("\\s+"), " ").trim() + " "
    }

    private fun String.hasWord(w: String): Boolean = contains(" $w ")

    private fun String.hasAny(words: List<String>): Boolean = words.any { hasWord(it) }

    /** Un solido resta un solido anche se la sua categoria nomina "beverages". */
    fun isDrink(categories: String): Boolean {
        val t = normalize(categories)
        if (t.hasAny(solidi)) return false
        return t.hasAny(bevande)
    }

    /** Frutta e verdura hanno la precedenza: sono le uniche che contano nei consigli. */
    fun groupFromText(categories: String): FoodGroup {
        val t = normalize(categories)
        val isSucco = t.hasAny(listOf("juice", "juices", "jus", "succo", "succhi", "nectar", "nectars"))
        return when {
            t.hasAny(verdura) -> FoodGroup.VERDURA
            t.hasAny(frutta) && !isSucco -> FoodGroup.FRUTTA
            t.hasAny(proteico) -> FoodGroup.PROTEICO
            t.hasAny(latticino) -> FoodGroup.LATTICINO
            t.hasAny(dolce) -> FoodGroup.DOLCE
            t.hasAny(grassi) -> FoodGroup.GRASSI
            t.hasAny(cereale) -> FoodGroup.CEREALE
            else -> FoodGroup.ALTRO
        }
    }
}
