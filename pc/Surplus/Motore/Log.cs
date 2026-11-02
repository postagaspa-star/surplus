using System.Globalization;
using System.Text;

namespace Surplus.Motore;

/// <summary>
/// Diario degli errori del programma (non quello del cibo): serve quando qualcosa
/// non va e bisogna capire cosa, senza chiedere all'utente di indovinare.
/// Tiene una sola copia vecchia e non cresce oltre il mezzo mega.
/// </summary>
public static class Log
{
    private const long MassimoByte = 512 * 1024;
    private static readonly object Serratura = new();
    private static string? file;

    public static void Inizia(string percorso)
    {
        lock (Serratura)
        {
            file = percorso;
        }
    }

    public static void Info(string messaggio) => Scrivi("INFO", messaggio, null);

    /// <summary>Qualcosa non è andato ma il programma continua: il NAS spento, un aggiornamento rimandato.</summary>
    public static void Avviso(string messaggio) => Scrivi("AVVISO", messaggio, null);

    public static void Errore(string messaggio, Exception? e = null) => Scrivi("ERRORE", messaggio, e);

    private static void Scrivi(string livello, string messaggio, Exception? e)
    {
        lock (Serratura)
        {
            if (file == null) return;
            try
            {
                var riga = new StringBuilder()
                    .Append(DateTimeOffset.Now.ToString("yyyy-MM-dd HH:mm:ss", CultureInfo.InvariantCulture))
                    .Append("  ").Append(livello).Append("  ").Append(messaggio);
                if (e != null) riga.Append("  | ").Append(e.GetType().Name).Append(": ").Append(e.Message);
                riga.AppendLine();

                var info = new FileInfo(file);
                if (info.Exists && info.Length > MassimoByte)
                {
                    File.Copy(file, file + ".1", overwrite: true);
                    File.Delete(file);
                }
                File.AppendAllText(file, riga.ToString(), Encoding.UTF8);
            }
            catch (Exception errore) when (errore is IOException or UnauthorizedAccessException)
            {
                // Se non si riesce nemmeno a scrivere il log, il programma deve comunque andare avanti.
            }
        }
    }
}
