using System.Text.Json.Nodes;
using Surplus.Nucleo;
using Xunit;

namespace Surplus.Tests;

/// <summary>
/// Il diario è lo stesso file del telefono: queste prove guardano che un
/// documento arrivato da fuori si apra sempre, e che un salvataggio sbagliato
/// non possa svuotarlo.
/// </summary>
public class DiarioTest
{
    private static JsonObject Con(int voci)
    {
        var d = Diario.Nuovo();
        var lista = new JsonArray();
        for (int i = 0; i < voci; i++)
        {
            lista.Add(new JsonObject { ["id"] = "v" + i, ["date"] = "2026-11-02", ["moment"] = "PRANZO", ["nome"] = "Riso", ["grams"] = 100 });
        }
        d["entries"] = lista;
        return d;
    }

    [Fact]
    public void Un_diario_nuovo_ha_tutte_le_liste()
    {
        var d = Diario.Nuovo();
        foreach (var nome in new[] { "entries", "weights", "customFoods", "workouts", "exercises", "routines" })
        {
            Assert.IsType<JsonArray>(d[nome]);
        }
        Assert.Equal(Versione.Schema, Json.Intero(d["schemaVersion"]));
    }

    [Fact]
    public void Un_documento_monco_viene_rimesso_a_posto_invece_di_rompere()
    {
        var d = Json.Oggetto("""{"entries":[{"id":"x","date":"2026-11-02"}]}""")!;
        var sistemato = Diario.Sistema(d);
        Assert.IsType<JsonObject>(sistemato["profile"]);
        Assert.IsType<JsonObject>(sistemato["waterByDate"]);
        Assert.Single(Json.Lista(sistemato["entries"]));
    }

    [Fact]
    public void Un_salvataggio_che_svuoterebbe_il_diario_viene_rifiutato()
    {
        var attuale = Con(20);
        var quasiVuoto = Con(3);
        Assert.False(Diario.Accettabile(quasiVuoto, attuale, out var motivo));
        Assert.Contains("17 voci su 20", motivo);
    }

    [Fact]
    public void Togliere_qualche_voce_resta_permesso()
    {
        Assert.True(Diario.Accettabile(Con(18), Con(20), out _));
        // Su un diario appena nato non scatta nessun sospetto.
        Assert.True(Diario.Accettabile(Con(0), Con(4), out _));
    }

    [Fact]
    public void Senza_profilo_non_si_salva()
    {
        var senza = Con(5);
        senza.Remove("profile");
        Assert.False(Diario.Accettabile(senza, Con(5), out var motivo));
        Assert.Contains("profilo", motivo);
        Assert.False(Diario.Accettabile(null, Con(5), out _));
    }
}
