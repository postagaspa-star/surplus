using System.Net;
using System.Net.Http;
using System.Text;
using System.Text.Json.Nodes;
using Surplus.Nucleo;

namespace Surplus.Rete;

/// <summary>Com'è andata una chiamata al NAS: o il corpo della risposta, o una frase da mostrare.</summary>
public sealed record EsitoNas(bool Ok, JsonObject? Corpo, string? Errore = null, string? Codice = null)
{
    public static EsitoNas Guasto(string frase, string codice) => new(false, null, frase, codice);
}

/// <summary>
/// Le chiamate al server di Surplus sul NAS di casa. Solo il diario esce da qui:
/// niente chiavi, niente impostazioni del computer.
///
/// Il token del dispositivo arriva da fuori a ogni chiamata: questo oggetto non
/// lo conserva, così non può finire in un log per sbaglio.
/// </summary>
public sealed class ClientNas
{
    private const string SenzaRete = "Il NAS non risponde. Controlla che sia acceso e che il computer sia sulla rete di casa.";

    private static readonly HttpClient Http = Crea();

    private static HttpClient Crea()
    {
        var c = new HttpClient { Timeout = TimeSpan.FromSeconds(30) };
        c.DefaultRequestHeaders.Add("User-Agent", "Surplus/" + Versione.Nome + " (computer)");
        return c;
    }

    /// <summary>
    /// Da quello che uno scrive nel campo ("192.168.1.20:8600") all'indirizzo
    /// completo. Senza http davanti si mette http, tranne per i nomi di Tailscale
    /// che vanno sempre in https.
    /// </summary>
    public static string? Normalizza(string? scritto)
    {
        // Niente TrimEnd('/') prima di guardare lo schema: "http://" diventerebbe
        // "http:", e allora si finirebbe per attaccargli davanti un altro schema e
        // credere che "http" sia il nome del NAS. La barra finale la toglie da sola
        // GetLeftPart, che tiene solo schema, nome e porta.
        var s = (scritto ?? "").Trim();
        if (s.Length == 0) return null;
        if (!s.Contains("://", StringComparison.Ordinal))
        {
            s = (s.Contains(".ts.net", StringComparison.OrdinalIgnoreCase) ? "https://" : "http://") + s;
        }
        if (!Uri.TryCreate(s, UriKind.Absolute, out var u)) return null;
        if (u.Scheme != Uri.UriSchemeHttp && u.Scheme != Uri.UriSchemeHttps) return null;
        if (string.IsNullOrEmpty(u.Host)) return null;
        return u.GetLeftPart(UriPartial.Authority);
    }

    public Task<EsitoNas> SaluteAsync(string indirizzo, CancellationToken annulla = default) =>
        ChiamaAsync(HttpMethod.Get, indirizzo, "api/salute", null, null, annulla);

    public Task<EsitoNas> VersioneAsync(string indirizzo, CancellationToken annulla = default) =>
        ChiamaAsync(HttpMethod.Get, indirizzo, "api/versione", null, null, annulla);

    public Task<EsitoNas> AbbinaAsync(string indirizzo, string codice, string nome, CancellationToken annulla = default) =>
        ChiamaAsync(HttpMethod.Post, indirizzo, "api/abbina",
            new JsonObject { ["codice"] = codice, ["nome"] = nome, ["tipo"] = "computer" }, null, annulla);

    public Task<EsitoNas> DiarioAsync(string indirizzo, string token, CancellationToken annulla = default) =>
        ChiamaAsync(HttpMethod.Get, indirizzo, "api/diario", null, token, annulla);

    /// <summary>
    /// Manda il diario. <paramref name="base"/> è la revisione dell'ultimo diario unito
    /// tenuto davvero: negativa se non si sa, e allora il NAS usa quella che ricorda lui.
    /// </summary>
    public Task<EsitoNas> SincronizzaAsync(string indirizzo, string token, JsonObject dati, long @base = -1,
                                           CancellationToken annulla = default)
    {
        var corpo = new JsonObject { ["dati"] = dati.DeepClone() };
        if (@base >= 0) corpo["base"] = @base;
        return ChiamaAsync(HttpMethod.Post, indirizzo, "api/sincronizza", corpo, token, annulla);
    }

