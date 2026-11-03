"""Il database: SQLite dentro la cartella dei dati.

Nel database non ci sono segreti in chiaro: del codice di abbinamento e del
token del dispositivo si tiene solo l'impronta (SHA-256). Se qualcuno leggesse
il file non potrebbe farsi passare per un dispositivo abbinato.

Il diario sta in una riga sola (`stato`), con un numero di revisione che cresce
a ogni unione. Le versioni vecchie finiscono in `storico/` come file: se una
sincronizzazione andasse storta, il diario di ieri è ancora lì.
"""

from __future__ import annotations

import contextlib
import hashlib
import json
import zlib
import sqlite3
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterator

from .config import REVISIONI_TENUTE, STORICO_MASSIMO, Impostazioni

SCHEMA = [
    """CREATE TABLE IF NOT EXISTS stato (
        id INTEGER PRIMARY KEY CHECK (id = 1),
        dati TEXT NOT NULL,
        revisione INTEGER NOT NULL,
        aggiornato_il TEXT NOT NULL
    )""",
    """CREATE TABLE IF NOT EXISTS dispositivi (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        nome TEXT NOT NULL,
        tipo TEXT NOT NULL,
        token_hash TEXT NOT NULL UNIQUE,
        creato_il TEXT NOT NULL,
        ultimo_contatto TEXT,
        base TEXT,
        base_revisione INTEGER
    )""",
    # Il diario a ogni revisione recente, compresso. Serve a ritrovare la base vera
    # di un dispositivo che non ha tenuto l'ultima risposta (vedi diario_alla_revisione).
    """CREATE TABLE IF NOT EXISTS revisioni (
        revisione INTEGER PRIMARY KEY,
        dati BLOB NOT NULL
    )""",
    """CREATE TABLE IF NOT EXISTS codici_abbinamento (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        codice_hash TEXT NOT NULL,
        creato_il TEXT NOT NULL,
        scade_il TEXT NOT NULL,
        usato_il TEXT,
        annullato_il TEXT
    )""",
    """CREATE TABLE IF NOT EXISTS tentativi_abbinamento (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        ip TEXT NOT NULL,
        il TEXT NOT NULL,
        gruppo TEXT NOT NULL
    )""",
]


def adesso() -> datetime:
    return datetime.now(timezone.utc)


def iso(quando: datetime) -> str:
    return quando.astimezone(timezone.utc).isoformat(timespec="seconds")


def hash_segreto(s: str) -> str:
    return hashlib.sha256(s.encode("utf-8")).hexdigest()


