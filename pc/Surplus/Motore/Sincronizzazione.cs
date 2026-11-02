using System.Text.Json.Nodes;
using Surplus.Nucleo;
using Surplus.Rete;

namespace Surplus.Motore;

/// <summary>
/// Tiene il diario del computer e quello del NAS d'accordo fra loro.
///
/// Il file sul disco resta la verità per questo computer: si lavora anche con il
/// NAS spento, e quando torna si manda tutto. Il NAS non comanda, unisce (la
/// regola sta in server/app/unione.py): nessuna delle due parti sovrascrive
/// l'altra, e una voce cancellata resta cancellata.
///
/// Quando: appena il programma si apre, qualche secondo dopo ogni modifica, ogni
/// dieci minuti e un'ultima volta alla chiusura. Se il NAS non risponde non
/// succede niente di grave: si riprova la volta dopo.
/// </summary>
public sealed class Sincronizzazione : IDisposable
{
    /// <summary>Dopo una modifica si aspetta un attimo: chi scrive un pasto ne scrive spesso tre di fila.</summary>
    public static readonly TimeSpan Attesa = TimeSpan.FromSeconds(4);

    public static readonly TimeSpan OgniTanto = TimeSpan.FromMinutes(10);

    /// <summary>Alla chiusura non si fa aspettare l'utente: o va, o si rimanda.</summary>
    public static readonly TimeSpan AttesaChiusura = TimeSpan.FromSeconds(8);

    private readonly Motore motore;
    private readonly ClientNas client = new();
    private readonly SemaphoreSlim unaAllaVolta = new(1, 1);
    private readonly System.Threading.Timer attesa;
    private readonly System.Threading.Timer periodico;
    private bool chiuso;

    public Sincronizzazione(Motore motore)
    {
        this.motore = motore;
        attesa = new System.Threading.Timer(_ => Sfondo("modifica"), null, Timeout.InfiniteTimeSpan, Timeout.InfiniteTimeSpan);
        periodico = new System.Threading.Timer(_ => Sfondo("ogni tanto"), null, Timeout.InfiniteTimeSpan, Timeout.InfiniteTimeSpan);
    }

    public bool Abbinato => Indirizzo != null && !string.IsNullOrEmpty(motore.Impostazioni.NasToken);

    public string? Indirizzo => ClientNas.Normalizza(motore.Impostazioni.NasIndirizzo);

    public DateTimeOffset? UltimaRiuscita { get; private set; }
    public string? UltimoErrore { get; private set; }
    public int Revisione { get; private set; }
    public bool InCorso { get; private set; }

    /// <summary>All'avvio: una sincronizzazione subito, poi una ogni dieci minuti.</summary>
    public void Avvia()
    {
        if (!Abbinato) return;
        periodico.Change(OgniTanto, OgniTanto);
        Sfondo("avvio");
    }

    /// <summary>La pagina ha salvato: fra qualche secondo si manda al NAS.</summary>
    public void DopoUnSalvataggio()
    {
        if (!Abbinato || chiuso) return;
        attesa.Change(Attesa, Timeout.InfiniteTimeSpan);
    }

    /// <summary>Alla chiusura: l'ultima sincronizzazione, con poco tempo a disposizione.</summary>
    public void Chiudendo()
    {
        chiuso = true;
        attesa.Change(Timeout.InfiniteTimeSpan, Timeout.InfiniteTimeSpan);
        periodico.Change(Timeout.InfiniteTimeSpan, Timeout.InfiniteTimeSpan);
        if (!Abbinato) return;
        try
        {
            using var tempo = new CancellationTokenSource(AttesaChiusura);
            AdessoAsync("chiusura", tempo.Token).GetAwaiter().GetResult();
        }
        catch (Exception e) when (e is OperationCanceledException or AggregateException)
        {
            Log.Avviso("alla chiusura il NAS non ha risposto in tempo: si manda al prossimo avvio");
        }
    }

