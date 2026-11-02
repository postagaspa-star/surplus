using System.ComponentModel;
using System.Diagnostics;
using System.Globalization;
using Surplus.Motore;

namespace Surplus.Sistema;

/// <summary>
/// L'installazione della versione nuova. Windows non lascia sostituire un
/// programma che sta girando, quindi il Surplus vecchio lancia il Surplus NUOVO
/// dalla cartella dove l'ha preparato
/// (<c>%LOCALAPPDATA%\Surplus\aggiornamento\nuova\Surplus</c>) ed esce. Quello
/// aspetta che il vecchio sia chiuso, copia i suoi file sopra il programma
/// mettendo da parte i vecchi (che tornano a posto se qualcosa va storto) e, se
/// gli è stato chiesto, riapre Surplus. Senza finestre e senza chiedere niente.
/// </summary>
public static class Sostituzione
{
    public const string Argomento = "--applica-aggiornamento";

    public static readonly TimeSpan AttesaChiusura = TimeSpan.FromSeconds(60);

    /// <summary>Un file appena chiuso può restare bloccato un attimo (l'antivirus che lo guarda).</summary>
    private const int Tentativi = 20;
    private static readonly TimeSpan PausaTentativo = TimeSpan.FromMilliseconds(250);

    /// <summary>Dal Surplus vecchio: lancia quello nuovo perché si installi appena questo è chiuso.</summary>
    public static bool Avvia(Pronta pronta, string cartellaProgramma, string cartellaDati, bool riapri)
    {
        var psi = new ProcessStartInfo(Path.Combine(pronta.Cartella, "Surplus.exe"))
        {
            UseShellExecute = false,
            WorkingDirectory = pronta.Cartella,
        };
        psi.ArgumentList.Add(Argomento);
        psi.ArgumentList.Add("--programma");
        psi.ArgumentList.Add(Path.GetFullPath(cartellaProgramma));
        psi.ArgumentList.Add("--attendi");
        psi.ArgumentList.Add(Environment.ProcessId.ToString(CultureInfo.InvariantCulture));
        psi.ArgumentList.Add("--dati");
        psi.ArgumentList.Add(Path.GetFullPath(cartellaDati));
        if (riapri) psi.ArgumentList.Add("--riapri");
        try
        {
            Process.Start(psi)?.Dispose();
            Log.Info($"installo la versione {pronta.Nome} appena Surplus è chiuso");
            return true;
        }
        catch (Exception e) when (e is Win32Exception or InvalidOperationException)
        {
            Log.Errore("installazione della versione nuova non partita", e);
            Aggiornatore.SegnaFallita(new Percorsi(cartellaDati).Aggiornamento, pronta.Codice, DateTimeOffset.Now);
            return false;
        }
    }

    /// <summary>
    /// Nel Surplus nuovo, lanciato con <see cref="Argomento"/>: aspetta, copia,
    /// riapre. Copia solo da dentro <c>aggiornamento\nuova</c> verso una cartella
    /// che contiene già <c>Surplus.exe</c>: due controlli che tolgono di mezzo
    /// l'idea di usare questo pezzo per scrivere altrove.
    /// </summary>
    public static int Esegui(string? cartellaProgramma, int? attendi, string cartellaDati, bool riapri)
    {
        var percorsi = new Percorsi(cartellaDati);
        Log.Inizia(percorsi.Log);
        var sorgente = Path.GetFullPath(AppContext.BaseDirectory).TrimEnd(Path.DirectorySeparatorChar);
        var destinazione = cartellaProgramma == null ? null : Path.GetFullPath(cartellaProgramma).TrimEnd(Path.DirectorySeparatorChar);
        var nuova = Path.GetFullPath(Path.Combine(percorsi.Aggiornamento, Aggiornatore.CartellaNuova)).TrimEnd(Path.DirectorySeparatorChar);
        if (destinazione == null || !Dentro(sorgente, nuova) || Dentro(destinazione, percorsi.Aggiornamento)
            || !File.Exists(Path.Combine(destinazione, "Surplus.exe")) || !File.Exists(Path.Combine(sorgente, "Surplus.exe")))
        {
            Log.Avviso("installazione rifiutata: cartelle non valide (" + sorgente + " -> " + destinazione + ")");
            return 2;
        }
        if (!AttendiChiusura(attendi, AttesaChiusura))
        {
            Log.Avviso("installazione rimandata: il Surplus di prima non si è chiuso");
            Aggiornatore.SegnaFallita(percorsi.Aggiornamento, Versione.Codice, DateTimeOffset.Now);
            return 3;
        }
        var ok = Copia(sorgente, destinazione, Path.Combine(percorsi.Aggiornamento, Aggiornatore.CartellaVecchia));
        if (ok)
        {
            Log.Info($"versione {Versione.Nome} installata in {destinazione}");
            try
            {
                Aggiornatore.ScriviVersione(Path.Combine(percorsi.Aggiornamento, Aggiornatore.FileFatto), Versione.Codice, Versione.Nome);
                File.Delete(Path.Combine(percorsi.Aggiornamento, Aggiornatore.FilePronta));
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException)
            {
                Log.Errore("segni dell'aggiornamento non scritti", e);
            }
        }
        else
        {
            Aggiornatore.SegnaFallita(percorsi.Aggiornamento, Versione.Codice, DateTimeOffset.Now);
        }
        if (riapri) Riapri(destinazione, cartellaDati);
        return ok ? 0 : 1;
    }