def connetti(percorso: Path) -> sqlite3.Connection:
    percorso.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(percorso, isolation_level=None, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode = WAL")
    conn.execute("PRAGMA synchronous = FULL")
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def init_db(conn: sqlite3.Connection) -> None:
    for sql in SCHEMA:
        conn.execute(sql)
    colonne = {r["name"] for r in conn.execute("PRAGMA table_info(dispositivi)")}
    if "base_dichiarata" not in colonne:
        # Database della 1.0.0: la colonna arriva con la 1.1.0.
        conn.execute("ALTER TABLE dispositivi ADD COLUMN base_dichiarata INTEGER")


@contextlib.contextmanager
def transazione(conn: sqlite3.Connection) -> Iterator[sqlite3.Connection]:
    conn.execute("BEGIN IMMEDIATE")
    try:
        yield conn
    except BaseException:
        conn.execute("ROLLBACK")
        raise
    else:
        conn.execute("COMMIT")


# --- il diario ------------------------------------------------------------------------


def leggi_stato(conn: sqlite3.Connection) -> tuple[dict[str, Any] | None, int]:
    riga = conn.execute("SELECT dati, revisione FROM stato WHERE id = 1").fetchone()
    if riga is None:
        return None, 0
    try:
        return json.loads(riga["dati"]), int(riga["revisione"])
    except (ValueError, TypeError):
        return None, int(riga["revisione"])


def scrivi_stato(conn: sqlite3.Connection, imp: Impostazioni, dati: dict[str, Any]) -> int:
    """Scrive il diario nuovo e restituisce la revisione. Prima mette da parte quello di adesso."""
    testo = json.dumps(dati, ensure_ascii=False, separators=(",", ":"))
    with transazione(conn):
        riga = conn.execute("SELECT dati, revisione FROM stato WHERE id = 1").fetchone()
        revisione = (int(riga["revisione"]) + 1) if riga else 1
        if riga is not None:
            _metti_da_parte(imp, int(riga["revisione"]), riga["dati"])
            _tieni_revisione(conn, int(riga["revisione"]), riga["dati"])
        conn.execute(
            "INSERT INTO stato (id, dati, revisione, aggiornato_il) VALUES (1, ?, ?, ?)"
            " ON CONFLICT(id) DO UPDATE SET dati = excluded.dati, revisione = excluded.revisione,"
            " aggiornato_il = excluded.aggiornato_il",
            (testo, revisione, iso(adesso())),
        )
        _tieni_revisione(conn, revisione, testo)
        _pota_revisioni(conn, revisione)
    return revisione


def _tieni_revisione(conn: sqlite3.Connection, revisione: int, testo: str) -> None:
    conn.execute("INSERT OR IGNORE INTO revisioni (revisione, dati) VALUES (?, ?)",
                 (revisione, zlib.compress(testo.encode("utf-8"))))


def _pota_revisioni(conn: sqlite3.Connection, corrente: int) -> None:
    """Restano le ultime REVISIONI_TENUTE e quelle che un dispositivo potrebbe
    avere ancora come base: chi è stato via una settimana deve ritrovarla."""
    protette = set()
    for r in conn.execute("SELECT base_revisione, base_dichiarata FROM dispositivi"):
        protette.update(v for v in (r["base_revisione"], r["base_dichiarata"]) if v is not None)
    soglia = corrente - REVISIONI_TENUTE
    for (n,) in conn.execute("SELECT revisione FROM revisioni WHERE revisione <= ?", (soglia,)).fetchall():
        if n not in protette:
            conn.execute("DELETE FROM revisioni WHERE revisione = ?", (n,))


def diario_alla_revisione(conn: sqlite3.Connection, revisione: int) -> dict[str, Any] | None:
    """Il diario com'era a quella revisione, o None se non c'è più.

    None vuol dire "base vuota": l'unione allora somma e non cancella niente.
    È il ripiego sicuro: al peggio torna su una voce cancellata, mai il contrario.
    """
    dati, corrente = leggi_stato(conn)
    if revisione == corrente:
        return dati
    riga = conn.execute("SELECT dati FROM revisioni WHERE revisione = ?", (revisione,)).fetchone()
    if riga is None:
        return None
    try:
        return json.loads(zlib.decompress(riga["dati"]).decode("utf-8"))
    except (ValueError, TypeError, zlib.error):
        return None


def _metti_da_parte(imp: Impostazioni, revisione: int, testo: str) -> None:
    try:
        imp.storico.mkdir(parents=True, exist_ok=True)
        (imp.storico / f"diario-{revisione:06d}.json").write_text(testo, encoding="utf-8")
        vecchi = sorted(imp.storico.glob("diario-*.json"))
        for f in vecchi[:-STORICO_MASSIMO]:
            f.unlink(missing_ok=True)
    except OSError:
        # Se la copia non si scrive, la sincronizzazione va avanti: meglio un
        # diario aggiornato senza copia che nessun aggiornamento.
        pass


# --- i dispositivi --------------------------------------------------------------------


def dispositivo_da_token(conn: sqlite3.Connection, token: str) -> sqlite3.Row | None:
    return conn.execute("SELECT * FROM dispositivi WHERE token_hash = ?", (hash_segreto(token),)).fetchone()


def segna_contatto(conn: sqlite3.Connection, id_dispositivo: int) -> None:
    conn.execute("UPDATE dispositivi SET ultimo_contatto = ? WHERE id = ?", (iso(adesso()), id_dispositivo))


def leggi_base(riga: sqlite3.Row | None) -> dict[str, Any] | None:
    if riga is None or not riga["base"]:
        return None
    try:
        return json.loads(riga["base"])
    except (ValueError, TypeError):
        return None


def segna_base_dichiarata(conn: sqlite3.Connection, id_dispositivo: int, revisione: int) -> None:
    conn.execute("UPDATE dispositivi SET base_dichiarata = ? WHERE id = ?", (revisione, id_dispositivo))


def scrivi_base(conn: sqlite3.Connection, id_dispositivo: int, dati: dict[str, Any], revisione: int) -> None:
    conn.execute(
        "UPDATE dispositivi SET base = ?, base_revisione = ?, ultimo_contatto = ? WHERE id = ?",
        (json.dumps(dati, ensure_ascii=False, separators=(",", ":")), revisione, iso(adesso()), id_dispositivo),
    )
