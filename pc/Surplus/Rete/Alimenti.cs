using System.Net.Http;
using System.Text.Json.Nodes;
using Surplus.Nucleo;

namespace Surplus.Rete;

/// <summary>
/// Ricerca di un alimento negli archivi pubblici: Open Food Facts (ottima
/// copertura italiana) e, per i codici a barre che lì non ci sono, USDA
/// FoodData Central (forte su integratori, barrette e marche estere).
///
/// Esce solo il testo cercato o il codice del prodotto: nessun dato del diario.
/// Il prodotto scelto finisce in dispensa, quindi la seconda volta è offline.
/// </summary>
public sealed class Alimenti
{
    private const string UA = "Surplus/1.0 (app personale; computer)";
    private const string Campi = "product_name,product_name_it,brands,quantity,nutriments,categories_tags,nova_group,additives_n";
    private const string SenzaRete = "Archivi non raggiungibili: controlla la connessione. Puoi comunque inserire il prodotto a mano.";

    private static readonly HttpClient Http = Crea();

    private static HttpClient Crea()
    {
        var c = new HttpClient { Timeout = TimeSpan.FromSeconds(12) };
        c.DefaultRequestHeaders.Add("User-Agent", UA);
        return c;
    }

    public async Task<JsonObject> CercaAsync(string testo)
    {
        testo = testo.Trim();
        if (testo.Length < 2) return new JsonObject { ["ok"] = true, ["risultati"] = new JsonArray() };

        // Un codice a barre scritto nella ricerca vale come scansione.
        if (ÈCodice(testo)) return await PerCodiceAsync(testo).ConfigureAwait(false);

        // Prima il motore di ricerca nuovo di Open Food Facts, veloce e che regge le
        // ricerche di fila; quello vecchio (search.pl) risponde "occupato" a metà delle
        // richieste e resta solo come riserva.
        string corpo;
        try
        {
            corpo = await Http.GetStringAsync("https://search.openfoodfacts.org/search?q=" + Uri.EscapeDataString(Ricerca(testo))
                                              + "&langs=it&page_size=15&fields=" + Campi).ConfigureAwait(false);
        }
        catch (Exception e) when (e is HttpRequestException or TaskCanceledException)
        {
            Motore.Log.Avviso("ricerca alimenti: motore nuovo non raggiunto, provo il vecchio (" + e.Message + ")");
            try
            {
                corpo = await Http.GetStringAsync("https://world.openfoodfacts.org/cgi/search.pl?search_terms=" + Uri.EscapeDataString(testo)
                                                  + "&search_simple=1&action=process&json=1&page_size=15&fields=" + Campi + "&countries_tags_en=italy").ConfigureAwait(false);
            }
            catch (Exception e2) when (e2 is HttpRequestException or TaskCanceledException)
            {
                Motore.Log.Errore("ricerca alimenti non riuscita", e2);
                return new JsonObject { ["errore"] = SenzaRete, ["codice"] = "rete" };
            }
        }
        return new JsonObject { ["ok"] = true, ["risultati"] = Risultati(corpo) };
    }

    /// <summary>I prodotti di una risposta: "hits" nel motore nuovo, "products" in quello vecchio.</summary>
    public static JsonArray Risultati(string corpo)
    {
        var radice = Json.Oggetto(corpo);
        var fuori = new JsonArray();
        foreach (var p in Json.Lista(radice?["hits"] ?? radice?["products"]))
        {
            if (p is JsonObject po && DaOpenFoodFacts(po, null) is JsonObject info) fuori.Add(info);
        }
        return fuori;
    }

    /// <summary>
    /// Il motore nuovo legge la ricerca come una piccola formula: due punti, virgolette e
    /// parentesi scritti a mano la romperebbero. Restano lettere, cifre, spazi e apostrofi,
    /// più il filtro sui prodotti venduti in Italia.
    /// </summary>
    public static string Ricerca(string testo)
    {
        var pulita = new string(testo.Select(c => char.IsLetterOrDigit(c) || c == '\'' ? c : ' ').ToArray());
        return string.Join(' ', pulita.Split(' ', StringSplitOptions.RemoveEmptyEntries)) + " countries_tags:\"en:italy\"";
    }

    public async Task<JsonObject> PerCodiceAsync(string codice)
    {
        codice = codice.Trim();
        if (!ÈCodice(codice))
        {
            return new JsonObject { ["errore"] = $"«{codice}» non sembra il codice a barre di un prodotto.", ["codice"] = "non_valido" };
        }

        var falliti = 0;
        foreach (var archivio in new Func<string, Task<JsonObject?>>[] { OpenFoodFactsAsync, UsdaAsync })
        {
            try
            {
                if (await archivio(codice).ConfigureAwait(false) is JsonObject trovato)
                {
                    return new JsonObject { ["ok"] = true, ["risultati"] = new JsonArray(trovato) };
                }
            }
            catch (Exception e) when (e is HttpRequestException or TaskCanceledException)
            {
                falliti++;
            }
        }
        return falliti == 2
            ? new JsonObject { ["errore"] = SenzaRete, ["codice"] = "rete" }
            : new JsonObject { ["ok"] = true, ["risultati"] = new JsonArray() };
    }

    /// <summary>Un codice a barre è fatto solo di cifre (EAN-8/13, UPC-A/E).</summary>
    public static bool ÈCodice(string codice) =>
        codice.Length is >= 6 and <= 14 && codice.All(char.IsDigit);

