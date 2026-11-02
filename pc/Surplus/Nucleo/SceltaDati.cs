using System.Text.Json.Nodes;

namespace Surplus.Nucleo;

/// <summary>
/// Quale copia del diario usare all'avvio. Stessa regola dell'app Android
/// (SceltaDati.kt): il file principale, poi l'ultima copia buona presa all'avvio
/// precedente, poi — solo se non c'è altro — si riparte da zero.
///
/// È il punto in cui il diario si salva o si perde, quindi sta da solo ed è testato.
/// </summary>
public static class SceltaDati
{
    public enum Fonte { Principale, CopiaPrecedente, Nuovi }

    public sealed record Esito(JsonObject Dati, Fonte Da, bool PrincipaleRovinato);

    /// <summary>I testi sono null quando il file non esiste.</summary>
    public static Esito Scegli(string? principale, string? copiaPrecedente, Func<JsonObject> nuovi)
    {
        static JsonObject? Prova(string? testo) =>
            string.IsNullOrWhiteSpace(testo) ? null : Json.Oggetto(testo);

        if (Prova(principale) is JsonObject buono) return new Esito(buono, Fonte.Principale, false);
        var rovinato = principale != null;
        if (Prova(copiaPrecedente) is JsonObject copia) return new Esito(copia, Fonte.CopiaPrecedente, rovinato);
        return new Esito(nuovi(), Fonte.Nuovi, rovinato);
    }
}
