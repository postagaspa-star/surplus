using System.Text.Json.Nodes;

namespace Surplus.Nucleo;

/// <summary>
/// Dal formato a sessioni fisse (Spinta/Trazione) alle schede personali: la
/// stessa conversione di MigrazioneSchede.kt sull'app Android, perché un diario
/// copiato dal telefono con la versione vecchia deve aprirsi anche qui.
///
/// Non cancella niente: le sessioni vecchie prendono il nome della loro scheda e
/// gli esercizi personali entrano nell'archivio insieme a quelli di partenza.
/// </summary>
public static class Migrazione
{
    public const string LiberoId = "libero";
    public const string LiberoNome = "Libero";

    public static JsonObject AlleSchede(JsonObject d)
    {
        if (Json.Booleano(d["schedeInizializzate"]) == true) return d;

        // Archivio esercizi: quelli già presenti, poi i semi, poi i personali del telefono.
        var archivio = new JsonArray();
        var visti = new HashSet<string>(StringComparer.Ordinal);
        void Aggiungi(JsonArray da)
        {
            foreach (var e in da.ToList())
            {
                var id = Json.Testo(e?["id"]);
                if (id == null || !visti.Add(id)) continue;
                archivio.Add(Json.Copia(e));
            }
        }
        Aggiungi(Json.Lista(d["exercises"]));
        Aggiungi(Semi.Esercizi());
        Aggiungi(Json.Lista(d["customExercises"]));

        // Sessioni: il tipo fisso diventa il nome della scheda, le serie restano come sono.
        var sessioni = new JsonArray();
        foreach (var s in Json.Lista(d["workouts"]).ToList())
        {
            var o = Json.Copia(s) as JsonObject;
            if (o == null) continue;
            var tipo = Json.Testo(o["type"]);
            if (Json.Testo(o["routineId"]) == null && tipo != null)
            {
                o["routineId"] = tipo.ToLowerInvariant();
                o["routineName"] = tipo == "SPINTA" ? "Spinta" : "Trazione";
            }
            sessioni.Add(o);
        }

        // Schede di partenza: solo gli esercizi fatti davvero almeno una volta.
        var schede = Json.Lista(d["routines"]);
        if (schede.Count == 0)
        {
            schede = new JsonArray(
                SchedaIniziale("SPINTA", "Spinta", archivio, sessioni),
                SchedaIniziale("TRAZIONE", "Trazione", archivio, sessioni));
        }
        else
        {
            schede = Json.Copia(schede) as JsonArray ?? new JsonArray();
        }

        d["exercises"] = archivio;
        d["customExercises"] = new JsonArray();
        d["routines"] = schede;
        d["workouts"] = sessioni;
        d["schedeInizializzate"] = true;
        return d;
    }

    private static JsonObject SchedaIniziale(string tipo, string nome, JsonArray archivio, JsonArray sessioni)
    {
        var fatti = new HashSet<string>(StringComparer.Ordinal);
        foreach (var s in sessioni)
        {
            if (Json.Testo(s?["type"]) != tipo) continue;
            foreach (var e in Json.Lista(s?["exercises"]))
            {
                if (Json.Lista(e?["sets"]).Count == 0) continue;
                var id = Json.Testo(e?["exerciseId"]);
                if (id != null) fatti.Add(id);
            }
        }

        var ids = new JsonArray();
        foreach (var e in archivio)
        {
            var id = Json.Testo(e?["id"]);
            if (id == null) continue;
            var dentro = fatti.Count > 0
                ? fatti.Contains(id)
                : Json.Booleano(e?[tipo == "SPINTA" ? "spinta" : "trazione"]) == true;
            // (JsonNode)id e non ids.Add(id): la seconda forma crea un valore che
            // si scrive solo con opzioni che hanno il risolutore dei tipi.
            if (dentro) ids.Add((JsonNode)id);
        }

        return new JsonObject
        {
            ["id"] = tipo.ToLowerInvariant(),
            ["nome"] = nome,
            ["exerciseIds"] = ids,
        };
    }
}
