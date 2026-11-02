using System.Text.RegularExpressions;

namespace Surplus.Rete;

/// <summary>
/// Deduce gruppo alimentare e "è una bevanda" dalle categorie testuali degli
/// archivi. Stessa logica di FoodTagger.kt sull'app, comprese le due trappole
/// imparate sul campo:
/// 1. Open Food Facts mette quasi tutto il cibo vegetale sotto la categoria
///    ombrello "plant-based-foods-and-beverages", che contiene "beverages" ma
///    copre anche pasta, pane e verdura: va tolta prima di decidere.
/// 2. Cercare pezzi di parola dà falsi positivi ("steak" contiene "tea"):
///    il confronto è per parola intera.
/// </summary>
public static class Etichette
{
    private static readonly string[] Ombrello =
    {
        "plant based foods and beverages",
        "beverages and beverages preparations",
    };

    /// <summary>
    /// Solidi la cui categoria può nominare una bevanda ("milk chocolate", "yogurt"):
    /// qui il solido vince. Frutta, verdura, semi e frutta secca NON vanno messi qui,
    /// altrimenti succhi e bevande vegetali diventerebbero solidi.
    /// </summary>
    private static readonly string[] Solidi =
    {
        "pasta", "pastas", "pane", "bread", "breads", "pizza", "biscuit", "biscuits",
        "cheese", "cheeses", "formaggio", "formaggi", "yogurt", "yogurts", "yoghurts",
        "meat", "meats", "carne", "carni", "fish", "pesce", "snack", "snacks",
        "cereal", "cereals", "cereali", "rice", "riso", "flour", "flours", "farina", "farine",
        "chocolate", "chocolates", "cioccolato", "candies", "sweets", "dolci",
        "dessert", "desserts", "creams", "sauce", "sauces", "salse", "soup", "soups",
        "spread", "spreads", "eggs", "uova",
    };

    private static readonly string[] Bevande =
    {
        "beverage", "beverages", "drink", "drinks", "bevanda", "bevande", "bibita", "bibite",
        "water", "waters", "acqua", "acque", "juice", "juices", "jus", "succo", "succhi",
        "soda", "sodas", "milk", "milks", "latte", "tea", "teas", "the", "coffee", "coffees",
        "caffe", "caffè", "smoothie", "smoothies", "nectar", "nectars", "nettare",
    };

    private static readonly string[] Verdura = { "vegetable", "vegetables", "verdura", "verdure", "ortaggi", "legume", "legumes", "legumi", "salad", "salads", "insalata", "insalate", "pomodori", "tomatoes" };
    private static readonly string[] Frutta = { "fruit", "fruits", "frutta", "frutti", "berries", "apples", "bananas" };
    private static readonly string[] Proteico = { "meat", "meats", "carne", "carni", "fish", "pesce", "poultry", "egg", "eggs", "uova", "seafood", "salumi", "prosciutto", "hams", "sausages" };
    private static readonly string[] Cereale = { "cereal", "cereals", "cereali", "pasta", "pastas", "bread", "breads", "pane", "rice", "riso", "biscuit", "biscuits", "flour", "flours", "farina", "farine" };
    private static readonly string[] Latticino = { "dairy", "dairies", "cheese", "cheeses", "formaggio", "formaggi", "yogurt", "yogurts", "yoghurts", "milk", "milks", "latte" };
    private static readonly string[] Grassi = { "oil", "oils", "olio", "nut", "nuts", "noci", "mandorle", "seeds", "semi", "butter", "burro" };
    private static readonly string[] Dolce = { "sweet", "sweets", "dolci", "sugar", "zucchero", "chocolate", "chocolates", "cioccolato", "dessert", "desserts", "candy", "candies", "confiture", "marmellata", "jams", "biscuits" };
    private static readonly string[] Succhi = { "juice", "juices", "jus", "succo", "succhi", "nectar", "nectars" };

    private static readonly Regex Lingua = new(@"\b[a-z]{2}:", RegexOptions.Compiled);
    private static readonly Regex Spazi = new(@"\s+", RegexOptions.Compiled);

    internal static string Normalizza(string categorie)
    {
        var t = Lingua.Replace(categorie.ToLowerInvariant(), " ").Replace('-', ' ').Replace(',', ' ');
        foreach (var o in Ombrello) t = t.Replace(o, " ");
        return " " + Spazi.Replace(t, " ").Trim() + " ";
    }

    private static bool HaParola(string t, string p) => t.Contains(" " + p + " ", StringComparison.Ordinal);

    private static bool HaQualcuna(string t, string[] parole) => parole.Any(p => HaParola(t, p));

    /// <summary>Un solido resta un solido anche se la sua categoria nomina "beverages".</summary>
    public static bool ÈBevanda(string categorie)
    {
        var t = Normalizza(categorie);
        if (HaQualcuna(t, Solidi)) return false;
        return HaQualcuna(t, Bevande);
    }

    /// <summary>Frutta e verdura hanno la precedenza: sono le uniche che contano nei consigli.</summary>
    public static string Gruppo(string categorie)
    {
        var t = Normalizza(categorie);
        var succo = HaQualcuna(t, Succhi);
        if (HaQualcuna(t, Verdura)) return "VERDURA";
        if (HaQualcuna(t, Frutta) && !succo) return "FRUTTA";
        if (HaQualcuna(t, Proteico)) return "PROTEICO";
        if (HaQualcuna(t, Latticino)) return "LATTICINO";
        if (HaQualcuna(t, Dolce)) return "DOLCE";
        if (HaQualcuna(t, Grassi)) return "GRASSI";
        if (HaQualcuna(t, Cereale)) return "CEREALE";
        return "ALTRO";
    }
}
