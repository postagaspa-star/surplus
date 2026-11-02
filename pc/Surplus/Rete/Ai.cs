using System.Net.Http;
using System.Text;
using System.Text.Json.Nodes;
using Surplus.Nucleo;

namespace Surplus.Rete;

/// <summary>
/// Le due funzioni che usano un servizio AI: riconoscere il cibo da una foto e
/// compilare un esercizio dal nome.
///
/// Regola di progetto, uguale all'app: <b>nessuna chiave dentro il programma</b>.
/// Chi vuole queste funzioni mette la propria nelle impostazioni; senza chiave
/// non compaiono e tutto il resto continua a lavorare offline. La chiave sta nel
/// programma, mai nella pagina.
/// </summary>
public sealed class Ai
{
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(120) };

    private const string PromptFoto = """
Sei l'assistente di un diario alimentare italiano. Guarda la foto e dimmi cosa c'è da mangiare.

DUE CASI DIVERSI:
1. ETICHETTA o CONFEZIONE di un prodotto: LEGGI i valori dichiarati sulla tabella nutrizionale, non stimarli. Usa il nome del prodotto e la marca. La quantità in grammi è quella della porzione che la persona sta per mangiare: se non è deducibile, usa 100.
2. CIBO VERO (un piatto, un panino, un pasto): identifica ogni componente separatamente (pane, affettato, formaggio, salsa...) e stima i grammi guardando le proporzioni nella foto.

PER OGNI ALIMENTO:
- nome: breve, in italiano
- grammi: la quantità che vedi nella foto
- kcal100, prot100, grassi100, carb100, fibra100: valori PER 100 g, MAI per porzione
- nova: grado di lavorazione, scala NOVA
    1 = non lavorato o poco lavorato (frutta, verdura, uova, carne e pesce freschi, riso, pasta secca, latte)
    2 = ingrediente di cucina (olio, burro, zucchero, sale)
    3 = lavorato (pane, formaggi, prosciutto crudo, conserve, cibo in scatola)
    4 = prodotto industriale (merendine, bibite, snack confezionati, salse pronte, affettati con additivi, cereali da colazione)
- gruppo: esattamente uno tra VERDURA, FRUTTA, PROTEICO, CEREALE, LATTICINO, GRASSI, DOLCE, ALTRO
- certezza: alta se leggi un'etichetta o riconosci senza dubbi, media se stimi, bassa se stai tirando a indovinare

DOMANDA: se un ingrediente che non riesci a stabilire cambierebbe molto i valori (il condimento di un panino, se la carne è magra o grassa, se c'è olio o maionese, il tipo di pane), scrivi UNA domanda breve e specifica nel campo "domanda". Se non hai dubbi importanti, lascia "domanda" come stringa vuota.

Non inventare precisione che non hai: se stimi a occhio, la certezza è media o bassa.
Rispondi in italiano.
""";

    private static readonly string[] Gruppi = { "VERDURA", "FRUTTA", "PROTEICO", "CEREALE", "LATTICINO", "GRASSI", "DOLCE", "ALTRO" };
    private static readonly string[] GruppiEsercizio =
    {
        "Petto", "Schiena", "Spalle", "Salute spalle", "Bicipiti", "Tricipiti", "Avambracci",
        "Trapezio", "Gambe", "Glutei", "Polpacci", "Addominali", "Altro",
    };

    private const string NonRiconosciuto = "nome non riconosciuto";

    /// <summary>Più foto dello stesso alimento vanno unite, non trattate come cibi diversi.</summary>
    internal static string IstruzioniFoto(int quante, bool unico)
    {
        var t = new StringBuilder();
        if (quante > 1)
        {
            t.Append($"Le {quante} foto mostrano LO STESSO alimento o lo stesso piatto da angolazioni ")
             .Append("diverse: una può essere la confezione o la tabella nutrizionale. Uniscile in ")
             .Append("un unico risultato. Se una foto mostra un'etichetta, i valori per 100 g ")
             .Append("vengono da lì; le altre foto servono a capire la quantità.\n\n");
        }
        if (unico)
        {
            t.Append("Restituisci UN SOLO alimento: non scomporlo negli ingredienti, ")
             .Append("trattalo come un'unica voce.\n\n");
        }
        return t.Append(PromptFoto.Trim()).ToString();
    }

    public async Task<JsonObject> FotoAsync(
        Motore.Impostazioni impostazioni, IReadOnlyList<string> file, bool unico,
        IReadOnlyList<(bool Utente, string Testo)> turni)
    {
        var preparate = new List<Immagini.Preparata>();
        foreach (var f in file)
        {
            if (Immagini.Prepara(f) is Immagini.Preparata p) preparate.Add(p);
        }
        if (preparate.Count == 0)
        {
            return new JsonObject { ["errore"] = "Non sono riuscito a leggere queste immagini.", ["codice"] = "non_valido" };
        }

        var testo = IstruzioniFoto(preparate.Count, unico);
        try
        {
            var grezzo = impostazioni.AiFornitore == "CLAUDE"
                ? await ClaudeAsync(impostazioni, testo, preparate, turni, SchemaFotoClaude(), 8000).ConfigureAwait(false)
                : await GeminiAsync(impostazioni, testo, preparate, turni, SchemaFotoGemini()).ConfigureAwait(false);
            var esito = LeggiFoto(grezzo);
            esito["token"] = preparate.Sum(p => Immagini.TokenStimati(p.Larghezza, p.Altezza));
            return esito;
        }
        catch (Exception e)
        {
            return new JsonObject { ["errore"] = Messaggio(e), ["codice"] = "servizio" };
        }
    }

    public async Task<JsonObject> EsercizioAsync(Motore.Impostazioni impostazioni, string nome)
    {
        var testo = $"""
Sei un allenatore di palestra italiano. Compila la scheda dell'esercizio «{nome}» come la scriveresti per un ragazzo che si allena per crescere di massa muscolare.
- gruppo: il muscolo principale, esattamente uno tra: {string.Join(", ", GruppiEsercizio)}
- serie: il numero di serie, come "3" oppure "3-4"
- repMin e repMax: il range di ripetizioni per l'ipertrofia (per un esercizio a tempo, come il plank, i secondi)
- incrementoKg: di quanto sale il peso con un disco in più con l'attrezzo tipico di questo esercizio (bilanciere 2.5, macchina o cavi 5, manubri 1 o 2); 0 se si fa a corpo libero
- nota: un consiglio di tecnica brevissimo, massimo 8 parole, oppure stringa vuota
Se il nome non è un esercizio che riconosci, metti gruppo "Altro" e nota "{NonRiconosciuto}".
Rispondi in italiano.
""";
        try
        {
            var grezzo = impostazioni.AiFornitore == "CLAUDE"
                ? await ClaudeAsync(impostazioni, testo, Array.Empty<Immagini.Preparata>(), Array.Empty<(bool, string)>(), SchemaEsercizioClaude(), 2000).ConfigureAwait(false)
                : await GeminiAsync(impostazioni, testo, Array.Empty<Immagini.Preparata>(), Array.Empty<(bool, string)>(), SchemaEsercizioGemini()).ConfigureAwait(false);
            return LeggiEsercizio(grezzo);
        }
        catch (Exception e)
        {
            return new JsonObject { ["errore"] = Messaggio(e), ["codice"] = "servizio" };
        }
    }

    // ---- chiamate ----

    private static async Task<string> ClaudeAsync(
        Motore.Impostazioni i, string testo, IReadOnlyList<Immagini.Preparata> immagini,
        IReadOnlyList<(bool Utente, string Testo)> turni, JsonObject schema, int massimoToken)
    {
        var contenuto = new JsonArray();
        foreach (var img in immagini)
        {
            contenuto.Add(new JsonObject
            {
                ["type"] = "image",
                ["source"] = new JsonObject { ["type"] = "base64", ["media_type"] = img.Tipo, ["data"] = img.Base64 },
            });
        }
        contenuto.Add(new JsonObject { ["type"] = "text", ["text"] = testo });

        var messaggi = new JsonArray(new JsonObject { ["role"] = "user", ["content"] = contenuto });
        foreach (var t in turni)
        {
            messaggi.Add(new JsonObject { ["role"] = t.Utente ? "user" : "assistant", ["content"] = t.Testo });
        }

        var corpo = new JsonObject
        {
            ["model"] = i.AiModelloEffettivo,
            ["max_tokens"] = massimoToken,
            ["output_config"] = new JsonObject
            {
                // Riconoscere un'etichetta non è un compito di ragionamento profondo:
                // sforzo basso costa meno e risponde prima, senza perdere qualità.
                ["effort"] = "low",
                ["format"] = new JsonObject { ["type"] = "json_schema", ["schema"] = schema },
            },
            ["messages"] = messaggi,
        };

        var risposta = await PostAsync("https://api.anthropic.com/v1/messages", corpo, new()
        {
            ["x-api-key"] = i.AiChiave,
            ["anthropic-version"] = "2023-06-01",
        }).ConfigureAwait(false);

        var root = Json.Oggetto(risposta);
        // Un rifiuto del modello non è una risposta: va distinto da un JSON vuoto.
        if (Json.Testo(root?["stop_reason"]) == "refusal")
        {
            throw new InvalidOperationException("Il servizio ha rifiutato di rispondere a questa richiesta.");
        }
        foreach (var blocco in Json.Lista(root?["content"]))
        {
            if (Json.Testo(blocco?["type"]) == "text" && Json.Testo(blocco?["text"]) is string t) return t;
        }
        throw new InvalidOperationException("Risposta senza testo.");
    }

    private static async Task<string> GeminiAsync(
        Motore.Impostazioni i, string testo, IReadOnlyList<Immagini.Preparata> immagini,
        IReadOnlyList<(bool Utente, string Testo)> turni, JsonObject schema)
    {
        var parti = new JsonArray();
        foreach (var img in immagini)
        {
            parti.Add(new JsonObject
            {
                ["inline_data"] = new JsonObject { ["mime_type"] = img.Tipo, ["data"] = img.Base64 },
            });
        }
        parti.Add(new JsonObject { ["text"] = testo });

        var contenuti = new JsonArray(new JsonObject { ["role"] = "user", ["parts"] = parti });
        foreach (var t in turni)
        {
            contenuti.Add(new JsonObject
            {
                ["role"] = t.Utente ? "user" : "model",
                ["parts"] = new JsonArray(new JsonObject { ["text"] = t.Testo }),
            });
        }

        var corpo = new JsonObject
        {
            ["contents"] = contenuti,
            ["generationConfig"] = new JsonObject
            {
                ["responseMimeType"] = "application/json",
                ["responseSchema"] = schema,
            },
        };

        var risposta = await PostAsync(
            $"https://generativelanguage.googleapis.com/v1beta/models/{i.AiModelloEffettivo}:generateContent",
            corpo, new() { ["x-goog-api-key"] = i.AiChiave }).ConfigureAwait(false);

        var root = Json.Oggetto(risposta);
        var testoFuori = Json.Lista(root?["candidates"]).FirstOrDefault()?["content"]?["parts"];
        foreach (var p in Json.Lista(testoFuori))
        {
            if (Json.Testo(p?["text"]) is string t) return t;
        }
        throw new InvalidOperationException("Risposta senza testo.");
    }

    private static async Task<string> PostAsync(string url, JsonObject corpo, Dictionary<string, string> intestazioni)
    {
        using var richiesta = new HttpRequestMessage(HttpMethod.Post, url)
        {
            Content = new StringContent(corpo.ToJsonString(), Encoding.UTF8, "application/json"),
        };
        foreach (var (k, v) in intestazioni) richiesta.Headers.TryAddWithoutValidation(k, v);
        using var risposta = await Http.SendAsync(richiesta).ConfigureAwait(false);
        var testo = await risposta.Content.ReadAsStringAsync().ConfigureAwait(false);
        if (!risposta.IsSuccessStatusCode) throw new ErroreServizio((int)risposta.StatusCode, testo);
        return testo;
    }

    // ---- lettura delle risposte ----

    internal static JsonObject LeggiFoto(string grezzo)
    {
        var o = Json.Oggetto(EstraiJson(grezzo));
        if (o == null) return new JsonObject { ["errore"] = "Risposta del servizio non comprensibile.", ["codice"] = "servizio" };

        var alimenti = new JsonArray();
        foreach (var e in Json.Lista(o["alimenti"]))
        {
            var nome = Json.Testo(e?["nome"])?.Trim();
            if (string.IsNullOrWhiteSpace(nome)) continue;
            var gruppo = (Json.Testo(e?["gruppo"]) ?? "").ToUpperInvariant();
            var nova = Json.Intero(e?["nova"]);
            alimenti.Add(new JsonObject
            {
                ["nome"] = nome,
                ["grammi"] = Json.Numero(e?["grammi"]) is double g && g > 0 ? g : 100.0,
                ["kcal100"] = Json.Numero(e?["kcal100"]),
                ["prot100"] = Json.Numero(e?["prot100"]),
                ["grassi100"] = Json.Numero(e?["grassi100"]),
                ["carb100"] = Json.Numero(e?["carb100"]),
                ["fibra100"] = Json.Numero(e?["fibra100"]),
                ["nova"] = nova is >= 1 and <= 4 ? nova : null,
                ["gruppo"] = Gruppi.Contains(gruppo) ? gruppo : "ALTRO",
                ["certezza"] = (Json.Testo(e?["certezza"]) ?? "media").ToLowerInvariant(),
            });
        }

        var domanda = Json.Testo(o["domanda"])?.Trim();
        var note = Json.Testo(o["note"])?.Trim();
        return new JsonObject
        {
            ["ok"] = true,
            ["alimenti"] = alimenti,
            ["domanda"] = string.IsNullOrWhiteSpace(domanda) ? null : domanda,
            ["note"] = string.IsNullOrWhiteSpace(note) ? null : note,
        };
    }

    internal static JsonObject LeggiEsercizio(string grezzo)
    {
        var o = Json.Oggetto(EstraiJson(grezzo));
        if (o == null) return new JsonObject { ["errore"] = "Risposta del servizio non comprensibile.", ["codice"] = "servizio" };

        var nota = (Json.Testo(o["nota"]) ?? "").Trim();
        if (nota.Contains(NonRiconosciuto, StringComparison.OrdinalIgnoreCase))
        {
            return new JsonObject { ["ok"] = true, ["riconosciuto"] = false };
        }

        var gruppo = GruppiEsercizio.FirstOrDefault(g => string.Equals(g, Json.Testo(o["gruppo"]), StringComparison.OrdinalIgnoreCase)) ?? "Altro";
        var serie = Json.Testo(o["serie"])?.Trim() ?? "";
        if (!System.Text.RegularExpressions.Regex.IsMatch(serie, @"^\d{1,2}(-\d{1,2})?$")) serie = "3";
        var a = Json.Intero(o["repMin"]);
        var b = Json.Intero(o["repMax"]);
        if (a is < 1 or > 120) a = null;
        if (b is < 1 or > 120) b = null;
        var (min, max) = (a, b) switch
        {
            (int x, int y) => x <= y ? (x, y) : (y, x),
            (int x, null) => (x, x),
            (null, int y) => (y, y),
            _ => (8, 12),
        };
        var salto = Json.Numero(o["incrementoKg"]);

        return new JsonObject
        {
            ["ok"] = true,
            ["riconosciuto"] = true,
            ["gruppo"] = gruppo,
            ["serie"] = serie,
            ["repMin"] = min,
            ["repMax"] = max,
            ["incrementoKg"] = salto is > 0 and <= 20 ? salto : null,
            ["nota"] = nota.Length == 0 ? null : nota[..Math.Min(80, nota.Length)],
        };
    }

    /// <summary>Se il modello incornicia il JSON con del testo, tiene solo l'oggetto.</summary>
    internal static string EstraiJson(string s)
    {
        var t = s.Trim();
        if (t.StartsWith("```json", StringComparison.Ordinal)) t = t[7..];
        else if (t.StartsWith("```", StringComparison.Ordinal)) t = t[3..];
        if (t.EndsWith("```", StringComparison.Ordinal)) t = t[..^3];
        t = t.Trim();
        var i = t.IndexOf('{');
        var j = t.LastIndexOf('}');
        return i >= 0 && j > i ? t[i..(j + 1)] : t;
    }

    /// <summary>Traduce l'errore del servizio in una frase che dice cosa fare.</summary>
    internal static string Messaggio(Exception e) => e switch
    {
        ErroreServizio s when s.Codice is 401 or 403 => "Chiave non valida o senza permessi. Controllala nelle impostazioni.",
        ErroreServizio s when s.Codice == 429 => "Hai finito il credito o superato il limite di richieste del tuo piano. Riprova più tardi.",
        ErroreServizio s when s.Codice == 400 =>
            "Richiesta rifiutata: forse il nome del modello nelle impostazioni non è più valido. Dettaglio: " + Taglia(s.Corpo),
        ErroreServizio s => $"Errore del servizio ({s.Codice}). Dettaglio: " + Taglia(s.Corpo),
        InvalidOperationException x => x.Message,
        _ => "Non sono riuscito a contattare il servizio: controlla la connessione.",
    };

    private static string Taglia(string s) => s.Length <= 160 ? s : s[..160];

    // ---- schemi della risposta ----

    private static JsonObject SchemaFotoClaude() => new()
    {
        ["type"] = "object",
        ["properties"] = new JsonObject
        {
            ["alimenti"] = new JsonObject
            {
                ["type"] = "array",
                ["items"] = new JsonObject
                {
                    ["type"] = "object",
                    ["properties"] = new JsonObject
                    {
                        ["nome"] = Tipo("string"),
                        ["grammi"] = Tipo("number"),
                        ["kcal100"] = Tipo("number"),
                        ["prot100"] = Tipo("number"),
                        ["grassi100"] = Tipo("number"),
                        ["carb100"] = Tipo("number"),
                        ["fibra100"] = Tipo("number"),
                        ["nova"] = Tipo("integer"),
                        ["gruppo"] = Enumerazione("string", Gruppi),
                        ["certezza"] = Enumerazione("string", new[] { "alta", "media", "bassa" }),
                    },
                    ["required"] = new JsonArray("nome", "grammi", "kcal100", "prot100", "grassi100", "carb100", "fibra100", "nova", "gruppo", "certezza"),
                    ["additionalProperties"] = false,
                },
            },
            ["domanda"] = Tipo("string"),
            ["note"] = Tipo("string"),
        },
        ["required"] = new JsonArray("alimenti", "domanda", "note"),
        ["additionalProperties"] = false,
    };

    private static JsonObject SchemaFotoGemini() => new()
    {
        ["type"] = "OBJECT",
        ["properties"] = new JsonObject
        {
            ["alimenti"] = new JsonObject
            {
                ["type"] = "ARRAY",
                ["items"] = new JsonObject
                {
                    ["type"] = "OBJECT",
                    ["properties"] = new JsonObject
                    {
                        ["nome"] = Tipo("STRING"),
                        ["grammi"] = Tipo("NUMBER"),
                        ["kcal100"] = Tipo("NUMBER"),
                        ["prot100"] = Tipo("NUMBER"),
                        ["grassi100"] = Tipo("NUMBER"),
                        ["carb100"] = Tipo("NUMBER"),
                        ["fibra100"] = Tipo("NUMBER"),
                        ["nova"] = Tipo("INTEGER"),
                        ["gruppo"] = Enumerazione("STRING", Gruppi),
                        ["certezza"] = Tipo("STRING"),
                    },
                    ["required"] = new JsonArray("nome", "grammi", "kcal100", "prot100", "grassi100", "carb100", "fibra100", "nova", "gruppo", "certezza"),
                },
            },
            ["domanda"] = Tipo("STRING"),
            ["note"] = Tipo("STRING"),
        },
        ["required"] = new JsonArray("alimenti", "domanda", "note"),
    };

    private static JsonObject SchemaEsercizioClaude() => new()
    {
        ["type"] = "object",
        ["properties"] = new JsonObject
        {
            ["gruppo"] = Enumerazione("string", GruppiEsercizio),
            ["serie"] = Tipo("string"),
            ["repMin"] = Tipo("integer"),
            ["repMax"] = Tipo("integer"),
            ["incrementoKg"] = Tipo("number"),
            ["nota"] = Tipo("string"),
        },
        ["required"] = new JsonArray("gruppo", "serie", "repMin", "repMax", "incrementoKg", "nota"),
        ["additionalProperties"] = false,
    };

    private static JsonObject SchemaEsercizioGemini() => new()
    {
        ["type"] = "OBJECT",
        ["properties"] = new JsonObject
        {
            ["gruppo"] = Enumerazione("STRING", GruppiEsercizio),
            ["serie"] = Tipo("STRING"),
            ["repMin"] = Tipo("INTEGER"),
            ["repMax"] = Tipo("INTEGER"),
            ["incrementoKg"] = Tipo("NUMBER"),
            ["nota"] = Tipo("STRING"),
        },
        ["required"] = new JsonArray("gruppo", "serie", "repMin", "repMax", "incrementoKg", "nota"),
    };

    private static JsonObject Tipo(string t) => new() { ["type"] = t };

    private static JsonObject Enumerazione(string t, IEnumerable<string> valori)
    {
        var lista = new JsonArray();
        foreach (var v in valori) lista.Add(v);
        return new JsonObject { ["type"] = t, ["enum"] = lista };
    }
}

/// <summary>Errore del servizio col suo corpo: è lì che scrivono "chiave non valida" o "credito esaurito".</summary>
public sealed class ErroreServizio(int codice, string corpo) : Exception($"HTTP {codice}")
{
    public int Codice { get; } = codice;
    public string Corpo { get; } = corpo;
}