    private async Task<JsonObject?> OpenFoodFactsAsync(string codice)
    {
        var corpo = await Http.GetStringAsync($"https://world.openfoodfacts.org/api/v2/product/{codice}.json?fields={Campi}").ConfigureAwait(false);
        var root = Json.Oggetto(corpo);
        if (root == null) return null;
        if (Json.Numero(root["status"]) == 0 || Json.Testo(root["status"]) == "0") return null;
        return root["product"] is JsonObject p ? DaOpenFoodFacts(p, codice) : null;
    }

    private static JsonObject? DaOpenFoodFacts(JsonObject p, string? codice)
    {
        var nome = (Json.Testo(p["product_name_it"]) ?? Json.Testo(p["product_name"]))?.Trim();
        if (string.IsNullOrWhiteSpace(nome)) return null;
        var n = p["nutriments"] as JsonObject;
        var kcal = Json.Numero(n?["energy-kcal_100g"]) ?? Json.Numero(n?["energy_100g"]) / 4.184;
        if (kcal is null or <= 0) return null; // senza calorie il prodotto non serve a nulla

        var categorie = string.Join(" ", Json.Lista(p["categories_tags"]).Select(x => Json.Testo(x) ?? ""));
        // La marca arriva come "A, B" dal motore vecchio e come ["A", "B"] da quello nuovo.
        var marca = (Json.Testo(p["brands"]) ?? Json.Testo(Json.Lista(p["brands"]).FirstOrDefault()))?.Split(',').FirstOrDefault()?.Trim();
        var nova = Json.Numero(p["nova_group"]) is double g && g >= 1 && g <= 4 ? (int)g : (int?)null;

        return Info(
            nome: nome!, marca: string.IsNullOrWhiteSpace(marca) ? null : marca,
            kcal: Math.Round(kcal.Value * 10) / 10,
            prot: Json.Numero(n?["proteins_100g"]), grassi: Json.Numero(n?["fat_100g"]),
            carb: Json.Numero(n?["carbohydrates_100g"]), fibra: Json.Numero(n?["fiber_100g"]),
            saturi: Json.Numero(n?["saturated-fat_100g"]), zuccheri: Json.Numero(n?["sugars_100g"]),
            sale: Json.Numero(n?["salt_100g"]), nova: nova,
            categorie: categorie, codice: codice, fonte: "Open Food Facts");
    }

    private async Task<JsonObject?> UsdaAsync(string codice)
    {
        // DEMO_KEY è la chiave pubblica documentata da api.data.gov: non è un segreto,
        // ma è limitata a poche richieste all'ora. Se il limite scatta resta il manuale.
        var corpo = await Http.GetStringAsync($"https://api.nal.usda.gov/fdc/v1/foods/search?api_key=DEMO_KEY&query={codice}&dataType=Branded&pageSize=5").ConfigureAwait(false);
        var cibi = Json.Lista(Json.Oggetto(corpo)?["foods"]);
        var atteso = codice.TrimStart('0');
        JsonObject? p = null;
        foreach (var c in cibi)
        {
            // Il confronto deve essere sul codice: la ricerca testuale può restituire altro.
            if (c is JsonObject co && Json.Testo(co["gtinUpc"])?.TrimStart('0') == atteso) { p = co; break; }
        }
        if (p == null) return null;

        var valori = new Dictionary<string, double?>(StringComparer.Ordinal);
        foreach (var v in Json.Lista(p["foodNutrients"]))
        {
            var numero = Json.Testo(v?["nutrientNumber"]) ?? Json.Numero(v?["nutrientNumber"])?.ToString(System.Globalization.CultureInfo.InvariantCulture);
            if (numero != null) valori[numero] = Json.Numero(v?["value"]);
        }
        double? V(string k) => valori.TryGetValue(k, out var x) ? x : null;
        var kcal = V("208");
        if (kcal is null or <= 0) return null;

        var nome = Json.Testo(p["description"])?.Trim();
        if (string.IsNullOrWhiteSpace(nome)) return null;
        nome = char.ToUpperInvariant(nome![0]) + nome[1..].ToLowerInvariant();
        var marca = Json.Testo(p["brandOwner"])?.Trim();

        return Info(
            nome: nome, marca: string.IsNullOrWhiteSpace(marca) ? null : marca,
            kcal: Math.Round(kcal.Value * 10) / 10,
            prot: V("203"), grassi: V("204"), carb: V("205"), fibra: V("291"),
            saturi: V("606"), zuccheri: V("269"),
            // Il sodio è in mg: sale (g) = sodio × 2,5 / 1000.
            sale: V("307") is double sodio ? Math.Round(sodio * 2.5 / 1000 * 100) / 100 : null,
            // USDA non pubblica il NOVA: un confezionato di marca è quasi sempre lavorato.
            nova: 4,
            categorie: Json.Testo(p["foodCategory"]) ?? "",
            codice: codice, fonte: "USDA FoodData Central");
    }

    private static JsonObject Info(
        string nome, string? marca, double? kcal, double? prot, double? grassi, double? carb,
        double? fibra, double? saturi, double? zuccheri, double? sale, int? nova,
        string categorie, string? codice, string fonte) => new()
        {
            ["nome"] = marca != null ? $"{nome} ({marca})" : nome,
            ["kcal100"] = kcal,
            ["prot100"] = prot,
            ["grassi100"] = grassi,
            ["carb100"] = carb,
            ["fibra100"] = fibra,
            ["grassiSaturi100"] = saturi,
            ["zuccheri100"] = zuccheri,
            ["sale100"] = sale,
            ["nova"] = nova,
            ["gruppo"] = Etichette.Gruppo(categorie),
            ["isDrink"] = Etichette.ÈBevanda(categorie),
            ["hint"] = "da " + fonte,
            ["barcode"] = codice,
            ["custom"] = true,
        };
}
