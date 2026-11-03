using System.IO.Compression;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json.Nodes;
using Surplus.Nucleo;
using Surplus.Rete;

namespace Surplus.Motore;

/// <summary>Una versione più nuova di questa, annunciata dal NAS.</summary>
public sealed record VersioneNuova(int Codice, string Nome, string Sha256, long? Byte, bool Firmata);

/// <summary>Una versione scaricata, controllata e scompattata: pronta da installare.</summary>
public sealed record Pronta(int Codice, string Nome, string Cartella);

/// <summary>Un pacchetto che non si può installare: la frase finisce nelle impostazioni.</summary>
public sealed class PacchettoNonValido(string messaggio) : Exception(messaggio);

/// <summary>
/// L'aggiornamento si fa da solo, come in Lectio.
///
/// Mezzo minuto dopo l'avvio, e poi ogni sei ore, si chiede al NAS
/// <c>GET /api/versione</c>. Se il numero del pacchetto per il computer è più alto
/// di questo, in sottofondo si scarica <c>/scarica/surplus-pc.zip</c> nella cartella
/// <c>aggiornamento</c> dei dati e si controllano tre cose: la dimensione,
/// l'impronta SHA-256 e la FIRMA (<c>/scarica/surplus-pc.zip.sig</c>, ECDSA P-256).
/// La firma la può fare solo chi ha la chiave privata, che sta sul computer di
/// chi pubblica, e non è né nel repository né sul NAS: così nemmeno chi entrasse nella
/// rete di casa può far installare a Surplus un programma suo.
///
/// Dentro lo zip c'è <c>versione.json</c> col numero vero: se non è più alto di
/// questo il pacchetto si scarta (niente ritorni a versioni vecchie). Si installa
/// quando Surplus si chiude (vedi <see cref="Sistema.Sostituzione"/>).
/// </summary>
public sealed class Aggiornatore
{
    public const string FileZip = "surplus-pc.zip";
    public const string FileFirma = "surplus-pc.zip.sig";
    public const string FileVersione = "versione.json";

    public const string CartellaNuova = "nuova";
    public const string CartellaVecchia = "vecchia";
    public const string FilePronta = "pronta.json";
    public const string FileFallita = "fallita.json";
    public const string FileFatto = "fatto.json";

    /// <summary>Quanto si aspetta prima di riprovare un'installazione che non è riuscita.</summary>
    public static readonly TimeSpan AttesaDopoErrore = TimeSpan.FromHours(24);

    public static readonly TimeSpan PrimoControllo = TimeSpan.FromSeconds(30);
    public static readonly TimeSpan OgniTanto = TimeSpan.FromHours(6);

    /// <summary>Lo zip scompattato non può superare questo (oggi sono circa 160 MB).</summary>
    public const long MassimoByteScompattati = 1024L * 1024 * 1024;

    /// <summary>La chiave pubblica che verifica le firme: la metà che si può pubblicare.</summary>
    public const string ChiaveX = "1FCDzK1XzPAzndpu8qt+6bukoC2YQjuxDtMLD3ObiL8=";
    public const string ChiaveY = "9dU5FFDFd4lgwiIeASpw+CuvqNiGC49Ui9ei2smu+EQ=";

    private readonly ClientNas client = new();
    private readonly int codiceMio;

    public Aggiornatore(int? codiceMio = null)
    {
        this.codiceMio = codiceMio ?? Versione.Codice;
    }

    public VersioneNuova? Disponibile { get; private set; }

    /// <summary>L'ultima frase da far vedere nelle impostazioni (null se va tutto bene).</summary>
    public string? Errore { get; private set; }

    public Pronta? Installabile { get; private set; }

    /// <summary>Falso se all'ultima domanda il NAS non ha risposto (per non dire "hai già l'ultima").</summary>
    public bool UltimoControlloRiuscito { get; private set; }

    public static ECDsa ChiavePubblica()
    {
        var ecdsa = ECDsa.Create();
        ecdsa.ImportParameters(new ECParameters
        {
            Curve = ECCurve.NamedCurves.nistP256,
            Q = new ECPoint { X = Convert.FromBase64String(ChiaveX), Y = Convert.FromBase64String(ChiaveY) },
        });
        return ecdsa;
    }

