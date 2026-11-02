"""Prove dell'unione: è il pezzo dove un errore fa perdere dei pasti."""

from __future__ import annotations

from app.unione import conta, unisci


def voce(id_: str, giorno: str, nome: str = "Pane", grammi: float = 100.0, ora: int = 0) -> dict:
    return {"id": id_, "date": giorno, "moment": "PRANZO", "nome": nome, "grams": grammi, "createdAt": ora}


def diario(voci: list[dict], **extra) -> dict:
    d = {"schemaVersion": 4, "profile": {}, "entries": voci, "waterByDate": {}, "weights": [],
         "customFoods": [], "workouts": [], "exercises": [], "routines": [], "schedeInizializzate": False}
    d.update(extra)
    return d


def test_primo_abbinamento_il_server_prende_tutto():
    mio = diario([voce("a", "2026-10-01"), voce("b", "2026-10-02")])
    unito, conflitti = unisci(None, mio, None)
    assert [v["id"] for v in unito["entries"]] == ["a", "b"]
    assert conflitti == []


def test_due_diari_che_non_si_sono_mai_visti_si_sommano():
    telefono = diario([voce("a", "2026-10-01")])
    computer = diario([voce("b", "2026-11-01")])
    unito, _ = unisci(None, computer, telefono)
    assert [v["id"] for v in unito["entries"]] == ["a", "b"]


def test_niente_viene_cancellato_senza_base():
    # Il caso che conta: il computer sincronizza per primo un diario recuperato,
    # poi arriva il telefono con giorni che il computer non ha mai avuto.
    nas = diario([voce("vecchia", "2026-08-01")])
    telefono = diario([voce("nuova", "2026-10-20")])
    unito, _ = unisci(None, telefono, nas)
    assert {v["id"] for v in unito["entries"]} == {"vecchia", "nuova"}


def test_cancellata_sul_dispositivo_sparisce_anche_dal_nas():
    base = diario([voce("a", "2026-10-01"), voce("b", "2026-10-02")])
    mio = diario([voce("a", "2026-10-01")])            # b cancellata qui
    suo = diario([voce("a", "2026-10-01"), voce("b", "2026-10-02")])
    unito, _ = unisci(base, mio, suo)
    assert [v["id"] for v in unito["entries"]] == ["a"]


def test_cancellata_sul_nas_sparisce_anche_qui():
    base = diario([voce("a", "2026-10-01"), voce("b", "2026-10-02")])
    mio = diario([voce("a", "2026-10-01"), voce("b", "2026-10-02")])
    suo = diario([voce("a", "2026-10-01")])            # b cancellata dall'altro dispositivo
    unito, _ = unisci(base, mio, suo)
    assert [v["id"] for v in unito["entries"]] == ["a"]


def test_cambiata_solo_da_una_parte_vince_quella_parte():
    base = diario([voce("a", "2026-10-01", grammi=100)])
    mio = diario([voce("a", "2026-10-01", grammi=100)])
    suo = diario([voce("a", "2026-10-01", grammi=150)])
    unito, conflitti = unisci(base, mio, suo)
    assert unito["entries"][0]["grams"] == 150
    assert conflitti == []


def test_cambiata_da_entrambe_vince_chi_sincronizza_adesso():
    base = diario([voce("a", "2026-10-01", grammi=100)])
    mio = diario([voce("a", "2026-10-01", grammi=120)])
    suo = diario([voce("a", "2026-10-01", grammi=150)])
    unito, conflitti = unisci(base, mio, suo)
    assert unito["entries"][0]["grams"] == 120
    assert conflitti == ["entries:a"]


def test_il_profilo_si_unisce_campo_per_campo():
    base = diario([], profile={"altezzaCm": 175, "pesoKg": 63.0})
    mio = diario([], profile={"altezzaCm": 175, "pesoKg": 65.5})       # pesato sul computer
    suo = diario([], profile={"altezzaCm": 178, "pesoKg": 63.0})       # misurato sul telefono
    unito, conflitti = unisci(base, mio, suo)
    assert unito["profile"] == {"altezzaCm": 178, "pesoKg": 65.5}
    assert conflitti == []


