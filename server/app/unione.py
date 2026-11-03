"""Unione dei diari: due dispositivi, nessuno dei due padrone.

Il problema vero: il telefono e il computer scrivono lo stesso diario in momenti
diversi, a volte senza rete. Se l'ultimo che parla vince, chi ha scritto prima
perde tutto quello che ha aggiunto. Quindi non si sovrascrive: si uniscono.

Come. Per ogni dispositivo il server ricorda il diario su cui le due parti si
erano trovate d'accordo l'ultima volta (la "base"). Quando arriva un diario
nuovo si guardano tre versioni: la base, quella del dispositivo e quella del
server. Per ogni voce:

  - uguali da tutte e due le parti        -> resta com'è
  - cambiata solo da una parte            -> vince quella parte
  - cambiata da entrambe                  -> vince chi sta sincronizzando adesso
  - nuova da una parte                    -> si aggiunge
  - c'era nella base e ora manca da una parte -> è stata cancellata, si toglie

L'ultimo punto è quello che fa funzionare le cancellazioni: senza la base,
"manca" e "non è mai esistita" sarebbero la stessa cosa e un pasto cancellato
sul computer tornerebbe su dal telefono per sempre.

Alla prima sincronizzazione la base è vuota: tutto è "nuovo", niente è
"cancellato". Due diari che non si sono mai visti si sommano, e nessuno dei due
perde una riga.

Qui dentro non ci sono né rete né database: solo dizionari. Così si può provare.
"""

from __future__ import annotations

from typing import Any

# Elenchi di oggetti con un id: la chiave con cui si riconoscono.
ELENCHI_CON_ID: dict[str, str] = {
    "entries": "id",
    "customFoods": "id",
    "workouts": "id",
    "exercises": "id",
    "routines": "id",
    "customExercises": "id",
    # I pesi sono uno al giorno: la chiave è il giorno.
    "weights": "date",
}

# Mappe giorno -> valore.
MAPPE = ("waterByDate",)

# Campi singoli: si tengono con la stessa regola a tre versioni.
SEMPLICI = ("schedeInizializzate",)


def _per_chiave(elenco: Any, chiave: str) -> dict[str, Any]:
    """Elenco -> {chiave: oggetto}. Le voci senza chiave si scartano (non si
    saprebbe riconoscerle da una sincronizzazione all'altra)."""
    fuori: dict[str, Any] = {}
    if not isinstance(elenco, list):
        return fuori
    for v in elenco:
        if not isinstance(v, dict):
            continue
        k = v.get(chiave)
        if isinstance(k, str) and k:
            fuori[k] = v
    return fuori


def _unisci_insieme(base: dict[str, Any], mio: dict[str, Any], suo: dict[str, Any],
                    conflitti: list[str], etichetta: str) -> dict[str, Any]:
    """Le tre regole, applicate a chiavi -> valore."""
    fuori: dict[str, Any] = {}
    # sorted() e non set(): l'ordine di un insieme in Python cambia da una volta
    # all'altra, e il risultato uscirebbe con le chiavi mescolate ogni volta. Il
    # contenuto sarebbe lo stesso, ma chi riceve vedrebbe un file diverso e lo
    # riscriverebbe sul disco a ogni sincronizzazione.
    for k in sorted(set(base) | set(mio) | set(suo)):
        in_base, in_mio, in_suo = k in base, k in mio, k in suo
        if in_mio and in_suo:
            if mio[k] == suo[k]:
                fuori[k] = mio[k]
            elif in_base and base[k] == suo[k]:
                fuori[k] = mio[k]          # l'ha cambiata solo il dispositivo
            elif in_base and base[k] == mio[k]:
                fuori[k] = suo[k]          # l'ha cambiata solo il server
            else:
                fuori[k] = mio[k]          # entrambi: vince chi sincronizza adesso
                conflitti.append(f"{etichetta}:{k}")
            continue
        if in_mio:
            # Se c'era nella base, il server l'ha cancellata: si rispetta.
            if not in_base:
                fuori[k] = mio[k]
            continue
        if in_suo:
            # Se c'era nella base, il dispositivo l'ha cancellata: si rispetta.
            if not in_base:
                fuori[k] = suo[k]
    return fuori