    /// <summary>
    /// Il campanello: risponde appena il diario del NAS non è più alla <paramref name="revisione"/>
    /// di questo computer, o dopo <paramref name="attesaSecondi"/> se nessuno ha scritto niente.
    /// Non scrive nel log quando il NAS non risponde: chi ascolta lo fa di continuo, e lo
    /// dice lui una volta sola.
    /// </summary>
    public Task<EsitoNas> NovitaAsync(string indirizzo, string token, long revisione, int attesaSecondi = 20,
                                      CancellationToken annulla = default) =>
        ChiamaAsync(HttpMethod.Get, indirizzo, $"api/novita?revisione={Math.Max(0, revisione)}&attesa={attesaSecondi}",
            null, token, annulla, silenzioso: true);

    /// <summary>Scarica un file (il pacchetto dell'aggiornamento) senza tenerlo in memoria.</summary>
    public async Task<bool> ScaricaAsync(string url, string file, CancellationToken annulla = default)
    {
        try
        {
            using var risposta = await Http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, annulla).ConfigureAwait(false);
            if (!risposta.IsSuccessStatusCode) return false;
            Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(file))!);
            await using var destinazione = new FileStream(file, FileMode.Create, FileAccess.Write, FileShare.None);
            await risposta.Content.CopyToAsync(destinazione, annulla).ConfigureAwait(false);
            return true;
        }
        catch (Exception e) when (e is HttpRequestException or TaskCanceledException or IOException or UnauthorizedAccessException)
        {
            Motore.Log.Errore("scaricamento da " + url + " non riuscito", e);
            return false;
        }
    }

    private static async Task<EsitoNas> ChiamaAsync(HttpMethod metodo, string indirizzo, string percorso,
        JsonObject? corpo, string? token, CancellationToken annulla, bool silenzioso = false)
    {
        var radice = Normalizza(indirizzo);
        if (radice == null) return EsitoNas.Guasto("L'indirizzo del NAS non è scritto bene.", "indirizzo");
        using var richiesta = new HttpRequestMessage(metodo, radice + "/" + percorso);
        if (corpo != null)
        {
            richiesta.Content = new StringContent(Json.Scrivi(corpo), Encoding.UTF8, "application/json");
        }
        if (!string.IsNullOrEmpty(token))
        {
            richiesta.Headers.TryAddWithoutValidation("Authorization", "Bearer " + token);
        }
        try
        {
            using var risposta = await Http.SendAsync(richiesta, annulla).ConfigureAwait(false);
            var testo = await risposta.Content.ReadAsStringAsync(annulla).ConfigureAwait(false);
            var o = Json.Oggetto(testo);
            if (risposta.IsSuccessStatusCode) return new EsitoNas(true, o ?? new JsonObject());
            var detto = Json.Testo(o?["detail"]) ?? Json.Testo(o?["errore"]);
            return risposta.StatusCode switch
            {
                HttpStatusCode.Unauthorized => EsitoNas.Guasto(detto ?? "Questo computer non è più abbinato al NAS. Abbinalo di nuovo con un codice.", "non_abbinato"),
                HttpStatusCode.TooManyRequests => EsitoNas.Guasto(detto ?? "Troppi codici sbagliati. Aspetta un quarto d'ora.", "troppi"),
                HttpStatusCode.NotFound => EsitoNas.Guasto("Sul NAS non c'è Surplus a questo indirizzo.", "indirizzo"),
                _ => EsitoNas.Guasto(detto ?? "Il NAS ha risposto male (" + (int)risposta.StatusCode + ").", "server"),
            };
        }
        catch (Exception e) when (e is HttpRequestException or TaskCanceledException)
        {
            if (!silenzioso) Motore.Log.Errore("chiamata al NAS non riuscita (" + percorso + ")", e);
            return EsitoNas.Guasto(SenzaRete, "rete");
        }
    }
}
