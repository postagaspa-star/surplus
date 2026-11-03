using System.Text.Json.Nodes;
using Surplus.Motore;
using Surplus.Nucleo;

namespace Surplus.Interfaccia;

/// <summary>
/// L'accordo fra la pagina (pc/ui) e il motore: le richieste a
/// <c>https://surplus.locale/locale/*</c> arrivano qui, dentro WebView2. Niente porte
/// aperte sul computer e nessuna chiave visibile alla pagina. Tutte le risposte sono JSON.
/// </summary>
public sealed class Ponte
{
    public const string Host = "surplus.locale";
    public const string Origine = "https://" + Host;

    private readonly Motore.Motore motore;
    private readonly Motore.Sincronizzazione? sincronizzazione;
    private readonly Rete.Alimenti alimenti;
    private readonly Rete.Ai ai;
    private readonly Motore.ControlloAggiornamenti? aggiornamenti;

    public Ponte(Motore.Motore motore, Motore.Sincronizzazione? sincronizzazione = null,
                 Motore.ControlloAggiornamenti? aggiornamenti = null)
    {
        this.motore = motore;
        this.sincronizzazione = sincronizzazione;
        this.aggiornamenti = aggiornamenti;
        alimenti = new Rete.Alimenti();
        ai = new Rete.Ai();
    }

    /// <summary>Scegli immagini dal disco: lo fa la finestra, perché serve un dialogo di Windows.</summary>
    public Func<Task<IReadOnlyList<string>>>? ScegliImmagini { get; set; }

    /// <summary>Esporta e importa il backup: anche questi passano da un dialogo.</summary>
    public Func<string, Task<string?>>? SalvaBackup { get; set; }

    public Func<Task<string?>>? ApriBackup { get; set; }

    public static bool ÈInterno(string? indirizzo)
    {
        if (string.IsNullOrEmpty(indirizzo)) return false;
        if (indirizzo == "about:blank") return true;
        return Uri.TryCreate(indirizzo, UriKind.Absolute, out var u)
               && u.Scheme == Uri.UriSchemeHttps
               && string.Equals(u.Host, Host, StringComparison.OrdinalIgnoreCase)
               && u.IsDefaultPort;
    }

    public static bool ÈApi(Uri uri) => uri.AbsolutePath.StartsWith("/locale/", StringComparison.Ordinal);

    /// <summary>
    /// Il file della cartella ui per un percorso della pagina ("/" = index.html), oppure null se il
    /// percorso prova a uscire dalla cartella o non è un nome di file pulito.
    /// </summary>
    public static string? FileDellInterfaccia(string cartellaUi, string percorsoUrl)
    {
        string relativo;
        try
        {
            relativo = Uri.UnescapeDataString(percorsoUrl).TrimStart('/');
        }
        catch (UriFormatException)
        {
            return null;
        }
        if (relativo.Length == 0) relativo = "index.html";
        if (relativo.Contains('\\') || relativo.Contains(':') || relativo.Contains('\0')) return null;
        if (relativo.Split('/').Any(p => p is "" or "." or "..")) return null;
        var radice = Path.GetFullPath(cartellaUi).TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
        var completo = Path.GetFullPath(Path.Combine(radice, relativo.Replace('/', Path.DirectorySeparatorChar)));
        return completo.StartsWith(radice, StringComparison.OrdinalIgnoreCase) ? completo : null;
    }

    public static string TipoContenuto(string file) => Path.GetExtension(file).ToLowerInvariant() switch
    {
        ".html" or ".htm" => "text/html; charset=utf-8",
        ".js" or ".mjs" => "text/javascript; charset=utf-8",
        ".css" => "text/css; charset=utf-8",
        ".json" => "application/json; charset=utf-8",
        ".svg" => "image/svg+xml",
        ".png" => "image/png",
        ".ico" => "image/x-icon",
        ".woff2" => "font/woff2",
        ".woff" => "font/woff",
        ".ttf" => "font/ttf",
        ".txt" => "text/plain; charset=utf-8",
        _ => "application/octet-stream",
    };

