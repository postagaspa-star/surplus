using System;
using System.IO;
using System.IO.Compression;
using System.Text;
using Surplus.Motore;
using Surplus.Nucleo;
using Surplus.Rete;
using Xunit;

namespace Surplus.Tests;

/// <summary>
/// Prove dell'aggiornamento automatico. Qui si controlla la parte che decide
/// COSA si installa: un pacchetto che non è firmato con la chiave giusta, o che prova a
/// scrivere fuori dalla sua cartella, o che è più vecchio di quello installato,
/// non deve passare. È il pezzo in cui un errore farebbe installare al programma
/// codice di qualcun altro.
/// </summary>
public class AggiornamentoTest : IDisposable
{
    private readonly string cartella = Path.Combine(Path.GetTempPath(), "surplus-prove-agg-" + Guid.NewGuid().ToString("N")[..8]);

    public AggiornamentoTest() => Directory.CreateDirectory(cartella);

    public void Dispose()
    {
        try { Directory.Delete(cartella, recursive: true); } catch (IOException) { }
    }

    private string Zip(string nome, Action<ZipArchive> contenuto)
    {
        var file = Path.Combine(cartella, nome);
        using var flusso = new FileStream(file, FileMode.Create);
        using var archivio = new ZipArchive(flusso, ZipArchiveMode.Create);
        contenuto(archivio);
        return file;
    }

    private static void Voce(ZipArchive a, string percorso, string testo)
    {
        using var w = new StreamWriter(a.CreateEntry(percorso).Open(), Encoding.UTF8);
        w.Write(testo);
    }

    private static string Versione(int codice, string nome) => "{\"codice\":" + codice + ",\"nome\":\"" + nome + "\"}";

    // --- la firma ---------------------------------------------------------------------

    [Fact]
    public void La_firma_fatta_con_la_chiave_di_andrea_e_valida()
    {
        // Firma di riferimento fatta con la chiave privata (che sta solo sul suo
        // computer, in surplus-keys). Se la chiave pubblica scritta nel codice
        // fosse sbagliata o storpiata, questa prova cade.
        var file = Path.Combine(cartella, "firmato.txt");
        File.WriteAllBytes(file, Encoding.UTF8.GetBytes("prova della firma di Surplus"));
        const string firma = "s/XG1bVu/QsMN5f8GIsZljy4QlJHM5M2PSh2hkQFzz5oR5M1rKVzGGbSr3PVFREeHLa+R+KhBIaLKqCEzl8xqQ==";
        Assert.True(Aggiornatore.FirmaValida(file, firma));
    }

    [Fact]
    public void Senza_firma_o_con_la_firma_sbagliata_non_si_installa()
    {
        var file = Path.Combine(cartella, "firmato.txt");
        File.WriteAllBytes(file, Encoding.UTF8.GetBytes("prova della firma di Surplus"));
        Assert.False(Aggiornatore.FirmaValida(file, null));
        Assert.False(Aggiornatore.FirmaValida(file, ""));
        Assert.False(Aggiornatore.FirmaValida(file, "non e' nemmeno base64 !!"));
        Assert.False(Aggiornatore.FirmaValida(file, Convert.ToBase64String(new byte[64])));
    }

    [Fact]
    public void Un_file_cambiato_dopo_la_firma_non_passa()
    {
        var file = Path.Combine(cartella, "firmato.txt");
        File.WriteAllBytes(file, Encoding.UTF8.GetBytes("prova della firma di Surplus!"));
        const string firma = "s/XG1bVu/QsMN5f8GIsZljy4QlJHM5M2PSh2hkQFzz5oR5M1rKVzGGbSr3PVFREeHLa+R+KhBIaLKqCEzl8xqQ==";
        Assert.False(Aggiornatore.FirmaValida(file, firma));
    }

    // --- impronta e dimensione --------------------------------------------------------

    [Fact]
    public void Impronta_e_dimensione_devono_coincidere()
    {
        var file = Path.Combine(cartella, "pacchetto.bin");
        File.WriteAllBytes(file, new byte[] { 1, 2, 3, 4 });
        var vera = Convert.ToHexString(System.Security.Cryptography.SHA256.HashData(new byte[] { 1, 2, 3, 4 }));
        Assert.Null(Aggiornatore.Controlla(file, vera, 4));
        Assert.Equal("la dimensione non coincide", Aggiornatore.Controlla(file, vera, 5));
        Assert.Equal("l'impronta non coincide", Aggiornatore.Controlla(file, new string('a', 64), 4));
    }

    // --- scompattamento ---------------------------------------------------------------