    /// <summary>La firma (base64 di r||s) è dello zip, fatta con la chiave di Surplus?</summary>
    public static bool FirmaValida(string file, string? firmaBase64)
    {
        if (string.IsNullOrWhiteSpace(firmaBase64)) return false;
        try
        {
            var firma = Convert.FromBase64String(firmaBase64.Trim());
            using var ecdsa = ChiavePubblica();
            using var flusso = File.OpenRead(file);
            return ecdsa.VerifyData(flusso, firma, HashAlgorithmName.SHA256);
        }
        catch (Exception e) when (e is FormatException or CryptographicException or IOException)
        {
            return false;
        }
    }

    /// <summary>null se il file è quello annunciato, altrimenti cosa non torna (in parole semplici).</summary>
    public static string? Controlla(string file, string sha256Atteso, long? byteAttesi)
    {
        var info = new FileInfo(file);
        if (byteAttesi is long attesi && info.Length != attesi) return "la dimensione non coincide";
        using var flusso = File.OpenRead(file);
        return string.Equals(Convert.ToHexString(SHA256.HashData(flusso)), sha256Atteso, StringComparison.OrdinalIgnoreCase)
            ? null : "l'impronta non coincide";
    }

    /// <summary>Chiede al NAS se c'è una versione più nuova.</summary>
    public async Task<VersioneNuova?> ControllaAsync(string indirizzo, CancellationToken annulla = default)
    {
        var esito = await client.VersioneAsync(indirizzo, annulla).ConfigureAwait(false);
        UltimoControlloRiuscito = esito.Ok;
        if (!esito.Ok) return null;
        var pc = esito.Corpo?["pc"] as JsonObject;
        var codice = Json.Intero(pc?["codice"]);
        var impronta = Json.Testo(pc?["sha256"]);
        if (codice == null || codice <= codiceMio || impronta == null || impronta.Length != 64)
        {
            Disponibile = null;
            return null;
        }
        Disponibile = new VersioneNuova(codice.Value, Json.Testo(pc?["nome"]) ?? codice.Value.ToString(),
            impronta, Json.Lungo(pc?["byte"]), Json.Booleano(pc?["firma"]) == true);
        return Disponibile;
    }

    /// <summary>
    /// Scarica, controlla e scompatta la versione nuova. Se è già pronta non
    /// riscarica niente. Lo zip si cancella sempre: resta la cartella scompattata.
    /// </summary>
    public async Task<Pronta?> PreparaAsync(string indirizzo, string cartella, DateTimeOffset adesso,
                                            CancellationToken annulla = default)
    {
        var v = Disponibile;
        if (v == null) return null;
        if (GiaPronta(cartella, codiceMio, adesso, ancheSeFallita: true) is { } gia && gia.Codice >= v.Codice)
        {
            Installabile = gia;
            return gia;
        }
        if (!v.Firmata)
        {
            Errore = "Sul NAS la versione nuova non ha la firma di Surplus, quindi non la scarico. Va rifatto il pacchetto.";
            Log.Avviso("versione nuova senza firma sul NAS: non la scarico");
            return null;
        }
        Directory.CreateDirectory(cartella);
        var zip = Path.Combine(cartella, FileZip + ".scaricamento");
        try
        {
            var radice = ClientNas.Normalizza(indirizzo);
            if (radice == null) return null;
            if (!await client.ScaricaAsync(radice + "/scarica/" + FileZip, zip, annulla).ConfigureAwait(false))
            {
                Errore = "Non sono riuscito a scaricare la versione nuova dal NAS. Riprovo più tardi.";
                return null;
            }
            if (Controlla(zip, v.Sha256, v.Byte) is string cosa)
            {
                Errore = "Il file scaricato non è quello giusto (" + cosa + "): l'ho scartato.";
                Log.Avviso("zip della versione nuova scartato: " + cosa);
                return null;
            }
            var fileFirma = zip + ".sig";
            string? firma = null;
            if (await client.ScaricaAsync(radice + "/scarica/" + FileFirma, fileFirma, annulla).ConfigureAwait(false))
            {
                firma = File.ReadAllText(fileFirma, Encoding.ASCII);
            }
            try { File.Delete(fileFirma); } catch (Exception e) when (e is IOException or UnauthorizedAccessException) { }
            if (!FirmaValida(zip, firma))
            {
                Errore = "La versione nuova non ha una firma valida di Surplus, quindi l'ho scartata.";
                Log.Avviso("zip della versione nuova scartato: firma " + (firma == null ? "mancante" : "non valida"));
                return null;
            }
            var pronta = Estrai(zip, cartella, codiceMio);
            Installabile = pronta;
            Errore = null;
            Log.Info($"versione {pronta.Nome} pronta: si installa quando Surplus si chiude");
            return pronta;
        }
        catch (PacchettoNonValido e)
        {
            Errore = "La versione nuova non si può installare (" + e.Message + "), l'ho scartata.";
            Log.Avviso("versione nuova scartata: " + e.Message);
            return null;
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or InvalidDataException)
        {
            Errore = "Non sono riuscito a preparare la versione nuova. Riprovo più tardi.";
            Log.Errore("versione nuova non scompattata", e);
            return null;
        }
        finally
        {
            try { File.Delete(zip); } catch (Exception e) when (e is IOException or UnauthorizedAccessException) { }
        }
    }

