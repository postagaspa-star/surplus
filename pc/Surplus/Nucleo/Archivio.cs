using System.Text;

namespace Surplus.Nucleo;

/// <summary>
/// Scrittura e lettura dei file del programma.
///
/// La regola è la stessa dell'app Android dopo il guaio della 2.5.1: si scrive in
/// un file a parte, si forza sul disco e solo allora prende il posto del vecchio.
/// Senza la forzatura, un computer che si spegne di colpo subito dopo un
/// salvataggio lascia il diario vuoto.
/// </summary>
public static class Archivio
{
    public static void ScriviTesto(string file, string testo)
    {
        var cartella = Path.GetDirectoryName(file);
        if (!string.IsNullOrEmpty(cartella)) Directory.CreateDirectory(cartella);
        var temporaneo = file + ".tmp";
        using (var uscita = new FileStream(temporaneo, FileMode.Create, FileAccess.Write, FileShare.None))
        {
            var byte_ = new UTF8Encoding(false).GetBytes(testo);
            uscita.Write(byte_, 0, byte_.Length);
            uscita.Flush(flushToDisk: true);
        }
        // Move con sostituzione: l'operazione è unica, non esiste un istante senza file.
        File.Move(temporaneo, file, overwrite: true);
    }

    /// <summary>Il testo del file, oppure null se non c'è. Un file illeggibile torna stringa vuota: per chi legge è "rovinato".</summary>
    public static string? LeggiTesto(string file)
    {
        if (!File.Exists(file)) return null;
        try
        {
            return File.ReadAllText(file, Encoding.UTF8);
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
            return "";
        }
    }

    public static void Copia(string da, string a)
    {
        var cartella = Path.GetDirectoryName(a);
        if (!string.IsNullOrEmpty(cartella)) Directory.CreateDirectory(cartella);
        File.Copy(da, a, overwrite: true);
    }
}
