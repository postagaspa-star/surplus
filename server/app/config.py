"""Impostazioni del server, tutte da variabili d'ambiente (niente segreti nel codice)."""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

VERSIONE = "1.0.0"

CIFRE_CODICE = 8
DURATA_CODICE_MINUTI = 15
TENTATIVI_SBAGLIATI_MASSIMI = 10
FINESTRA_TENTATIVI_MINUTI = 15

# Un diario con anni di pasti sta sotto il megabyte: oltre questo qualcosa non torna.
MASSIMO_BYTE_DIARIO = 8 * 1024 * 1024

# Quante versioni vecchie del diario si tengono da parte.
STORICO_MASSIMO = 30


@dataclass(frozen=True)
class Impostazioni:
    cartella_dati: Path
    cartella_pacchetti: Path

    @property
    def database(self) -> Path:
        return self.cartella_dati / "surplus.db"

    @property
    def storico(self) -> Path:
        return self.cartella_dati / "storico"


def carica() -> Impostazioni:
    dati = Path(os.environ.get("SURPLUS_DATI") or "/dati")
    pacchetti = Path(os.environ.get("SURPLUS_PACCHETTI") or "/pacchetti")
    return Impostazioni(cartella_dati=dati, cartella_pacchetti=pacchetti)


def assicura_cartelle(imp: Impostazioni) -> None:
    imp.cartella_dati.mkdir(parents=True, exist_ok=True)
    imp.storico.mkdir(parents=True, exist_ok=True)
