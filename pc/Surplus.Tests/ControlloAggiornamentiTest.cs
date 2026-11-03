using System;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using Surplus.Motore;
using Xunit;

namespace Surplus.Tests;

/// <summary>
/// Prove del pulsante "Cerca aggiornamenti": deve dire la cosa giusta in ogni caso
/// (NAS spento, già aggiornato, non abbinato) e "Installa adesso" non deve chiudere
/// Surplus se non c'è niente di pronto.
/// </summary>
public class ControlloAggiornamentiTest : IDisposable
{
    private readonly string cartella = Path.Combine(Path.GetTempPath(), "surplus-prove-cerca-" + Guid.NewGuid().ToString("N")[..8]);

    public ControlloAggiornamentiTest() => Directory.CreateDirectory(cartella);

    public void Dispose()
    {
        try { Directory.Delete(cartella, recursive: true); } catch (IOException) { }
    }

    /// <summary>Un NAS finto che risponde sempre la stessa cosa a /api/versione.</summary>
    private static (HttpListener Ascolto, string Indirizzo) NasFinto(string risposta)
    {
        var porta = PortaLibera();
        var indirizzo = $"http://127.0.0.1:{porta}/";
        var ascolto = new HttpListener();
        ascolto.Prefixes.Add(indirizzo);
        ascolto.Start();
        _ = Task.Run(async () =>
        {
            while (ascolto.IsListening)
            {
                HttpListenerContext c;
                try { c = await ascolto.GetContextAsync(); }
                catch (Exception) { return; }
                var corpo = Encoding.UTF8.GetBytes(risposta);
                c.Response.ContentType = "application/json";
                await c.Response.OutputStream.WriteAsync(corpo);
                c.Response.Close();
            }
        });
        return (ascolto, indirizzo.TrimEnd('/'));
    }

    private static int PortaLibera()
    {
        var l = new TcpListener(IPAddress.Loopback, 0);
        l.Start();
        var porta = ((IPEndPoint)l.LocalEndpoint).Port;
        l.Stop();
        return porta;
    }

    private static string Testo(System.Text.Json.Nodes.JsonObject o) => (string?)o["testo"] ?? "";

    [Fact]
    public async Task Senza_nas_abbinato_lo_dice()
    {
        var c = new ControlloAggiornamenti(new Aggiornatore(5), () => null, cartella, attivo: true);
        Assert.Contains("abbinato al NAS", Testo(await c.CercaAdessoAsync()));
    }

    [Fact]
    public async Task In_una_copia_di_prova_non_cerca_niente()
    {
        var c = new ControlloAggiornamenti(new Aggiornatore(5), () => "http://127.0.0.1:1", cartella, attivo: false);
        Assert.Contains("non si aggiorna da sola", Testo(await c.CercaAdessoAsync()));
    }

    [Fact]
    public async Task Col_nas_spento_non_dice_che_e_tutto_aggiornato()
    {
        var porta = PortaLibera();
        var c = new ControlloAggiornamenti(new Aggiornatore(5), () => $"http://127.0.0.1:{porta}", cartella, attivo: true);
        Assert.Equal("Il NAS non risponde: riprova tra poco.", Testo(await c.CercaAdessoAsync()));
    }

    [Fact]
    public async Task Con_la_stessa_versione_sul_nas_dice_che_e_l_ultima()
    {
        var (nas, indirizzo) = NasFinto("{\"pc\":{\"codice\":5,\"nome\":\"1.1.3\",\"sha256\":\"" + new string('a', 64) + "\",\"firma\":true}}");
        try
        {
            var c = new ControlloAggiornamenti(new Aggiornatore(5), () => indirizzo, cartella, attivo: true);
            var r = await c.CercaAdessoAsync();
            Assert.Equal("Hai già l'ultima versione.", Testo(r));
            Assert.Equal(false, (bool?)r["stato"]?["pronta"]);
        }
        finally
        {
            nas.Stop();
        }
    }

    [Fact]
    public void Installa_adesso_senza_niente_di_pronto_non_chiude_surplus()
    {
        var chiusa = false;
        var c = new ControlloAggiornamenti(new Aggiornatore(5), () => null, cartella, attivo: true)
        {
            ChiudiPerInstallare = () => chiusa = true,
        };
        var r = c.InstallaAdesso();
        Assert.NotNull(r["errore"]);
        Assert.False(chiusa);
        Assert.False(c.Riapri);
    }
}
