package it.gaspa.surplus.data

/**
 * Dispensa precaricata: valori per 100 g (100 ml per le bevande), presi dalla
 * tabella del piano dove disponibili, altrimenti valori standard italiani.
 * Approssimati apposta: la costanza conta più della precisione.
 *
 * `nova` = grado di lavorazione (1 cibo vero → 4 ultra-processato), `fibra` in g,
 * `gruppo` per i segnali su frutta/verdura. Servono ai consigli sulla qualità,
 * mai a bollare un cibo come "cattivo".
 */
object SeedFoods {

    private fun f(
        id: String, nome: String, kcal: Double, prot: Double,
        grassi: Double? = null, drink: Boolean = false, hint: String? = null,
        nova: Int = 1, fibra: Double = 0.0, gruppo: FoodGroup = FoodGroup.ALTRO,
    ) = Food(
        id = id, nome = nome, kcal100 = kcal, prot100 = prot, grassi100 = grassi,
        carb100 = null, fibra100 = fibra, nova = nova, gruppo = gruppo,
        isDrink = drink, hint = hint, custom = false,
    )

    val all: List<Food> = listOf(
        // Base / carboidrati
        f("pane", "Pane", 268.0, 8.0, 0.5, hint = "1 fetta ≈ 30 g", nova = 3, fibra = 3.5, gruppo = FoodGroup.CEREALE),
        f("fette-biscottate", "Fette biscottate", 410.0, 11.0, hint = "1 fetta ≈ 8 g", nova = 3, fibra = 3.5, gruppo = FoodGroup.CEREALE),
        f("pasta", "Pasta secca (cruda)", 350.0, 12.0, 1.5, hint = "porzione piano: 120–150 g", nova = 1, fibra = 3.0, gruppo = FoodGroup.CEREALE),
        f("riso", "Riso (crudo)", 360.0, 7.0, nova = 1, fibra = 1.4, gruppo = FoodGroup.CEREALE),
        f("taralli", "Taralli", 450.0, 10.0, 20.0, hint = "1 tarallo ≈ 9 g — controlla etichetta", nova = 3, fibra = 2.5, gruppo = FoodGroup.CEREALE),
        f("cereali", "Cereali da colazione", 380.0, 8.0, nova = 4, fibra = 7.0, gruppo = FoodGroup.CEREALE),
        f("patate", "Patate (crude)", 80.0, 2.0, nova = 1, fibra = 2.1, gruppo = FoodGroup.ALTRO),

        // Formaggi e latticini
        f("asiago-fresco", "Asiago fresco", 370.0, 24.0, 29.0, nova = 3, gruppo = FoodGroup.LATTICINO),
        f("asiago-stagionato", "Asiago stagionato", 415.0, 27.5, 33.5, nova = 3, gruppo = FoodGroup.LATTICINO),
        f("mozzarella", "Mozzarella", 250.0, 18.0, 19.0, nova = 3, gruppo = FoodGroup.LATTICINO),
        f("grana", "Grana Padano", 390.0, 33.0, 28.0, hint = "1 cucchiaio ≈ 10 g", nova = 3, gruppo = FoodGroup.LATTICINO),
        f("yogurt-greco", "Yogurt greco intero", 95.0, 9.0, 5.0, hint = "1 vasetto ≈ 170 g", nova = 1, gruppo = FoodGroup.LATTICINO),
        f("yogurt-bianco", "Yogurt bianco intero", 65.0, 3.8, 3.6, hint = "1 vasetto ≈ 125 g", nova = 1, gruppo = FoodGroup.LATTICINO),

        // Affettati e condimenti proteici
        f("cotto-cubetti", "Prosciutto cotto (cubetti)", 165.0, 19.0, 9.0, nova = 4, gruppo = FoodGroup.PROTEICO),
        f("crudo", "Prosciutto crudo", 270.0, 26.0, 18.0, nova = 3, gruppo = FoodGroup.PROTEICO),
        f("bresaola", "Bresaola", 150.0, 32.0, 2.6, nova = 4, gruppo = FoodGroup.PROTEICO),
        f("ragu", "Ragù di carne (pronto)", 130.0, 8.0, 9.0, nova = 4, fibra = 1.2, gruppo = FoodGroup.PROTEICO),

        // Uova, carne, pesce (peso crudo)
        f("uova", "Uova", 143.0, 12.5, 9.5, hint = "1 uovo ≈ 60 g", nova = 1, gruppo = FoodGroup.PROTEICO),
        f("pollo", "Petto di pollo", 110.0, 23.0, 1.5, nova = 1, gruppo = FoodGroup.PROTEICO),
        f("manzo", "Manzo magro", 130.0, 21.0, 5.0, nova = 1, gruppo = FoodGroup.PROTEICO),
        f("tacchino", "Tacchino", 105.0, 22.0, 1.5, nova = 1, gruppo = FoodGroup.PROTEICO),
        f("hamburger", "Hamburger di manzo", 230.0, 17.0, 17.0, nova = 3, gruppo = FoodGroup.PROTEICO),
        f("pesce-bianco", "Pesce bianco (orata/branzino)", 110.0, 19.0, 3.5, nova = 1, gruppo = FoodGroup.PROTEICO),
        f("tonno", "Tonno sott'olio (sgocciolato)", 190.0, 25.0, 10.0, nova = 3, gruppo = FoodGroup.PROTEICO),
        f("salmone", "Salmone", 200.0, 20.0, 13.0, nova = 1, gruppo = FoodGroup.PROTEICO),

        // Frutta e frutta secca
        f("frutta-secca", "Frutta secca mista", 600.0, 18.0, 53.0, hint = "una manciata ≈ 30 g", nova = 1, fibra = 7.0, gruppo = FoodGroup.GRASSI),
        f("mandorle", "Mandorle", 600.0, 21.0, 52.0, nova = 1, fibra = 12.5, gruppo = FoodGroup.GRASSI),
        f("noci", "Noci", 650.0, 15.0, 63.0, nova = 1, fibra = 6.7, gruppo = FoodGroup.GRASSI),
        f("banana", "Banana", 89.0, 1.1, hint = "1 media ≈ 120 g", nova = 1, fibra = 2.6, gruppo = FoodGroup.FRUTTA),
        f("mela", "Mela", 52.0, 0.3, hint = "1 media ≈ 180 g", nova = 1, fibra = 2.4, gruppo = FoodGroup.FRUTTA),
        f("frutto", "Frutto generico", 60.0, 0.8, hint = "1 frutto ≈ 150 g", nova = 1, fibra = 2.0, gruppo = FoodGroup.FRUTTA),

        // Dolci / spalmabili
        f("miele", "Miele", 304.0, 0.4, hint = "1 cucchiaino ≈ 10 g", nova = 1, gruppo = FoodGroup.DOLCE),
        f("marmellata", "Marmellata", 240.0, 0.5, nova = 3, fibra = 1.0, gruppo = FoodGroup.DOLCE),
        f("nutella", "Nutella", 539.0, 6.0, 30.5, hint = "1 cucchiaio ≈ 15 g", nova = 4, fibra = 3.4, gruppo = FoodGroup.DOLCE),

        // Grassi
        f("olio", "Olio d'oliva", 900.0, 0.0, 100.0, hint = "1 cucchiaio ≈ 10 g", nova = 2, gruppo = FoodGroup.GRASSI),
        f("burro", "Burro", 750.0, 0.7, 83.0, nova = 2, gruppo = FoodGroup.GRASSI),

        // Verdura
        f("verdura", "Verdura mista", 30.0, 1.5, nova = 1, fibra = 2.5, gruppo = FoodGroup.VERDURA),
        f("insalata", "Insalata", 20.0, 1.4, nova = 1, fibra = 1.8, gruppo = FoodGroup.VERDURA),
        f("pomodori", "Pomodori", 20.0, 1.0, nova = 1, fibra = 1.2, gruppo = FoodGroup.VERDURA),

        // Bevande (valori per 100 ml)
        f("latte", "Latte intero", 64.0, 3.3, 3.6, drink = true, hint = "1 bicchiere ≈ 200 ml", nova = 1, gruppo = FoodGroup.LATTICINO),
        f("succo", "Succo di frutta", 45.0, 0.2, drink = true, nova = 3, fibra = 0.2, gruppo = FoodGroup.ALTRO),
        f("te-freddo", "Tè freddo", 30.0, 0.0, drink = true, nova = 4, gruppo = FoodGroup.ALTRO),
    )

    fun byId(id: String): Food? = all.find { it.id == id }
}
