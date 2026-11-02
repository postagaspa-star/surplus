"""Abbinamento dei dispositivi, con la stessa logica di Pactum, Sol e Lectio.

Dal NAS:  docker exec surplus python -m app.abbina
stampa un codice di 8 cifre, valido 15 minuti, buono una volta sola. Un codice
nuovo annulla quelli ancora aperti. Nel database c'è solo l'impronta del codice.

Contro chi prova i codici a caso: 10 tentativi sbagliati in 15 minuti fanno
rispondere 429 e annullano i codici aperti. I tentativi si contano in due gruppi
separati, "casa" e "fuori": chi prova da internet non riesce a bloccare
l'abbinamento fatto in casa.
"""

from __future__ import annotations

import ipaddress
import secrets
import sqlite3
from datetime import datetime, timedelta

from .config import (
    CIFRE_CODICE,
    DURATA_CODICE_MINUTI,
    FINESTRA_TENTATIVI_MINUTI,
    TENTATIVI_SBAGLIATI_MASSIMI,
    assicura_cartelle,
    carica,
)
from .db import adesso, connetti, hash_segreto, init_db, iso, transazione

BLOCCATO = "bloccato"
TIPI = ("computer", "telefono")


def gruppo_di(ip: str) -> str:
    """"casa" per gli indirizzi della rete locale, "fuori" per tutti gli altri."""
    try:
        indirizzo = ipaddress.ip_address(ip)
    except ValueError:
        return "fuori"
    return "casa" if indirizzo.is_private or indirizzo.is_loopback else "fuori"


def nuovo_codice(conn: sqlite3.Connection, ora: datetime | None = None) -> tuple[str, datetime]:
    ora = ora or adesso()
    codice = f"{secrets.randbelow(10 ** CIFRE_CODICE):0{CIFRE_CODICE}d}"
    scade = ora + timedelta(minutes=DURATA_CODICE_MINUTI)
    with transazione(conn):
        conn.execute(
            "UPDATE codici_abbinamento SET annullato_il = ? WHERE usato_il IS NULL AND annullato_il IS NULL",
            (iso(ora),),
        )
        conn.execute(
            "INSERT INTO codici_abbinamento (codice_hash, creato_il, scade_il) VALUES (?, ?, ?)",
            (hash_segreto(codice), iso(ora), iso(scade)),
        )
    return codice, scade


def _sbagli_recenti(conn: sqlite3.Connection, ora: datetime, gruppo: str) -> int:
    dal = iso(ora - timedelta(minutes=FINESTRA_TENTATIVI_MINUTI))
    return conn.execute(
        "SELECT COUNT(*) AS n FROM tentativi_abbinamento WHERE il > ? AND gruppo = ?", (dal, gruppo)
    ).fetchone()["n"]


def abbina(conn: sqlite3.Connection, codice: str, nome: str, tipo: str, ora: datetime,
           ip: str = "sconosciuto", gruppo: str = "casa") -> tuple[str, int] | str | None:
    """Consuma il codice e crea il dispositivo -> (token, id).

    BLOCCATO se ci sono stati troppi tentativi sbagliati; None se il codice è
    sbagliato, scaduto o già usato (per chi prova sono la stessa cosa).
    """
    with transazione(conn):
        if _sbagli_recenti(conn, ora, gruppo) >= TENTATIVI_SBAGLIATI_MASSIMI:
            return BLOCCATO
        riga = conn.execute(
            "SELECT id FROM codici_abbinamento WHERE codice_hash = ? AND usato_il IS NULL"
            " AND annullato_il IS NULL AND scade_il > ?",
            (hash_segreto(codice), iso(ora)),
        ).fetchone()
        if riga is None:
            conn.execute("INSERT INTO tentativi_abbinamento (ip, il, gruppo) VALUES (?, ?, ?)",
                         (ip, iso(ora), gruppo))
            if _sbagli_recenti(conn, ora, gruppo) >= TENTATIVI_SBAGLIATI_MASSIMI:
                conn.execute(
                    "UPDATE codici_abbinamento SET annullato_il = ? WHERE usato_il IS NULL AND annullato_il IS NULL",
                    (iso(ora),),
                )
            return None
        conn.execute("UPDATE codici_abbinamento SET usato_il = ? WHERE id = ?", (iso(ora), riga["id"]))
        token = secrets.token_urlsafe(32)
        cur = conn.execute(
            "INSERT INTO dispositivi (nome, tipo, token_hash, creato_il) VALUES (?, ?, ?, ?)",
            (nome[:60] or "senza nome", tipo if tipo in TIPI else "computer", hash_segreto(token), iso(ora)),
        )
        return token, int(cur.lastrowid or 0)


def _da_riga_di_comando() -> None:
    imp = carica()
    assicura_cartelle(imp)
    conn = connetti(imp.database)
    init_db(conn)
    codice, scade = nuovo_codice(conn)
    print()
    print("  Codice per abbinare un dispositivo a Surplus:  " + codice)
    print("  Vale 15 minuti e una volta sola (scade alle " + scade.astimezone().strftime("%H:%M") + ").")
    print()


if __name__ == "__main__":
    _da_riga_di_comando()
