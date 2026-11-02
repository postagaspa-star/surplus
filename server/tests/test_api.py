"""Prove delle rotte: abbinamento, sincronizzazione vera fra due dispositivi, versione."""

from __future__ import annotations

import json

import pytest
from fastapi.testclient import TestClient

from app import abbina as modulo_abbina
from app.config import TENTATIVI_SBAGLIATI_MASSIMI, carica
from app.db import adesso, connetti, init_db
from app.main import crea_app


@pytest.fixture()
def banco(tmp_path, monkeypatch):
    monkeypatch.setenv("SURPLUS_DATI", str(tmp_path / "dati"))
    monkeypatch.setenv("SURPLUS_PACCHETTI", str(tmp_path / "pacchetti"))
    (tmp_path / "pacchetti").mkdir()
    app = crea_app()
    return TestClient(app), app


def codice_nuovo(app) -> str:
    codice, _ = modulo_abbina.nuovo_codice(app.state.conn)
    return codice


def abbina(client, app, nome="Computer di prova", tipo="computer") -> str:
    r = client.post("/api/abbina", json={"codice": codice_nuovo(app), "nome": nome, "tipo": tipo})
    assert r.status_code == 200, r.text
    return r.json()["token"]


def diario(voci):
    return {"schemaVersion": 4, "profile": {}, "entries": voci, "waterByDate": {}, "weights": [],
            "customFoods": [], "workouts": [], "exercises": [], "routines": [], "schedeInizializzate": True}


def voce(id_, giorno):
    return {"id": id_, "date": giorno, "moment": "PRANZO", "nome": "Pane", "grams": 100.0, "createdAt": 0}


def test_salute_risponde_anche_senza_dispositivi(banco):
    client, _ = banco
    r = client.get("/api/salute")
    assert r.status_code == 200
    assert r.json()["ok"] is True
    assert r.json()["revisione"] == 0
    assert r.json()["dispositivi"] == 0


def test_senza_token_non_si_legge_il_diario(banco):
    client, _ = banco
    assert client.get("/api/diario").status_code == 401
    assert client.post("/api/sincronizza", json={"dati": diario([])}).status_code == 401


def test_token_inventato_non_vale(banco):
    client, _ = banco
    r = client.get("/api/diario", headers={"Authorization": "Bearer inventato"})
    assert r.status_code == 401


def test_codice_sbagliato_non_abbina(banco):
    client, app = banco
    codice_nuovo(app)
    r = client.post("/api/abbina", json={"codice": "00000000", "nome": "finto", "tipo": "computer"})
    assert r.status_code == 401


def test_il_codice_vale_una_volta_sola(banco):
    client, app = banco
    codice = codice_nuovo(app)
    assert client.post("/api/abbina", json={"codice": codice, "nome": "uno", "tipo": "computer"}).status_code == 200
    assert client.post("/api/abbina", json={"codice": codice, "nome": "due", "tipo": "telefono"}).status_code == 401


def test_troppi_codici_sbagliati_bloccano_per_un_quarto_d_ora(banco):
    client, app = banco
    codice_nuovo(app)
    for _ in range(TENTATIVI_SBAGLIATI_MASSIMI):
        client.post("/api/abbina", json={"codice": "11111111", "nome": "x", "tipo": "computer"})
    r = client.post("/api/abbina", json={"codice": "11111111", "nome": "x", "tipo": "computer"})
    assert r.status_code == 429


def test_il_diario_recuperato_sale_sul_nas_e_il_telefono_lo_ritrova(banco):
    client, app = banco
    pc = abbina(client, app, "Computer", "computer")
    telefono = abbina(client, app, "Telefono", "telefono")

    # Il computer manda il diario recuperato.
    r = client.post("/api/sincronizza", json={"dati": diario([voce("vecchia", "2026-08-01")])},
                    headers={"Authorization": f"Bearer {pc}"})
    assert r.status_code == 200
    assert r.json()["cambiato"] is True
    assert r.json()["revisione"] == 1

    # Il telefono, che ha altri giorni suoi, non perde niente e prende anche gli altri.
    r = client.post("/api/sincronizza", json={"dati": diario([voce("sua", "2026-10-20")])},
                    headers={"Authorization": f"Bearer {telefono}"})
    assert r.status_code == 200
    ids = {v["id"] for v in r.json()["dati"]["entries"]}
    assert ids == {"vecchia", "sua"}

    # E ora il computer vede tutto.
    r = client.get("/api/diario", headers={"Authorization": f"Bearer {pc}"})
    assert {v["id"] for v in r.json()["dati"]["entries"]} == {"vecchia", "sua"}


