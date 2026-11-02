using System.Text.Json;
using System.Text.Json.Nodes;

namespace Surplus.Nucleo;

/// <summary>
/// Letture difensive del JSON: il file dei dati arriva dall'app Android, da un
/// backup o dalla pagina, quindi un campo può mancare o avere il tipo sbagliato.
/// Qui non si lancia mai: si restituisce null e chi chiama decide.
/// </summary>
public static class Json
{
    public static readonly JsonSerializerOptions Opzioni = new()
    {
        WriteIndented = true,
        // Il file è lo stesso dell'app Android: accenti e simboli vanno scritti come sono.
        Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping,
    };

    public static JsonNode? Nodo(string? testo)
    {
        if (string.IsNullOrWhiteSpace(testo)) return null;
        try
        {
            return JsonNode.Parse(testo);
        }
        catch (JsonException)
        {
            return null;
        }
    }

    public static JsonObject? Oggetto(string? testo) => Nodo(testo) as JsonObject;

    public static string? Testo(JsonNode? n)
    {
        if (n is not JsonValue v) return null;
        if (v.TryGetValue<string>(out var s)) return s;
        return null;
    }

    /// <summary>
    /// Un numero, comunque sia stato scritto: un valore letto da file arriva come
    /// JsonElement, uno creato qui dentro è un int o un long, e gli archivi
    /// alimentari a volte mandano i numeri dentro una stringa.
    /// </summary>
    public static double? Numero(JsonNode? n)
    {
        if (n is not JsonValue v) return null;
        if (v.TryGetValue<double>(out var d)) return d;
        if (v.TryGetValue<int>(out var i)) return i;
        if (v.TryGetValue<long>(out var l)) return l;
        if (v.TryGetValue<decimal>(out var m)) return (double)m;
        if (v.TryGetValue<string>(out var s) && double.TryParse(s.Replace(',', '.'), System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out var p)) return p;
        return null;
    }

    public static int? Intero(JsonNode? n) => Numero(n) is double d && d >= int.MinValue && d <= int.MaxValue ? (int)Math.Round(d) : null;

    public static long? Lungo(JsonNode? n) => Numero(n) is double d ? (long)Math.Round(d) : null;

    public static bool? Booleano(JsonNode? n)
    {
        if (n is not JsonValue v) return null;
        if (v.TryGetValue<bool>(out var b)) return b;
        return null;
    }

    public static JsonArray Lista(JsonNode? n) => n as JsonArray ?? new JsonArray();

    /// <summary>Copia staccata: i nodi di System.Text.Json hanno un solo genitore, riusarli dà errore.</summary>
    public static JsonNode? Copia(JsonNode? n) => n?.DeepClone();

    public static string Scrivi(JsonNode n) => n.ToJsonString(Opzioni);
}