def _ordina_voci(voci: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Diario in ordine di giorno e poi di orario: le due app lo mostrano così."""
    return sorted(voci, key=lambda v: (str(v.get("date") or ""), v.get("createdAt") or 0, str(v.get("id") or "")))


def unisci(base: dict[str, Any] | None, mio: dict[str, Any] | None,
           suo: dict[str, Any] | None) -> tuple[dict[str, Any], list[str]]:
    """Restituisce (diario unito, elenco dei conflitti risolti a favore di `mio`).

    `mio` è il diario che arriva dal dispositivo, `suo` quello che il server ha
    adesso, `base` quello su cui si erano trovati d'accordo l'ultima volta.
    """
    base = base if isinstance(base, dict) else {}
    mio = mio if isinstance(mio, dict) else {}
    suo = suo if isinstance(suo, dict) else {}
    conflitti: list[str] = []

    unito: dict[str, Any] = {}

    # La versione dello schema non torna mai indietro.
    versioni = [x.get("schemaVersion") for x in (mio, suo) if isinstance(x.get("schemaVersion"), int)]
    if versioni:
        unito["schemaVersion"] = max(versioni)

    # Il profilo campo per campo: cambiare il peso sul computer non deve
    # riportare indietro l'altezza scritta sul telefono.
    profilo_base = base.get("profile") if isinstance(base.get("profile"), dict) else {}
    profilo_mio = mio.get("profile") if isinstance(mio.get("profile"), dict) else {}
    profilo_suo = suo.get("profile") if isinstance(suo.get("profile"), dict) else {}
    if not profilo_base and profilo_suo.get("isConfigured") and not profilo_mio.get("isConfigured"):
        # Un apparecchio nuovo alla prima sincronizzazione: il suo profilo è quello
        # vuoto di fabbrica. Senza base "vince chi sincronizza", e il profilo vero
        # del NAS (età, peso, obiettivi) verrebbe azzerato. Quello vuoto non conta.
        profilo_mio = profilo_suo
    unito["profile"] = _unisci_insieme(profilo_base, profilo_mio, profilo_suo, conflitti, "profile")

    for nome, chiave in ELENCHI_CON_ID.items():
        unione = _unisci_insieme(
            _per_chiave(base.get(nome), chiave),
            _per_chiave(mio.get(nome), chiave),
            _per_chiave(suo.get(nome), chiave),
            conflitti, nome)
        voci = list(unione.values())
        unito[nome] = _ordina_voci(voci) if nome == "entries" else sorted(
            voci, key=lambda v: str(v.get(chiave) or ""))

    for nome in MAPPE:
        unito[nome] = _unisci_insieme(
            base.get(nome) if isinstance(base.get(nome), dict) else {},
            mio.get(nome) if isinstance(mio.get(nome), dict) else {},
            suo.get(nome) if isinstance(suo.get(nome), dict) else {},
            conflitti, nome)

    for nome in SEMPLICI:
        # Una volta accese, queste bandierine restano accese.
        unito[nome] = bool(mio.get(nome)) or bool(suo.get(nome))

    # Campi che non conosciamo (una versione nuova delle app): si tengono, con
    # la stessa regola, così un server vecchio non cancella roba nuova.
    noti = set(unito) | {"schemaVersion"}
    for nome in set(mio) | set(suo):
        if nome in noti:
            continue
        in_base, in_mio, in_suo = nome in base, nome in mio, nome in suo
        if in_mio and (not in_base or base.get(nome) != mio.get(nome) or not in_suo):
            unito[nome] = mio[nome]
        elif in_suo and not (in_base and base.get(nome) == suo.get(nome) and not in_mio):
            unito[nome] = suo[nome]

    return unito, conflitti


def conta(diario: dict[str, Any] | None) -> dict[str, int]:
    """Due numeri per i log e per la pagina: quanto c'è dentro."""
    d = diario if isinstance(diario, dict) else {}
    fuori = {nome: len(d.get(nome) or []) for nome in ELENCHI_CON_ID}
    fuori["waterByDate"] = len(d.get("waterByDate") or {})
    fuori["giorni"] = len({str(v.get("date")) for v in (d.get("entries") or []) if isinstance(v, dict) and v.get("date")})
    return fuori