    /// <summary>
    /// Scompatta lo zip (già controllato) in <c>nuova</c>: solo file dentro la
    /// cartella <c>Surplus\</c> dello zip, con <c>Surplus.exe</c> e un
    /// <c>versione.json</c> più nuovo di questo programma. Poi scrive <c>pronta.json</c>.
    /// </summary>
    public static Pronta Estrai(string zip, string cartella, int codiceMio)
    {
        var nuova = Path.Combine(cartella, CartellaNuova);
        Directory.CreateDirectory(cartella);
        File.Delete(Path.Combine(cartella, FilePronta));
        if (Directory.Exists(nuova)) Directory.Delete(nuova, recursive: true);
        Directory.CreateDirectory(nuova);
        var radice = Path.GetFullPath(nuova).TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
        long totale = 0;
        try
        {
            using (var archivio = ZipFile.OpenRead(zip))
            {
                foreach (var voce in archivio.Entries)
                {
                    // Compress-Archive di PowerShell 5.1 scrive i percorsi con la barra rovesciata.
                    var nome = voce.FullName.Replace('\\', '/');
                    if (!nome.StartsWith("Surplus/", StringComparison.Ordinal))
                        throw new PacchettoNonValido("c'è un file fuori dalla cartella Surplus");
                    var destinazione = Path.GetFullPath(Path.Combine(nuova, nome));
                    if (!destinazione.StartsWith(radice, StringComparison.OrdinalIgnoreCase))
                        throw new PacchettoNonValido("c'è un file che esce dalla sua cartella");
                    totale += voce.Length;
                    if (totale > MassimoByteScompattati) throw new PacchettoNonValido("è troppo grande");
                    if (nome.EndsWith('/'))
                    {
                        Directory.CreateDirectory(destinazione);
                        continue;
                    }
                    Directory.CreateDirectory(Path.GetDirectoryName(destinazione)!);
                    voce.ExtractToFile(destinazione, overwrite: false);
                }
            }
            var programma = Path.Combine(nuova, "Surplus");
            if (!File.Exists(Path.Combine(programma, "Surplus.exe"))) throw new PacchettoNonValido("manca Surplus.exe");
            var v = LeggiVersione(Path.Combine(programma, FileVersione));
            if (v == null) throw new PacchettoNonValido("manca il numero di versione");
            if (v.Value.Codice <= codiceMio) throw new PacchettoNonValido("non è più nuova di questa");
            ScriviVersione(Path.Combine(cartella, FilePronta), v.Value.Codice, v.Value.Nome);
            return new Pronta(v.Value.Codice, v.Value.Nome, programma);
        }
        catch
        {
            try { Directory.Delete(nuova, recursive: true); } catch (Exception e) when (e is IOException or UnauthorizedAccessException) { }
            throw;
        }
    }

