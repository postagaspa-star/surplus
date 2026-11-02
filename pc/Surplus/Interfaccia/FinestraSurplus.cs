using System.Drawing;
using System.Text;
using System.Windows.Forms;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;
using Surplus.Motore;
using Surplus.Sistema;

namespace Surplus.Interfaccia;

/// <summary>
/// La finestra di Surplus: un telefono appoggiato sulla scrivania.
///
/// Dentro c'è una WebView2 che mostra l'interfaccia da <c>https://surplus.locale/</c>
/// (cartella <c>ui</c>) e non può andare altrove. La cornice scura e lo schermo
/// stondato li disegna la pagina; qui si tiene solo la finestra della forma giusta,
/// con lo stesso rapporto di un telefono, e si lascia che la pagina si adatti.
/// </summary>
public sealed class FinestraSurplus : Form
{
    /// <summary>Schermo 412×915 (le misure di un telefono Android) più la cornice e un po' di scrivania.</summary>
    private const int LarghezzaPiena = 480;
    private const int AltezzaPiena = 1000;

    private readonly WebView2 vista;
    private readonly Ponte ponte;
    private readonly string cartellaUi;
    private readonly string cartellaDatiWebView;
    private CoreWebView2Environment? ambiente;

    public FinestraSurplus(Ponte ponte, string cartellaUi, string cartellaDatiWebView)
    {
        this.ponte = ponte;
        this.cartellaUi = cartellaUi;
        this.cartellaDatiWebView = cartellaDatiWebView;

        Text = "Surplus";
        AutoScaleMode = AutoScaleMode.Dpi;
        AutoScaleDimensions = new SizeF(96F, 96F);
        // Fondo della cornice: prima che la pagina sia pronta si vede questo, non un lampo bianco.
        BackColor = Color.FromArgb(0x1C, 0x1B, 0x1F);
        StartPosition = FormStartPosition.CenterScreen;
        MinimumSize = new Size(360, 520);
        ClientSize = DimensioneIniziale();

        vista = new WebView2 { Dock = DockStyle.Fill, DefaultBackgroundColor = BackColor };
        Controls.Add(vista);
        Load += async (_, _) => await InizializzaAsync();
        HandleCreated += (_, _) => Tema.BarraDelTitolo(Handle, true);

        ponte.ScegliImmagini = ScegliImmaginiAsync;
        ponte.SalvaBackup = SalvaBackupAsync;
        ponte.ApriBackup = ApriBackupAsync;
        FormClosed += (_, _) =>
        {
            ponte.ScegliImmagini = null;
            ponte.SalvaBackup = null;
            ponte.ApriBackup = null;
        };
    }

    /// <summary>
    /// Il telefono intero se ci sta, altrimenti più basso ma con lo stesso rapporto:
    /// su un portatile 1080p una finestra alta 1000 px non entrerebbe sotto la barra.
    /// </summary>
    internal static Size DimensioneIniziale(int altezzaDisponibile = 0)
    {
        if (altezzaDisponibile <= 0)
        {
            altezzaDisponibile = Screen.PrimaryScreen?.WorkingArea.Height ?? AltezzaPiena;
        }
        var altezza = Math.Min(AltezzaPiena, Math.Max(520, altezzaDisponibile - 80));
        var larghezza = (int)Math.Round(altezza * (double)LarghezzaPiena / AltezzaPiena);
        return new Size(larghezza, altezza);
    }

