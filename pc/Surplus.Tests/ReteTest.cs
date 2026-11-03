using System.Linq;
using Surplus.Rete;
using Xunit;

namespace Surplus.Tests;

/// <summary>
/// Le due trappole degli archivi alimentari (categoria ombrello e pezzi di
/// parola) e la lettura difensiva delle risposte del servizio AI.
/// </summary>
public class ReteTest
{
    [Theory]
    [InlineData("en:beverages,en:waters", true)]
    [InlineData("en:plant-based-foods-and-beverages,en:pastas", false)]  // la pasta non si beve
    [InlineData("en:dairies,en:milk-chocolate", false)]                  // il cioccolato al latte nemmeno
    [InlineData("en:beverages,en:fruit-juices", true)]
    [InlineData("en:meats,en:steaks", false)]                            // "steak" contiene "tea"
    public void Bevanda_o_solido(string categorie, bool atteso) =>
        Assert.Equal(atteso, Etichette.ÈBevanda(categorie));

    [Theory]
    [InlineData("en:plant-based-foods-and-beverages,en:vegetables", "VERDURA")]
    [InlineData("en:fruits,en:apples", "FRUTTA")]
    [InlineData("en:beverages,en:fruit-juices", "ALTRO")]   // un succo non vale come frutta
    [InlineData("en:meats,en:hams", "PROTEICO")]
    [InlineData("en:dairies,en:cheeses", "LATTICINO")]
    [InlineData("en:cereals-and-potatoes,en:breads", "CEREALE")]
    [InlineData("", "ALTRO")]
    public void Gruppo_dalle_categorie(string categorie, string atteso) =>
        Assert.Equal(atteso, Etichette.Gruppo(categorie));

    [Theory]
    [InlineData("8001234567890", true)]
    [InlineData("12345", false)]
    [InlineData("800123456789012345", false)]
    [InlineData("80012A4567890", false)]
    public void Codice_a_barre_solo_cifre(string codice, bool atteso) =>
        Assert.Equal(atteso, Alimenti.ÈCodice(codice));

    [Fact]
    public void Motore_nuovo_hits_e_marca_come_elenco()
    {
        var r = Alimenti.Risultati("""
            {"hits":[
              {"product_name":"Crema di verdure","brands":["Pam & Panorama","Pam"],"nova_group":3,
               "nutriments":{"energy-kcal_100g":41,"proteins_100g":1.1},"categories_tags":["en:soups"]},
              {"product_name":"Senza marca","brands":null,"nutriments":{"energy-kcal_100g":31}},
              {"product_name":"Senza calorie","nutriments":{}}
            ]}
            """);
        Assert.Equal(2, r.Count);
        Assert.Equal("Crema di verdure (Pam & Panorama)", (string?)r[0]!["nome"]);
        Assert.Equal(3, (int?)r[0]!["nova"]);
        Assert.Equal("Senza marca", (string?)r[1]!["nome"]);
    }

    [Fact]
    public void Motore_vecchio_products_e_marca_come_testo()
    {
        var r = Alimenti.Risultati("""{"products":[{"product_name":"Penne","brands":"Barilla, X","nutriments":{"energy-kcal_100g":355}}]}""");
        Assert.Equal("Penne (Barilla)", (string?)r.Single()!["nome"]);
    }

    [Fact]
    public void Ricerca_senza_segni_che_rompono_il_motore() =>
        Assert.Equal("caffe d'orzo 100 countries_tags:\"en:italy\"", Alimenti.Ricerca("  caffe: d'orzo (100) \"  "));

    [Fact]
    public void Piu_foto_dello_stesso_alimento_vanno_unite()
    {
        var t = Ai.IstruzioniFoto(3, unico: false);
        Assert.Contains("Le 3 foto mostrano LO STESSO alimento", t);
        Assert.Contains("etichetta", t);
        Assert.DoesNotContain("UN SOLO alimento", t);
    }

    [Fact]
    public void Una_foto_sola_nessuna_istruzione_di_unione()
    {
        var t = Ai.IstruzioniFoto(1, unico: false);
        Assert.DoesNotContain("foto mostrano", t);
        Assert.Contains("nova", t);
    }

    [Fact]
    public void Un_solo_alimento_non_si_scompone()
    {
        Assert.Contains("UN SOLO alimento", Ai.IstruzioniFoto(1, unico: true));
    }

