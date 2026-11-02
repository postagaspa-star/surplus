using System.Text.Json.Nodes;

namespace Surplus.Nucleo;

/// <summary>
/// Dispensa ed esercizi di partenza: gli stessi dell'app Android (SeedFoods.kt e
/// SeedExercises.kt), perché i due diari devono poter convivere e, quando
/// arriverà la sincronizzazione, parlare degli stessi alimenti con gli stessi id.
///
/// Valori per 100 g (100 ml per le bevande), approssimati apposta: la costanza
/// conta più della precisione al grammo.
/// </summary>
public static class Semi
{
    private static JsonObject Alimento(
        string id, string nome, double kcal, double prot, double? grassi = null,
        bool bevanda = false, string? suggerimento = null, int nova = 1, double fibra = 0,
        string gruppo = "ALTRO")
    {
        var o = new JsonObject
        {
            ["id"] = id,
            ["nome"] = nome,
            ["kcal100"] = kcal,
            ["prot100"] = prot,
            ["grassi100"] = grassi,
            ["carb100"] = null,
            ["fibra100"] = fibra,
            ["grassiSaturi100"] = null,
            ["zuccheri100"] = null,
            ["sale100"] = null,
            ["nova"] = nova,
            ["gruppo"] = gruppo,
            ["isDrink"] = bevanda,
            ["hint"] = suggerimento,
            ["barcode"] = null,
            ["custom"] = false,
        };
        return o;
    }

    public static JsonArray Alimenti() => new(
        // Base / carboidrati
        Alimento("pane", "Pane", 268, 8, 0.5, suggerimento: "1 fetta ≈ 30 g", nova: 3, fibra: 3.5, gruppo: "CEREALE"),
        Alimento("fette-biscottate", "Fette biscottate", 410, 11, suggerimento: "1 fetta ≈ 8 g", nova: 3, fibra: 3.5, gruppo: "CEREALE"),
        Alimento("pasta", "Pasta secca (cruda)", 350, 12, 1.5, suggerimento: "porzione piano: 120–150 g", nova: 1, fibra: 3.0, gruppo: "CEREALE"),
        Alimento("riso", "Riso (crudo)", 360, 7, nova: 1, fibra: 1.4, gruppo: "CEREALE"),
        Alimento("taralli", "Taralli", 450, 10, 20, suggerimento: "1 tarallo ≈ 9 g — controlla etichetta", nova: 3, fibra: 2.5, gruppo: "CEREALE"),
        Alimento("cereali", "Cereali da colazione", 380, 8, nova: 4, fibra: 7.0, gruppo: "CEREALE"),
        Alimento("patate", "Patate (crude)", 80, 2, nova: 1, fibra: 2.1, gruppo: "ALTRO"),
        // Formaggi e latticini
        Alimento("asiago-fresco", "Asiago fresco", 370, 24, 29, nova: 3, gruppo: "LATTICINO"),
        Alimento("asiago-stagionato", "Asiago stagionato", 415, 27.5, 33.5, nova: 3, gruppo: "LATTICINO"),
        Alimento("mozzarella", "Mozzarella", 250, 18, 19, nova: 3, gruppo: "LATTICINO"),
        Alimento("grana", "Grana Padano", 390, 33, 28, suggerimento: "1 cucchiaio ≈ 10 g", nova: 3, gruppo: "LATTICINO"),
        Alimento("yogurt-greco", "Yogurt greco intero", 95, 9, 5, suggerimento: "1 vasetto ≈ 170 g", nova: 1, gruppo: "LATTICINO"),
        Alimento("yogurt-bianco", "Yogurt bianco intero", 65, 3.8, 3.6, suggerimento: "1 vasetto ≈ 125 g", nova: 1, gruppo: "LATTICINO"),
        // Affettati e condimenti proteici
        Alimento("cotto-cubetti", "Prosciutto cotto (cubetti)", 165, 19, 9, nova: 4, gruppo: "PROTEICO"),
        Alimento("crudo", "Prosciutto crudo", 270, 26, 18, nova: 3, gruppo: "PROTEICO"),
        Alimento("bresaola", "Bresaola", 150, 32, 2.6, nova: 4, gruppo: "PROTEICO"),
        Alimento("ragu", "Ragù di carne (pronto)", 130, 8, 9, nova: 4, fibra: 1.2, gruppo: "PROTEICO"),
        // Uova, carne, pesce (peso crudo)
        Alimento("uova", "Uova", 143, 12.5, 9.5, suggerimento: "1 uovo ≈ 60 g", nova: 1, gruppo: "PROTEICO"),
        Alimento("pollo", "Petto di pollo", 110, 23, 1.5, nova: 1, gruppo: "PROTEICO"),
        Alimento("manzo", "Manzo magro", 130, 21, 5, nova: 1, gruppo: "PROTEICO"),
        Alimento("tacchino", "Tacchino", 105, 22, 1.5, nova: 1, gruppo: "PROTEICO"),
        Alimento("hamburger", "Hamburger di manzo", 230, 17, 17, nova: 3, gruppo: "PROTEICO"),
        Alimento("pesce-bianco", "Pesce bianco (orata/branzino)", 110, 19, 3.5, nova: 1, gruppo: "PROTEICO"),
        Alimento("tonno", "Tonno sott'olio (sgocciolato)", 190, 25, 10, nova: 3, gruppo: "PROTEICO"),
        Alimento("salmone", "Salmone", 200, 20, 13, nova: 1, gruppo: "PROTEICO"),
        // Frutta e frutta secca
        Alimento("frutta-secca", "Frutta secca mista", 600, 18, 53, suggerimento: "una manciata ≈ 30 g", nova: 1, fibra: 7.0, gruppo: "GRASSI"),
        Alimento("mandorle", "Mandorle", 600, 21, 52, nova: 1, fibra: 12.5, gruppo: "GRASSI"),
        Alimento("noci", "Noci", 650, 15, 63, nova: 1, fibra: 6.7, gruppo: "GRASSI"),
        Alimento("banana", "Banana", 89, 1.1, suggerimento: "1 media ≈ 120 g", nova: 1, fibra: 2.6, gruppo: "FRUTTA"),
        Alimento("mela", "Mela", 52, 0.3, suggerimento: "1 media ≈ 180 g", nova: 1, fibra: 2.4, gruppo: "FRUTTA"),
        Alimento("frutto", "Frutto generico", 60, 0.8, suggerimento: "1 frutto ≈ 150 g", nova: 1, fibra: 2.0, gruppo: "FRUTTA"),
        // Dolci / spalmabili
        Alimento("miele", "Miele", 304, 0.4, suggerimento: "1 cucchiaino ≈ 10 g", nova: 1, gruppo: "DOLCE"),
        Alimento("marmellata", "Marmellata", 240, 0.5, nova: 3, fibra: 1.0, gruppo: "DOLCE"),
        Alimento("nutella", "Nutella", 539, 6, 30.5, suggerimento: "1 cucchiaio ≈ 15 g", nova: 4, fibra: 3.4, gruppo: "DOLCE"),
        // Grassi
        Alimento("olio", "Olio d'oliva", 900, 0, 100, suggerimento: "1 cucchiaio ≈ 10 g", nova: 2, gruppo: "GRASSI"),
        Alimento("burro", "Burro", 750, 0.7, 83, nova: 2, gruppo: "GRASSI"),
        // Verdura
        Alimento("verdura", "Verdura mista", 30, 1.5, nova: 1, fibra: 2.5, gruppo: "VERDURA"),
        Alimento("insalata", "Insalata", 20, 1.4, nova: 1, fibra: 1.8, gruppo: "VERDURA"),
        Alimento("pomodori", "Pomodori", 20, 1, nova: 1, fibra: 1.2, gruppo: "VERDURA"),
        // Bevande (valori per 100 ml)
        Alimento("latte", "Latte intero", 64, 3.3, 3.6, bevanda: true, suggerimento: "1 bicchiere ≈ 200 ml", nova: 1, gruppo: "LATTICINO"),
        Alimento("succo", "Succo di frutta", 45, 0.2, bevanda: true, nova: 3, fibra: 0.2, gruppo: "ALTRO"),
        Alimento("te-freddo", "Tè freddo", 30, 0, bevanda: true, nova: 4, gruppo: "ALTRO"));

