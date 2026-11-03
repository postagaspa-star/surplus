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
/// Quando: appena il programma si apre, un secondo e mezzo dopo ogni modifica, ogni
/// dieci minuti e un'ultima volta alla chiusura. E in più, finché il programma è
/// aperto, resta in ascolto del NAS: appena il telefono scrive qualcosa il NAS lo
/// dice e si sincronizza subito (vedi AscoltaAsync). Se il NAS non risponde non
/// succede niente di grave: si riprova la volta dopo.
/// </summary>
public sealed class Sincronizzazione : IDisposable
{
    /// <summary>Dopo una modifica si aspetta un attimo: chi scrive un pasto ne scrive spesso tre di fila.</summary>
    public static readonly TimeSpan Attesa = TimeSpan.FromSeconds(1.5);

    /// <summary>Se il NAS non risponde si riprova ad ascoltarlo sempre più di rado, fino a questo.</summary>
    public static readonly TimeSpan PausaMassimaAscolto = TimeSpan.FromSeconds(15);

    public static readonly TimeSpan OgniTanto = TimeSpan.FromMinutes(10);

    /// <summary>Alla chiusura non si fa aspettare l'utente: o va, o si rimanda.</summary>
    public static readonly TimeSpan AttesaChiusura = TimeSpan.FromSeconds(8);