    private void Sfondo(string motivo)
    {
        _ = Task.Run(async () =>
        {
            try
            {
                await AdessoAsync(motivo).ConfigureAwait(false);
            }
            catch (Exception e)
            {
                Log.Errore("sincronizzazione (" + motivo + ") non riuscita", e);
            }
        });
    }

    /// <summary>
    /// Manda il diario di adesso e tiene quello che torna. Il diario unito passa
    /// dagli stessi controlli di un salvataggio della pagina: se togliesse più di
    /// metà delle voci, non si scrive e si dice perché.
    /// </summary>
    public async Task<JsonObject> AdessoAsync(string motivo, CancellationToken annulla = default)
    {
        var indirizzo = Indirizzo;
        var token = motore.Impostazioni.NasToken;
        if (indirizzo == null || string.IsNullOrEmpty(token))
        {
            return new JsonObject { ["ok"] = true, ["saltata"] = true, ["stato"] = PerLaPagina() };
        }
        if (!await unaAllaVolta.WaitAsync(TimeSpan.FromSeconds(20), annulla).ConfigureAwait(false))
        {
            return new JsonObject { ["ok"] = true, ["saltata"] = true, ["stato"] = PerLaPagina() };
        }
        InCorso = true;
        try
        {
            var mio = motore.Dati();
            var esito = await client.SincronizzaAsync(indirizzo, token, mio, annulla).ConfigureAwait(false);
            if (!esito.Ok)
            {
                UltimoErrore = esito.Errore;
                if (esito.Codice == "non_abbinato")
                {
                    // Il NAS non ci conosce più: si butta il token, così l'interfaccia
                    // chiede un codice nuovo invece di riprovare a vuoto per sempre.
                    motore.AggiornaImpostazioni(i => i.NasToken = "");
                }
                Log.Avviso("sincronizzazione (" + motivo + ") non riuscita: " + esito.Errore);
                return new JsonObject { ["errore"] = esito.Errore, ["codice"] = esito.Codice, ["stato"] = PerLaPagina() };
            }
            var corpo = esito.Corpo!;
            Revisione = Json.Intero(corpo["revisione"]) ?? Revisione;
            var unito = corpo["dati"] as JsonObject;
            var cambiato = false;
            if (unito != null && !Uguali(unito, mio))
            {
                var salvato = motore.Salva((JsonObject)unito.DeepClone());
                if (salvato["errore"] != null)
                {
                    UltimoErrore = Json.Testo(salvato["errore"]);
                    Log.Errore("diario unito non salvato: " + UltimoErrore);
                    return new JsonObject { ["errore"] = UltimoErrore, ["codice"] = "non_valido", ["stato"] = PerLaPagina() };
                }
                cambiato = true;
                // Il salvataggio appena fatto riaccenderebbe l'attesa: è roba che
                // viene dal NAS, non c'è niente da rimandargli.
                attesa.Change(Timeout.InfiniteTimeSpan, Timeout.InfiniteTimeSpan);
            }
            UltimaRiuscita = DateTimeOffset.Now;
            UltimoErrore = null;
            var conflitti = Json.Lista(corpo["conflitti"]).Count;
            Log.Info($"sincronizzato col NAS ({motivo}): revisione {Revisione}"
                     + (cambiato ? ", diario aggiornato da qui" : ", niente da cambiare")
                     + (conflitti > 0 ? $", {conflitti} voci cambiate da entrambe le parti" : ""));
            return new JsonObject
            {
                ["ok"] = true,
                ["cambiato"] = cambiato,
                ["conflitti"] = conflitti,
                ["stato"] = PerLaPagina(),
            };
        }
        finally
        {
            InCorso = false;
            unaAllaVolta.Release();
        }
    }

