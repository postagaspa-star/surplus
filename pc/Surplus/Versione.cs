namespace Surplus;

/// <summary>
/// La versione del programma per il computer. Segue quella dell'app Android quando
/// condividono una funzione, ma ha una sua numerazione: qui si parte da 1.0.0.
/// </summary>
public static class Versione
{
    public const string Nome = "1.1.4";

    /// <summary>
    /// Il numero che cresce di uno a ogni versione: lo usa l'aggiornamento
    /// automatico per capire se quella sul NAS è più nuova di questa. Va alzato
    /// insieme a <see cref="Nome"/>, e lo stesso numero finisce in versioni.json.
    /// </summary>
    public const int Codice = 6;

    /// <summary>Formato dei dati che questo programma sa leggere e scrivere (lo stesso dell'app).</summary>
    public const int Schema = 4;
}
