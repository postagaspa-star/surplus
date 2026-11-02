using System.Text.Json.Nodes;
using Surplus.Nucleo;
using Surplus.Sistema;

namespace Surplus.Motore;

/// <summary>La risposta del programma alla pagina: stato HTTP e JSON, niente altro.</summary>
public sealed record RispostaPonte(int Stato, string Json);

/// <summary>
/// Il motore: tiene il diario in memoria, lo salva sul disco e fa le poche cose
/// che la pagina non può fare da sola (rete, file, chiave dell'AI).
///
/// Le decisioni sul cibo e sull'allenamento NON stanno qui: stanno nella pagina
/// (ui/logica.js), portate dall'app Android e provate con gli stessi test.
/// </summary>
public sealed class Motore : IDisposable
{
    private readonly Percorsi percorsi;
    private readonly object serratura = new();
    private JsonObject dati;
    private long versione;

    public Motore(Percorsi percorsi)
    {
        this.percorsi = percorsi;
        Impostazioni = Impostazioni.Leggi(percorsi.Impostazioni);
        dati = Carica();
    }

    public Impostazioni Impostazioni { get; }

    /// <summary>Cambia a ogni salvataggio: la pagina se ne accorge se qualcun altro ha scritto.</summary>
    public long VersioneDati => Interlocked.Read(ref versione);

    private JsonObject Carica()
    {
        var testoPrincipale = Archivio.LeggiTesto(percorsi.Dati);
        var esito = SceltaDati.Scegli(testoPrincipale, Archivio.LeggiTesto(percorsi.DatiCopia), Diario.Nuovo);

        if (esito.PrincipaleRovinato && new FileInfo(percorsi.Dati).Length > 0)
        {
            // Un diario che non si legge si mette da parte intero: un errore del
            // programma non deve mai diventare la perdita dei dati.
            try
            {
                Archivio.Copia(percorsi.Dati, percorsi.DiarioRovinato(DateTimeOffset.Now));
                Log.Errore("diario illeggibile messo da parte");
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException)
            {
                Log.Errore("diario illeggibile non messo da parte", e);
            }
        }

        var prima = esito.Dati.ToJsonString();
        var sistemato = Diario.Sistema(esito.Dati);
        if (esito.Da != SceltaDati.Fonte.Principale || sistemato.ToJsonString() != prima)
        {
            ScriviSulDisco(sistemato);
        }
        else if (testoPrincipale != null)
        {
            // Il file letto bene diventa la copia di sicurezza per il prossimo avvio.
            try { Archivio.ScriviTesto(percorsi.DatiCopia, testoPrincipale); }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException) { Log.Errore("copia di sicurezza non scritta", e); }
        }

        Log.Info($"diario aperto da {esito.Da}, {Json.Lista(sistemato["entries"]).Count} voci");
        return sistemato;
    }

    /// <summary>Una copia staccata del diario: la pagina la modifica e la rimanda intera.</summary>
    public JsonObject Dati()
    {
        lock (serratura) return (JsonObject)dati.DeepClone();
    }

    public JsonObject Stato()
    {
        lock (serratura)
        {
            return new JsonObject
            {
                ["versioneProgramma"] = Surplus.Versione.Nome,
                ["versione"] = VersioneDati,
                ["dati"] = dati.DeepClone(),
                ["impostazioni"] = Impostazioni.PerLaPagina(),
            };
        }
    }

    /// <summary>Salva quello che manda la pagina, dopo i controlli che impediscono di svuotare il diario.</summary>
    public JsonObject Salva(JsonObject? nuovo)
    {
        lock (serratura)
        {
            if (!Diario.Accettabile(nuovo, dati, out var motivo))
            {
                Log.Errore("salvataggio rifiutato: " + motivo);
                return new JsonObject { ["errore"] = motivo, ["codice"] = "non_valido" };
            }
            var sistemato = Diario.Sistema(nuovo!);
            try
            {
                ScriviSulDisco(sistemato);
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException)
            {
                Log.Errore("diario non salvato", e);
                return new JsonObject { ["errore"] = "Il diario non si è salvato sul disco. Riprova.", ["codice"] = "disco" };
            }
            dati = sistemato;
            Interlocked.Increment(ref versione);
            DopoSalvataggio?.Invoke();
            return new JsonObject { ["ok"] = true, ["versione"] = VersioneDati };
        }
    }

    private void ScriviSulDisco(JsonObject d) => Archivio.ScriviTesto(percorsi.Dati, Json.Scrivi(d));

    public JsonObject CambiaImpostazioni(JsonObject o)
    {
        lock (serratura)
        {
            Impostazioni.Applica(o);
            try
            {
                Impostazioni.Salva(percorsi.Impostazioni);
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException)
            {
                Log.Errore("impostazioni non salvate", e);
                return new JsonObject { ["errore"] = "Le impostazioni non si sono salvate. Riprova.", ["codice"] = "disco" };
            }
            ApplicaAvvioLogin?.Invoke(Impostazioni.AvvioAlLogin);
            return new JsonObject { ["ok"] = true, ["impostazioni"] = Impostazioni.PerLaPagina() };
        }
    }

    /// <summary>
    /// Cambia le impostazioni da dentro il programma (non dalla pagina) e le salva:
    /// serve all'abbinamento col NAS, che scrive l'indirizzo e il permesso.
    /// </summary>
    public bool AggiornaImpostazioni(Action<Impostazioni> cambia)
    {
        lock (serratura)
        {
            cambia(Impostazioni);
            try
            {
                Impostazioni.Salva(percorsi.Impostazioni);
                return true;
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException)
            {
                Log.Errore("impostazioni non salvate", e);
                return false;
            }
        }
    }

    /// <summary>Chiamato dopo ogni salvataggio riuscito: lo usa la sincronizzazione col NAS.</summary>
    public Action? DopoSalvataggio { get; set; }

    /// <summary>Scrive o toglie la voce di avvio automatico (solo quando gira dal pacchetto).</summary>
    public Action<bool>? ApplicaAvvioLogin { get; set; }

    public void Dispose()
    {
    }
}
