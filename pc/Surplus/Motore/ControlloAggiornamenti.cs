using System.Text.Json.Nodes;

namespace Surplus.Motore;

/// <summary>
/// Gli aggiornamenti del programma, da soli e a richiesta.
///
/// Da soli: mezzo minuto dopo l'avvio e poi ogni sei ore si guarda sul NAS; se c'è
/// una versione nuova si scarica e si installa quando Surplus si chiude.
///
/// A richiesta: il pulsante "Cerca aggiornamenti" delle impostazioni guarda subito, e
/// "Installa adesso" chiude Surplus, installa e lo riapre, senza dover chiudere e
/// riaprire a mano due volte. Le due strade passano da qui una alla volta: due
/// scaricamenti insieme nella stessa cartella si rovinerebbero a vicenda.
/// </summary>
public sealed class ControlloAggiornamenti
{
    private readonly Aggiornatore aggiornatore;
    private readonly Func<string?> indirizzo;
    private readonly string cartella;
    private readonly SemaphoreSlim unoAllaVolta = new(1, 1);

    /// <param name="attivo">
    /// Falso in sviluppo e nelle prove: lì un aggiornamento sostituirebbe la cartella di
    /// lavoro con il programma del NAS.
    /// </param>
    public ControlloAggiornamenti(Aggiornatore aggiornatore, Func<string?> indirizzo, string cartella, bool attivo)
    {
        this.aggiornatore = aggiornatore;
        this.indirizzo = indirizzo;
        this.cartella = cartella;
        Attivo = attivo;
    }

    public bool Attivo { get; }

    /// <summary>Vero dopo "Installa adesso": a chiusura avvenuta Surplus si riapre da solo.</summary>
    public bool Riapri { get; private set; }

    /// <summary>La finestra si chiude per lasciare il posto alla versione nuova.</summary>
    public Action? ChiudiPerInstallare { get; set; }

    /// <summary>Il ciclo in sottofondo: mezzo minuto dopo l'avvio, poi ogni sei ore.</summary>
    public async Task InSottofondoAsync(CancellationToken ferma)
    {
        if (!Attivo) return;
        try
        {
            var attesa = Aggiornatore.PrimoControllo;
            while (!ferma.IsCancellationRequested)
            {
                await Task.Delay(attesa, ferma).ConfigureAwait(false);
                attesa = Aggiornatore.OgniTanto;
                await GuardaAsync(daSolo: true, ferma).ConfigureAwait(false);
            }
        }
        catch (OperationCanceledException)
        {
            // Surplus si è chiuso: niente da dire.
        }
        catch (Exception e)
        {
            Log.Errore("controllo degli aggiornamenti non riuscito", e);
        }
    }

    /// <summary>Il pulsante "Cerca aggiornamenti": guarda adesso e, se c'è, prepara la versione nuova.</summary>
    public async Task<JsonObject> CercaAdessoAsync(CancellationToken annulla = default)
    {
        if (!Attivo)
        {
            return Risposta("Questa copia di Surplus non si aggiorna da sola (è una versione di prova).");
        }
        if (indirizzo() == null)
        {
            return Risposta("Per cercare gli aggiornamenti il computer deve essere abbinato al NAS.");
        }
        var esito = await GuardaAsync(daSolo: false, annulla).ConfigureAwait(false);
        return Risposta(esito);
    }

    /// <summary>"Installa adesso": se la versione nuova è pronta chiude Surplus, che si installa e si riapre.</summary>
    public JsonObject InstallaAdesso()
    {
        if (!Attivo || aggiornatore.Installabile == null)
        {
            return new JsonObject { ["errore"] = "La versione nuova non è ancora pronta: prima cerca gli aggiornamenti.", ["codice"] = "non_pronta" };
        }
        Riapri = true;
        Log.Info($"installo subito la versione {aggiornatore.Installabile.Nome}, su richiesta");
        ChiudiPerInstallare?.Invoke();
        return new JsonObject { ["ok"] = true, ["stato"] = aggiornatore.PerLaPagina() };
    }

    /// <summary>Quello che la pagina mostra nelle impostazioni.</summary>
    public JsonObject PerLaPagina()
    {
        var o = aggiornatore.PerLaPagina();
        o["attivo"] = Attivo;
        return o;
    }

    private JsonObject Risposta(string testo)
    {
        var stato = PerLaPagina();
        return new JsonObject { ["ok"] = true, ["testo"] = testo, ["stato"] = stato };
    }

    /// <summary>Una frase per chi ha premuto il pulsante, in parole semplici.</summary>
    private async Task<string> GuardaAsync(bool daSolo, CancellationToken annulla)
    {
        var dove = indirizzo();
        if (dove == null) return "Per cercare gli aggiornamenti il computer deve essere abbinato al NAS.";
        await unoAllaVolta.WaitAsync(annulla).ConfigureAwait(false);
        try
        {
            var v = await aggiornatore.ControllaAsync(dove, annulla).ConfigureAwait(false);
            if (v == null)
            {
                return aggiornatore.UltimoControlloRiuscito
                    ? "Hai già l'ultima versione."
                    : "Il NAS non risponde: riprova tra poco.";
            }
            if (daSolo) Log.Info($"sul NAS c'è la versione {v.Nome}: la scarico");
            else Log.Info($"sul NAS c'è la versione {v.Nome}: la scarico (cercata a mano)");
            var pronta = await aggiornatore.PreparaAsync(dove, cartella, DateTimeOffset.Now, annulla).ConfigureAwait(false);
            return pronta != null
                ? $"La versione {pronta.Nome} è pronta."
                : aggiornatore.Errore ?? "Non sono riuscito a scaricare la versione nuova. Riprova tra poco.";
        }
        finally
        {
            unoAllaVolta.Release();
        }
    }
}
