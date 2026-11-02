using System.Text.Json.Nodes;
using Surplus.Nucleo;
using Xunit;

namespace Surplus.Tests;

public class JsonTest
{
    [Fact]
    public void Due_documenti_uguali_scritti_in_ordine_diverso_sono_uguali()
    {
        var uno = Json.Oggetto("{\"b\":1,\"a\":{\"y\":true,\"x\":\"ciao\"}}")!;
        var due = Json.Oggetto("{\"a\":{\"x\":\"ciao\",\"y\":true},\"b\":1}")!;
        Assert.NotEqual(Json.Scrivi(uno), Json.Scrivi(due));
        Assert.Equal(Json.Canonico(uno), Json.Canonico(due));
    }

    [Fact]
    public void Un_valore_diverso_resta_diverso()
    {
        var uno = Json.Oggetto("{\"a\":1}")!;
        var due = Json.Oggetto("{\"a\":2}")!;
        Assert.NotEqual(Json.Canonico(uno), Json.Canonico(due));
    }

    [Fact]
    public void Negli_elenchi_l_ordine_conta()
    {
        // Le serie di un esercizio sono una dopo l'altra: 10 ripetizioni e poi 8
        // non è la stessa cosa di 8 e poi 10.
        var uno = Json.Oggetto("{\"serie\":[{\"rip\":10},{\"rip\":8}]}")!;
        var due = Json.Oggetto("{\"serie\":[{\"rip\":8},{\"rip\":10}]}")!;
        Assert.NotEqual(Json.Canonico(uno), Json.Canonico(due));
    }

    [Fact]
    public void Il_documento_di_partenza_non_viene_toccato()
    {
        var o = Json.Oggetto("{\"b\":1,\"a\":2}")!;
        var prima = Json.Scrivi(o);
        Json.Canonico(o);
        Assert.Equal(prima, Json.Scrivi(o));
    }

    [Fact]
    public void Un_diario_intero_passa_per_il_confronto()
    {
        var diario = Diario.Nuovo();
        var copia = (JsonObject)diario.DeepClone();
        Assert.Equal(Json.Canonico(diario), Json.Canonico(copia));
    }
}
