"""Le rotte del server. Poche, e tutte con una cosa sola da fare.

  GET  /api/salute          sta in piedi? cosa c'è nel diario?
  POST /api/abbina          codice di 8 cifre -> token del dispositivo
  GET  /api/diario          il diario del NAS (serve a un dispositivo nuovo)
  POST /api/sincronizza     manda il tuo diario, ti torna quello unito
  GET  /api/versione        quale versione delle app c'è da scaricare
  GET  /scarica/<file>      il pacchetto del computer, la sua firma, l'app Android

L'abbinamento e la versione sono pubblici (senza token non si combina niente:
uno è protetto dal codice, l'altro dice solo dei numeri). Tutto il resto vuole
l'intestazione `Authorization: Bearer <token>`.
"""

from __future__ import annotations

import hashlib
import json
import sqlite3
from pathlib import Path
from typing import Any

from fastapi import APIRouter, Depends, Header, HTTPException, Request
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field

from . import abbina as modulo_abbina
from . import unione
from .config import MASSIMO_BYTE_DIARIO, VERSIONE, Impostazioni
from .db import (
    adesso,
    dispositivo_da_token,
    leggi_base,
    leggi_stato,
    scrivi_base,
    scrivi_stato,
    segna_contatto,
)

router = APIRouter()

SENZA_CACHE = {"Cache-Control": "no-store"}

FILE_SCARICABILI = {
    "surplus-pc.zip": "application/zip",
    "surplus-pc.zip.sig": "text/plain",
    "surplus.apk": "application/vnd.android.package-archive",
}

# Impronte già calcolate: lo zip del computer è grosso, non si ricalcola a ogni domanda.
_impronte: dict[tuple[str, int, int], str] = {}


class AbbinaIn(BaseModel):
    codice: str = Field(min_length=1, max_length=20)
    nome: str = Field(default="", max_length=60)
    tipo: str = Field(default="computer", max_length=20)


class SincronizzaIn(BaseModel):
    dati: dict[str, Any]


def _imp(request: Request) -> Impostazioni:
    return request.app.state.impostazioni


def _conn(request: Request) -> sqlite3.Connection:
    return request.app.state.conn


def dispositivo(request: Request, authorization: str = Header(default="")) -> sqlite3.Row:
    """Il dispositivo che sta parlando, o 401."""
    token = authorization[7:].strip() if authorization.lower().startswith("bearer ") else ""
    riga = dispositivo_da_token(_conn(request), token) if token else None
    if riga is None:
        raise HTTPException(401, "Questo dispositivo non è abbinato. Chiedi un codice nuovo e riprova.")
    return riga


@router.get("/api/salute")
def salute(request: Request) -> dict[str, Any]:
    conn = _conn(request)
    dati, revisione = leggi_stato(conn)
    quanti = conn.execute("SELECT COUNT(*) AS n FROM dispositivi").fetchone()["n"]
    return {
        "ok": True,
        "versione": VERSIONE,
        "revisione": revisione,
        "dispositivi": quanti,
        "diario": unione.conta(dati),
    }


@router.post("/api/abbina")
def abbina_dispositivo(corpo: AbbinaIn, request: Request) -> dict[str, Any]:
    ip = request.client.host if request.client else "sconosciuto"
    esito = modulo_abbina.abbina(
        _conn(request), corpo.codice.strip(), corpo.nome.strip(), corpo.tipo, adesso(), ip,
        modulo_abbina.gruppo_di(ip))
    if esito == modulo_abbina.BLOCCATO:
        raise HTTPException(429, "Troppi codici sbagliati. Aspetta un quarto d'ora e chiedi un codice nuovo.")
    if esito is None or isinstance(esito, str):
        raise HTTPException(401, "Il codice è sbagliato o scaduto. Chiedine uno nuovo.")
    token, id_dispositivo = esito
    return {"ok": True, "token": token, "id": id_dispositivo}