    private readonly Motore motore;
    private readonly ClientNas client = new();
    private readonly SemaphoreSlim unaAllaVolta = new(1, 1);
    private readonly System.Threading.Timer attesa;
    private readonly System.Threading.Timer periodico;
    private bool chiuso;
    private CancellationTokenSource? ascolto;

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
        IniziaAscolto();
    }

    private void IniziaAscolto()
    {
        FermaAscolto();
        var nuovo = new CancellationTokenSource();
        ascolto = nuovo;
        _ = Task.Run(() => AscoltaAsync(nuovo.Token));
    }

    private void FermaAscolto()
    {
        var vecchio = Interlocked.Exchange(ref ascolto, null);
        if (vecchio == null) return;
        vecchio.Cancel();
        vecchio.Dispose();
    }

    /// <summary>
    /// Il campanello: una domanda al NAS che resta aperta finché qualcuno non scrive
    /// (o per 20 secondi, poi si rifà). Quando il NAS ha un diario che questo computer
    /// non ha ancora, si sincronizza subito: così il pasto scritto sul telefono compare
    /// qui in un paio di secondi, e la pagina lo vede al suo controllo successivo.
    /// </summary>
    private async Task AscoltaAsync(CancellationToken ferma)
    {
        var pausa = TimeSpan.Zero;
        var muto = false;
        while (!ferma.IsCancellationRequested)
        {
            try
            {
                var indirizzo = Indirizzo;
                var token = motore.Impostazioni.NasToken;
                if (indirizzo == null || string.IsNullOrEmpty(token)) return;
                var esito = await client.NovitaAsync(indirizzo, token, motore.Impostazioni.NasBase, annulla: ferma).ConfigureAwait(false);
                if (!esito.Ok && esito.Codice == "indirizzo")
                {
                    // Un NAS col server di prima, senza campanello: si chiede "c'è niente?"
                    // ogni quindici secondi con una sincronizzazione, come si poteva fare prima.
                    await Task.Delay(TimeSpan.FromSeconds(15), ferma).ConfigureAwait(false);
                    await AdessoAsync("controllo", ferma).ConfigureAwait(false);
                    continue;
                }
                if (!esito.Ok)
                {
                    if (esito.Codice == "non_abbinato")
                    {
                        // La sincronizzazione butta il token e lo dice alla pagina.
                        await AdessoAsync("controllo", ferma).ConfigureAwait(false);
                        return;
                    }
                    if (!muto) Log.Avviso("il NAS non risponde: lo riascolto fra poco (" + esito.Errore + ")");
                    muto = true;
                    pausa = pausa == TimeSpan.Zero
                        ? TimeSpan.FromSeconds(5)
                        : TimeSpan.FromTicks(Math.Min(pausa.Ticks * 2, PausaMassimaAscolto.Ticks));
                    await Task.Delay(pausa, ferma).ConfigureAwait(false);
                    continue;
                }
                // Il NAS è appena tornato: quello che hai scritto nel frattempo parte
                // subito, senza aspettare la prossima modifica.
                var eraGiu = muto;
                if (muto) Log.Info("il NAS risponde di nuovo");
                muto = false;
                pausa = TimeSpan.Zero;
                // La base si rilegge adesso: se intanto questo computer ha sincronizzato
                // da sé, la revisione nuova è la sua e non c'è niente da prendere.
                if (eraGiu || (Json.Lungo(esito.Corpo?["revisione"]) is long r && r != motore.Impostazioni.NasBase))
                {
                    await AdessoAsync(eraGiu ? "NAS tornato" : "dal NAS", ferma).ConfigureAwait(false);
                }
                await Task.Delay(TimeSpan.FromMilliseconds(500), ferma).ConfigureAwait(false);
            }
            catch (OperationCanceledException) when (ferma.IsCancellationRequested)
            {
                return;
            }
            catch (Exception e)
            {
                Log.Errore("ascolto del NAS interrotto, riprendo fra poco", e);
                try
                {
                    await Task.Delay(TimeSpan.FromSeconds(10), ferma).ConfigureAwait(false);
                }
                catch (OperationCanceledException)
                {
                    return;
                }
            }
        }
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
        FermaAscolto();
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
            var (mio, versioneMia) = motore.DatiConVersione();
            var esito = await client.SincronizzaAsync(indirizzo, token, mio, motore.Impostazioni.NasBase, annulla).ConfigureAwait(false);
            if (!esito.Ok)
            {
                UltimoErrore = esito.Errore;
                if (esito.Codice == "non_abbinato")
                {
                    // Il NAS non ci conosce più: si butta il token, così l'interfaccia
                    // chiede un codice nuovo invece di riprovare a vuoto per sempre.
                    motore.AggiornaImpostazioni(i => { i.NasToken = ""; i.NasBase = -1; });
                }
                Log.Avviso("sincronizzazione (" + motivo + ") non riuscita: " + esito.Errore);
                return new JsonObject { ["errore"] = esito.Errore, ["codice"] = esito.Codice, ["stato"] = PerLaPagina() };
            }
            var corpo = esito.Corpo!;
            var revisioneUnita = Json.Lungo(corpo["revisione"]);
            Revisione = Json.Intero(corpo["revisione"]) ?? Revisione;
            var unito = corpo["dati"] as JsonObject;
            var cambiato = false;
            if (unito != null && !Uguali(unito, mio))
            {
                var salvato = motore.Salva((JsonObject)unito.DeepClone(), versioneMia);
                if (Json.Testo(salvato["codice"]) == Motore.Superato)
                {
                    // Hai scritto qualcosa mentre il NAS rispondeva: il diario unito non lo
                    // contiene. Non si tiene, la base resta quella di prima e fra poco si
                    // rimanda tutto, modifica compresa.
                    Log.Info($"sincronizzazione ({motivo}): diario cambiato nel frattempo, si riprova");
                    DopoUnSalvataggio();
                    return new JsonObject { ["ok"] = true, ["saltata"] = true, ["stato"] = PerLaPagina() };
                }
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
            // Il diario unito è sul disco (o era già uguale al nostro): da qui vale come base.
            if (revisioneUnita is long r) motore.AggiornaImpostazioni(i => i.NasBase = r);
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
        // Appena abbinato non c'è ancora una base: la prima volta si somma e basta.
        if (!motore.AggiornaImpostazioni(i => { i.NasIndirizzo = indirizzo; i.NasToken = token; i.NasBase = -1; }))
        {
            return new JsonObject { ["errore"] = "L'abbinamento non si è salvato sul disco. Riprova.", ["codice"] = "disco" };
        }
        chiuso = false;
        Log.Info("computer abbinato al NAS (" + indirizzo + ")");
        periodico.Change(OgniTanto, OgniTanto);
        var prima = await AdessoAsync("appena abbinato").ConfigureAwait(false);
        IniziaAscolto();
        if (prima["errore"] != null) return prima;
        return new JsonObject { ["ok"] = true, ["cambiato"] = prima["cambiato"]?.DeepClone(), ["stato"] = PerLaPagina() };
    }

    /// <summary>Stacca questo computer: resta l'indirizzo, va via il permesso.</summary>
    public JsonObject Stacca()
    {
        motore.AggiornaImpostazioni(i => { i.NasToken = ""; i.NasBase = -1; });
        FermaAscolto();
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
        FermaAscolto();
        attesa.Dispose();
        periodico.Dispose();
        unaAllaVolta.Dispose();
    }
}
