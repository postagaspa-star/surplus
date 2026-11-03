"""Prove del campanello: chi aspetta le novità deve saperlo subito, e solo quando c'è davvero qualcosa."""

from __future__ import annotations

import threading
import time

from tests.test_base import abbina, banco, diario  # noqa: F401 (banco è la fixture)


def test_risponde_subito_se_il_nas_e_gia_avanti(banco):
    client, app = banco
    tel = abbina(client, app, "Telefono", "telefono")
    nuova = client.post("/api/sincronizza", json={"dati": diario("a"), "base": 0}, headers=tel).json()["revisione"]

    inizio = time.monotonic()
    r = client.get("/api/novita", params={"revisione": 0, "attesa": 10}, headers=tel)
    assert time.monotonic() - inizio < 2
    assert r.json() == {"ok": True, "revisione": nuova, "cambiato": True}


def test_senza_novita_aspetta_e_poi_dice_niente(banco):
    client, app = banco
    tel = abbina(client, app, "Telefono", "telefono")
    nuova = client.post("/api/sincronizza", json={"dati": diario("a"), "base": 0}, headers=tel).json()["revisione"]

    inizio = time.monotonic()
    r = client.get("/api/novita", params={"revisione": nuova, "attesa": 0.4}, headers=tel)
    assert time.monotonic() - inizio >= 0.35
    assert r.json()["cambiato"] is False


def test_il_pasto_del_telefono_sveglia_il_computer(banco):
    client, app = banco
    pc = abbina(client, app, "Computer", "computer")
    tel = abbina(client, app, "Telefono", "telefono")
    base = client.post("/api/sincronizza", json={"dati": diario("a"), "base": 0}, headers=pc).json()["revisione"]

    esito = {}

    def computer_in_ascolto():
        inizio = time.monotonic()
        esito["risposta"] = client.get("/api/novita", params={"revisione": base, "attesa": 15}, headers=pc).json()
        esito["dopo"] = time.monotonic() - inizio

    t = threading.Thread(target=computer_in_ascolto)
    t.start()
    time.sleep(0.5)
    client.post("/api/sincronizza", json={"dati": diario("a", "tel"), "base": base}, headers=tel)
    t.join(10)

    assert not t.is_alive()
    assert esito["risposta"]["cambiato"] is True
    assert esito["risposta"]["revisione"] > base
    assert esito["dopo"] < 5  # svegliato dal pasto, non dalla scadenza dei 15 secondi


def test_una_sincronizzazione_senza_niente_di_nuovo_non_sveglia_nessuno(banco):
    client, app = banco
    pc = abbina(client, app, "Computer", "computer")
    tel = abbina(client, app, "Telefono", "telefono")
    base = client.post("/api/sincronizza", json={"dati": diario("a"), "base": 0}, headers=pc).json()["revisione"]

    esito = {}
    t = threading.Thread(target=lambda: esito.update(
        client.get("/api/novita", params={"revisione": base, "attesa": 1}, headers=pc).json()))
    t.start()
    time.sleep(0.2)
    # Il telefono manda lo stesso identico diario: la revisione non cambia.
    client.post("/api/sincronizza", json={"dati": diario("a"), "base": base}, headers=tel)
    t.join(5)
    assert esito["cambiato"] is False
    assert esito["revisione"] == base


def test_senza_abbinamento_niente_campanello(banco):
    client, _ = banco
    assert client.get("/api/novita", params={"revisione": 0, "attesa": 0}).status_code == 401
    assert client.get("/api/novita", params={"revisione": 0, "attesa": 0},
                      headers={"Authorization": "Bearer sbagliato"}).status_code == 401


def test_attesa_troppo_lunga_rifiutata(banco):
    client, app = banco
    tel = abbina(client, app, "Telefono", "telefono")
    assert client.get("/api/novita", params={"revisione": 0, "attesa": 600}, headers=tel).status_code == 422