@router.get("/api/diario")
def diario(request: Request, chi: sqlite3.Row = Depends(dispositivo)) -> dict[str, Any]:
    conn = _conn(request)
    dati, revisione = leggi_stato(conn)
    segna_contatto(conn, int(chi["id"]))
    return {"ok": True, "revisione": revisione, "dati": dati, "diario": unione.conta(dati)}


@router.post("/api/sincronizza")
def sincronizza(corpo: SincronizzaIn, request: Request,
                chi: sqlite3.Row = Depends(dispositivo)) -> dict[str, Any]:
    conn = _conn(request)
    imp = _imp(request)
    mio = corpo.dati
    if len(json.dumps(mio, ensure_ascii=False)) > MASSIMO_BYTE_DIARIO:
        raise HTTPException(413, "Il diario è troppo grande: qualcosa non torna, non l'ho salvato.")
    suo, revisione = leggi_stato(conn)
    base = leggi_base(chi)
    unito, conflitti = unione.unisci(base, mio, suo)
    if suo is not None and unito == suo:
        # Niente di nuovo da nessuna delle due parti: non si tocca la revisione.
        scrivi_base(conn, int(chi["id"]), unito, revisione)
        return {"ok": True, "revisione": revisione, "dati": unito, "cambiato": False,
                "conflitti": conflitti, "diario": unione.conta(unito)}
    nuova = scrivi_stato(conn, imp, unito)
    scrivi_base(conn, int(chi["id"]), unito, nuova)
    return {"ok": True, "revisione": nuova, "dati": unito, "cambiato": True,
            "conflitti": conflitti, "diario": unione.conta(unito)}


def _impronta(percorso: Path) -> str | None:
    try:
        st = percorso.stat()
    except OSError:
        return None
    chiave = (str(percorso), int(st.st_mtime), int(st.st_size))
    if chiave in _impronte:
        return _impronte[chiave]
    h = hashlib.sha256()
    with percorso.open("rb") as f:
        for pezzo in iter(lambda: f.read(1024 * 1024), b""):
            h.update(pezzo)
    _impronte.clear()
    _impronte[chiave] = h.hexdigest()
    return _impronte[chiave]


@router.get("/api/versione")
def versione(request: Request) -> dict[str, Any]:
    """Cosa c'è da scaricare. Impronta e dimensione si leggono dai file veri,
    così non possono dire una cosa diversa da quello che si scarica."""
    imp = _imp(request)
    fuori: dict[str, Any] = {"ok": True, "server": VERSIONE}
    file_versioni = imp.cartella_pacchetti / "versioni.json"
    if not file_versioni.is_file():
        file_versioni = Path(__file__).with_name("versioni.json")
    try:
        dichiarato = json.loads(file_versioni.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        dichiarato = {}
    for parte, nome_file in (("pc", "surplus-pc.zip"), ("android", "surplus.apk")):
        voce = dichiarato.get(parte)
        if not isinstance(voce, dict) or not isinstance(voce.get("codice"), int):
            continue
        percorso = imp.cartella_pacchetti / nome_file
        if not percorso.is_file():
            continue
        impronta = _impronta(percorso)
        if impronta is None:
            continue
        firma = (imp.cartella_pacchetti / (nome_file + ".sig")).is_file()
        fuori[parte] = {
            "codice": voce["codice"],
            "nome": str(voce.get("nome") or voce["codice"]),
            "sha256": impronta,
            "byte": percorso.stat().st_size,
            "firma": firma,
        }
    if isinstance(dichiarato.get("nota"), str):
        fuori["nota"] = dichiarato["nota"]
    return fuori


@router.get("/scarica/{nome_file}")
def scarica(nome_file: str, request: Request) -> FileResponse:
    tipo = FILE_SCARICABILI.get(nome_file)
    percorso = _imp(request).cartella_pacchetti / nome_file
    if tipo is None or not percorso.is_file():
        raise HTTPException(404, "Questo file non c'è ancora sul NAS.")
    return FileResponse(percorso, media_type=tipo, filename=nome_file, headers=SENZA_CACHE)