    [Fact]
    public void Un_pacchetto_buono_si_scompatta_e_si_segna_pronto()
    {
        var zip = Zip("buono.zip", a =>
        {
            Voce(a, "Surplus/Surplus.exe", "finto eseguibile");
            Voce(a, "Surplus/versione.json", Versione(5, "1.5.0"));
            Voce(a, "Surplus/ui/app.js", "// pagina");
        });
        var pronta = Aggiornatore.Estrai(zip, cartella, codiceMio: 2);
        Assert.Equal(5, pronta.Codice);
        Assert.Equal("1.5.0", pronta.Nome);
        Assert.True(File.Exists(Path.Combine(pronta.Cartella, "Surplus.exe")));
        Assert.True(File.Exists(Path.Combine(pronta.Cartella, "ui", "app.js")));
        var vista = Aggiornatore.GiaPronta(cartella, codiceMio: 2, DateTimeOffset.Now);
        Assert.NotNull(vista);
        Assert.Equal(5, vista!.Codice);
    }

    [Fact]
    public void Un_file_fuori_dalla_cartella_surplus_fa_scartare_tutto()
    {
        var zip = Zip("furbo.zip", a =>
        {
            Voce(a, "Surplus/Surplus.exe", "finto");
            Voce(a, "Surplus/versione.json", Versione(5, "1.5.0"));
            Voce(a, "altrove/cattivo.dll", "no");
        });
        var e = Assert.Throws<PacchettoNonValido>(() => Aggiornatore.Estrai(zip, cartella, 2));
        Assert.Contains("fuori dalla cartella", e.Message);
        Assert.False(Directory.Exists(Path.Combine(cartella, Aggiornatore.CartellaNuova)));
    }

    [Fact]
    public void Un_percorso_che_esce_dalla_cartella_non_passa()
    {
        var zip = Zip("salto.zip", a =>
        {
            Voce(a, "Surplus/Surplus.exe", "finto");
            Voce(a, "Surplus/versione.json", Versione(5, "1.5.0"));
            Voce(a, "Surplus/../../fuori.txt", "no");
        });
        Assert.Throws<PacchettoNonValido>(() => Aggiornatore.Estrai(zip, cartella, 2));
    }

    [Fact]
    public void Una_versione_piu_vecchia_non_si_installa()
    {
        var zip = Zip("vecchio.zip", a =>
        {
            Voce(a, "Surplus/Surplus.exe", "finto");
            Voce(a, "Surplus/versione.json", Versione(1, "1.0.0"));
        });
        var e = Assert.Throws<PacchettoNonValido>(() => Aggiornatore.Estrai(zip, cartella, 2));
        Assert.Contains("non è più nuova", e.Message);
    }

    [Fact]
    public void Senza_il_numero_di_versione_il_pacchetto_non_vale()
    {
        var zip = Zip("senza.zip", a => Voce(a, "Surplus/Surplus.exe", "finto"));
        Assert.Throws<PacchettoNonValido>(() => Aggiornatore.Estrai(zip, cartella, 2));
    }

    [Fact]
    public void Dopo_un_errore_la_stessa_versione_si_riprova_domani()
    {
        var zip = Zip("buono.zip", a =>
        {
            Voce(a, "Surplus/Surplus.exe", "finto");
            Voce(a, "Surplus/versione.json", Versione(5, "1.5.0"));
        });
        Aggiornatore.Estrai(zip, cartella, 2);
        var adesso = DateTimeOffset.Now;
        Aggiornatore.SegnaFallita(cartella, 5, adesso);
        Assert.Null(Aggiornatore.GiaPronta(cartella, 2, adesso.AddHours(1)));
        Assert.NotNull(Aggiornatore.GiaPronta(cartella, 2, adesso.AddHours(25)));
        // Per non riscaricare, chi prepara la vede comunque.
        Assert.NotNull(Aggiornatore.GiaPronta(cartella, 2, adesso.AddHours(1), ancheSeFallita: true));
    }

    [Fact]
    public void La_pulizia_tiene_la_versione_nuova_e_butta_il_resto()
    {
        var zip = Zip("buono.zip", a =>
        {
            Voce(a, "Surplus/Surplus.exe", "finto");
            Voce(a, "Surplus/versione.json", Versione(5, "1.5.0"));
        });
        Aggiornatore.Estrai(zip, cartella, 2);
        Directory.CreateDirectory(Path.Combine(cartella, Aggiornatore.CartellaVecchia));
        Aggiornatore.Pulisci(cartella, codiceMio: 2);
        Assert.True(Directory.Exists(Path.Combine(cartella, Aggiornatore.CartellaNuova)));
        Assert.False(Directory.Exists(Path.Combine(cartella, Aggiornatore.CartellaVecchia)));

        // Dopo l'installazione (il nostro codice è ormai quello nuovo) si butta tutto.
        Aggiornatore.Pulisci(cartella, codiceMio: 5);
        Assert.False(Directory.Exists(Path.Combine(cartella, Aggiornatore.CartellaNuova)));
        Assert.False(File.Exists(Path.Combine(cartella, Aggiornatore.FilePronta)));
    }

