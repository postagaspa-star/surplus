package it.gaspa.surplus

import it.gaspa.surplus.data.FoodGroup
import it.gaspa.surplus.data.FoodLookup
import it.gaspa.surplus.data.FoodTagger
import it.gaspa.surplus.data.OpenFoodFacts
import it.gaspa.surplus.data.UsdaFoodData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodLookupTest {

    // ---- riconoscimento del codice ----

    @Test
    fun `codice a barre valido solo se tutte cifre e lunghezza plausibile`() {
        assertTrue(FoodLookup.isBarcode("8001234567890")) // EAN-13
        assertTrue(FoodLookup.isBarcode("12345670"))      // EAN-8
        assertFalse(FoodLookup.isBarcode("https://esempio.it")) // QR di un sito
        assertFalse(FoodLookup.isBarcode("123"))
        assertFalse(FoodLookup.isBarcode("800123456789X"))
    }

    // ---- Open Food Facts ----

    @Test
    fun `OFF prodotto - legge nutrienti, fibra e grado di lavorazione`() {
        val body = """
            {"status":1,"product":{
              "product_name":"Penne Rigate","product_name_it":"Penne Rigate",
              "brands":"Barilla, Gruppo",
              "categories_tags":["en:pastas","en:cereals"],
              "nova_group":1,
              "nutriments":{"energy-kcal_100g":359,"proteins_100g":12.5,
                            "fat_100g":2,"carbohydrates_100g":71,"fiber_100g":3.2}
            }}
        """.trimIndent()
        val info = OpenFoodFacts.parseProduct(body, "8076809513711")!!
        assertEquals("Penne Rigate", info.nome)
        assertEquals("Barilla", info.marca)
        assertEquals(359.0, info.kcal100!!, 0.001)
        assertEquals(3.2, info.fibra100!!, 0.001)
        assertEquals(1, info.nova)
        assertEquals(FoodGroup.CEREALE, info.gruppo)
        assertEquals("8076809513711", info.barcode)
        assertFalse(info.isDrink)
    }

    @Test
    fun `OFF prodotto assente - null, non un errore`() {
        assertNull(OpenFoodFacts.parseProduct("""{"status":0}""", "0000000000000"))
    }

    @Test
    fun `OFF senza calorie - scartato perche inutile`() {
        val body = """
            {"status":1,"product":{"product_name":"Misterioso","nutriments":{}}}
        """.trimIndent()
        assertNull(OpenFoodFacts.parseProduct(body, "123"))
    }

    @Test
    fun `OFF ricerca - energia in kJ convertita in kcal`() {
        val body = """
            {"products":[{"product_name":"Latte","categories_tags":["en:milks","en:beverages"],
             "nutriments":{"energy_100g":268,"proteins_100g":3.3}}]}
        """.trimIndent()
        val r = OpenFoodFacts.parseSearch(body).single()
        assertEquals(64.0, r.kcal100!!, 0.5) // 268 kJ ≈ 64 kcal
        assertTrue(r.isDrink)
    }

    // ---- USDA (secondo archivio) ----

    @Test
    fun `USDA - prende il prodotto col codice corrispondente`() {
        val body = """
            {"foods":[
              {"description":"SOMETHING ELSE","gtinUpc":"999999999999",
               "foodNutrients":[{"nutrientNumber":"208","value":100}]},
              {"description":"WHEY PROTEIN POWDER","brandOwner":"Acme Nutrition",
               "gtinUpc":"0748927024760","foodCategory":"Protein supplements",
               "foodNutrients":[{"nutrientNumber":"208","value":380},
                                {"nutrientNumber":"203","value":75},
                                {"nutrientNumber":"204","value":6},
                                {"nutrientNumber":"291","value":2.5}]}
            ]}
        """.trimIndent()
        val info = UsdaFoodData.parse(body, "748927024760")!!
        assertEquals("Whey protein powder", info.nome)
        assertEquals("Acme Nutrition", info.marca)
        assertEquals(380.0, info.kcal100!!, 0.001)
        assertEquals(75.0, info.prot100!!, 0.001)
        assertEquals(2.5, info.fibra100!!, 0.001)
        assertEquals("USDA FoodData Central", info.fonte)
    }

    @Test
    fun `USDA - nessun codice corrispondente, nessun falso positivo`() {
        val body = """
            {"foods":[{"description":"ALTRO PRODOTTO","gtinUpc":"111111111111",
             "foodNutrients":[{"nutrientNumber":"208","value":250}]}]}
        """.trimIndent()
        assertNull(UsdaFoodData.parse(body, "748927024760"))
    }

    @Test
    fun `USDA - risposta vuota`() {
        assertNull(UsdaFoodData.parse("""{"foods":[]}""", "748927024760"))
    }

    // ---- classificazione ----

    @Test
    fun `succo di frutta non viene classificato come frutta`() {
        assertEquals(FoodGroup.ALTRO, FoodTagger.groupFromText("en:fruit-juices en:beverages"))
        assertTrue(FoodTagger.isDrink("en:fruit-juices en:beverages"))
    }

    @Test
    fun `verdura riconosciuta prima di tutto il resto`() {
        assertEquals(FoodGroup.VERDURA, FoodTagger.groupFromText("en:canned-vegetables en:legumes"))
    }

    @Test
    fun `categoria sconosciuta - nessuna invenzione`() {
        assertEquals(FoodGroup.ALTRO, FoodTagger.groupFromText(""))
        assertNotNull(FoodTagger.groupFromText("qualcosa di mai visto"))
    }

    // ---- regressioni sul riconoscimento bevanda/solido (v2.1.1) ----

    /**
     * Caso reale: le penne Rummo venivano misurate in ml. Colpa della categoria
     * ombrello di OFF "plant-based-foods-and-beverages", che contiene "beverages"
     * ma copre anche la pasta.
     */
    @Test
    fun `pasta sotto la categoria ombrello con beverages NON e una bevanda`() {
        val cats = "en:plant-based-foods-and-beverages en:plant-based-foods " +
            "en:cereals-and-potatoes en:cereals-and-their-products en:pastas en:dry-pastas"
        assertFalse(FoodTagger.isDrink(cats))
        assertEquals(FoodGroup.CEREALE, FoodTagger.groupFromText(cats))
    }

    @Test
    fun `verdura in scatola non diventa bevanda per colpa dell ombrello`() {
        val cats = "en:plant-based-foods-and-beverages en:canned-vegetables"
        assertFalse(FoodTagger.isDrink(cats))
        assertEquals(FoodGroup.VERDURA, FoodTagger.groupFromText(cats))
    }

    @Test
    fun `le bevande vere restano bevande`() {
        assertTrue(FoodTagger.isDrink("en:beverages en:dairies en:milks"))
        assertTrue(FoodTagger.isDrink("en:waters en:spring-waters"))
        assertTrue(FoodTagger.isDrink("it:bibite it:bevande-analcoliche"))
    }

    @Test
    fun `confronto per parola intera - steak non contiene la bevanda tea`() {
        assertFalse(FoodTagger.isDrink("en:steaks en:beef"))
        assertFalse(FoodTagger.isDrink("en:proteins"))
    }

    @Test
    fun `normalize - toglie prefissi di lingua, trattini e categorie ombrello`() {
        val t = FoodTagger.normalize("en:plant-based-foods-and-beverages,it:pasta-secca")
        assertFalse(t.contains("en:"))
        assertFalse(t.contains("beverages"))
        assertTrue(t.contains(" pasta secca "))
    }
}
