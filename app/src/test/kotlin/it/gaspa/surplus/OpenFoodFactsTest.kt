package it.gaspa.surplus

import it.gaspa.surplus.data.OpenFoodFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenFoodFactsTest {

    @Test
    fun `parse - estrae nome, marca e valori per 100 g`() {
        val body = """
            {"products":[
              {"product_name":"Penne Rigate","brands":"Barilla",
               "nutriments":{"energy-kcal_100g":355,"proteins_100g":12.5,"fat_100g":2.0,"carbohydrates_100g":71.2},
               "categories_tags":["en:pastas"]}
            ]}
        """.trimIndent()
        val r = OpenFoodFacts.parseSearch(body).single()
        assertEquals("Penne Rigate", r.nome)
        assertEquals("Barilla", r.marca)
        assertEquals(355.0, r.kcal100!!, 0.01)
        assertEquals(12.5, r.prot100!!, 0.01)
        assertFalse(r.isDrink)
    }

    @Test
    fun `parse - preferisce il nome italiano quando c'e`() {
        val body = """
            {"products":[{"product_name":"Whole milk","product_name_it":"Latte intero",
             "nutriments":{"energy-kcal_100g":64},"categories_tags":["en:milks"]}]}
        """.trimIndent()
        assertEquals("Latte intero", OpenFoodFacts.parseSearch(body).single().nome)
    }

    @Test
    fun `parse - le bevande sono riconosciute come tali`() {
        val body = """
            {"products":[{"product_name":"Succo d'arancia","brands":"X",
             "nutriments":{"energy-kcal_100g":45},"categories_tags":["en:beverages","en:juices"]}]}
        """.trimIndent()
        assertTrue(OpenFoodFacts.parseSearch(body).single().isDrink)
    }

    @Test
    fun `parse - converte i kJ quando mancano le kcal`() {
        val body = """
            {"products":[{"product_name":"Pane","nutriments":{"energy_100g":1120},"categories_tags":[]}]}
        """.trimIndent()
        // 1120 kJ / 4.184 ≈ 267.7 kcal
        assertEquals(267.7, OpenFoodFacts.parseSearch(body).single().kcal100!!, 0.1)
    }

    @Test
    fun `parse - scarta i prodotti senza calorie e senza nome`() {
        val body = """
            {"products":[
              {"product_name":"Senza valori","nutriments":{},"categories_tags":[]},
              {"product_name":"","nutriments":{"energy-kcal_100g":100},"categories_tags":[]},
              {"product_name":"Buono","nutriments":{"energy-kcal_100g":200},"categories_tags":[]}
            ]}
        """.trimIndent()
        val results = OpenFoodFacts.parseSearch(body)
        assertEquals(1, results.size)
        assertEquals("Buono", results.single().nome)
    }

    @Test
    fun `parse - risposta malformata non esplode`() {
        assertTrue(OpenFoodFacts.parseSearch("""{"count":0}""").isEmpty())
    }

    @Test
    fun `parse - risposta vuota o malformata non esplode`() {
        assertTrue(OpenFoodFacts.parseSearch("""{"products":[]}""").isEmpty())
        assertTrue(OpenFoodFacts.parseSearch("""{"count":0}""").isEmpty())
    }

    @Test
    fun `toFood - il nome include la marca e finisce in dispensa`() {
        val r = OpenFoodFacts.parseSearch(
            """{"products":[{"product_name":"Penne","brands":"Barilla",
               "nutriments":{"energy-kcal_100g":355},"categories_tags":[]}]}"""
        ).single()
        val food = r.toFood("off-1")
        assertEquals("Penne (Barilla)", food.nome)
        assertTrue(food.custom)
        assertNotNull(food.hint)
    }
}
