using System.Linq;
using System.Text.Json.Nodes;
using Surplus.Nucleo;
using Xunit;

namespace Surplus.Tests;

/// <summary>
/// Un diario copiato dal telefono con la versione vecchia (sessioni Spinta e
/// Trazione fisse) deve aprirsi anche qui, senza perdere né sessioni né serie.
/// </summary>
public class MigrazioneTest
{
    private const string Vecchio = """
    {
      "schemaVersion": 3,
      "profile": {"userId":"local","nome":"","isConfigured":true},
      "entries": [],
      "waterByDate": {},
      "weights": [],
      "customFoods": [],
      "workouts": [
        {"id":"w1","date":"2026-10-28","type":"SPINTA","startedAt":1,"firstSetAt":5,"lastSetAt":9,
         "exercises":[
           {"exerciseId":"shoulder-press","nome":"Shoulder Press","sets":[{"weightKg":12.0,"reps":10}]},
           {"exerciseId":"floor-press","nome":"Floor Press con manubri","sets":[]}
         ]}
      ],
      "customExercises": [
        {"id":"custom-ex-1","nome":"Dip alla sedia","gruppo":"Tricipiti","spinta":true,"trazione":false,"custom":true}
      ]
    }
    """;

    private static JsonObject Migrato() => Diario.Sistema(Json.Oggetto(Vecchio)!);

    [Fact]
    public void Le_sessioni_prendono_il_nome_della_scheda_e_le_serie_restano()
    {
        var d = Migrato();
        var s = Json.Lista(d["workouts"]).Single()!;
        Assert.Equal("spinta", Json.Testo(s["routineId"]));
        Assert.Equal("Spinta", Json.Testo(s["routineName"]));
        Assert.Equal(5, Json.Lungo(s["firstSetAt"]));
        var serie = Json.Lista(Json.Lista(s["exercises"]).First()?["sets"]);
        Assert.Single(serie);
        Assert.Equal(12.0, Json.Numero(serie.First()?["weightKg"]));
    }

    [Fact]
    public void Gli_esercizi_personali_entrano_nell_archivio_coi_semi()
    {
        var d = Migrato();
        var archivio = Json.Lista(d["exercises"]).Select(e => Json.Testo(e?["id"])).ToList();
        Assert.Contains("custom-ex-1", archivio);
        Assert.Contains("shoulder-press", archivio);
        Assert.Contains("curl-manubri", archivio);
        Assert.Empty(Json.Lista(d["customExercises"]));
        Assert.Equal(archivio.Count, archivio.Distinct().Count());
    }

    [Fact]
    public void La_scheda_iniziale_contiene_solo_gli_esercizi_fatti_davvero()
    {
        var d = Migrato();
        var spinta = Json.Lista(d["routines"]).First(r => Json.Testo(r?["id"]) == "spinta")!;
        var ids = Json.Lista(spinta["exerciseIds"]).Select(x => Json.Testo(x)).ToList();
        // floor-press era in lista ma senza serie: resta in archivio, non nella scheda.
        Assert.Equal(new[] { "shoulder-press" }, ids);
    }

    [Fact]
    public void Senza_sessioni_di_quel_tipo_la_scheda_parte_con_tutti_i_suoi_esercizi()
    {
        var d = Migrato();
        var trazione = Json.Lista(d["routines"]).First(r => Json.Testo(r?["id"]) == "trazione")!;
        var ids = Json.Lista(trazione["exerciseIds"]).Select(x => Json.Testo(x)).ToList();
        Assert.Contains("curl-manubri", ids);
        Assert.DoesNotContain("shoulder-press", ids);
    }

    [Fact]
    public void Gira_una_volta_sola()
    {
        var d = Migrato();
        var prima = d.ToJsonString();
        var ancora = Diario.Sistema(d);
        Assert.Equal(prima, ancora.ToJsonString());
    }

    [Fact]
    public void Il_diario_migrato_si_riesce_a_scrivere_sul_disco()
    {
        // Non basta che la migrazione produca l'albero giusto: deve passare da
        // Json.Scrivi, cioè dalle opzioni vere del programma. Con ToJsonString()
        // (opzioni di serie) questo caso passava e il programma invece si chiudeva
        // da solo al primo avvio con un diario vecchio che aveva allenamenti.
        var d = Migrato();
        var testo = Json.Scrivi(d);
        var riletto = Json.Oggetto(testo);
        Assert.NotNull(riletto);
        var ids = Json.Lista(Json.Lista(riletto!["routines"]).First(r => Json.Testo(r?["id"]) == "spinta")!["exerciseIds"]);
        Assert.Contains("shoulder-press", ids.Select(x => Json.Testo(x)));
    }

    [Fact]
    public void Un_diario_gia_nuovo_non_viene_toccato()
    {
        var d = Diario.Nuovo();
        d["schedeInizializzate"] = true;
        d["routines"] = new JsonArray(new JsonObject { ["id"] = "mia", ["nome"] = "Mia", ["exerciseIds"] = new JsonArray() });
        var dopo = Diario.Sistema(d);
        Assert.Single(Json.Lista(dopo["routines"]));
        Assert.Equal("Mia", Json.Testo(Json.Lista(dopo["routines"]).First()?["nome"]));
    }
}
