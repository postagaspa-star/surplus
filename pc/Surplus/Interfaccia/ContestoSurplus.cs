using System.Windows.Forms;
using Surplus.Motore;
using Surplus.Sistema;

namespace Surplus.Interfaccia;

/// <summary>
/// Tiene in piedi il programma finché la finestra è aperta. Un secondo avvio non
/// apre una seconda finestra — due finestre sullo stesso diario si
/// sovrascriverebbero a vicenda — ma riporta davanti questa.
/// </summary>
public sealed class ContestoSurplus : ApplicationContext
{
    private readonly Motore.Motore motore;
    private readonly Motore.Sincronizzazione? sincronizzazione;
    private readonly Percorsi percorsi;
    private readonly Istanza istanza;
    private readonly Motore.ControlloAggiornamenti? aggiornamenti;
    private FinestraSurplus? finestra;

    public ContestoSurplus(Motore.Motore motore, Percorsi percorsi, Istanza istanza, int? esciDopoSecondi,
                           Motore.Sincronizzazione? sincronizzazione = null,
                           Motore.ControlloAggiornamenti? aggiornamenti = null)
    {
        this.motore = motore;
        this.sincronizzazione = sincronizzazione;
        this.aggiornamenti = aggiornamenti;
        if (aggiornamenti != null) aggiornamenti.ChiudiPerInstallare = ChiudiPerInstallare;
        this.percorsi = percorsi;
        this.istanza = istanza;

        istanza.RichiestaApertura += PortaDavanti;
        Apri();
        istanza.SvuotaAperturaInSospeso();

        if (esciDopoSecondi is int secondi)
        {
            // Serve alle prove: il programma si chiude da solo invece di restare aperto.
            var timer = new System.Windows.Forms.Timer { Interval = Math.Max(1, secondi) * 1000 };
            timer.Tick += (_, _) => { timer.Stop(); timer.Dispose(); ExitThread(); };
            timer.Start();
        }
    }

    private void Apri()
    {
        var f = new FinestraSurplus(new Ponte(motore, sincronizzazione, aggiornamenti), Path.Combine(AppContext.BaseDirectory, "ui"), percorsi.Finestra);
        f.FormClosed += (_, _) =>
        {
            finestra = null;
            ExitThread();
        };
        finestra = f;
        f.Show();
    }

    /// <summary>
    /// "Installa adesso": si chiude la finestra come con la X (il diario si salva e si
    /// sincronizza), poi Program installa la versione nuova e la riapre.
    /// </summary>
    private void ChiudiPerInstallare()
    {
        var f = finestra;
        if (f == null || f.IsDisposed)
        {
            ExitThread();
            return;
        }
        try
        {
            // Un attimo di respiro: la pagina fa in tempo a ricevere la risposta e a dirlo.
            f.BeginInvoke(async () =>
            {
                await Task.Delay(600);
                if (!f.IsDisposed) f.Close();
            });
        }
        catch (InvalidOperationException)
        {
            // la finestra si sta già chiudendo
        }
    }

    private void PortaDavanti()
    {
        var f = finestra;
        if (f == null || f.IsDisposed) return;
        try
        {
            f.BeginInvoke(() =>
            {
                if (f.WindowState == FormWindowState.Minimized) f.WindowState = FormWindowState.Normal;
                f.Activate();
            });
        }
        catch (InvalidOperationException)
        {
            // la finestra si sta chiudendo
        }
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            istanza.RichiestaApertura -= PortaDavanti;
            finestra?.Dispose();
        }
        base.Dispose(disposing);
    }
}