def test_sincronizzare_di_nuovo_la_stessa_cosa_non_crea_revisioni(banco):
    client, app = banco
    token = abbina(client, app)
    dati = diario([voce("a", "2026-10-01")])
    prima = client.post("/api/sincronizza", json={"dati": dati}, headers={"Authorization": f"Bearer {token}"}).json()
    dopo = client.post("/api/sincronizza", json={"dati": prima["dati"]},
                       headers={"Authorization": f"Bearer {token}"}).json()
    assert dopo["cambiato"] is False
    assert dopo["revisione"] == prima["revisione"]


def test_una_voce_cancellata_sul_computer_non_torna_su_dal_nas(banco):
    client, app = banco
    token = abbina(client, app)
    intestazione = {"Authorization": f"Bearer {token}"}
    client.post("/api/sincronizza", json={"dati": diario([voce("a", "2026-10-01"), voce("b", "2026-10-02")])},
                headers=intestazione)
    r = client.post("/api/sincronizza", json={"dati": diario([voce("a", "2026-10-01")])}, headers=intestazione)
    assert [v["id"] for v in r.json()["dati"]["entries"]] == ["a"]
    r = client.get("/api/diario", headers=intestazione)
    assert [v["id"] for v in r.json()["dati"]["entries"]] == ["a"]


def test_la_copia_di_sicurezza_resta_nello_storico(banco):
    client, app = banco
    token = abbina(client, app)
    intestazione = {"Authorization": f"Bearer {token}"}
    client.post("/api/sincronizza", json={"dati": diario([voce("a", "2026-10-01")])}, headers=intestazione)
    client.post("/api/sincronizza", json={"dati": diario([voce("a", "2026-10-01"), voce("b", "2026-10-02")])},
                headers=intestazione)
    copie = sorted(app.state.impostazioni.storico.glob("diario-*.json"))
    assert len(copie) == 1
    assert "a" in copie[0].read_text(encoding="utf-8")


def test_versione_dice_solo_quello_che_c_e_davvero(banco):
    client, app = banco
    pacchetti = app.state.impostazioni.cartella_pacchetti
    # Senza i file, niente da annunciare.
    r = client.get("/api/versione")
    assert r.status_code == 200
    assert "pc" not in r.json()
    # Con lo zip, impronta e dimensione sono quelle del file vero.
    (pacchetti / "versioni.json").write_text(json.dumps({"pc": {"codice": 2, "nome": "1.0.1"}}), encoding="utf-8")
    (pacchetti / "surplus-pc.zip").write_bytes(b"finto zip")
    r = client.get("/api/versione")
    pc = r.json()["pc"]
    assert pc["codice"] == 2
    assert pc["byte"] == len(b"finto zip")
    assert pc["firma"] is False
    (pacchetti / "surplus-pc.zip.sig").write_text("firma", encoding="utf-8")
    assert client.get("/api/versione").json()["pc"]["firma"] is True


def test_si_scaricano_solo_i_file_previsti(banco):
    client, app = banco
    pacchetti = app.state.impostazioni.cartella_pacchetti
    (pacchetti / "surplus-pc.zip").write_bytes(b"pacchetto")
    (pacchetti / "segreto.txt").write_text("no", encoding="utf-8")
    assert client.get("/scarica/surplus-pc.zip").status_code == 200
    assert client.get("/scarica/segreto.txt").status_code == 404
    assert client.get("/scarica/surplus.apk").status_code == 404


def test_il_database_tiene_solo_le_impronte_dei_segreti(banco, tmp_path):
    client, app = banco
    token = abbina(client, app)
    conn = connetti(app.state.impostazioni.database)
    init_db(conn)
    righe = conn.execute("SELECT token_hash FROM dispositivi").fetchall()
    assert righe and token not in [r["token_hash"] for r in righe]
    assert len(righe[0]["token_hash"]) == 64