    public async Task<RispostaPonte> GestisciAsync(string metodo, Uri uri, string? corpo)
    {
        metodo = metodo.ToUpperInvariant();
        var percorso = uri.AbsolutePath;
        if (!percorso.StartsWith("/locale/", StringComparison.Ordinal))
        {
            return new RispostaPonte(404, new JsonObject { ["errore"] = "Richiesta sconosciuta." }.ToJsonString());
        }
        var (stato, o) = await LocaleAsync(metodo, percorso["/locale/".Length..].TrimEnd('/'), uri.Query, corpo).ConfigureAwait(false);
        return new RispostaPonte(stato, o.ToJsonString(Json.Opzioni));
    }

    private async Task<(int, JsonObject)> LocaleAsync(string metodo, string nome, string query, string? corpo)
    {
        var o = Json.Nodo(corpo) as JsonObject;
        (string Metodo, Func<Task<JsonObject>> Azione)? rotta = nome switch
        {
            "stato" => ("GET", () => Task.FromResult(motore.Stato())),
            "salva" => ("POST", () => Task.FromResult(motore.Salva(o?["dati"] as JsonObject, Json.Lungo(o?["versione"])))),
            "impostazioni" => ("POST", () => Task.FromResult(motore.CambiaImpostazioni(o ?? new JsonObject()))),
            "cerca" => ("GET", () => alimenti.CercaAsync(Valore(query, "q"))),
            "codice" => ("GET", () => alimenti.PerCodiceAsync(Valore(query, "codice"))),
            "foto" => ("POST", () => FotoAsync(o)),
            "esercizio" => ("POST", () => EsercizioAsync(o)),
            "scegli-immagini" => ("POST", ScegliImmaginiAsync),
            "nas" => ("GET", () => Task.FromResult(StatoNas())),
            "nas/abbina" => ("POST", () => AbbinaNasAsync(o)),
            "nas/sincronizza" => ("POST", SincronizzaNasAsync),
            "nas/stacca" => ("POST", () => Task.FromResult(sincronizzazione?.Stacca() ?? StatoNas())),
            "aggiornamenti/cerca" => ("POST", () => aggiornamenti?.CercaAdessoAsync()
                ?? Task.FromResult(new JsonObject { ["ok"] = true, ["testo"] = "Questa copia di Surplus non si aggiorna da sola." })),
            "aggiornamenti/installa" => ("POST", () => Task.FromResult(aggiornamenti?.InstallaAdesso()
                ?? new JsonObject { ["errore"] = "Questa copia di Surplus non si aggiorna da sola.", ["codice"] = "non_pronta" })),
            "esporta" => ("POST", EsportaAsync),
            "importa" => ("POST", ImportaAsync),
            _ => null,
        };
        if (rotta == null) return (404, new JsonObject { ["errore"] = "Richiesta sconosciuta.", ["codice"] = "non_trovato" });
        if (rotta.Value.Metodo != metodo) return (405, new JsonObject { ["errore"] = "Richiesta sconosciuta.", ["codice"] = "non_valido" });
        var esito = await rotta.Value.Azione().ConfigureAwait(false);
        return (esito["errore"] != null ? 422 : 200, esito);
    }

    /// <summary>Lo stato della sincronizzazione col NAS. Senza NAS: "non abbinato", e basta.</summary>
    private JsonObject StatoNas() => new()
    {
        ["ok"] = true,
        ["stato"] = sincronizzazione?.PerLaPagina() ?? new JsonObject
        {
            ["abbinato"] = false,
            ["indirizzo"] = "",
            ["versioneDati"] = motore.VersioneDati,
        },
    };

    private Task<JsonObject> AbbinaNasAsync(JsonObject? o)
    {
        if (sincronizzazione == null) return Task.FromResult(StatoNas());
        return sincronizzazione.AbbinaAsync(Json.Testo(o?["indirizzo"]), Json.Testo(o?["codice"]));
    }

    private Task<JsonObject> SincronizzaNasAsync()
    {
        if (sincronizzazione == null) return Task.FromResult(StatoNas());
        if (!sincronizzazione.Abbinato)
        {
            return Task.FromResult(new JsonObject
            {
                ["errore"] = "Questo computer non è abbinato al NAS.",
                ["codice"] = "non_abbinato",
            });
        }
        return sincronizzazione.AdessoAsync("a mano");
    }

    private static string Valore(string query, string nome)
    {
        foreach (var pezzo in query.TrimStart('?').Split('&', StringSplitOptions.RemoveEmptyEntries))
        {
            var i = pezzo.IndexOf('=');
            if (i <= 0) continue;
            if (pezzo[..i] != nome) continue;
            try
            {
                return Uri.UnescapeDataString(pezzo[(i + 1)..].Replace('+', ' '));
            }
            catch (UriFormatException)
            {
                return "";
            }
        }
        return "";
    }

