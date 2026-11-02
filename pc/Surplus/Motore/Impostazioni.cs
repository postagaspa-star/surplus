using System.Text.Json.Nodes;
using Surplus.Nucleo;
using Surplus.Sistema;

namespace Surplus.Motore;

/// <summary>
/// Le impostazioni del programma: gli stessi valori dell'app Android più quelli
/// che hanno senso solo sul computer (avvio all'accesso).
///
/// La chiave del servizio AI NON esce mai verso la pagina: sul disco è cifrata da
/// Windows e all'interfaccia si dice soltanto se c'è o non c'è.
/// </summary>
public sealed class Impostazioni
{
    public int KcalMin { get; set; } = 3300;
    public int KcalMax { get; set; } = 3700;
    public int ProtMin { get; set; } = 130;
    public int ProtMax { get; set; } = 150;
    public int AcquaMl { get; set; } = 3250;
    public bool MostraScheda { get; set; } = true;
    public bool SchedaTutorialVisto { get; set; }
    public bool AvvioAlLogin { get; set; }

    public string AiFornitore { get; set; } = "GEMINI";
    public string AiModello { get; set; } = "";
    public string AiChiave { get; set; } = "";

    /// <summary>Il server di Surplus sul NAS di casa, per esempio "192.168.1.20:8600".</summary>
    public string NasIndirizzo { get; set; } = "";

    /// <summary>Il permesso dato dal NAS a questo computer: cifrato sul disco, mai verso la pagina.</summary>
    public string NasToken { get; set; } = "";

    public bool AiAttiva => !string.IsNullOrWhiteSpace(AiChiave);

    public string AiModelloEffettivo => string.IsNullOrWhiteSpace(AiModello)
        ? (AiFornitore == "CLAUDE" ? "claude-opus-5" : "gemini-2.5-flash")
        : AiModello;

    public static Impostazioni Leggi(string file)
    {
        var o = Json.Oggetto(Archivio.LeggiTesto(file));
        var i = new Impostazioni();
        if (o == null) return i;
        i.KcalMin = Json.Intero(o["kcalMin"]) ?? i.KcalMin;
        i.KcalMax = Json.Intero(o["kcalMax"]) ?? i.KcalMax;
        i.ProtMin = Json.Intero(o["protMin"]) ?? i.ProtMin;
        i.ProtMax = Json.Intero(o["protMax"]) ?? i.ProtMax;
        i.AcquaMl = Json.Intero(o["acquaMl"]) ?? i.AcquaMl;
        i.MostraScheda = Json.Booleano(o["mostraScheda"]) ?? i.MostraScheda;
        i.SchedaTutorialVisto = Json.Booleano(o["schedaTutorialVisto"]) ?? i.SchedaTutorialVisto;
        i.AvvioAlLogin = Json.Booleano(o["avvioAlLogin"]) ?? i.AvvioAlLogin;
        i.AiFornitore = Json.Testo(o["aiFornitore"]) ?? i.AiFornitore;
        i.AiModello = Json.Testo(o["aiModello"]) ?? i.AiModello;
        i.AiChiave = Dpapi.Decifra(Json.Testo(o["aiChiaveCifrata"])) ?? "";
        i.NasIndirizzo = Json.Testo(o["nasIndirizzo"]) ?? i.NasIndirizzo;
        i.NasToken = Dpapi.Decifra(Json.Testo(o["nasTokenCifrato"])) ?? "";
        return i;
    }

    public void Salva(string file) => Archivio.ScriviTesto(file, Json.Scrivi(new JsonObject
    {
        ["kcalMin"] = KcalMin,
        ["kcalMax"] = KcalMax,
        ["protMin"] = ProtMin,
        ["protMax"] = ProtMax,
        ["acquaMl"] = AcquaMl,
        ["mostraScheda"] = MostraScheda,
        ["schedaTutorialVisto"] = SchedaTutorialVisto,
        ["avvioAlLogin"] = AvvioAlLogin,
        ["aiFornitore"] = AiFornitore,
        ["aiModello"] = AiModello,
        ["aiChiaveCifrata"] = Dpapi.Cifra(AiChiave),
        ["nasIndirizzo"] = NasIndirizzo,
        ["nasTokenCifrato"] = Dpapi.Cifra(NasToken),
    }));

    /// <summary>Quello che la pagina può vedere: tutto tranne la chiave.</summary>
    public JsonObject PerLaPagina() => new()
    {
        ["kcalMin"] = KcalMin,
        ["kcalMax"] = KcalMax,
        ["protMin"] = ProtMin,
        ["protMax"] = ProtMax,
        ["acquaMl"] = AcquaMl,
        ["mostraScheda"] = MostraScheda,
        ["schedaTutorialVisto"] = SchedaTutorialVisto,
        ["avvioAlLogin"] = AvvioAlLogin,
        ["aiFornitore"] = AiFornitore,
        ["aiModello"] = AiModello,
        ["aiAttiva"] = AiAttiva,
    };

    /// <summary>Applica quello che manda la pagina. La chiave si cambia solo se è stata scritta.</summary>
    public void Applica(JsonObject o)
    {
        KcalMin = Limite(Json.Intero(o["kcalMin"]) ?? KcalMin, 800, 12000);
        KcalMax = Limite(Json.Intero(o["kcalMax"]) ?? KcalMax, KcalMin, 12000);
        ProtMin = Limite(Json.Intero(o["protMin"]) ?? ProtMin, 20, 500);
        ProtMax = Limite(Json.Intero(o["protMax"]) ?? ProtMax, ProtMin, 500);
        AcquaMl = Limite(Json.Intero(o["acquaMl"]) ?? AcquaMl, 500, 8000);
        MostraScheda = Json.Booleano(o["mostraScheda"]) ?? MostraScheda;
        SchedaTutorialVisto = Json.Booleano(o["schedaTutorialVisto"]) ?? SchedaTutorialVisto;
        AvvioAlLogin = Json.Booleano(o["avvioAlLogin"]) ?? AvvioAlLogin;
        var fornitore = Json.Testo(o["aiFornitore"]);
        if (fornitore is "CLAUDE" or "GEMINI") AiFornitore = fornitore;
        AiModello = (Json.Testo(o["aiModello"]) ?? AiModello).Trim();
        if (Json.Testo(o["aiChiave"]) is string chiave) AiChiave = chiave.Trim();
        if (Json.Booleano(o["aiChiaveTogli"]) == true) AiChiave = "";
    }

    private static int Limite(int v, int min, int max) => v < min ? min : v > max ? max : v;
}
