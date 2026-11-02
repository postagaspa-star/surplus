using System.Globalization;
using System.Windows.Forms;
using Surplus.Interfaccia;
using Surplus.Motore;
using Surplus.Sistema;

namespace Surplus;

/// <summary>Le opzioni della riga di comando: servono all'avvio automatico e alle prove.</summary>
public sealed class Opzioni
{
    /// <summary>Avviato da Windows all'accesso.</summary>
    public bool Avvio { get; private set; }

    /// <summary>Niente avvio automatico (prove e sviluppo).</summary>
    public bool NonInstallare { get; private set; }

    public string CartellaDati { get; private set; } = Percorsi.Predefinita;
    public bool CartellaDatiPersonale { get; private set; }
    public int? EsciDopoSecondi { get; private set; }

    /// <summary>Questo processo è la versione nuova, lanciata per installarsi sopra la vecchia.</summary>
    public bool ApplicaAggiornamento { get; private set; }

    public string? CartellaProgramma { get; private set; }
    public int? AttendiProcesso { get; private set; }
    public bool Riapri { get; private set; }

    /// <summary>
    /// Abbina al NAS senza aprire la finestra: <c>--abbina 192.168.1.20:8600 --codice 12345678</c>.
    /// Fa la stessa cosa del riquadro nelle impostazioni ed esce (0 fatto, 3 non riuscito,
    /// 4 c'è già un Surplus aperto). Serve a chi prepara il computer da remoto.
    /// </summary>
    public string? AbbinaIndirizzo { get; private set; }

    public string? AbbinaCodice { get; private set; }

    public static Opzioni Da(string[] args)
    {
        var o = new Opzioni();
        for (int i = 0; i < args.Length; i++)
        {
            string Prossimo() => i + 1 < args.Length ? args[++i] : throw new ArgumentException($"manca il valore di {args[i]}");
            switch (args[i])
            {
                case "--avvio": o.Avvio = true; break;
                case "--no-installa": o.NonInstallare = true; break;
                case "--dati":
                    o.CartellaDati = Path.GetFullPath(Prossimo());
                    o.CartellaDatiPersonale = true;
                    break;
                case "--esci-dopo": o.EsciDopoSecondi = int.Parse(Prossimo(), CultureInfo.InvariantCulture); break;
                case Sostituzione.Argomento: o.ApplicaAggiornamento = true; break;
                case "--programma": o.CartellaProgramma = Path.GetFullPath(Prossimo()); break;
                case "--attendi": o.AttendiProcesso = int.Parse(Prossimo(), CultureInfo.InvariantCulture); break;
                case "--riapri": o.Riapri = true; break;
                case "--abbina": o.AbbinaIndirizzo = Prossimo(); break;
                case "--codice": o.AbbinaCodice = Prossimo(); break;
            }
        }
        return o;
    }

    /// <summary>Con una cartella dati di prova serve un'istanza a parte; su quella vera resta una sola.</summary>
    public string SuffissoIstanza =>
        CartellaDatiPersonale && !string.Equals(Path.GetFullPath(CartellaDati).TrimEnd('\\'), Path.GetFullPath(Percorsi.Predefinita).TrimEnd('\\'), StringComparison.OrdinalIgnoreCase)
            ? "." + Convert.ToHexString(System.Security.Cryptography.SHA256.HashData(System.Text.Encoding.UTF8.GetBytes(Path.GetFullPath(CartellaDati).ToLowerInvariant())))[..12]
            : "";
}