    private static JsonObject Esercizio(
        string id, string nome, string gruppo, bool spinta, bool trazione,
        string? serie = null, int? repMin = null, int? repMax = null, string? nota = null) =>
        new()
        {
            ["id"] = id,
            ["nome"] = nome,
            ["gruppo"] = gruppo,
            ["spinta"] = spinta,
            ["trazione"] = trazione,
            ["serie"] = serie,
            ["repMin"] = repMin,
            ["repMax"] = repMax,
            ["nota"] = nota,
            ["custom"] = false,
            ["incrementoKg"] = null,
        };

    public static JsonArray Esercizi() => new(
        // Spinta
        Esercizio("shoulder-press", "Shoulder Press", "Spalle", true, false, "3-4", 8, 12),
        Esercizio("alzate-laterali", "Alzate Laterali", "Spalle", true, false, "3", 12, 20),
        Esercizio("french-press", "French Press", "Tricipiti", true, false, "3", 10, 15, "estensione sopra la testa"),
        Esercizio("diamond-pushup", "Diamond Push-up", "Tricipiti", true, false, null, null, null, "vicino al cedimento"),
        Esercizio("floor-press", "Floor Press con manubri", "Petto", true, false, "3", 8, 12),
        // Trazione
        Esercizio("rematore-un-braccio", "Rematore a un braccio", "Schiena", false, true, "3", 10, 12, "per lato"),
        Esercizio("superman-row", "Superman Row", "Schiena", false, true, "2-3", 12, 15, "nuotata a terra"),
        Esercizio("curl-manubri", "Curl con manubri", "Bicipiti", false, true, "3", 8, 12),
        Esercizio("hammer-curl", "Hammer Curl", "Bicipiti", false, true, "2-3", 10, 15),
        Esercizio("reverse-fly", "Reverse Fly", "Salute spalle", false, true, "2-3", 12, 15),
        Esercizio("face-pull", "Face Pull", "Salute spalle", false, true, "2-3", 12, 15, "con elastico"),
        // In entrambe
        Esercizio("leg-raises", "Leg Raises a terra", "Addominali", true, true, "3", 12, 20),
        Esercizio("crunch", "Crunch", "Addominali", true, true, "3", 15, 20, "anche con manubrio leggero sul petto"),
        Esercizio("polpacci-una-gamba", "Alzate polpacci a una gamba", "Polpacci", true, true, "3", 15, 25, "vicino al cedimento"));
}