    [Fact]
    public void Il_segno_dell_installazione_fatta_si_legge_una_volta_sola()
    {
        Aggiornatore.ScriviVersione(Path.Combine(cartella, Aggiornatore.FileFatto), 5, "1.5.0");
        Assert.Equal("1.5.0", Aggiornatore.LeggiFatto(cartella));
        Assert.Null(Aggiornatore.LeggiFatto(cartella));
    }

    // --- l'indirizzo del NAS ----------------------------------------------------------

    [Theory]
    [InlineData("192.168.1.20:8600", "http://192.168.1.20:8600")]
    [InlineData("  192.168.1.20:8600/  ", "http://192.168.1.20:8600")]
    [InlineData("http://nas.casa:8600", "http://nas.casa:8600")]
    [InlineData("https://surplus.example.ts.net", "https://surplus.example.ts.net")]
    [InlineData("surplus.example.ts.net", "https://surplus.example.ts.net")]
    public void L_indirizzo_scritto_a_mano_diventa_un_indirizzo_completo(string scritto, string atteso)
    {
        Assert.Equal(atteso, ClientNas.Normalizza(scritto));
    }

    [Theory]
    [InlineData("")]
    [InlineData("   ")]
    [InlineData(null)]
    [InlineData("ftp://nas")]
    [InlineData("http://")]
    public void Un_indirizzo_che_non_va_bene_torna_null(string? scritto)
    {
        Assert.Null(ClientNas.Normalizza(scritto));
    }

    [Fact]
    public void La_copia_di_un_aggiornamento_che_va_storto_rimette_i_file_vecchi()
    {
        var sorgente = Path.Combine(cartella, "nuova");
        var destinazione = Path.Combine(cartella, "programma");
        var daParte = Path.Combine(cartella, "vecchia");
        Directory.CreateDirectory(sorgente);
        Directory.CreateDirectory(destinazione);
        File.WriteAllText(Path.Combine(sorgente, "Surplus.exe"), "nuovo");
        File.WriteAllText(Path.Combine(destinazione, "Surplus.exe"), "vecchio");

        // Un file della destinazione tenuto aperto: la copia non può riuscire.
        var bloccato = Path.Combine(destinazione, "bloccato.dll");
        File.WriteAllText(Path.Combine(sorgente, "bloccato.dll"), "nuovo");
        File.WriteAllText(bloccato, "vecchio");
        using (var tieni = new FileStream(bloccato, FileMode.Open, FileAccess.Read, FileShare.None))
        {
            Assert.False(Sistema.Sostituzione.Copia(sorgente, destinazione, daParte));
        }
        Assert.Equal("vecchio", File.ReadAllText(Path.Combine(destinazione, "Surplus.exe")));
        Assert.Equal("vecchio", File.ReadAllText(bloccato));
    }

    [Fact]
    public void La_copia_che_riesce_mette_i_file_nuovi_e_salva_i_vecchi()
    {
        var sorgente = Path.Combine(cartella, "nuova");
        var destinazione = Path.Combine(cartella, "programma");
        var daParte = Path.Combine(cartella, "vecchia");
        Directory.CreateDirectory(Path.Combine(sorgente, "ui"));
        Directory.CreateDirectory(destinazione);
        File.WriteAllText(Path.Combine(sorgente, "Surplus.exe"), "nuovo");
        File.WriteAllText(Path.Combine(sorgente, "ui", "app.js"), "// nuovo");
        File.WriteAllText(Path.Combine(destinazione, "Surplus.exe"), "vecchio");
        File.WriteAllText(Path.Combine(destinazione, "mio.txt"), "resta");

        Assert.True(Sistema.Sostituzione.Copia(sorgente, destinazione, daParte));
        Assert.Equal("nuovo", File.ReadAllText(Path.Combine(destinazione, "Surplus.exe")));
        Assert.Equal("// nuovo", File.ReadAllText(Path.Combine(destinazione, "ui", "app.js")));
        Assert.Equal("vecchio", File.ReadAllText(Path.Combine(daParte, "Surplus.exe")));
        // Un file che la versione nuova non ha resta dov'è.
        Assert.Equal("resta", File.ReadAllText(Path.Combine(destinazione, "mio.txt")));
    }
}
