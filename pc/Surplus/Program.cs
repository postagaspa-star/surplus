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

        using var istanza = new Istanza(opzioni.SuffissoIstanza);
        if (!istanza.Prima)
        {
            istanza.ChiediApertura();
            return 0;
        }
        istanza.Ascolta();

        var percorsi = new Percorsi(opzioni.CartellaDati);
        Log.Inizia(percorsi.Log);
        Log.Info($"Surplus {Versione.Nome} avviato");
        Installazione.AssicuraUi(AppContext.BaseDirectory);

        using var motore = new Motore.Motore(percorsi);
        if (!opzioni.NonInstallare && Installazione.ÈPacchetto)
        {
            var eseguibile = Environment.ProcessPath!;
            motore.ApplicaAvvioLogin = attivo => Installazione.ImpostaAvvio(eseguibile, attivo);
            Installazione.ImpostaAvvio(eseguibile, motore.Impostazioni.AvvioAlLogin);
        }

        Application.Run(new ContestoSurplus(motore, percorsi, istanza, opzioni.EsciDopoSecondi));
        Log.Info("Surplus chiuso");
        return 0;
    }
}