    private async Task InizializzaAsync()
    {
        try
        {
            ambiente = await CoreWebView2Environment.CreateAsync(null, cartellaDatiWebView);
            await vista.EnsureCoreWebView2Async(ambiente);
        }
        catch (WebView2RuntimeNotFoundException)
        {
            MostraMessaggio("Per aprire Surplus serve Microsoft Edge WebView2, che su Windows 11 c'è sempre.");
            Log.Errore("WebView2 non trovata");
            return;
        }
        catch (Exception e)
        {
            MostraMessaggio("La finestra di Surplus non si è aperta.");
            Log.Errore("WebView2 non avviata", e);
            return;
        }

        var core = vista.CoreWebView2;
        await SvuotaCacheSeCambiataAsync(core);
        core.Settings.AreDevToolsEnabled = false;
        core.Settings.AreDefaultContextMenusEnabled = true; // copia e incolla nei campi
        core.Settings.IsStatusBarEnabled = false;
        core.Settings.AreHostObjectsAllowed = false;
        core.Settings.IsWebMessageEnabled = false;
        core.Settings.IsGeneralAutofillEnabled = false;
        core.Settings.IsPasswordAutosaveEnabled = false;
        core.Settings.IsZoomControlEnabled = false; // lo zoom lo fa la pagina, adattando il telefono alla finestra

        core.AddWebResourceRequestedFilter(Ponte.Origine + "/*", CoreWebView2WebResourceContext.All);
        core.WebResourceRequested += SuRichiesta;
        core.NavigationStarting += SuNavigazione;
        core.FrameNavigationStarting += SuNavigazione;
        core.NewWindowRequested += (_, e) => e.Handled = true;
        core.Navigate(Ponte.Origine + "/index.html");
    }

    /// <summary>Dopo un aggiornamento la WebView servirebbe i file vecchi dalla cache: se l'interfaccia è cambiata, la si svuota.</summary>
    private async Task SvuotaCacheSeCambiataAsync(CoreWebView2 core)
    {
        var fileImpronta = Path.Combine(cartellaDatiWebView, "impronta-ui.txt");
        var impronta = ImprontaUi(cartellaUi);
        string? precedente = null;
        try
        {
            if (File.Exists(fileImpronta)) precedente = File.ReadAllText(fileImpronta);
        }
        catch (IOException)
        {
        }
        if (precedente == impronta) return;
        try
        {
            await core.Profile.ClearBrowsingDataAsync(
                CoreWebView2BrowsingDataKinds.DiskCache | CoreWebView2BrowsingDataKinds.CacheStorage | CoreWebView2BrowsingDataKinds.ServiceWorkers);
            Nucleo.Archivio.ScriviTesto(fileImpronta, impronta);
        }
        catch (Exception e)
        {
            Log.Errore("cache della finestra non svuotata", e);
        }
    }

    public static string ImprontaUi(string cartella)
    {
        var testo = new StringBuilder(Versione.Nome);
        if (Directory.Exists(cartella))
        {
            foreach (var f in Directory.EnumerateFiles(cartella, "*", SearchOption.AllDirectories).OrderBy(x => x, StringComparer.OrdinalIgnoreCase))
            {
                var info = new FileInfo(f);
                testo.Append('|').Append(Path.GetRelativePath(cartella, f)).Append(':').Append(info.Length).Append(':').Append(info.LastWriteTimeUtc.Ticks);
            }
        }
        return Convert.ToHexString(System.Security.Cryptography.SHA256.HashData(Encoding.UTF8.GetBytes(testo.ToString())));
    }

    private async void SuRichiesta(object? mittente, CoreWebView2WebResourceRequestedEventArgs e)
    {
        var rinvio = e.GetDeferral();
        try
        {
            var metodo = e.Request.Method;
            var uri = new Uri(e.Request.Uri);
            if (!Ponte.ÈApi(uri))
            {
                e.Response = FileInterfaccia(metodo, uri);
                return;
            }
            string? corpo = null;
            if (e.Request.Content != null)
            {
                using var lettore = new StreamReader(e.Request.Content, Encoding.UTF8);
                corpo = await lettore.ReadToEndAsync();
            }
            var risposta = await ponte.GestisciAsync(metodo, uri, corpo);
            e.Response = Risposta(risposta);
        }
        catch (Exception ex)
        {
            Log.Errore("richiesta dell'interfaccia", ex);
            e.Response = Risposta(new RispostaPonte(500, "{\"errore\":\"Qualcosa non ha funzionato nel programma. Riprova.\"}"));
        }
        finally
        {
            rinvio.Complete();
        }
    }

