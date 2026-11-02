using System.Text.Json.Nodes;

namespace Surplus.Nucleo;

/// <summary>
/// Il diario: lo stesso identico file dell'app Android (<c>data-local.json</c>,
/// formato 4). Qui non si interpreta il contenuto voce per voce — lo fa la
/// pagina — ma si garantiscono due cose: che il documento abbia sempre la forma
/// giusta e che nessun salvataggio possa svuotarlo.
/// </summary>
public static class Diario
{
    /// <summary>Le liste che devono esserci sempre: la pagina conta di trovarle.</summary>
    private static readonly string[] Liste =
    {
        "entries", "weights", "customFoods", "workouts", "customExercises", "exercises", "routines",
    };

    public static JsonObject Nuovo() => Sistema(new JsonObject
    {
        ["schemaVersion"] = Versione.Schema,
        ["profile"] = new JsonObject
        {
            ["userId"] = "local",
            ["nome"] = "",
            ["annoNascita"] = 0,
            ["sesso"] = "MASCHIO",
            ["altezzaCm"] = 0,
            ["pesoKg"] = 0.0,
            ["attivita"] = "MODERATA",
            ["obiettivo"] = "MASSA",
            ["isConfigured"] = false,
            ["disclaimerAcceptedAt"] = 0,
            ["isMinor"] = false,
        },
        ["waterByDate"] = new JsonObject(),
    });

    /// <summary>
    /// Rimette a posto un documento arrivato da fuori (file dell'app, backup, pagina):
    /// campi mancanti, liste sparite, formato vecchio. Non butta mai via niente.
    /// </summary>
    public static JsonObject Sistema(JsonObject d)
    {
        if (d["profile"] is not JsonObject) d["profile"] = Nuovo()["profile"]!.DeepClone();
        if (d["waterByDate"] is not JsonObject) d["waterByDate"] = new JsonObject();
        foreach (var nome in Liste)
        {
            if (d[nome] is not JsonArray) d[nome] = new JsonArray();
        }
        var schema = Json.Intero(d["schemaVersion"]) ?? 0;
        if (schema < Versione.Schema) d = Migrazione.AlleSchede(d);
        d["schemaVersion"] = Versione.Schema;
        return d;
    }

    /// <summary>
    /// Il documento che arriva dalla pagina è accettabile? Serve a non sovrascrivere
    /// mesi di diario con una risposta vuota per un errore dell'interfaccia.
    /// </summary>
    public static bool Accettabile(JsonObject? nuovo, JsonObject attuale, out string motivo)
    {
        motivo = "";
        if (nuovo == null)
        {
            motivo = "Il programma non ha capito i dati da salvare.";
            return false;
        }
        if (nuovo["profile"] is not JsonObject)
        {
            motivo = "Nei dati da salvare manca il profilo.";
            return false;
        }
        // Una perdita di oltre metà delle voci in un colpo solo non è mai una modifica voluta.
        var prima = Json.Lista(attuale["entries"]).Count;
        var dopo = Json.Lista(nuovo["entries"]).Count;
        if (prima >= 10 && dopo * 2 < prima)
        {
            motivo = $"Il salvataggio avrebbe tolto {prima - dopo} voci su {prima}: annullato per sicurezza.";
            return false;
        }
        return true;
    }
}