    private async Task<JsonObject> FotoAsync(JsonObject? o)
    {
        var i = motore.Impostazioni;
        if (!i.AiAttiva)
        {
            return new JsonObject
            {
                ["errore"] = "Per usare le foto serve la tua chiave: mettila in Impostazioni → Riconoscimento foto.",
                ["codice"] = "senza_chiave",
            };
        }
        var immagini = Json.Lista(o?["immagini"]).Select(x => Json.Testo(x)).Where(x => !string.IsNullOrEmpty(x)).Select(x => x!).ToList();
        if (immagini.Count == 0) return new JsonObject { ["errore"] = "Nessuna foto da guardare.", ["codice"] = "non_valido" };
        var turni = Json.Lista(o?["turni"]).Select(t => (Json.Booleano(t?["utente"]) ?? true, Json.Testo(t?["testo"]) ?? "")).ToList();
        return await ai.FotoAsync(i, immagini, Json.Booleano(o?["unico"]) ?? true, turni).ConfigureAwait(false);
    }

    private async Task<JsonObject> EsercizioAsync(JsonObject? o)
    {
        var i = motore.Impostazioni;
        var nome = (Json.Testo(o?["nome"]) ?? "").Trim();
        if (nome.Length == 0) return new JsonObject { ["errore"] = "Scrivi prima il nome dell'esercizio.", ["codice"] = "non_valido" };
        if (!i.AiAttiva)
        {
            return new JsonObject
            {
                ["errore"] = "Non riconosco questo nome. Scrivi i dettagli a mano, oppure metti la tua chiave AI in Impostazioni → Riconoscimento foto: così compila anche i nomi insoliti.",
                ["codice"] = "senza_chiave",
            };
        }
        return await ai.EsercizioAsync(i, nome).ConfigureAwait(false);
    }

    private async Task<JsonObject> ScegliImmaginiAsync()
    {
        if (ScegliImmagini == null) return new JsonObject { ["errore"] = "La finestra non è aperta.", ["codice"] = "non_disponibile" };
        var scelte = await ScegliImmagini().ConfigureAwait(false);
        var lista = new JsonArray();
        foreach (var s in scelte)
        {
            // L'anteprima parte subito con la scelta: chi guarda deve vedere cosa ha preso,
            // non un elenco di nomi di file.
            var preparata = await Task.Run(() => Rete.Immagini.Prepara(s)).ConfigureAwait(false);
            if (preparata == null) continue;
            lista.Add(new JsonObject
            {
                ["file"] = s,
                ["nome"] = Path.GetFileName(s),
                ["anteprima"] = preparata.Anteprima,
                ["token"] = Rete.Immagini.TokenStimati(preparata.Larghezza, preparata.Altezza),
            });
        }
        return new JsonObject { ["ok"] = true, ["immagini"] = lista };
    }

    private async Task<JsonObject> EsportaAsync()
    {
        if (SalvaBackup == null) return new JsonObject { ["errore"] = "La finestra non è aperta.", ["codice"] = "non_disponibile" };
        var dove = await SalvaBackup(Json.Scrivi(motore.Dati())).ConfigureAwait(false);
        return dove == null
            ? new JsonObject { ["annullato"] = true }
            : new JsonObject { ["ok"] = true, ["file"] = dove };
    }

    private async Task<JsonObject> ImportaAsync()
    {
        if (ApriBackup == null) return new JsonObject { ["errore"] = "La finestra non è aperta.", ["codice"] = "non_disponibile" };
        var testo = await ApriBackup().ConfigureAwait(false);
        if (testo == null) return new JsonObject { ["annullato"] = true };
        var letto = Json.Oggetto(testo);
        if (letto == null) return new JsonObject { ["errore"] = "Quel file non è un backup di Surplus.", ["codice"] = "non_valido" };
        var schema = Json.Intero(letto["schemaVersion"]) ?? 0;
        if (schema > Versione.Schema)
        {
            return new JsonObject { ["errore"] = "Backup creato con una versione più recente: aggiorna Surplus e riprova.", ["codice"] = "non_valido" };
        }
        return new JsonObject { ["ok"] = true, ["dati"] = Diario.Sistema(letto) };
    }
}