    /// <summary>Abbina questo computer con un codice preso dal NAS.</summary>
    public async Task<JsonObject> AbbinaAsync(string? indirizzoScritto, string? codice)
    {
        var indirizzo = ClientNas.Normalizza(indirizzoScritto);
        if (indirizzo == null)
        {
            return new JsonObject { ["errore"] = "Scrivi l'indirizzo del NAS, per esempio 192.168.1.20:8600.", ["codice"] = "indirizzo" };
        }
        var pulito = (codice ?? "").Trim();
        if (pulito.Length == 0)
        {
            return new JsonObject { ["errore"] = "Scrivi il codice di 8 cifre che ti dà il NAS.", ["codice"] = "codice" };
        }
        var nome = "Computer di " + Environment.UserName;
        var esito = await client.AbbinaAsync(indirizzo, pulito, nome).ConfigureAwait(false);
        if (!esito.Ok)
        {
            UltimoErrore = esito.Errore;
            return new JsonObject { ["errore"] = esito.Errore, ["codice"] = esito.Codice, ["stato"] = PerLaPagina() };
        }
        var token = Json.Testo(esito.Corpo?["token"]);
        if (string.IsNullOrEmpty(token))
        {
            return new JsonObject { ["errore"] = "Il NAS non ha dato il permesso. Riprova con un codice nuovo.", ["codice"] = "server" };
        }
        if (!motore.AggiornaImpostazioni(i => { i.NasIndirizzo = indirizzo; i.NasToken = token; }))
        {
            return new JsonObject { ["errore"] = "L'abbinamento non si è salvato sul disco. Riprova.", ["codice"] = "disco" };
        }
        chiuso = false;
        Log.Info("computer abbinato al NAS (" + indirizzo + ")");
        periodico.Change(OgniTanto, OgniTanto);
        var prima = await AdessoAsync("appena abbinato").ConfigureAwait(false);
        if (prima["errore"] != null) return prima;
        return new JsonObject { ["ok"] = true, ["cambiato"] = prima["cambiato"]?.DeepClone(), ["stato"] = PerLaPagina() };
    }

    /// <summary>Stacca questo computer: resta l'indirizzo, va via il permesso.</summary>
    public JsonObject Stacca()
    {
        motore.AggiornaImpostazioni(i => i.NasToken = "");
        periodico.Change(Timeout.InfiniteTimeSpan, Timeout.InfiniteTimeSpan);
        UltimaRiuscita = null;
        UltimoErrore = null;
        Revisione = 0;
        Log.Info("computer staccato dal NAS");
        return new JsonObject { ["ok"] = true, ["stato"] = PerLaPagina() };
    }

    /// <summary>Quello che la pagina può sapere: mai il token.</summary>
    public JsonObject PerLaPagina() => new()
    {
        ["abbinato"] = Abbinato,
        ["indirizzo"] = motore.Impostazioni.NasIndirizzo,
        ["ultima"] = UltimaRiuscita?.ToUnixTimeMilliseconds(),
        ["errore"] = UltimoErrore,
        ["revisione"] = Revisione,
        ["inCorso"] = InCorso,
        ["versioneDati"] = motore.VersioneDati,
        ["aggiornamento"] = Aggiornamento?.Invoke(),
    };

    /// <summary>Lo stato dell'aggiornamento automatico, se c'è chi lo sa.</summary>
    public Func<JsonObject?>? Aggiornamento { get; set; }

    /// <summary>
    /// Uguali nel contenuto, non nell'ordine in cui sono scritti: il diario che
    /// torna dal NAS dice le stesse cose ma può averle in un altro ordine, e
    /// salvarlo di nuovo per quello sarebbe lavoro (e allarmi) per niente.
    /// </summary>
    private static bool Uguali(JsonObject a, JsonObject b) => Json.Canonico(a) == Json.Canonico(b);

    public void Dispose()
    {
        attesa.Dispose();
        periodico.Dispose();
        unaAllaVolta.Dispose();
    }
}