    [Fact]
    public void Risposta_della_foto_letta_coi_valori_giusti()
    {
        var o = Ai.LeggiFoto("""
        {"alimenti":[{"nome":"Pane","grammi":90,"kcal100":268,"prot100":8,"grassi100":0.5,
          "carb100":50,"fibra100":3.5,"nova":3,"gruppo":"CEREALE","certezza":"media"}],
         "domanda":"C'è salsa?","note":""}
        """);
        var a = Surplus.Nucleo.Json.Lista(o["alimenti"]).Single()!;
        Assert.Equal("Pane", Surplus.Nucleo.Json.Testo(a["nome"]));
        Assert.Equal(90.0, Surplus.Nucleo.Json.Numero(a["grammi"]));
        Assert.Equal(3, Surplus.Nucleo.Json.Intero(a["nova"]));
        Assert.Equal("C'è salsa?", Surplus.Nucleo.Json.Testo(o["domanda"]));
        Assert.Null(Surplus.Nucleo.Json.Testo(o["note"]));
    }

    [Fact]
    public void Valori_assurdi_della_foto_vengono_scartati()
    {
        var o = Ai.LeggiFoto("""
        {"alimenti":[
          {"nome":"","grammi":100,"nova":2,"gruppo":"ALTRO","certezza":"alta"},
          {"nome":"X","grammi":0,"nova":9,"gruppo":"INVENTATO","certezza":"bassa"}],
         "domanda":"","note":""}
        """);
        var a = Surplus.Nucleo.Json.Lista(o["alimenti"]).Single()!;
        Assert.Equal("X", Surplus.Nucleo.Json.Testo(a["nome"]));
        Assert.Equal(100.0, Surplus.Nucleo.Json.Numero(a["grammi"])); // grammi a zero → porzione da 100
        Assert.Null(Surplus.Nucleo.Json.Intero(a["nova"]));           // fuori scala → nessun grado
        Assert.Equal("ALTRO", Surplus.Nucleo.Json.Testo(a["gruppo"]));
    }

    [Fact]
    public void JSON_incorniciato_da_testo_viene_comunque_letto()
    {
        var t = Ai.EstraiJson("Ecco:\n```json\n{\"a\":1}\n```\nSpero vada bene.");
        Assert.Equal("{\"a\":1}", t);
    }

    [Fact]
    public void Esercizio_nome_non_riconosciuto()
    {
        var o = Ai.LeggiEsercizio("""{"gruppo":"Altro","serie":"3","repMin":8,"repMax":12,"incrementoKg":0,"nota":"nome non riconosciuto"}""");
        Assert.False(Surplus.Nucleo.Json.Booleano(o["riconosciuto"]));
    }

    [Fact]
    public void Esercizio_valori_assurdi_corretti()
    {
        var o = Ai.LeggiEsercizio("""{"gruppo":"Muscoli vari","serie":"tante","repMin":500,"repMax":12,"incrementoKg":80,"nota":"ok"}""");
        Assert.Equal("Altro", Surplus.Nucleo.Json.Testo(o["gruppo"]));
        Assert.Equal("3", Surplus.Nucleo.Json.Testo(o["serie"]));
        Assert.Equal(12, Surplus.Nucleo.Json.Intero(o["repMin"]));
        Assert.Null(Surplus.Nucleo.Json.Numero(o["incrementoKg"]));
    }

    [Fact]
    public void Esercizio_range_scritto_al_contrario_si_rimette_in_ordine()
    {
        var o = Ai.LeggiEsercizio("""{"gruppo":"Gambe","serie":"3","repMin":15,"repMax":8,"incrementoKg":2,"nota":""}""");
        Assert.Equal(8, Surplus.Nucleo.Json.Intero(o["repMin"]));
        Assert.Equal(15, Surplus.Nucleo.Json.Intero(o["repMax"]));
        Assert.Null(Surplus.Nucleo.Json.Testo(o["nota"]));
    }

    [Fact]
    public void Gli_schemi_mandati_al_servizio_si_riescono_a_scrivere()
    {
        // Gli elenchi di valori ammessi ("enum") nascevano come valori che le
        // opzioni di scrittura del programma non sapevano serializzare: la
        // richiesta partiva solo se qualcuno la scriveva con le opzioni di serie.
        foreach (var schema in new[] { Ai.SchemaFotoClaude(), Ai.SchemaFotoGemini(), Ai.SchemaEsercizioClaude(), Ai.SchemaEsercizioGemini() })
        {
            var testo = Surplus.Nucleo.Json.Scrivi(schema);
            Assert.NotNull(Surplus.Nucleo.Json.Oggetto(testo));
        }
    }

    [Fact]
    public void Messaggi_di_errore_che_dicono_cosa_fare()
    {
        Assert.Contains("Chiave non valida", Ai.Messaggio(new ErroreServizio(401, "")));
        Assert.Contains("credito", Ai.Messaggio(new ErroreServizio(429, "")));
        Assert.Contains("modello", Ai.Messaggio(new ErroreServizio(400, "bad model")));
        Assert.Contains("connessione", Ai.Messaggio(new System.Net.Http.HttpRequestException("giù")));
    }
}