    /// <summary>
    /// La versione già scompattata, se c'è, è più nuova di questa e non è appena
    /// fallita (in quel caso si riprova dopo <see cref="AttesaDopoErrore"/>).
    /// </summary>
    public static Pronta? GiaPronta(string cartella, int codiceMio, DateTimeOffset adesso, bool ancheSeFallita = false)
    {
        var p = LeggiVersione(Path.Combine(cartella, FilePronta));
        if (p == null || p.Value.Codice <= codiceMio) return null;
        var programma = Path.Combine(cartella, CartellaNuova, "Surplus");
        if (!File.Exists(Path.Combine(programma, "Surplus.exe"))) return null;
        if (LeggiVersione(Path.Combine(programma, FileVersione))?.Codice != p.Value.Codice) return null;
        var f = Json.Oggetto(Archivio.LeggiTesto(Path.Combine(cartella, FileFallita)));
        if (!ancheSeFallita && f != null && Json.Intero(f["codice"]) == p.Value.Codice
            && Json.Lungo(f["quando"]) is long quando
            && adesso - DateTimeOffset.FromUnixTimeMilliseconds(quando) < AttesaDopoErrore)
        {
            return null;
        }
        return new Pronta(p.Value.Codice, p.Value.Nome, programma);
    }

    public static void SegnaFallita(string cartella, int codice, DateTimeOffset adesso)
    {
        try
        {
            Directory.CreateDirectory(cartella);
            Archivio.ScriviTesto(Path.Combine(cartella, FileFallita), Json.Scrivi(new JsonObject
            {
                ["codice"] = codice,
                ["quando"] = adesso.ToUnixTimeMilliseconds(),
            }));
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
            Log.Errore("segno dell'aggiornamento non riuscito non scritto", e);
        }
    }

    /// <summary>Dopo un aggiornamento si toglie quello che resta. Una versione pronta e più nuova resta.</summary>
    public static void Pulisci(string cartella, int codiceMio)
    {
        if (!Directory.Exists(cartella)) return;
        var p = LeggiVersione(Path.Combine(cartella, FilePronta));
        var togli = new List<string> { Path.Combine(cartella, CartellaVecchia) };
        if (p == null || p.Value.Codice <= codiceMio)
        {
            togli.Add(Path.Combine(cartella, CartellaNuova));
            togli.Add(Path.Combine(cartella, FilePronta));
            togli.Add(Path.Combine(cartella, FileFallita));
        }
        togli.AddRange(Directory.GetFiles(cartella, FileZip + "*"));
        foreach (var x in togli)
        {
            try
            {
                if (Directory.Exists(x)) Directory.Delete(x, recursive: true);
                else if (File.Exists(x)) File.Delete(x);
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException)
            {
                Log.Avviso("dopo l'aggiornamento non ho tolto " + Path.GetFileName(x) + ": " + e.Message);
            }
        }
    }

    /// <summary>Il segno di un'installazione riuscita: il nome della versione, letto (e tolto) al primo avvio.</summary>
    public static string? LeggiFatto(string cartella)
    {
        var file = Path.Combine(cartella, FileFatto);
        var v = LeggiVersione(file);
        if (v == null) return null;
        try { File.Delete(file); } catch (Exception e) when (e is IOException or UnauthorizedAccessException) { }
        return v.Value.Nome;
    }

    public static void ScriviVersione(string file, int codice, string nome) =>
        Archivio.ScriviTesto(file, Json.Scrivi(new JsonObject { ["codice"] = codice, ["nome"] = nome }));

    public static (int Codice, string Nome)? LeggiVersione(string file)
    {
        var o = Json.Oggetto(Archivio.LeggiTesto(file));
        var codice = Json.Intero(o?["codice"]);
        var nome = Json.Testo(o?["nome"]);
        if (codice == null || codice <= 0 || string.IsNullOrWhiteSpace(nome)) return null;
        return (codice.Value, nome!);
    }

    /// <summary>Quello che la pagina mostra nelle impostazioni.</summary>
    public JsonObject PerLaPagina() => new()
    {
        ["versione"] = Versione.Nome,
        ["nuova"] = Installabile?.Nome ?? Disponibile?.Nome,
        ["pronta"] = Installabile != null,
        ["errore"] = Errore,
    };
}
