namespace Surplus.Sistema;

/// <summary>
/// Dove vivono i dati: una cartella per utente, fuori dal programma, così
/// scompattare un aggiornamento sopra la cartella vecchia non tocca il diario.
/// </summary>
public sealed class Percorsi
{
    public Percorsi(string cartella)
    {
        Cartella = Path.GetFullPath(cartella);
        Directory.CreateDirectory(Cartella);
    }

    public static string Predefinita =>
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Surplus");

    public string Cartella { get; }

    /// <summary>Il diario, nello stesso formato dell'app Android.</summary>
    public string Dati => Path.Combine(Cartella, "data-local.json");

    /// <summary>Copia dell'ultimo diario buono, presa a ogni avvio.</summary>
    public string DatiCopia => Path.Combine(Cartella, "data-local.prev.json");

    public string Impostazioni => Path.Combine(Cartella, "impostazioni.json");

    public string Log => Path.Combine(Cartella, "surplus.log");

    /// <summary>Cartella di lavoro della finestra (cache della pagina): si può cancellare senza danni.</summary>
    public string Finestra => Path.Combine(Cartella, "finestra");

    /// <summary>Dove si prepara la versione nuova prima di installarla: si può cancellare senza danni.</summary>
    public string Aggiornamento => Path.Combine(Cartella, "aggiornamento");

    public string DiarioRovinato(DateTimeOffset quando) =>
        Path.Combine(Cartella, $"data-local.illeggibile-{quando.ToUnixTimeMilliseconds()}.json");
}