internal static class Program
{
    [STAThread]
    private static int Main(string[] args)
    {
        Opzioni opzioni;
        try
        {
            opzioni = Opzioni.Da(args);
        }
        catch (Exception e) when (e is ArgumentException or FormatException)
        {
            MessageBox.Show(e.Message, "Surplus", MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 2;
        }

        ApplicationConfiguration.Initialize();
        Application.SetUnhandledExceptionMode(UnhandledExceptionMode.CatchException);
        Application.ThreadException += (_, e) => Log.Errore("eccezione non gestita nella finestra", e.Exception);
        AppDomain.CurrentDomain.UnhandledException += (_, e) => Log.Errore("eccezione non gestita", e.ExceptionObject as Exception);
        TaskScheduler.UnobservedTaskException += (_, e) =>
        {
            Log.Errore("eccezione in un compito", e.Exception);
            e.SetObserved();
        };

        if (opzioni.ApplicaAggiornamento)
        {
            // Questo processo è la versione nuova: installa e esce, senza finestre.
            // Non prende il posto unico: quello ce l'ha ancora il Surplus che si sta chiudendo.
            return Sostituzione.Esegui(opzioni.CartellaProgramma, opzioni.AttendiProcesso, opzioni.CartellaDati, opzioni.Riapri);
        }

        using var istanza = new Istanza(opzioni.SuffissoIstanza);
        if (!istanza.Prima)
        {
            // Abbinare mentre Surplus è aperto vorrebbe dire due programmi che
            // scrivono lo stesso diario: meglio dirlo e fermarsi.
            if (opzioni.AbbinaIndirizzo != null) return 4;
            istanza.ChiediApertura();
            return 0;
        }
        istanza.Ascolta();

        var percorsi = new Percorsi(opzioni.CartellaDati);
        Log.Inizia(percorsi.Log);
        Log.Info($"Surplus {Versione.Nome} avviato");
        Installazione.AssicuraUi(AppContext.BaseDirectory);

        if (Aggiornatore.LeggiFatto(percorsi.Aggiornamento) is string appena)
        {
            Log.Info("aggiornato alla versione " + appena);
        }
        Aggiornatore.Pulisci(percorsi.Aggiornamento, Versione.Codice);

        using var motore = new Motore.Motore(percorsi);
        if (!opzioni.NonInstallare && Installazione.ÈPacchetto)
        {
            var eseguibile = Environment.ProcessPath!;
            motore.ApplicaAvvioLogin = attivo => Installazione.ImpostaAvvio(eseguibile, attivo);
            Installazione.ImpostaAvvio(eseguibile, motore.Impostazioni.AvvioAlLogin);
        }

        using var sincronizzazione = new Sincronizzazione(motore);

        if (opzioni.AbbinaIndirizzo is string indirizzoNas)
        {
            // Senza finestra: abbina, manda subito il diario e esce. Il risultato
            // sta nel log (questo programma non ha una console dove scrivere).
            var esito = sincronizzazione.AbbinaAsync(indirizzoNas, opzioni.AbbinaCodice).GetAwaiter().GetResult();
            if (esito["errore"] != null)
            {
                Log.Errore("abbinamento dalla riga di comando non riuscito: " + esito["errore"]);
                return 3;
            }
            Log.Info("abbinamento dalla riga di comando riuscito");
            return 0;
        }

        motore.DopoSalvataggio = sincronizzazione.DopoUnSalvataggio;
        var aggiornatore = new Aggiornatore();
        sincronizzazione.Aggiornamento = aggiornatore.PerLaPagina;
        sincronizzazione.Avvia();

        // L'aggiornamento si cerca solo quando Surplus gira dal pacchetto: in
        // sviluppo sostituirebbe la cartella di lavoro con quella del NAS.
        using var ferma = new CancellationTokenSource();
        if (Installazione.ÈPacchetto && !opzioni.NonInstallare)
        {
            _ = CercaAggiornamentiAsync(aggiornatore, sincronizzazione, percorsi, ferma.Token);
        }

        Application.Run(new ContestoSurplus(motore, percorsi, istanza, opzioni.EsciDopoSecondi, sincronizzazione));
        ferma.Cancel();

        // L'ultima sincronizzazione a finestra chiusa: se il NAS non risponde si
        // rimanda, il diario sul disco è già salvato.
        sincronizzazione.Chiudendo();

        var pronta = aggiornatore.Installabile ?? Aggiornatore.GiaPronta(percorsi.Aggiornamento, Versione.Codice, DateTimeOffset.Now);
        if (pronta != null && Installazione.ÈPacchetto && !opzioni.NonInstallare)
        {
            Sostituzione.Avvia(pronta, AppContext.BaseDirectory, percorsi.Cartella, riapri: false);
        }

        Log.Info("Surplus chiuso");
        return 0;
    }

    /// <summary>
    /// Mezzo minuto dopo l'avvio, e poi ogni sei ore: c'è una versione nuova sul
    /// NAS? Se sì la scarica e la prepara in sottofondo; si installerà alla
    /// chiusura. Qualunque cosa vada storta resta nel log e si riprova dopo.
    /// </summary>
    private static async Task CercaAggiornamentiAsync(Aggiornatore aggiornatore, Sincronizzazione sincronizzazione,
                                                      Percorsi percorsi, CancellationToken ferma)
    {
        try
        {
            var attesa = Aggiornatore.PrimoControllo;
            while (!ferma.IsCancellationRequested)
            {
                await Task.Delay(attesa, ferma).ConfigureAwait(false);
                attesa = Aggiornatore.OgniTanto;
                var indirizzo = sincronizzazione.Indirizzo;
                if (indirizzo == null) continue;
                if (await aggiornatore.ControllaAsync(indirizzo, ferma).ConfigureAwait(false) is not VersioneNuova v) continue;
                Log.Info($"sul NAS c'è la versione {v.Nome}: la scarico");
                await aggiornatore.PreparaAsync(indirizzo, percorsi.Aggiornamento, DateTimeOffset.Now, ferma).ConfigureAwait(false);
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
}
