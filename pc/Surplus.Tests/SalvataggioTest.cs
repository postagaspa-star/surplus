using System;
using System.IO;
using System.Linq;
using System.Text.Json.Nodes;
using Surplus.Nucleo;
using Surplus.Sistema;
using Xunit;
using MotoreSurplus = Surplus.Motore.Motore;

namespace Surplus.Tests;

/// <summary>
/// La pagina manda sempre il diario intero. Se nel frattempo il NAS ha portato
/// un pasto dal telefono, un diario preso prima non deve cancellarlo.
/// </summary>
public class SalvataggioTest : IDisposable
{
    private readonly string cartella = Path.Combine(Path.GetTempPath(), "surplus-prova-" + Guid.NewGuid().ToString("N"));

    public void Dispose()
    {
        try { Directory.Delete(cartella, true); } catch (IOException) { }
    }

    private static JsonObject Voce(string id) => new()
    {
        ["id"] = id, ["date"] = "2026-11-03", ["moment"] = "PRANZO", ["nome"] = id, ["grams"] = 100, ["createdAt"] = 0,
    };

    private static string[] Voci(JsonObject d) =>
        Json.Lista(d["entries"]).Select(v => Json.Testo(v?["id"]) ?? "").OrderBy(x => x).ToArray();

    private static JsonObject Con(JsonObject d, params string[] ids)
    {
        var nuovo = (JsonObject)d.DeepClone();
        nuovo["entries"] = new JsonArray(ids.Select(i => (JsonNode)Voce(i)).ToArray());
        return nuovo;
    }

    [Fact]
    public void Un_diario_preso_prima_del_nas_non_cancella_il_pasto_del_telefono()
    {
        var motore = new MotoreSurplus(new Percorsi(cartella));
        var (pagina, versionePagina) = motore.DatiConVersione();
        Assert.True(motore.Salva(Con(pagina, "a"), versionePagina)["ok"] != null);
        var versioneDopo = motore.VersioneDati;

        // Arriva dal NAS il pasto scritto sul telefono.
        Assert.True(motore.Salva(Con(motore.Dati(), "a", "telefono"), motore.VersioneDati)["ok"] != null);

        // La pagina, che non l'ha ancora visto, manda il suo diario con una voce in più.
        var esito = motore.Salva(Con(pagina, "a", "computer"), versioneDopo);
        Assert.Equal(MotoreSurplus.Superato, Json.Testo(esito["codice"]));
        Assert.Equal(new[] { "a", "telefono" }, Voci(motore.Dati()));
    }

    [Fact]
    public void Senza_numero_si_salva_come_prima()
    {
        var motore = new MotoreSurplus(new Percorsi(cartella));
        Assert.True(motore.Salva(Con(motore.Dati(), "a"))["ok"] != null);
        Assert.True(motore.Salva(Con(motore.Dati(), "a", "b"))["ok"] != null);
        Assert.Equal(new[] { "a", "b" }, Voci(motore.Dati()));
    }

    [Fact]
    public void La_base_del_nas_resta_dopo_un_riavvio()
    {
        var percorsi = new Percorsi(cartella);
        var motore = new MotoreSurplus(percorsi);
        Assert.Equal(-1, motore.Impostazioni.NasBase);
        motore.AggiornaImpostazioni(i => i.NasBase = 42);
        Assert.Equal(42, new MotoreSurplus(percorsi).Impostazioni.NasBase);
    }
}
