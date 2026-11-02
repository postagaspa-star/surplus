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
    Motore/           stato, impostazioni, salvataggio su disco
    Rete/             archivi alimentari (Open Food Facts, USDA) e chiamate AI
    Interfaccia/      finestra col telefono disegnato, ponte verso la pagina
    Sistema/          istanza unica, avvio all'accesso, chiave cifrata, tema
  ui/                 l'interfaccia vera e propria (HTML, CSS, JavaScript)
    logica.js         consigli, qualità della dieta, regole dell'allenamento
    app.js            schermata Oggi e strumenti comuni
    schermate.js      Allenamento, Storico, Impostazioni
  Surplus.Tests/      prove del programma (xUnit)
  prove/
    js/               prove della logica (node --test)
    banco.js          banco di prova: serve l'interfaccia su http con dati finti
    crea-pacchetto.ps1
```

La pagina parla col programma solo attraverso `https://surplus.locale/locale/*`,
intercettato dentro WebView2: nessuna porta aperta sul computer e niente internet
dentro la finestra. La chiave del servizio AI resta nel programma, cifrata da
Windows: la pagina sa soltanto se c'è o se non c'è.

## I dati

Un unico file JSON, `%LOCALAPPDATA%\Surplus\data-local.json`, **identico a quello
del telefono**: si può spostare da una parte all'altra con l'esporta/importa.

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
