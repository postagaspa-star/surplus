# Surplus sul NAS

Il pezzo che tiene insieme il telefono e il computer: un server piccolo, in casa,
che conserva il diario e unisce le due versioni. Niente account, niente servizi
esterni, niente dati fuori dalla rete di casa.

Stessa architettura di Pactum, Sol e Lectio: FastAPI dentro un container Docker
sul NAS Synology, abbinamento con un codice di 8 cifre, segreti salvati solo come
impronta.

## Perché non "l'ultimo che parla vince"

Se il telefono mandasse semplicemente il suo diario e il server lo tenesse,
quello scritto sul computer sparirebbe (e viceversa). Qui si uniscono: il server
ricorda, per ogni dispositivo, il diario su cui si erano trovati d'accordo
l'ultima volta (la **base**), e confronta tre versioni — base, quella del
dispositivo, quella sul server.

| Cosa è successo alla voce | Cosa resta |
|---|---|
| nessuno l'ha toccata | com'era |
| cambiata solo da una parte | la versione cambiata |
| cambiata da tutte e due | vince chi sincronizza adesso (e si conta il conflitto) |
| nuova da una parte | si aggiunge |
| c'era nella base e ora manca da una parte | **è stata cancellata, si toglie** |

L'ultima riga è quella che fa funzionare le cancellazioni: senza la base, "manca"
e "non è mai esistita" sarebbero la stessa cosa, e un pasto cancellato sul
computer tornerebbe su dal telefono per sempre.

Alla **prima** sincronizzazione la base è vuota: tutto è nuovo, niente è
cancellato. Due diari che non si sono mai visti si sommano, e nessuno dei due
perde una riga.

La regola sta tutta in `app/unione.py`, senza rete né database: si può leggere e
si può provare (`tests/test_unione.py`).

### La base la dice il dispositivo (dalla 1.1.0)

Con la 1.0.0 la base la decideva il server: "quello che ti ho appena mandato".
Ma se quella risposta non arriva (il Wi-Fi cade proprio in quel momento) o il
dispositivo non la tiene (hai scritto un pasto mentre il NAS rispondeva), il
dispositivo resta col diario di prima, e alla volta dopo tutto quello che non ha
ancora visto sembrerebbe "cancellato da lui".

Ora ogni dispositivo manda anche `base`: la revisione dell'ultimo diario unito
che ha **davvero** tenuto e scritto sul disco. Il server tiene le ultime 30
revisioni (compresse, tabella `revisioni`) più quelle che qualche dispositivo ha
ancora come base, così anche un telefono rimasto fuori casa una settimana ritrova
la sua. Se una base non c'è più si usa una base vuota: si somma e non si cancella
niente — al peggio torna su una voce cancellata, mai il contrario.

Le app vecchie, che `base` non lo mandano, funzionano come prima. E le
sincronizzazioni passano una alla volta: se telefono e computer arrivano
insieme, il secondo si unisce al diario già unito del primo.

## Com'è fatto

```
server/
  app/
    main.py       mette su l'app
    api.py        le rotte (poche)
    unione.py     la regola dell'unione (logica pura, provata)
    abbina.py     i codici di abbinamento e i tentativi sbagliati
    db.py         SQLite + le copie di sicurezza in storico/
    config.py     tutto da variabili d'ambiente
  tests/          pytest
  Dockerfile, docker-compose.yml
```

Rotte:

| Rotta | A cosa serve |
|---|---|
| `GET /api/salute` | sta in piedi? cosa c'è nel diario? |
| `POST /api/abbina` | codice di 8 cifre → permesso per il dispositivo |
| `GET /api/diario` | il diario del NAS (un dispositivo nuovo) |
| `POST /api/sincronizza` | mandi il tuo (e la tua base), ti torna quello unito con la sua revisione |
| `GET /api/versione` | che versione delle app c'è da scaricare |
| `GET /scarica/<file>` | il pacchetto del computer, la sua firma, l'app Android |

Solo abbinamento e versione sono pubblici. Tutto il resto vuole
`Authorization: Bearer <token>`.

## Sicurezza, in concreto

- Nel database non c'è niente in chiaro: del codice e del token resta solo
  l'impronta SHA-256.
- 10 codici sbagliati in un quarto d'ora bloccano i tentativi e annullano i
  codici aperti. I tentativi si contano separati fra "casa" e "fuori": chi prova
  da internet non riesce a bloccare l'abbinamento fatto in casa.
- Un diario più grande di 8 MB viene rifiutato.
- A ogni unione la versione di prima finisce in `dati/storico/` (ne restano 30).
- Il pacchetto del computer si installa solo se ha la **firma** di Andrea (ECDSA
  P-256): la chiave privata non sta né qui né nel repository. L'app Android non
  ne ha bisogno, perché Android accetta solo aggiornamenti firmati con la stessa
  chiave dell'app già installata.

## Metterlo in piedi

Sul NAS, dentro `/volume1/docker/surplus-nas`:

```
docker compose up -d --build
```

Dati in `/volume1/docker/surplus-nas/dati`, pacchetti da scaricare in
`/volume1/docker/surplus-nas/pacchetti`. Risponde sulla rete locale, porta 8600.

Per abbinare un apparecchio serve un codice:

```
docker exec surplus python -m app.abbina
```

Vale un quarto d'ora e una volta sola.

Per usarlo anche fuori casa serve un contenitore Tailscale come quelli di Pactum
e Sol (e una chiave di Tailscale): oggi non c'è, il server risponde solo in casa.

## Lavorarci

```
pytest -q                     le prove (non serve il NAS: tutto in memoria)
SURPLUS_DATI=./dati SURPLUS_PACCHETTI=./pacchetti uvicorn app.main:app --reload
```
