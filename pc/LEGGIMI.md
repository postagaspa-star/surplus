# Surplus per il computer

La stessa app del telefono, su Windows: stesse schermate, stessi colori, stesso
file del diario. La finestra ha la forma di un telefono, come il simulatore
Android, perché l'interfaccia è nata per uno schermo stretto e verticale.

## Com'è fatto

Stessa architettura di Pactum e Sol:

```
pc/
  Surplus/            il programma (.NET 8, WinForms + WebView2)
    Nucleo/           diario, salvataggio sicuro, migrazione, semi   (logica pura, testata)
    Motore/           stato, impostazioni, sincronizzazione col NAS, aggiornamento
    Rete/             archivi alimentari (Open Food Facts, USDA), AI, client del NAS
    Interfaccia/      finestra col telefono disegnato, ponte verso la pagina
    Sistema/          istanza unica, avvio all'accesso, chiave cifrata, tema,
                      sostituzione del programma durante l'aggiornamento
  ui/                 l'interfaccia vera e propria (HTML, CSS, JavaScript)
    logica.js         consigli, qualità della dieta, regole dell'allenamento
    app.js            schermata Oggi e strumenti comuni
    schermate.js      Allenamento, Storico, Impostazioni
    caratteri/        Roboto, lo stesso carattere del telefono (43 KB, Apache 2.0)
  Surplus.Tests/      prove del programma (xUnit)
  strumenti/
    recupera-dal-telefono.js   rimette insieme il diario dalle esportazioni
  prove/
    js/               prove della logica (node --test)
    banco.js          banco di prova: serve l'interfaccia su http con dati finti
    crea-pacchetto.ps1
```

## Il diario sul NAS

Dalle impostazioni si abbina il computer al NAS di casa (vedi `server/`): da quel
momento il diario sta in tutte e due le parti e si allinea da solo all'avvio,
qualche secondo dopo ogni modifica, ogni dieci minuti e alla chiusura.

Il file sul disco resta la verità per questo computer: si lavora anche col NAS
spento, e quello che hai scritto parte alla prima occasione. Il NAS non comanda,
unisce: nessuna delle due parti sovrascrive l'altra e una voce cancellata resta
cancellata. Il diario che torna dal NAS passa dagli stessi controlli di un
salvataggio normale: se togliesse più di metà delle voci non viene accettato.

Il permesso dato dal NAS è cifrato da Windows come la chiave dell'AI, e alla
pagina non arriva mai.

Per abbinare senza aprire la finestra (per esempio da remoto):

```
Surplus.exe --abbina 192.168.1.51:8600 --codice 12345678
```

0 = fatto, 3 = non riuscito (il motivo è nel log), 4 = c'è già un Surplus aperto.

## L'aggiornamento si fa da solo

Mezzo minuto dopo l'avvio, e poi ogni sei ore, il programma chiede al NAS se c'è
una versione nuova. Se c'è la scarica in sottofondo e ne controlla dimensione,
impronta e **firma** (ECDSA P-256: la chiave privata sta solo sul computer di
Andrea, in `surplus-keys`, e non è nel repository né sul NAS). Si installa quando
chiudi Surplus, perché Windows non lascia sostituire un programma che sta
girando: il Surplus nuovo aspetta che il vecchio sia chiuso, copia i suoi file
mettendo da parte i vecchi, e se qualcosa va storto rimette tutto com'era.

Pubblicare una versione nuova: alza `Nome` e `Codice` in `Surplus/Versione.cs`
(e `<Version>` nel `.csproj`, che lo script controlla), poi

```
powershell -File pc/prove/crea-pacchetto.ps1 -Pubblica -Nas <indirizzo> -UtenteNas <nome>
```

(l'indirizzo e l'utente del NAS non stanno nel repository: si passano così, o
una volta per tutte con le variabili d'ambiente `SURPLUS_NAS` e
`SURPLUS_NAS_UTENTE`.)

La pagina parla col programma solo attraverso `https://surplus.locale/locale/*`,
intercettato dentro WebView2: nessuna porta aperta sul computer e niente internet
dentro la finestra. La chiave del servizio AI resta nel programma, cifrata da
Windows: la pagina sa soltanto se c'è o se non c'è.

## I dati

Un unico file JSON, `%LOCALAPPDATA%\Surplus\data-local.json`, **identico a quello
del telefono**: si può spostare da una parte all'altra con l'esporta/importa, o
tenere allineato da solo col NAS.

Il salvataggio è quello corretto nella 2.5.1 dell'app: si scrive in un file a
parte, si forza sul disco, e solo allora prende il posto del vecchio. A ogni
avvio resta una copia dell'ultimo diario buono (`data-local.prev.json`); se il
file principale si rovina, il programma riparte da quella.

## Lavorarci

```
node pc/prove/banco.js                 interfaccia su http://127.0.0.1:8777 con dati finti
node --test "pc/prove/js/*.test.js"    prove della logica (la sola cartella non basta: su
                                       Windows node prova a caricarla come modulo e fallisce)
dotnet test pc/Surplus.Tests           prove del programma
dotnet run --project pc/Surplus -- --no-installa --dati %TEMP%\surplus-prova
powershell -File pc/prove/crea-pacchetto.ps1    costruisce pc/dist/surplus-pc.zip
```

Il pacchetto è self-contained a cartella: si scompatta dove si vuole e si avvia
`Surplus.exe`, senza installazione e senza amministratore.

## Recuperare il diario da un telefono che non si accende

Il file vero sta nella memoria privata dell'app: su un telefono senza root nessun
backup lo copia. Quello che resta sono le esportazioni finite in Download. Lo
strumento le rimette insieme in un diario solo:

```
node pc/strumenti/recupera-dal-telefono.js --json <export.json> --csv <diario.csv> --uscita data-local.json
```

Il JSON è la base (profilo, dispensa, allenamenti, acqua, pesi, voci con id e
orario); dal CSV si aggiungono solo le voci che nel JSON non ci sono, contate per
giorno, pasto, nome e grammi. Le voci recuperate non hanno l'orario, e le due app
lo sanno: non mostrano l'ora quando non c'è. Niente orari inventati.