    /// <summary>
    /// Copia tutti i file di <paramref name="sorgente"/> in <paramref name="destinazione"/>.
    /// Ogni file che c'era già va in <paramref name="daParte"/>; se una copia non
    /// riesce si torna com'era: i file nuovi via, i vecchi al loro posto.
    /// </summary>
    public static bool Copia(string sorgente, string destinazione, string daParte)
    {
        var relativi = Directory.GetFiles(sorgente, "*", SearchOption.AllDirectories)
            .Select(f => Path.GetRelativePath(sorgente, f)).ToList();
        var fatti = new List<(string Relativo, bool CeraPrima)>();
        try
        {
            if (Directory.Exists(daParte)) Directory.Delete(daParte, recursive: true);
            foreach (var r in relativi)
            {
                var file = Path.Combine(destinazione, r);
                Directory.CreateDirectory(Path.GetDirectoryName(file)!);
                var ceraPrima = File.Exists(file);
                if (ceraPrima)
                {
                    var vecchio = Path.Combine(daParte, r);
                    Directory.CreateDirectory(Path.GetDirectoryName(vecchio)!);
                    Riprova(() => File.Move(file, vecchio));
                }
                fatti.Add((r, ceraPrima));
                Riprova(() => File.Copy(Path.Combine(sorgente, r), file, overwrite: false));
            }
            return true;
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
            Log.Errore("copia della versione nuova non riuscita: torno com'era", e);
            for (int i = fatti.Count - 1; i >= 0; i--)
            {
                var (r, ceraPrima) = fatti[i];
                var file = Path.Combine(destinazione, r);
                try
                {
                    if (File.Exists(file)) Riprova(() => File.Delete(file));
                    if (ceraPrima) Riprova(() => File.Move(Path.Combine(daParte, r), file));
                }
                catch (Exception e2) when (e2 is IOException or UnauthorizedAccessException)
                {
                    Log.Errore("non ho rimesso a posto " + r, e2);
                }
            }
            return false;
        }
    }

    private static void Riprova(Action azione)
    {
        for (int i = 1; ; i++)
        {
            try
            {
                azione();
                return;
            }
            catch (Exception e) when ((e is IOException or UnauthorizedAccessException) && i < Tentativi)
            {
                Thread.Sleep(PausaTentativo);
            }
        }
    }

    private static bool AttendiChiusura(int? pid, TimeSpan attesa)
    {
        if (pid is not int id) return true;
        try
        {
            using var p = Process.GetProcessById(id);
            return p.WaitForExit(attesa);
        }
        catch (Exception e) when (e is ArgumentException or InvalidOperationException)
        {
            return true; // già chiuso
        }
    }

    private static void Riapri(string cartellaProgramma, string cartellaDati)
    {
        var psi = new ProcessStartInfo(Path.Combine(cartellaProgramma, "Surplus.exe"))
        {
            UseShellExecute = false,
            WorkingDirectory = cartellaProgramma,
        };
        if (!string.Equals(Path.GetFullPath(cartellaDati).TrimEnd('\\'), Path.GetFullPath(Percorsi.Predefinita).TrimEnd('\\'),
                StringComparison.OrdinalIgnoreCase))
        {
            psi.ArgumentList.Add("--dati");
            psi.ArgumentList.Add(cartellaDati);
        }
        try
        {
            Process.Start(psi)?.Dispose();
        }
        catch (Exception e) when (e is Win32Exception or InvalidOperationException)
        {
            Log.Errore("Surplus non riaperto dopo l'aggiornamento", e);
        }
    }

    private static bool Dentro(string percorso, string cartella)
    {
        var c = Path.GetFullPath(cartella).TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
        return (Path.GetFullPath(percorso).TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar)
            .StartsWith(c, StringComparison.OrdinalIgnoreCase);
    }
}
