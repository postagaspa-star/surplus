"""Prove della base dichiarata: una risposta persa non deve mai diventare una cancellazione."""

from __future__ import annotations

import threading

import pytest
from fastapi.testclient import TestClient

from app import abbina as modulo_abbina
from app.db import connetti, init_db
from app.main import crea_app


@pytest.fixture()
def banco(tmp_path, monkeypatch):
    monkeypatch.setenv("SURPLUS_DATI", str(tmp_path / "dati"))
    monkeypatch.setenv("SURPLUS_PACCHETTI", str(tmp_path / "pacchetti"))
    (tmp_path / "pacchetti").mkdir()
    app = crea_app()
    return TestClient(app), app


def abbina(client, app, nome, tipo):
    codice, _ = modulo_abbina.nuovo_codice(app.state.conn)
    r = client.post("/api/abbina", json={"codice": codice, "nome": nome, "tipo": tipo})
    return {"Authorization": f"Bearer {r.json()['token']}"}


def diario(*ids):
    return {"schemaVersion": 4, "profile": {}, "waterByDate": {}, "weights": [], "customFoods": [],
            "workouts": [], "exercises": [], "routines": [], "schedeInizializzate": True,
            "entries": [{"id": i, "date": "2026-11-03", "moment": "PRANZO", "nome": i, "grams": 100.0,
                         "createdAt": n} for n, i in enumerate(ids)]}


def ids(risposta):
    return sorted(v["id"] for v in risposta.json()["dati"]["entries"])


def test_risposta_persa_non_cancella_quello_dell_altro(banco):
    client, app = banco
    pc = abbina(client, app, "Computer", "computer")
    telefono = abbina(client, app, "Telefono", "telefono")

    r = client.post("/api/sincronizza", json={"dati": diario("a"), "base": 0}, headers=telefono)
    base_telefono = r.json()["revisione"]
    # Il computer aggiunge "pc".
    client.post("/api/sincronizza", json={"dati": diario("a", "pc"), "base": 0}, headers=pc)

    # Il telefono sincronizza, il NAS unisce... ma la risposta non arriva mai.
    client.post("/api/sincronizza", json={"dati": diario("a"), "base": base_telefono}, headers=telefono)

    # Il telefono non ha mai visto "pc": riprova con la base di prima e un pasto nuovo.
    r = client.post("/api/sincronizza", json={"dati": diario("a", "tel"), "base": base_telefono},
                    headers=telefono)
    assert ids(r) == ["a", "pc", "tel"]


def test_le_cancellazioni_passano_ancora(banco):
    client, app = banco
    telefono = abbina(client, app, "Telefono", "telefono")
    r = client.post("/api/sincronizza", json={"dati": diario("a", "b"), "base": 0}, headers=telefono)
    r = client.post("/api/sincronizza", json={"dati": diario("a"), "base": r.json()["revisione"]},
                    headers=telefono)
    assert ids(r) == ["a"]


def test_base_sparita_somma_e_non_cancella(banco):
    client, app = banco
    telefono = abbina(client, app, "Telefono", "telefono")
    client.post("/api/sincronizza", json={"dati": diario("a", "b"), "base": 0}, headers=telefono)
    # Una base che il NAS non ha (troppo vecchia, inventata): niente sembra cancellato.
    r = client.post("/api/sincronizza", json={"dati": diario("a"), "base": 999}, headers=telefono)
    assert ids(r) == ["a", "b"]


def test_le_app_vecchie_senza_base_funzionano_come_prima(banco):
    client, app = banco
    telefono = abbina(client, app, "Telefono", "telefono")
    client.post("/api/sincronizza", json={"dati": diario("a", "b")}, headers=telefono)
    r = client.post("/api/sincronizza", json={"dati": diario("a")}, headers=telefono)
    assert ids(r) == ["a"]


def test_la_base_di_chi_e_via_da_tanto_non_si_butta(banco):
    client, app = banco
    pc = abbina(client, app, "Computer", "computer")
    telefono = abbina(client, app, "Telefono", "telefono")
    r = client.post("/api/sincronizza", json={"dati": diario("a", "via"), "base": 0}, headers=telefono)
    base_telefono = r.json()["revisione"]
    # Il telefono resta fuori casa mentre il computer fa cento modifiche.
    voci = ["a", "via"]
    rev = base_telefono
    for n in range(100):
        voci.append(f"pc{n}")
        rev = client.post("/api/sincronizza", json={"dati": diario(*voci), "base": rev},
                          headers=pc).json()["revisione"]
    # Fuori casa il telefono ha cancellato "via": al ritorno la cancellazione vale.
    r = client.post("/api/sincronizza", json={"dati": diario("a"), "base": base_telefono}, headers=telefono)
    assert "via" not in ids(r)
    assert len(ids(r)) == 101
    # E il database non tiene tutte e cento le revisioni.
    n = app.state.conn.execute("SELECT COUNT(*) AS n FROM revisioni").fetchone()["n"]
    assert n < 60


def test_due_apparecchi_insieme_non_si_perdono_a_vicenda(banco):
    client, app = banco
    pc = abbina(client, app, "Computer", "computer")
    telefono = abbina(client, app, "Telefono", "telefono")
    client.post("/api/sincronizza", json={"dati": diario("a"), "base": 0}, headers=pc)
    errori = []

    def manda(intestazione, voce):
        try:
            client.post("/api/sincronizza", json={"dati": diario("a", voce), "base": 0}, headers=intestazione)
        except Exception as e:  # pragma: no cover - lo segnala l'assert sotto
            errori.append(e)

    fili = [threading.Thread(target=manda, args=(pc, "pc")), threading.Thread(target=manda, args=(telefono, "tel"))]
    for f in fili:
        f.start()
    for f in fili:
        f.join()
    assert not errori
    r = client.get("/api/diario", headers=pc)
    assert sorted(v["id"] for v in r.json()["dati"]["entries"]) == ["a", "pc", "tel"]


def test_un_database_della_1_0_0_si_aggiorna(tmp_path):
    import sqlite3
    percorso = tmp_path / "vecchio.db"
    vecchio = sqlite3.connect(percorso)
    vecchio.execute("""CREATE TABLE dispositivi (id INTEGER PRIMARY KEY AUTOINCREMENT, nome TEXT NOT NULL,
        tipo TEXT NOT NULL, token_hash TEXT NOT NULL UNIQUE, creato_il TEXT NOT NULL, ultimo_contatto TEXT,
        base TEXT, base_revisione INTEGER)""")
    vecchio.commit()
    vecchio.close()
    conn = connetti(percorso)
    init_db(conn)
    init_db(conn)  # due volte: la seconda non deve rompersi
    colonne = {r["name"] for r in conn.execute("PRAGMA table_info(dispositivi)")}
    assert "base_dichiarata" in colonne