    private CoreWebView2WebResourceResponse FileInterfaccia(string metodo, Uri uri)
    {
        var file = metodo is "GET" or "HEAD" ? Ponte.FileDellInterfaccia(cartellaUi, uri.AbsolutePath) : null;
        if (file == null || !File.Exists(file))
        {
            return ambiente!.CreateWebResourceResponse(new MemoryStream(Encoding.UTF8.GetBytes("non trovato")), 404, "Not Found",
                "Content-Type: text/plain; charset=utf-8\r\nCache-Control: no-store");
        }
        return ambiente!.CreateWebResourceResponse(new MemoryStream(File.ReadAllBytes(file)), 200, "OK",
            $"Content-Type: {Ponte.TipoContenuto(file)}\r\nCache-Control: no-cache\r\nX-Content-Type-Options: nosniff");
    }

    private CoreWebView2WebResourceResponse Risposta(RispostaPonte r) =>
        ambiente!.CreateWebResourceResponse(
            new MemoryStream(Encoding.UTF8.GetBytes(r.Json)), r.Stato, r.Stato < 400 ? "OK" : "Error",
            "Content-Type: application/json; charset=utf-8\r\nCache-Control: no-store");

    /// <summary>La pagina non può andare da nessun'altra parte: niente internet dentro la finestra.</summary>
    private static void SuNavigazione(object? mittente, CoreWebView2NavigationStartingEventArgs e)
    {
        if (!Ponte.ÈInterno(e.Uri)) e.Cancel = true;
    }

    // ---- dialoghi di Windows ----

    private Task<IReadOnlyList<string>> ScegliImmaginiAsync()
    {
        var esito = new TaskCompletionSource<IReadOnlyList<string>>();
        BeginInvoke(() =>
        {
            try
            {
                using var dialogo = new OpenFileDialog
                {
                    Title = "Scegli le foto del cibo",
                    Filter = "Immagini|*.jpg;*.jpeg;*.png;*.webp;*.bmp|Tutti i file|*.*",
                    Multiselect = true,
                };
                esito.SetResult(dialogo.ShowDialog(this) == DialogResult.OK ? dialogo.FileNames : Array.Empty<string>());
            }
            catch (Exception e)
            {
                Log.Errore("scelta immagini", e);
                esito.SetResult(Array.Empty<string>());
            }
        });
        return esito.Task;
    }

    private Task<string?> SalvaBackupAsync(string contenuto)
    {
        var esito = new TaskCompletionSource<string?>();
        BeginInvoke(() =>
        {
            try
            {
                using var dialogo = new SaveFileDialog
                {
                    Title = "Salva una copia del diario",
                    Filter = "Backup di Surplus|*.json",
                    FileName = $"surplus-{DateTime.Now:yyyy-MM-dd}.json",
                };
                if (dialogo.ShowDialog(this) != DialogResult.OK) { esito.SetResult(null); return; }
                Nucleo.Archivio.ScriviTesto(dialogo.FileName, contenuto);
                esito.SetResult(dialogo.FileName);
            }
            catch (Exception e)
            {
                Log.Errore("backup non salvato", e);
                esito.SetResult(null);
            }
        });
        return esito.Task;
    }

    private Task<string?> ApriBackupAsync()
    {
        var esito = new TaskCompletionSource<string?>();
        BeginInvoke(() =>
        {
            try
            {
                using var dialogo = new OpenFileDialog
                {
                    Title = "Scegli il backup da ripristinare",
                    Filter = "Backup di Surplus|*.json|Tutti i file|*.*",
                };
                if (dialogo.ShowDialog(this) != DialogResult.OK) { esito.SetResult(null); return; }
                esito.SetResult(File.ReadAllText(dialogo.FileName, Encoding.UTF8));
            }
            catch (Exception e)
            {
                Log.Errore("backup non letto", e);
                esito.SetResult(null);
            }
        });
        return esito.Task;
    }

    private void MostraMessaggio(string testo)
    {
        Controls.Clear();
        Controls.Add(new Label
        {
            Text = testo,
            Dock = DockStyle.Fill,
            TextAlign = ContentAlignment.MiddleCenter,
            ForeColor = Color.White,
            Font = new Font("Segoe UI", 11F),
            Padding = new Padding(40),
        });
    }
}