def test_acqua_e_pesi():
    base = diario([], waterByDate={"2026-10-01": 1000}, weights=[{"date": "2026-10-01", "kg": 63.0}])
    mio = diario([], waterByDate={"2026-10-01": 1500}, weights=[{"date": "2026-10-01", "kg": 63.0},
                                                                {"date": "2026-10-02", "kg": 63.4}])
    suo = diario([], waterByDate={"2026-10-01": 1000, "2026-10-02": 500},
                 weights=[{"date": "2026-10-01", "kg": 63.0}])
    unito, _ = unisci(base, mio, suo)
    assert unito["waterByDate"] == {"2026-10-01": 1500, "2026-10-02": 500}
    assert [w["date"] for w in unito["weights"]] == ["2026-10-01", "2026-10-02"]


def test_le_schede_create_restano_create():
    unito, _ = unisci(diario([]), diario([], schedeInizializzate=False), diario([], schedeInizializzate=True))
    assert unito["schedeInizializzate"] is True


def test_la_versione_dello_schema_non_torna_indietro():
    unito, _ = unisci(None, diario([], schemaVersion=3), diario([], schemaVersion=4))
    assert unito["schemaVersion"] == 4


def test_un_campo_che_il_server_non_conosce_non_si_perde():
    mio = diario([])
    mio["qualcosaDiNuovo"] = {"acceso": True}
    unito, _ = unisci(None, mio, diario([]))
    assert unito["qualcosaDiNuovo"] == {"acceso": True}


def test_il_diario_esce_in_ordine_di_giorno_e_di_ora():
    mio = diario([voce("c", "2026-10-02", ora=100), voce("a", "2026-10-01", ora=50),
                  voce("b", "2026-10-01", ora=10)])
    unito, _ = unisci(None, mio, None)
    assert [v["id"] for v in unito["entries"]] == ["b", "a", "c"]


def test_sincronizzare_due_volte_non_cambia_niente():
    mio = diario([voce("a", "2026-10-01")])
    primo, _ = unisci(None, mio, None)
    secondo, conflitti = unisci(primo, mio, primo)
    assert secondo == primo
    assert conflitti == []


def test_le_voci_senza_id_non_fanno_cadere_l_unione():
    mio = diario([{"date": "2026-10-01", "nome": "senza id"}, voce("a", "2026-10-01")])
    unito, _ = unisci(None, mio, None)
    assert [v["id"] for v in unito["entries"]] == ["a"]


def test_l_ordine_delle_chiavi_non_cambia_da_una_volta_all_altra():
    # Se il risultato uscisse con le chiavi in ordine diverso ogni volta, il
    # computer riscriverebbe il diario sul disco a ogni sincronizzazione anche
    # quando non è cambiato niente (successo davvero, con il profilo).
    profilo = {"altezzaCm": 175, "pesoKg": 63.0, "sesso": "MASCHIO", "attivita": "MODERATA", "obiettivo": "MASSA"}
    mio = diario([voce("a", "2026-10-01")], profile=dict(profilo))
    suo = diario([voce("a", "2026-10-01")], profile=dict(profilo))
    primo, _ = unisci(None, mio, suo)
    secondo, _ = unisci(None, mio, suo)
    assert list(primo["profile"]) == list(secondo["profile"])
    assert list(primo["profile"]) == sorted(profilo)
    assert list(primo) == list(secondo)


def test_conta_racconta_cosa_c_e_dentro():
    d = diario([voce("a", "2026-10-01"), voce("b", "2026-10-01"), voce("c", "2026-10-02")],
               waterByDate={"2026-10-01": 500})
    numeri = conta(d)
    assert numeri["entries"] == 3
    assert numeri["giorni"] == 2
    assert numeri["waterByDate"] == 1
