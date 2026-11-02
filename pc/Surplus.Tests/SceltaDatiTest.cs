using Surplus.Nucleo;
using Xunit;

namespace Surplus.Tests;

/// <summary>
/// Quale copia del diario si apre: è il punto in cui i dati si salvano o si
/// perdono. Il caso che l'ha fatto nascere è un file rimasto a 0 byte dopo uno
/// spegnimento improvviso, visto davvero sull'app Android.
/// </summary>
public class SceltaDatiTest
{
    private static string Diario(string nome) =>
        $$"""{"schemaVersion":4,"profile":{"nome":"{{nome}}"},"entries":[]}""";

    [Fact]
    public void Di_norma_si_usa_il_file_principale()
    {
        var e = SceltaDati.Scegli(Diario("oggi"), Diario("ieri"), Nucleo.Diario.Nuovo);
        Assert.Equal(SceltaDati.Fonte.Principale, e.Da);
        Assert.Equal("oggi", Json.Testo(e.Dati["profile"]?["nome"]));
        Assert.False(e.PrincipaleRovinato);
    }

    [Fact]
    public void File_vuoto_dopo_uno_spegnimento_si_riparte_dalla_copia()
    {
        var e = SceltaDati.Scegli("", Diario("ieri"), Nucleo.Diario.Nuovo);
        Assert.Equal(SceltaDati.Fonte.CopiaPrecedente, e.Da);
        Assert.Equal("ieri", Json.Testo(e.Dati["profile"]?["nome"]));
        Assert.True(e.PrincipaleRovinato);
    }

    [Fact]
    public void File_troncato_a_meta_si_riparte_dalla_copia()
    {
        var intero = Diario("oggi");
        var e = SceltaDati.Scegli(intero[..(intero.Length / 2)], Diario("ieri"), Nucleo.Diario.Nuovo);
        Assert.Equal(SceltaDati.Fonte.CopiaPrecedente, e.Da);
        Assert.True(e.PrincipaleRovinato);
    }

    [Fact]
    public void Primo_avvio_in_assoluto_diario_nuovo()
    {
        var e = SceltaDati.Scegli(null, null, Nucleo.Diario.Nuovo);
        Assert.Equal(SceltaDati.Fonte.Nuovi, e.Da);
        Assert.False(e.PrincipaleRovinato);
    }

    [Fact]
    public void Tutto_rovinato_si_riparte_da_zero_ma_il_principale_va_messo_da_parte()
    {
        var e = SceltaDati.Scegli("{rotto", "", Nucleo.Diario.Nuovo);
        Assert.Equal(SceltaDati.Fonte.Nuovi, e.Da);
        Assert.True(e.PrincipaleRovinato);
    }
}
