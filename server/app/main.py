"""Il server di Surplus sul NAS: tiene il diario e lo unisce fra telefono e computer.

Non c'è nessuna pagina da guardare: il server esiste solo perché le due app
parlino fra loro anche quando una delle due è spenta. Gira in un container sul
NAS di casa, sulla porta 8600 della rete locale.
"""

from __future__ import annotations

import logging

from fastapi import FastAPI
from fastapi.responses import JSONResponse

from . import api
from .config import VERSIONE, assicura_cartelle, carica
from .db import connetti, init_db

logging.basicConfig(level=logging.INFO, format="%(asctime)s  %(levelname)s  %(message)s")
log = logging.getLogger("surplus")


def crea_app() -> FastAPI:
    imp = carica()
    assicura_cartelle(imp)
    conn = connetti(imp.database)
    init_db(conn)

    app = FastAPI(title="Surplus", version=VERSIONE, docs_url=None, redoc_url=None, openapi_url=None)
    app.state.impostazioni = imp
    app.state.conn = conn
    app.include_router(api.router)

    @app.get("/")
    def radice() -> JSONResponse:
        return JSONResponse({"ok": True, "nome": "Surplus", "versione": VERSIONE})

    log.info("Surplus %s pronto: dati in %s, pacchetti in %s", VERSIONE, imp.cartella_dati, imp.cartella_pacchetti)
    return app


app = crea_app()
