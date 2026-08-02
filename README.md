# Surplus

![Piattaforma](https://img.shields.io/badge/piattaforma-Android%208.0%2B-3DDC84?logo=android&logoColor=white)
![Versione](https://img.shields.io/badge/versione-2.2.0-14532D)
![Privacy](https://img.shields.io/badge/dati-solo%20sul%20telefono-blue)

**App Android per tracciare l'alimentazione e gli allenamenti in fase di surplus calorico** (massa muscolare pulita). Pensata per essere veloce e usabile ogni giorno, non un tracker complesso da nutrizionista: la costanza conta più della precisione al grammo.

Nessun account, nessuna pubblicità, nessun abbonamento. I dati restano sul telefono.

## Cosa fa

- **Diario alimentare** per momenti della giornata (colazione, spuntini, pranzo, post-allenamento, cena) con quantità in grammi/ml obbligatorie e valori nutrizionali facoltativi.
- **Consigli automatici**, deterministici e sempre spiegabili: confrontano quello che hai mangiato con il tuo piano e suggeriscono cosa aggiungere, mai cosa "evitare" — nessun cibo viene mai bollato come cattivo.
- **Scanner del codice a barre**: inquadra il prodotto e i valori si compilano da soli, cercando su due database alimentari pubblici (Open Food Facts e USDA FoodData Central) in cascata.
- **Scheda del prodotto** in stile Yuka dopo ogni inserimento: energia, proteine, fibre, grassi, zuccheri, sale e grado di lavorazione — con giudizi tarati sulla massa (un cibo calorico è un pregio, non un difetto).
- **Sezione allenamento**: sessioni Spinta/Trazione, storico "ultima volta" per ogni esercizio, avviso di progressione quando è il momento di aumentare il carico.
- **Promemoria** solo per gli spuntini (mai per i pasti principali), orari e giorni scelti liberamente, con modalità dinamica legata alla fine dell'allenamento.
- **Peso settimanale** con la regola: se dopo 3-4 settimane il peso non si muove, l'app te lo dice.
- **Backup** su file, quando vuoi, dove vuoi.

## Download

Vai alla sezione **[Releases](../../releases)** di questo repository e scarica l'ultimo file `.apk`.

Non essendo distribuita tramite Play Store, Android chiederà di **consentire l'installazione da questa app** (di solito il browser): è normale per le app fuori dagli store ufficiali, va confermato una volta sola.

Gli aggiornamenti si installano sopra la versione precedente, senza perdere i dati.

## Privacy

Surplus funziona interamente offline. L'unica eccezione è la ricerca facoltativa di un prodotto (per nome o codice a barre): in quel caso parte una richiesta ai database alimentari pubblici citati sopra, contenente solo il testo cercato o il codice del prodotto — **nessun dato personale, nessuna traccia dell'utente**. Tutto il resto (diario, pesate, allenamenti, impostazioni) resta salvato solo sul telefono.

## Requisiti

Android 8.0 (API 26) o superiore.

## Avvertenza

Surplus è un diario personale, non un servizio medico e non sostituisce il parere di un medico o di un dietista. I target proposti sono stime da formule standard e non tengono conto di storia clinica, allergie o terapie in corso. Se hai meno di 18 anni, parlane con un adulto responsabile e con il tuo medico prima di seguire una dieta ipercalorica.

## Codice sorgente

Questo repository contiene solo la pagina di presentazione e le release compilate: il codice sorgente non è pubblicato. Per segnalazioni o domande, apri una [Issue](../../issues).

## Licenza

Progetto personale, tutti i diritti riservati. L'app è distribuita gratuitamente per uso personale; non è consentito redistribuirla con modifiche o utilizzarla come base per altri progetti senza autorizzazione.
