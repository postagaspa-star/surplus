/*
 * Le altre tre schermate: Allenamento, Storico, Impostazioni.
 *
 * Stanno qui e non in app.js solo per non avere un file unico lunghissimo:
 * usano gli stessi strumenti (SurplusApp) e le stesse regole (SurplusLogica).
 */
(function (radice) {
  'use strict';

  const L = radice.SurplusLogica;
  const Api = radice.SurplusApi;

  function A() { return radice.SurplusApp; }

  // ================= ALLENAMENTO =================

  function allenamento(app) {
    const a = A();
    const d = a.stato.dati;
    if (a.stato.sessione) { sessione(app, a.stato.sessione); return; }
    if (a.stato.schedaBozza) { editorScheda(app); return; }

    const el = a.el;
    const oggi = a.isoDiOggi();

    app.appendChild(el('div', { class: 'titolo-schermata', testo: 'Le tue schede' }));

    if ((d.routines || []).length === 0) {
      app.appendChild(el('div', { class: 'piccolo', testo: 'Nessuna scheda: creane una con gli esercizi che fai davvero.' }));
    }

    (d.routines || []).forEach((r) => {
      const serieOggi = (d.workouts || [])
        .filter((s) => s.date === oggi && s.routineId === r.id)
        .reduce((t, s) => t + (s.exercises || []).reduce((x, e) => x + (e.sets || []).length, 0), 0);
      const ultima = L.ultimaVoltaScheda(d.workouts || [], r.id);
      const quanti = (r.exerciseIds || []).length;
      const sottotitolo = serieOggi > 0
        ? 'In corso oggi · ' + serieOggi + (serieOggi === 1 ? ' serie' : ' serie') + ' — tocca per continuare'
        : (quanti === 1 ? '1 esercizio' : quanti + ' esercizi') + (ultima ? ' · ultima volta ' + L.etichettaDataBreve(ultima).toLowerCase() : '');

      app.appendChild(el('div', { class: 'riquadro toccabile' + (serieOggi > 0 ? ' acceso' : ''), click: () => avviaScheda(r) }, [
        el('div', { class: 'riga' }, [
          el('div', { class: 'cresci' }, [
            el('div', { class: 'titolo-riquadro', testo: r.nome || 'Senza nome' }),
            el('div', { class: 'sottotitolo', testo: sottotitolo }),
          ]),
          el('button', {
            class: 'togli', type: 'button', 'aria-label': 'Modifica ' + r.nome,
            click: (e) => { e.stopPropagation(); apriEditor(r); },
          }, [a.icona(a.ICONE.matita)]),
        ]),
      ]));
    });

    app.appendChild(el('div', { class: 'pulsanti', style: 'margin:4px 0 14px' }, [
      el('button', { class: 'pulsante contorno', type: 'button', testo: 'Nuova scheda', click: () => apriEditor(null) }),
      el('button', { class: 'pulsante contorno', type: 'button', testo: 'Allenamento libero', click: () => avviaScheda(null) }),
    ]));

    const sessioni = (d.workouts || []).slice().sort(ordineSessioni);
    if (sessioni.length === 0) {
      app.appendChild(el('div', { class: 'piccolo', testo: 'Tocca una scheda per iniziare. Gli esercizi che salti non vengono salvati: nessuno è obbligatorio.' }));
      return;
    }

    app.appendChild(el('div', { class: 'titolo-schermata', testo: 'Sessioni' }));
    sessioni.forEach((s) => {
      const nEx = (s.exercises || []).filter((e) => (e.sets || []).length > 0).length;
      const nSerie = (s.exercises || []).reduce((t, e) => t + (e.sets || []).length, 0);
      const orari = s.firstSetAt > 0 && s.lastSetAt > s.firstSetAt
        ? ' · ' + L.fmtOra(s.firstSetAt) + '→' + L.fmtOra(s.lastSetAt)
        : s.firstSetAt > 0 ? ' · ' + L.fmtOra(s.firstSetAt) : '';
      app.appendChild(el('div', { class: 'riquadro toccabile', click: () => { A().stato.sessione = s.id; A().disegna(); } }, [
        el('div', { class: 'riga' }, [
          el('div', { class: 'cresci' }, [
            el('div', { class: 'testo grassetto', testo: etichettaSessione(s) + ' — ' + L.etichettaDataBreve(s.date) }),
            el('div', { class: 'sottotitolo', testo: nSerie === 0 ? 'vuota' : (nEx === 1 ? '1 esercizio' : nEx + ' esercizi') + ' · ' + (nSerie === 1 ? '1 serie' : nSerie + ' serie') + orari }),
          ]),
          a.icona(a.ICONE.freccia, 'icona-piccola'),
        ]),
      ]));
    });
  }

  function ordineSessioni(x, y) {
    if (x.date !== y.date) return x.date < y.date ? 1 : -1;
    return (y.startedAt || 0) - (x.startedAt || 0);
  }

  function etichettaSessione(s) {
    if (s.routineName) return s.routineName;
    if (s.type === 'SPINTA') return 'Spinta';
    if (s.type === 'TRAZIONE') return 'Trazione';
    return 'Allenamento';
  }

  function archivio() { return A().stato.dati.exercises || []; }

  function esercizioPerId(id) { return archivio().find((e) => e.id === id) || null; }

  /** Un tocco e si parte. Se la scheda è già aperta oggi si riprende, invece di creare un doppione. */
  async function avviaScheda(scheda) {
    const a = A();
    const oggi = a.isoDiOggi();
    const rid = scheda ? scheda.id : 'libero';
    const diOggi = (a.stato.dati.workouts || [])
      .filter((s) => s.date === oggi && s.routineId === rid)
      .sort((x, y) => (y.startedAt || 0) - (x.startedAt || 0))[0];

    if (diOggi) {
      if (scheda) {
        await a.salva((d) => {
          const s = d.workouts.find((w) => w.id === diOggi.id);
          if (!s) return;
          const dentro = (scheda.exerciseIds || []).map((id) => {
            const fatto = (s.exercises || []).find((e) => e.exerciseId === id);
            if (fatto) return fatto;
            const ex = esercizioPerId(id);
            return ex ? { exerciseId: ex.id, nome: ex.nome, sets: [] } : null;
          }).filter(Boolean);
          const extra = (s.exercises || []).filter((e) => (scheda.exerciseIds || []).indexOf(e.exerciseId) < 0);
          s.exercises = dentro.concat(extra);
        });
      }
      a.stato.sessione = diOggi.id;
      a.disegna();
      return;
    }

    const id = 'w-' + a.idNuovo();
    await a.salva((d) => {
      const esercizi = (scheda ? scheda.exerciseIds : []).map((eid) => {
        const ex = (d.exercises || []).find((x) => x.id === eid);
        return ex ? { exerciseId: ex.id, nome: ex.nome, sets: [] } : null;
      }).filter(Boolean);
      d.workouts = (d.workouts || []).concat([{
        id: id,
        date: oggi,
        type: null,
        exercises: esercizi,
        startedAt: Date.now(),
        firstSetAt: 0,
        lastSetAt: 0,
        routineId: scheda ? scheda.id : 'libero',
        routineName: scheda ? scheda.nome : 'Libero',
      }]);
    });
    a.stato.sessione = id;
    a.disegna();
  }

  // ---- la sessione in corso ----

  function sessione(app, id) {
    const a = A();
    const el = a.el;
    const d = a.stato.dati;
    const s = (d.workouts || []).find((w) => w.id === id);
    if (!s) { a.stato.sessione = null; a.disegna(); return; }

    const orari = s.firstSetAt > 0 && s.lastSetAt > s.firstSetAt
      ? 'Prima serie ' + L.fmtOra(s.firstSetAt) + ' → ultima ' + L.fmtOra(s.lastSetAt) + ' (' + Math.round((s.lastSetAt - s.firstSetAt) / 60000) + ' min)'
      : s.firstSetAt > 0 ? 'Prima serie ' + L.fmtOra(s.firstSetAt) : null;

    app.appendChild(el('div', { class: 'riga riga-alto', style: 'padding:6px 0 10px' }, [
      el('button', { class: 'freccia', type: 'button', 'aria-label': 'Chiudi sessione', click: () => chiudiSessione(s) }, [a.icona(a.ICONE.indietro)]),
      el('div', { class: 'cresci' }, [
        el('div', { style: 'font-size:22px;font-weight:700', testo: etichettaSessione(s) }),
        el('div', { class: 'piccolo', testo: L.etichettaDataBreve(s.date) }),
        orari ? el('div', { class: 'piccolo verde', testo: orari }) : null,
      ]),
      el('button', { class: 'togli', type: 'button', 'aria-label': 'Elimina sessione', click: () => confermaEliminaSessione(s) }, [a.icona(a.ICONE.cestino)]),
    ]));

    if (!a.stato.esercizioAperto || !(s.exercises || []).some((e) => e.exerciseId === a.stato.esercizioAperto)) {
      const daFare = (s.exercises || []).find((e) => (e.sets || []).length === 0);
      a.stato.esercizioAperto = daFare ? daFare.exerciseId : ((s.exercises || [])[(s.exercises || []).length - 1] || {}).exerciseId || null;
    }

    (s.exercises || []).forEach((log, i) => {
      const ex = esercizioPerId(log.exerciseId);
      const prec = L.ultimeSerie(d.workouts || [], log.exerciseId, s.id);
      if (log.exerciseId === a.stato.esercizioAperto) {
        app.appendChild(esercizioAperto(s, log, ex, prec, i));
      } else {
        app.appendChild(esercizioChiuso(log, ex, prec));
      }
    });

    app.appendChild(el('div', { class: 'pulsanti', style: 'margin-top:4px' }, [
      el('button', { class: 'pulsante contorno', type: 'button', click: () => dialogoArchivio(s) }, [a.icona(a.ICONE.piu), 'Esercizio']),
      el('button', { class: 'pulsante pieno', type: 'button', testo: 'Termina', click: () => chiudiSessione(s) }),
    ]));
    app.appendChild(el('div', { class: 'minuscolo', style: 'margin-top:6px', testo: 'Gli esercizi senza serie non vengono salvati.' }));
  }

  function esercizioChiuso(log, ex, prec) {
    const a = A();
    const el = a.el;
    const fatto = (log.sets || []).length > 0;
    const sotto = fatto
      ? (log.sets || []).map((x) => L.fmtSerie(x)).join(' · ')
      : prec ? 'Ultima volta: ' + prec.serie.map((x) => L.fmtSerie(x)).join(' · ') : (obiettivo(ex) || 'Mai fatto');
    return el('div', { class: 'riquadro toccabile', click: () => { a.stato.esercizioAperto = log.exerciseId; a.disegna(); } }, [
      el('div', { class: 'riga' }, [
        el('div', { class: 'cresci' }, [
          el('div', { class: 'testo grassetto', testo: (ex && ex.nome) || log.nome }),
          el('div', { class: 'sottotitolo' + (fatto ? ' verde' : ''), testo: sotto }),
        ]),
        fatto ? el('span', { class: 'verde grassetto', testo: '✓ ' + log.sets.length }) : null,
      ]),
    ]);
  }

  function esercizioAperto(s, log, ex, prec, indice) {
    const a = A();
    const el = a.el;
    // Nel file le serie sono {weightKg, reps}, la logica le restituisce {kg, rip}:
    // leggendo il nome sbagliato peso e ripetizioni sparivano appena si riapriva
    // un esercizio già fatto oggi.
    const ultima = (log.sets || [])[(log.sets || []).length - 1];
    const proposta = ultima
      ? { set: { kg: ultima.weightKg, rip: ultima.reps }, progressione: false }
      : L.serieProposta(prec ? prec.serie : null, ex);

    let peso = proposta ? proposta.set.kg : 0;
    let rip = proposta ? proposta.set.rip : (ex && ex.repMin) || 10;
    const passo = (ex && ex.incrementoKg > 0) ? ex.incrementoKg : 1;

    const mostraPeso = el('span', { class: 'cresci grassetto', style: 'text-align:center;font-size:17px', testo: peso > 0 ? L.fmt1(peso) + ' kg' : 'corpo' });
    const mostraRip = el('span', { class: 'cresci grassetto', style: 'text-align:center;font-size:17px', testo: rip + ' rip' });

    function aggiorna() {
      mostraPeso.textContent = peso > 0 ? L.fmt1(peso) + ' kg' : 'corpo';
      mostraRip.textContent = rip + ' rip';
    }

    function tondo(simbolo, azione, etichetta) {
      return el('button', {
        class: 'pulsante', type: 'button', 'aria-label': etichetta,
        style: 'width:42px;min-height:42px;padding:0;border-radius:21px;background:var(--sfondo);color:var(--verde);font-size:20px;border-color:transparent;flex:0 0 auto',
        testo: simbolo, click: azione,
      });
    }

    const serieFatte = el('div', { class: 'scelte' });
    (log.sets || []).forEach((x, i) => {
      serieFatte.appendChild(el('button', {
        class: 'scelta', type: 'button', testo: (i + 1) + '. ' + L.fmtSerie(x),
        click: () => confermaTogliSerie(s, log, i),
      }));
    });

    const prossimo = (s.exercises || []).slice(indice + 1).find((e) => (e.sets || []).length === 0)
      || (s.exercises || []).find((e) => (e.sets || []).length === 0 && e.exerciseId !== log.exerciseId);

    return el('div', { class: 'riquadro acceso' }, [
      el('div', { class: 'riga riga-alto' }, [
        el('div', { class: 'cresci' }, [
          el('div', { class: 'testo grassetto', style: 'font-size:17px', testo: (ex && ex.nome) || log.nome }),
          obiettivo(ex) ? el('div', { class: 'sottotitolo', testo: obiettivo(ex) }) : null,
        ]),
        el('button', { class: 'pulsante piatto', style: 'padding:4px 8px;min-height:0', type: 'button', testo: 'Storico', click: () => dialogoStorico(log, ex) }),
        el('button', { class: 'togli', type: 'button', 'aria-label': 'Togli da oggi', click: () => togliEsercizio(s, log) }, [a.icona(a.ICONE.chiudi, 'icona-piccola')]),
      ]),
      prec ? el('div', { class: 'piccolo verde', style: 'margin:8px 0', testo: 'Ultima volta (' + L.etichettaDataBreve(prec.data) + '): ' + prec.serie.map((x) => L.fmtSerie(x)).join(' · ') }) : null,
      (log.sets || []).length === 0 && proposta && proposta.progressione
        ? el('div', { class: 'piccolo verde grassetto', style: 'margin-bottom:8px', testo: 'L\'ultima volta tutte le serie erano al massimo: ti propongo ' + L.fmt1(proposta.set.kg) + ' kg.' })
        : null,
      (log.sets || []).length > 0 ? serieFatte : null,
      el('div', { class: 'riga', style: 'gap:8px;margin-bottom:12px' }, [
        tondo('−', () => { peso = L.arrotondaKg(Math.max(0, peso - passo)); aggiorna(); }, 'Meno peso'),
        mostraPeso,
        tondo('+', () => { peso = L.arrotondaKg(peso + passo); aggiorna(); }, 'Più peso'),
        tondo('−', () => { rip = Math.max(1, rip - 1); aggiorna(); }, 'Meno ripetizioni'),
        mostraRip,
        tondo('+', () => { rip += 1; aggiorna(); }, 'Più ripetizioni'),
      ]),
      el('button', {
        class: 'pulsante pieno', style: 'width:100%', type: 'button',
        testo: 'Segna serie ' + ((log.sets || []).length + 1),
        click: () => segnaSerie(s, log, peso, rip),
      }),
      prossimo && (log.sets || []).length > 0
        ? el('button', { class: 'pulsante piatto', style: 'margin-left:auto;display:block', type: 'button', testo: 'Prossimo esercizio ›', click: () => { a.stato.esercizioAperto = prossimo.exerciseId; a.disegna(); } })
        : null,
    ]);
  }

  /** "3-4 serie × 8-12 rip — nota", solo con i pezzi presenti. */
  function obiettivo(ex) {
    if (!ex) return null;
    const pezzi = [];
    if (ex.serie) pezzi.push(ex.serie + ' serie');
    let rip = null;
    if (ex.repMin != null && ex.repMax != null && ex.repMin !== ex.repMax) rip = ex.repMin + '-' + ex.repMax + ' rip';
    else if (ex.repMin != null) rip = ex.repMin + ' rip';
    else if (ex.repMax != null) rip = ex.repMax + ' rip';
    let testo = pezzi.concat(rip ? [rip] : []).join(' × ');
    if (ex.nota) testo = testo ? testo + ' — ' + ex.nota : ex.nota;
    return testo || null;
  }

  function segnaSerie(s, log, peso, rip) {
    const a = A();
    a.salva((d) => {
      const sessione = d.workouts.find((w) => w.id === s.id);
      if (!sessione) return;
      const adesso = Date.now();
      if (!sessione.firstSetAt) sessione.firstSetAt = adesso;
      sessione.lastSetAt = adesso;
      const e = sessione.exercises.find((x) => x.exerciseId === log.exerciseId);
      if (e) e.sets = (e.sets || []).concat([{ weightKg: peso, reps: rip }]);
    });
  }

  function confermaTogliSerie(s, log, indice) {
    const a = A();
    const x = (log.sets || [])[indice];
    if (!x) return;
    a.apriFinestrella('Togliere la serie ' + (indice + 1) + '?', [a.el('p', { class: 'testo', testo: L.fmtSerie(x) })], [
      a.bottoneChiudi('Annulla'),
      a.el('button', {
        class: 'pulsante piatto', type: 'button', testo: 'Togli',
        click: () => {
          a.chiudiFinestrella();
          a.salva((d) => {
            const sessione = d.workouts.find((w) => w.id === s.id);
            const e = sessione && sessione.exercises.find((y) => y.exerciseId === log.exerciseId);
            if (e) e.sets = e.sets.filter((_, i) => i !== indice);
          });
        },
      }),
    ]);
  }

  function togliEsercizio(s, log) {
    const a = A();
    const togli = () => a.salva((d) => {
      const sessione = d.workouts.find((w) => w.id === s.id);
      if (sessione) sessione.exercises = sessione.exercises.filter((e) => e.exerciseId !== log.exerciseId);
    });
    if ((log.sets || []).length === 0) { togli(); return; }
    a.apriFinestrella('Togliere l\'esercizio da oggi?', [
      a.el('p', { class: 'testo', testo: 'Le serie già segnate oggi per questo esercizio verranno cancellate.' }),
    ], [a.bottoneChiudi('Annulla'), a.el('button', { class: 'pulsante piatto', type: 'button', testo: 'Togli', click: () => { a.chiudiFinestrella(); togli(); } })]);
  }

  /** Chiudendo restano solo gli esercizi fatti; una sessione senza serie sparisce. */
  function chiudiSessione(s) {
    const a = A();
    a.salva((d) => {
      const sessione = d.workouts.find((w) => w.id === s.id);
      if (!sessione) return;
      const fatti = (sessione.exercises || []).filter((e) => (e.sets || []).length > 0);
      if (fatti.length === 0) d.workouts = d.workouts.filter((w) => w.id !== s.id);
      else sessione.exercises = fatti;
    });
    a.stato.sessione = null;
    a.stato.esercizioAperto = null;
    a.disegna();
  }

  function confermaEliminaSessione(s) {
    const a = A();
    a.apriFinestrella('Eliminare la sessione?', [
      a.el('p', { class: 'testo', testo: 'Verranno cancellate tutte le serie registrate in questa sessione.' }),
    ], [
      a.bottoneChiudi('Annulla'),
      a.el('button', {
        class: 'pulsante piatto rosso', type: 'button', testo: 'Elimina',
        click: () => {
          a.chiudiFinestrella();
          a.stato.sessione = null;
          a.salva((d) => { d.workouts = d.workouts.filter((w) => w.id !== s.id); });
        },
      }),
    ]);
  }

  function dialogoStorico(log, ex) {
    const a = A();
    const righe = L.storicoEsercizio(A().stato.dati.workouts || [], log.exerciseId);
    const corpo = righe.length === 0
      ? [a.el('p', { class: 'testo', testo: 'Nessuna sessione precedente con questo esercizio.' })]
      : righe.map((r) => a.el('div', { style: 'margin-bottom:10px' }, [
          a.el('div', { class: 'testo grassetto', testo: L.etichettaDataBreve(r.data) + ' — ' + r.etichetta }),
          a.el('div', { class: 'piccolo', testo: r.serie.map((x) => L.fmtSerie(x)).join(' · ') }),
        ]));
    a.apriFinestrella((ex && ex.nome) || log.nome, corpo, [a.bottoneChiudi()]);
  }

  // ---- archivio esercizi ----

  function dialogoArchivio(s) {
    const a = A();
    const el = a.el;
    const disponibili = archivio().filter((e) => !(s.exercises || []).some((x) => x.exerciseId === e.id));
    const elenco = el('div');
    const cerca = el('input', { type: 'text', placeholder: 'Cerca' });

    function disegnaElenco() {
      elenco.textContent = '';
      const q = cerca.value.trim().toLowerCase();
      const filtrati = disponibili.filter((e) => !q || e.nome.toLowerCase().indexOf(q) >= 0 || (e.gruppo || '').toLowerCase().indexOf(q) >= 0);
      if (filtrati.length === 0) {
        elenco.appendChild(el('div', { class: 'piccolo', testo: disponibili.length === 0 ? 'Hai già aggiunto tutti gli esercizi dell\'archivio.' : 'Nessun esercizio con questo nome.' }));
        return;
      }
      const gruppi = {};
      filtrati.forEach((e) => { (gruppi[e.gruppo || 'Altro'] = gruppi[e.gruppo || 'Altro'] || []).push(e); });
      Object.keys(gruppi).sort().forEach((g) => {
        elenco.appendChild(el('div', { class: 'piccolo verde', style: 'margin-top:8px', testo: g }));
        gruppi[g].forEach((e) => {
          elenco.appendChild(el('button', {
            class: 'pulsante piatto', style: 'display:block;width:100%;text-align:left;justify-content:flex-start', type: 'button',
            click: () => { a.chiudiFinestrella(); aggiungiEsercizioASessione(s, e); },
          }, [el('span', {}, [el('div', { class: 'testo', testo: e.nome }), obiettivo(e) ? el('div', { class: 'piccolo', testo: obiettivo(e) }) : null])]));
        });
      });
    }

    cerca.addEventListener('input', disegnaElenco);
    disegnaElenco();

    a.apriFinestrella('Aggiungi esercizio', [
      disponibili.length > 8 ? el('label', { class: 'campo' }, [el('span', { testo: 'Cerca' }), cerca]) : null,
      el('button', { class: 'pulsante contorno', style: 'width:100%;margin-bottom:10px', type: 'button', testo: '+ Nuovo esercizio', click: () => { a.chiudiFinestrella(); dialogoEsercizio(null, (nuovo) => aggiungiEsercizioASessione(s, nuovo)); } }),
      elenco,
    ], [a.bottoneChiudi('Fatto')]);
  }

  function aggiungiEsercizioASessione(s, ex) {
    const a = A();
    a.salva((d) => {
      const sessione = d.workouts.find((w) => w.id === s.id);
      if (!sessione) return;
      if (!(d.exercises || []).some((x) => x.id === ex.id)) d.exercises = (d.exercises || []).concat([ex]);
      if (!sessione.exercises.some((x) => x.exerciseId === ex.id)) {
        sessione.exercises = sessione.exercises.concat([{ exerciseId: ex.id, nome: ex.nome, sets: [] }]);
      }
    });
    a.stato.esercizioAperto = ex.id;
    a.disegna();
  }

  /** Il modulo dell'esercizio, col tasto che compila da solo. */
  function dialogoEsercizio(esistente, quandoSalvato, quandoEliminato) {
    const a = A();
    const el = a.el;
    const nome = el('input', { type: 'text', value: (esistente && esistente.nome) || '' });
    const gruppo = el('input', { type: 'text', value: (esistente && esistente.gruppo) || '' });
    const serie = el('input', { type: 'text', value: (esistente && esistente.serie) || '' });
    const repMin = el('input', { type: 'text', inputmode: 'numeric', value: esistente && esistente.repMin != null ? String(esistente.repMin) : '' });
    const repMax = el('input', { type: 'text', inputmode: 'numeric', value: esistente && esistente.repMax != null ? String(esistente.repMax) : '' });
    const salto = el('input', { type: 'text', inputmode: 'decimal', value: esistente && esistente.incrementoKg ? L.fmt1(esistente.incrementoKg) : '' });
    const nota = el('input', { type: 'text', value: (esistente && esistente.nota) || '' });
    const messaggio = el('div', { class: 'minuscolo', testo: 'Scrivi il nome e tocca qui: muscolo, serie, ripetizioni e salto di peso si compilano da soli.' });

    function riempi(p) {
      let quanti = 0;
      if (!gruppo.value.trim() && p.gruppo) { gruppo.value = p.gruppo; quanti++; }
      if (!serie.value.trim() && p.serie) { serie.value = p.serie; quanti++; }
      if (!repMin.value.trim() && p.repMin != null) { repMin.value = String(p.repMin); quanti++; }
      if (!repMax.value.trim() && p.repMax != null) { repMax.value = String(p.repMax); quanti++; }
      if (!salto.value.trim() && p.incrementoKg) { salto.value = L.fmt1(p.incrementoKg); quanti++; }
      if (!nota.value.trim() && p.nota) { nota.value = p.nota; quanti++; }
      return quanti;
    }

    async function compila() {
      const n = nome.value.trim();
      if (!n) { a.avviso('Scrivi prima il nome.', true); return; }
      // Prima le regole dell'app: gratis, senza internet e senza chiave.
      const saltoAbituale = L.saltoAbituale(archivio().map((e) => e.incrementoKg));
      const dalleRegole = L.compilaEsercizio(n, saltoAbituale);
      if (dalleRegole) {
        messaggio.textContent = riempi(dalleRegole) === 0
          ? 'Nessun campo vuoto da compilare: svuota quelli che vuoi rifare.'
          : 'Compilato dalle regole dell\'app: controlla e cambia quello che vuoi.';
        return;
      }
      messaggio.textContent = 'Cerco…';
      const r = await Api.post('/locale/esercizio', { nome: n });
      if (!r.ok) { messaggio.textContent = r.errore; return; }
      if (!r.dati.riconosciuto) { messaggio.textContent = 'Neanche l\'AI riconosce questo nome: scrivi i dettagli a mano.'; return; }
      messaggio.textContent = riempi(r.dati) === 0
        ? 'Nessun campo vuoto da compilare: svuota quelli che vuoi rifare.'
        : 'Compilato dall\'AI: controlla e cambia quello che vuoi.';
    }

    const azioni = [
      a.bottoneChiudi('Annulla'),
      el('button', {
        class: 'pulsante piatto', type: 'button', testo: 'Salva',
        click: () => {
          const n = nome.value.trim();
          if (!n) { a.avviso('Serve almeno il nome.', true); return; }
          const min = parseInt(repMin.value, 10);
          const max = parseInt(repMax.value, 10);
          const ordinati = (!isNaN(min) && !isNaN(max) && min > max) ? [max, min] : [min, max];
          const ex = {
            id: (esistente && esistente.id) || 'custom-ex-' + a.idNuovo(),
            nome: n,
            gruppo: gruppo.value.trim() || 'Altro',
            spinta: esistente ? !!esistente.spinta : false,
            trazione: esistente ? !!esistente.trazione : false,
            serie: serie.value.trim() || null,
            repMin: isNaN(ordinati[0]) ? null : ordinati[0],
            repMax: isNaN(ordinati[1]) ? null : ordinati[1],
            nota: nota.value.trim() || null,
            custom: esistente ? !!esistente.custom : true,
            incrementoKg: a.numero(salto.value) || null,
          };
          a.chiudiFinestrella();
          if (esistente) {
            a.salva((d) => { d.exercises = (d.exercises || []).map((x) => (x.id === ex.id ? ex : x)); });
          } else {
            const uguale = archivio().find((x) => x.nome.toLowerCase() === n.toLowerCase());
            if (uguale) { if (quandoSalvato) quandoSalvato(uguale); return; }
            a.salva((d) => { d.exercises = (d.exercises || []).concat([ex]); });
          }
          if (quandoSalvato) quandoSalvato(ex);
        },
      }),
    ];

    a.apriFinestrella(esistente ? 'Modifica esercizio' : 'Nuovo esercizio', [
      el('label', { class: 'campo' }, [el('span', { testo: 'Nome' }), nome]),
      el('button', { class: 'pulsante contorno', type: 'button', testo: 'Compila da solo', click: compila }),
      messaggio,
      el('label', { class: 'campo', style: 'margin-top:10px' }, [el('span', { testo: 'Muscolo (es. Petto)' }), gruppo]),
      el('label', { class: 'campo' }, [el('span', { testo: 'Serie (es. 3 oppure 3-4)' }), serie]),
      el('div', { class: 'campi' }, [
        el('label', { class: 'campo' }, [el('span', { testo: 'Rip. min' }), repMin]),
        el('label', { class: 'campo' }, [el('span', { testo: 'Rip. max' }), repMax]),
      ]),
      el('div', { class: 'minuscolo', testo: 'Con minimo e massimo, quando l\'ultima volta hai chiuso tutte le serie al massimo l\'app ti propone da sola di salire di peso.' }),
      el('label', { class: 'campo', style: 'margin-top:10px' }, [el('span', { testo: 'Salto di peso in kg (vuoto = 1)' }), salto]),
      el('label', { class: 'campo' }, [el('span', { testo: 'Nota (facoltativa)' }), nota]),
      esistente && quandoEliminato
        ? el('button', { class: 'pulsante piatto rosso', type: 'button', testo: 'Elimina dall\'archivio', click: () => { a.chiudiFinestrella(); quandoEliminato(); } })
        : null,
    ], azioni);
  }

  // ---- modifica di una scheda ----

  function apriEditor(scheda) {
    const a = A();
    a.stato.schedaBozza = scheda
      ? { id: scheda.id, nome: scheda.nome, exerciseIds: (scheda.exerciseIds || []).slice(), esistente: true }
      : { id: 'r-' + a.idNuovo(), nome: '', exerciseIds: [], esistente: false };
    a.disegna();
  }

  function editorScheda(app) {
    const a = A();
    const el = a.el;
    const b = a.stato.schedaBozza;
    const esercizi = b.exerciseIds.map(esercizioPerId).filter(Boolean);

    function esci() {
      const vuota = !b.nome.trim() && esercizi.length === 0;
      if (!(vuota && !b.esistente)) {
        const nome = b.nome.trim() || 'Scheda ' + ((a.stato.dati.routines || []).length + 1);
        a.salva((d) => {
          const nuova = { id: b.id, nome: nome, exerciseIds: b.exerciseIds.slice() };
          d.routines = (d.routines || []).some((r) => r.id === b.id)
            ? d.routines.map((r) => (r.id === b.id ? nuova : r))
            : (d.routines || []).concat([nuova]);
        });
      }
      a.stato.schedaBozza = null;
      a.disegna();
    }

    const nome = el('input', { type: 'text', value: b.nome });
    nome.addEventListener('input', () => { b.nome = nome.value; });

    app.appendChild(el('div', { class: 'riga', style: 'padding:6px 0 10px' }, [
      el('button', { class: 'freccia', type: 'button', 'aria-label': 'Salva e torna indietro', click: esci }, [a.icona(a.ICONE.indietro)]),
      el('div', { class: 'cresci', style: 'font-size:22px;font-weight:700', testo: b.esistente ? 'Modifica scheda' : 'Nuova scheda' }),
      el('button', { class: 'pulsante piatto', type: 'button', testo: 'Fatto', click: esci }),
    ]));

    app.appendChild(el('label', { class: 'campo' }, [el('span', { testo: 'Nome (es. Spinta, Gambe, Full body)' }), nome]));
    app.appendChild(el('div', { class: 'piccolo', style: 'margin:10px 0 6px', testo: 'Esercizi, nell\'ordine in cui li fai' }));

    if (esercizi.length === 0) {
      app.appendChild(el('div', { class: 'piccolo', testo: 'Ancora nessuno: aggiungi solo quelli che fai davvero.' }));
    }

    esercizi.forEach((e, i) => {
      app.appendChild(el('div', { class: 'riquadro' }, [
        el('div', { class: 'riga' }, [
          el('button', {
            class: 'cresci', type: 'button', style: 'border:0;background:none;font:inherit;color:inherit;text-align:left;cursor:pointer',
            click: () => dialogoEsercizio(e, () => a.disegna(), () => {
              b.exerciseIds = b.exerciseIds.filter((x) => x !== e.id);
              a.salva((d) => {
                d.exercises = (d.exercises || []).filter((x) => x.id !== e.id);
                d.routines = (d.routines || []).map((r) => Object.assign({}, r, { exerciseIds: (r.exerciseIds || []).filter((x) => x !== e.id) }));
              });
            }),
          }, [
            el('div', { class: 'testo', testo: e.nome }),
            el('div', { class: 'piccolo', testo: obiettivo(e) || 'tocca per impostare serie e ripetizioni' }),
          ]),
          el('button', { class: 'togli', type: 'button', 'aria-label': 'Sposta su', disabled: i === 0, click: () => { sposta(b, i, i - 1); a.disegna(); } }, ['▲']),
          el('button', { class: 'togli', type: 'button', 'aria-label': 'Sposta giù', disabled: i === esercizi.length - 1, click: () => { sposta(b, i, i + 1); a.disegna(); } }, ['▼']),
          el('button', { class: 'togli', type: 'button', 'aria-label': 'Togli dalla scheda', click: () => { b.exerciseIds = b.exerciseIds.filter((x) => x !== e.id); a.disegna(); } }, [a.icona(a.ICONE.chiudi, 'icona-piccola')]),
        ]),
      ]));
    });

    app.appendChild(el('button', {
      class: 'pulsante contorno', style: 'width:100%;margin-top:6px', type: 'button', testo: '+ Aggiungi esercizio',
      click: () => dialogoArchivioScheda(b),
    }));
    app.appendChild(el('div', { class: 'minuscolo', style: 'margin-top:6px', testo: 'Tocca un esercizio per cambiare nome, serie, ripetizioni e salto di peso.' }));

    if (b.esistente) {
      app.appendChild(el('button', {
        class: 'pulsante piatto rosso', style: 'margin-top:10px', type: 'button', testo: 'Elimina scheda',
        click: () => a.apriFinestrella('Eliminare la scheda?', [
          el('p', { class: 'testo', testo: 'Le sessioni già fatte restano nello storico, con il loro nome.' }),
        ], [
          a.bottoneChiudi('Annulla'),
          el('button', {
            class: 'pulsante piatto rosso', type: 'button', testo: 'Elimina',
            click: () => {
              a.chiudiFinestrella();
              a.stato.schedaBozza = null;
              a.salva((d) => { d.routines = (d.routines || []).filter((r) => r.id !== b.id); });
            },
          }),
        ]),
      }));
    }
  }

  function sposta(b, da, a_) {
    if (a_ < 0 || a_ >= b.exerciseIds.length) return;
    const l = b.exerciseIds;
    const x = l.splice(da, 1)[0];
    l.splice(a_, 0, x);
  }

  function dialogoArchivioScheda(b) {
    const a = A();
    const el = a.el;
    const elenco = el('div');
    const cerca = el('input', { type: 'text', placeholder: 'Cerca' });

    function disegnaElenco() {
      elenco.textContent = '';
      const q = cerca.value.trim().toLowerCase();
      const disponibili = archivio().filter((e) => b.exerciseIds.indexOf(e.id) < 0)
        .filter((e) => !q || e.nome.toLowerCase().indexOf(q) >= 0 || (e.gruppo || '').toLowerCase().indexOf(q) >= 0);
      if (disponibili.length === 0) {
        elenco.appendChild(el('div', { class: 'piccolo', testo: 'Nessun esercizio da aggiungere.' }));
        return;
      }
      disponibili.forEach((e) => {
        elenco.appendChild(el('button', {
          class: 'pulsante piatto', style: 'display:block;width:100%;text-align:left;justify-content:flex-start', type: 'button',
          // Resta aperto: di solito si aggiungono più esercizi di fila.
          click: () => { b.exerciseIds = b.exerciseIds.concat([e.id]); disegnaElenco(); a.disegna(); },
        }, [el('span', {}, [el('div', { class: 'testo', testo: e.nome }), obiettivo(e) ? el('div', { class: 'piccolo', testo: obiettivo(e) }) : null])]));
      });
    }

    cerca.addEventListener('input', disegnaElenco);
    disegnaElenco();

    a.apriFinestrella('Aggiungi alla scheda', [
      el('label', { class: 'campo' }, [el('span', { testo: 'Cerca' }), cerca]),
      el('button', {
        class: 'pulsante contorno', style: 'width:100%;margin-bottom:10px', type: 'button', testo: '+ Nuovo esercizio',
        click: () => { a.chiudiFinestrella(); dialogoEsercizio(null, (nuovo) => { b.exerciseIds = b.exerciseIds.concat([nuovo.id]); a.disegna(); }); },
      }),
      elenco,
    ], [a.bottoneChiudi('Fatto')]);
  }

  // ================= STORICO =================

  function storico(app) {
    const a = A();
    const el = a.el;
    const d = a.stato.dati;
    const o = a.obiettivi();
    const oggi = a.isoDiOggi();

    const giorni = giorniRegistrati(d, oggi);
    const settimana = [];
    for (let i = 0; i < 7; i++) {
      const g = a.spostaGiorno(oggi, -i);
      const s = a.statoGiorno(g);
      if (s.kcal > 0) settimana.push(s);
    }
    const tutti = giorni.map((g) => a.statoGiorno(g)).filter((s) => s.kcal > 0);

    app.appendChild(riquadroMedia('Media ultimi 7 giorni', settimana, o, 'Solo i giorni con qualcosa di registrato.'));
    app.appendChild(riquadroMedia('Media complessiva', tutti, o, 'Tutti i giorni con qualcosa di registrato, dal primo a oggi.'));

    const qualita = L.mediaQualita(settimana.map((s) => s.qualita));
    if (qualita) app.appendChild(riquadroQualita(qualita));

    app.appendChild(el('div', { class: 'titolo-schermata', testo: giorni.length <= 1 ? 'Giorni registrati' : 'Giorni registrati (' + giorni.length + ')' }));
    giorni.forEach((g) => {
      const s = a.statoGiorno(g);
      const pezzi = [L.fmtKcal(s.kcal) + ' kcal', L.fmt1(s.prot) + ' g prot'];
      if (s.acquaMl > 0) pezzi.push(L.fmtLitri(s.acquaMl) + ' L');
      const peso = (d.weights || []).find((p) => p.date === g);
      if (peso) pezzi.push(L.fmt1(peso.kg) + ' kg');
      const allenamenti = (d.workouts || []).filter((w) => w.date === g && (w.exercises || []).some((e) => (e.sets || []).length > 0));
      if (allenamenti.length > 0) pezzi.push(allenamenti.map(etichettaSessione).join('+'));
      app.appendChild(el('div', { class: 'riquadro toccabile', click: () => dettaglioGiorno(g) }, [
        el('div', { class: 'riga' }, [
          el('div', { class: 'cresci' }, [
            el('div', { class: 'testo', testo: L.etichettaDataBreve(g) }),
            el('div', { class: 'sottotitolo', testo: pezzi.join(' · ') }),
          ]),
          a.icona(a.ICONE.freccia, 'icona-piccola'),
        ]),
      ]));
    });
  }

  function giorniRegistrati(d, oggi) {
    const visti = {};
    (d.entries || []).forEach((e) => { visti[e.date] = true; });
    Object.keys(d.waterByDate || {}).forEach((g) => { if (d.waterByDate[g] > 0) visti[g] = true; });
    (d.weights || []).forEach((p) => { visti[p.date] = true; });
    (d.workouts || []).forEach((w) => { if ((w.exercises || []).some((e) => (e.sets || []).length > 0)) visti[w.date] = true; });
    visti[oggi] = true;
    return Object.keys(visti).filter((g) => g <= oggi).sort().reverse();
  }

  function riquadroMedia(titolo, giorni, o, nota) {
    const a = A();
    const el = a.el;
    if (giorni.length === 0) {
      return el('div', { class: 'riquadro' }, [
        el('div', { class: 'titolo-riquadro', testo: titolo }),
        el('div', { class: 'piccolo', style: 'margin-top:6px', testo: 'Ancora nessun giorno registrato.' }),
      ]);
    }
    const kcal = giorni.reduce((t, s) => t + s.kcal, 0) / giorni.length;
    const prot = giorni.reduce((t, s) => t + s.prot, 0) / giorni.length;
    return el('div', { class: 'riquadro' }, [
      el('div', { class: 'titolo-riquadro', style: 'margin-bottom:10px', testo: titolo }),
      a.barraObiettivo('Calorie (su ' + giorni.length + (giorni.length === 1 ? ' giorno' : ' giorni') + ')', kcal, o.kcalMin, o.kcalMax, 'kcal'),
      a.barraObiettivo('Proteine', prot, o.protMin, o.protMax, 'g'),
      el('div', { class: 'minuscolo', style: 'margin-top:8px', testo: nota }),
    ]);
  }

  function riquadroQualita(q) {
    const a = A();
    const el = a.el;
    const righe = [];
    righe.push(['Frutta e verdura', L.fmt1(q.porzioniFruttaVerdura) + ' porzioni al giorno', q.porzioniFruttaVerdura >= 3]);
    righe.push(['Fibra', L.fmt1(q.fibraG) + ' g al giorno', q.fibraG >= 30]);
    if (q.quotaUltraProcessati != null) {
      righe.push(['Da prodotti industriali', Math.round(q.quotaUltraProcessati * 100) + '% delle calorie', q.quotaUltraProcessati <= 0.35]);
    }
    return el('div', { class: 'riquadro' }, [
      el('div', { class: 'titolo-riquadro', style: 'margin-bottom:8px', testo: 'Qualità della settimana' }),
    ].concat(righe.map(([nome, valore, ok]) => el('div', { class: 'riga', style: 'padding:4px 0' }, [
      el('span', { class: 'testo cresci', testo: nome }),
      el('span', { class: 'piccolo' + (ok ? ' verde' : ''), testo: valore + (ok ? '  ✓' : '') }),
    ]))));
  }

  function dettaglioGiorno(g) {
    const a = A();
    const el = a.el;
    const d = a.stato.dati;
    const s = a.statoGiorno(g);
    const corpo = [
      el('div', { class: 'piccolo', style: 'margin-bottom:10px', testo: L.fmtKcal(s.kcal) + ' kcal · ' + L.fmt1(s.prot) + ' g proteine · ' + L.fmtLitri(s.acquaMl) + ' L' }),
    ];
    A().MOMENTI.forEach(([id, nome]) => {
      const mie = a.voci(g).filter((v) => v.moment === id);
      if (mie.length === 0) return;
      corpo.push(el('div', { class: 'piccolo verde', style: 'margin-top:8px', testo: nome }));
      mie.forEach((v) => {
        const unita = v.isDrink ? ' ml' : ' g';
        // Senza il controllo sull'ora, le voci vecchie aprirebbero la riga con un separatore.
        const pezzi = [];
        const ora = L.fmtOra(v.createdAt);
        if (ora) pezzi.push(ora);
        pezzi.push(L.fmt1(v.grams) + unita);
        if (v.kcal != null) pezzi.push('≈ ' + L.fmtKcal(v.kcal) + ' kcal');
        corpo.push(el('div', { class: 'voce' }, [
          el('div', { class: 'cresci' }, [
            el('div', { class: 'nome', testo: v.nome }),
            el('div', { class: 'dettaglio', testo: pezzi.join(' · ') }),
          ]),
        ]));
      });
    });
    const peso = (d.weights || []).find((p) => p.date === g);
    if (peso) corpo.push(el('div', { class: 'piccolo', style: 'margin-top:8px', testo: 'Peso: ' + L.fmt1(peso.kg) + ' kg' }));
    const allenamenti = (d.workouts || []).filter((w) => w.date === g && (w.exercises || []).some((e) => (e.sets || []).length > 0));
    allenamenti.forEach((w) => {
      corpo.push(el('div', { class: 'piccolo verde', style: 'margin-top:8px', testo: etichettaSessione(w) }));
      (w.exercises || []).filter((e) => (e.sets || []).length > 0).forEach((e) => {
        corpo.push(el('div', { class: 'piccolo', testo: e.nome + ': ' + e.sets.map((x) => L.fmtSerie(x)).join(' · ') }));
      });
    });
    if (corpo.length === 1) corpo.push(el('div', { class: 'piccolo', testo: 'Niente registrato in questo giorno.' }));
    a.apriFinestrella(L.etichettaData(g), corpo, [a.bottoneChiudi()]);
  }

  // ================= IMPOSTAZIONI =================

  function impostazioni(app) {
    const a = A();
    const el = a.el;
    const i = a.stato.impostazioni;

    app.appendChild(el('div', { class: 'titolo-schermata', testo: 'Impostazioni' }));

    const campi = {};
    function campoNumero(chiave, etichetta) {
      const c = el('input', { type: 'text', inputmode: 'numeric', value: String(i[chiave]) });
      campi[chiave] = c;
      return el('label', { class: 'campo' }, [el('span', { testo: etichetta }), c]);
    }

    app.appendChild(el('div', { class: 'riquadro' }, [
      el('div', { class: 'titolo-riquadro', style: 'margin-bottom:10px', testo: 'I tuoi obiettivi' }),
      el('div', { class: 'campi' }, [campoNumero('kcalMin', 'kcal minime'), campoNumero('kcalMax', 'kcal massime')]),
      el('div', { class: 'campi' }, [campoNumero('protMin', 'proteine min (g)'), campoNumero('protMax', 'proteine max (g)')]),
      campoNumero('acquaMl', 'acqua al giorno (ml)'),
      el('button', {
        class: 'pulsante contorno', type: 'button', testo: 'Salva gli obiettivi',
        click: () => salvaImpostazioni({
          kcalMin: parseInt(campi.kcalMin.value, 10), kcalMax: parseInt(campi.kcalMax.value, 10),
          protMin: parseInt(campi.protMin.value, 10), protMax: parseInt(campi.protMax.value, 10),
          acquaMl: parseInt(campi.acquaMl.value, 10),
        }),
      }),
    ]));

    // Riconoscimento foto
    const chiave = el('input', { type: 'password', placeholder: i.aiAttiva ? '•••••••• (già messa)' : 'incolla qui la tua chiave' });
    const modello = el('input', { type: 'text', value: i.aiModello, placeholder: i.aiFornitore === 'CLAUDE' ? 'claude-opus-5' : 'gemini-2.5-flash' });
    let fornitore = i.aiFornitore;
    const scelte = el('div', { class: 'scelte' });
    [['GEMINI', 'Google Gemini'], ['CLAUDE', 'Claude (Anthropic)']].forEach(([id, nome]) => {
      const b = el('button', { class: 'scelta', type: 'button', testo: nome, 'aria-pressed': String(fornitore === id) });
      b.addEventListener('click', () => {
        fornitore = id;
        Array.prototype.forEach.call(scelte.children, (c) => c.setAttribute('aria-pressed', 'false'));
        b.setAttribute('aria-pressed', 'true');
        modello.placeholder = id === 'CLAUDE' ? 'claude-opus-5' : 'gemini-2.5-flash';
      });
      scelte.appendChild(b);
    });

    app.appendChild(el('div', { class: 'riquadro' }, [
      el('div', { class: 'titolo-riquadro', style: 'margin-bottom:6px', testo: 'Riconoscimento foto' }),
      el('div', { class: 'piccolo', testo: 'Facoltativo. Con una tua chiave puoi scegliere le foto di un piatto o di un\'etichetta e farti proporre gli alimenti. Il programma non contiene nessuna chiave: paghi solo il tuo uso.' }),
      scelte,
      el('label', { class: 'campo' }, [el('span', { testo: 'La tua chiave' }), chiave]),
      el('label', { class: 'campo' }, [el('span', { testo: 'Modello (vuoto = quello predefinito)' }), modello]),
      el('div', { class: 'minuscolo', testo: 'La chiave resta su questo computer, cifrata da Windows, e viene usata solo quando lo chiedi tu: per le foto che scegli e per compilare un esercizio dal nome. Non esce nient\'altro del diario.' }),
      el('div', { class: 'pulsanti', style: 'margin-top:10px' }, [
        el('button', {
          class: 'pulsante contorno', type: 'button', testo: 'Salva la chiave',
          click: () => {
            const c = { aiFornitore: fornitore, aiModello: modello.value.trim() };
            if (chiave.value.trim()) c.aiChiave = chiave.value.trim();
            chiave.value = '';
            salvaImpostazioni(c);
          },
        }),
        i.aiAttiva ? el('button', { class: 'pulsante piatto rosso', type: 'button', testo: 'Togli la chiave', click: () => salvaImpostazioni({ aiChiaveTogli: true }) }) : null,
      ]),
    ]));

    // Scheda prodotto + avvio
    app.appendChild(el('div', { class: 'riquadro' }, [
      interruttore('Scheda del prodotto dopo ogni inserimento', i.mostraScheda, (v) => salvaImpostazioni({ mostraScheda: v })),
      el('div', { class: 'minuscolo', testo: 'Compare per cinque secondi con tutti i valori; un clic la mette in pausa.' }),
      el('div', { style: 'height:10px' }),
      interruttore('Apri Surplus all\'accesso a Windows', i.avvioAlLogin, (v) => salvaImpostazioni({ avvioAlLogin: v })),
    ]));

    // Dati sul NAS
    const riquadroNas = el('div', { class: 'riquadro' });
    app.appendChild(riquadroNas);
    disegnaNas(riquadroNas);
    if (statoNas === null) chiediStatoNas(riquadroNas);

    // Backup
    app.appendChild(el('div', { class: 'riquadro' }, [
      el('div', { class: 'titolo-riquadro', style: 'margin-bottom:6px', testo: 'Copia del diario' }),
      el('div', { class: 'piccolo', testo: 'Lo stesso file del telefono: puoi spostare i dati da una parte all\'altra.' }),
      el('div', { class: 'pulsanti', style: 'margin-top:10px' }, [
        el('button', { class: 'pulsante contorno', type: 'button', testo: 'Esporta', click: esporta }),
        el('button', { class: 'pulsante contorno', type: 'button', testo: 'Importa', click: importa }),
      ]),
    ]));

    app.appendChild(el('div', { class: 'minuscolo', style: 'text-align:center;padding:14px 0' , testo: 'Surplus per il computer ' + (a.stato.versioneProgramma || '') + (statoNas && statoNas.abbinato ? ' — diario su questo computer e sul NAS' : ' — dati salvati su questo computer') }));
    const agg = statoNas && statoNas.aggiornamento;
    if (agg && agg.pronta) {
      app.appendChild(el('div', { class: 'minuscolo verde', style: 'text-align:center', testo: 'La versione ' + agg.nuova + ' è pronta: si installa da sola quando chiudi Surplus.' }));
    } else if (agg && agg.errore) {
      app.appendChild(el('div', { class: 'minuscolo rosso', style: 'text-align:center', testo: agg.errore }));
    }
    app.appendChild(el('div', { class: 'minuscolo', style: 'text-align:center;padding-bottom:20px', testo: 'I promemoria degli spuntini restano sul telefono.' }));
  }

  // ---- il NAS di casa ----

  /** Ultimo stato della sincronizzazione (null = non l'abbiamo ancora chiesto). */
  let statoNas = null;

  async function chiediStatoNas(riquadro) {
    const r = await Api.get('/locale/nas');
    if (!r.ok) return;
    statoNas = r.dati.stato;
    if (riquadro && riquadro.isConnected) disegnaNas(riquadro);
  }

  function oraDi(ms) {
    if (!ms) return null;
    const d = new Date(ms);
    const oggi = new Date();
    const stessoGiorno = d.toDateString() === oggi.toDateString();
    const ora = String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
    return stessoGiorno ? 'alle ' + ora : 'il ' + String(d.getDate()).padStart(2, '0') + '/' + String(d.getMonth() + 1).padStart(2, '0') + ' alle ' + ora;
  }

  function disegnaNas(riquadro) {
    const a = A();
    const el = a.el;
    riquadro.textContent = '';
    riquadro.appendChild(el('div', { class: 'titolo-riquadro', style: 'margin-bottom:6px', testo: 'Diario sul NAS di casa' }));

    if (statoNas === null) {
      riquadro.appendChild(el('div', { class: 'piccolo', testo: 'Sto guardando…' }));
      return;
    }

    if (!statoNas.abbinato) {
      const indirizzo = el('input', { type: 'text', value: statoNas.indirizzo || '', placeholder: 'nome-del-nas:8600' });
      const codice = el('input', { type: 'text', inputmode: 'numeric', placeholder: '8 cifre' });
      riquadro.appendChild(el('div', { class: 'piccolo', testo: 'Il diario può stare anche sul NAS: così il telefono e il computer vedono le stesse cose, e quello che scrivi non vive su un solo apparecchio. Niente account e niente internet: i dati restano in casa.' }));
      riquadro.appendChild(el('label', { class: 'campo' }, [el('span', { testo: 'Indirizzo del NAS' }), indirizzo]));
      riquadro.appendChild(el('label', { class: 'campo' }, [el('span', { testo: 'Codice del NAS' }), codice]));
      riquadro.appendChild(el('div', { class: 'minuscolo', testo: 'Il codice lo dà il NAS e vale un quarto d\'ora.' }));
      riquadro.appendChild(el('div', { class: 'pulsanti', style: 'margin-top:10px' }, [
        el('button', {
          class: 'pulsante contorno', type: 'button', testo: 'Abbina questo computer',
          click: async (e) => {
            const b = e.currentTarget;
            b.disabled = true;
            b.textContent = 'Sto abbinando…';
            const r = await Api.post('/locale/nas/abbina', { indirizzo: indirizzo.value, codice: codice.value });
            if (r.dati && r.dati.stato) statoNas = r.dati.stato;
            if (!r.ok) {
              a.avviso(r.errore, true);
              disegnaNas(riquadro);
              return;
            }
            await ricaricaDopoSincronizzazione();
            a.avviso('Computer abbinato al NAS.');
          },
        }),
      ]));
      return;
    }

    const ultima = oraDi(statoNas.ultima);
    riquadro.appendChild(el('div', { class: 'piccolo', testo: 'Abbinato a ' + (statoNas.indirizzo || 'il NAS') + '.' }));
    riquadro.appendChild(el('div', { class: 'piccolo', testo: ultima ? 'Ultima sincronizzazione ' + ultima + '.' : 'Non si è ancora sincronizzato.' }));
    if (statoNas.errore) {
      riquadro.appendChild(el('div', { class: 'piccolo rosso', style: 'margin-top:6px', testo: statoNas.errore }));
    }
    riquadro.appendChild(el('div', { class: 'minuscolo', style: 'margin-top:6px', testo: 'Si sincronizza da solo: quello che scrivi sul telefono compare qui in un paio di secondi, e viceversa. Quello che scrivi senza NAS acceso parte appena torna.' }));
    riquadro.appendChild(el('div', { class: 'pulsanti', style: 'margin-top:10px' }, [
      el('button', {
        class: 'pulsante contorno', type: 'button', testo: 'Sincronizza adesso',
        click: async (e) => {
          const b = e.currentTarget;
          b.disabled = true;
          b.textContent = 'Sto sincronizzando…';
          const r = await Api.post('/locale/nas/sincronizza');
          if (r.dati && r.dati.stato) statoNas = r.dati.stato;
          if (!r.ok) {
            a.avviso(r.errore, true);
            disegnaNas(riquadro);
            return;
          }
          await ricaricaDopoSincronizzazione();
          a.avviso(r.dati && r.dati.cambiato ? 'Diario aggiornato dal NAS.' : 'Già uguale al NAS.');
        },
      }),
      el('button', {
        class: 'pulsante piatto rosso', type: 'button', testo: 'Stacca',
        click: () => {
          a.apriFinestrella('Staccare questo computer?', [
            a.el('p', { class: 'testo', testo: 'Il diario resta su questo computer e resta anche sul NAS, ma non si aggiornano più a vicenda. Per rimetterli insieme serve un codice nuovo.' }),
          ], [
            a.bottoneChiudi('Annulla'),
            a.el('button', {
              class: 'pulsante piatto rosso', type: 'button', testo: 'Stacca',
              click: async () => {
                a.chiudiFinestrella();
                const r = await Api.post('/locale/nas/stacca');
                if (r.dati && r.dati.stato) statoNas = r.dati.stato;
                a.disegna();
                a.avviso('Computer staccato dal NAS.');
              },
            }),
          ]);
        },
      }),
    ]));
  }

  /** Dopo una sincronizzazione il diario può essere cambiato: si rilegge dal programma. */
  async function ricaricaDopoSincronizzazione() {
    const a = A();
    const r = await Api.get('/locale/stato');
    if (r.ok) {
      a.stato.dati = r.dati.dati;
      a.stato.versione = r.dati.versione;
      a.stato.impostazioni = r.dati.impostazioni;
    }
    a.disegna();
  }

  function interruttore(etichetta, acceso, quandoCambia) {
    const a = A();
    const spunta = a.el('input', { type: 'checkbox', checked: acceso });
    spunta.addEventListener('change', () => quandoCambia(spunta.checked));
    return a.el('label', { class: 'riga', style: 'justify-content:flex-start;gap:10px;cursor:pointer' }, [
      spunta, a.el('span', { class: 'testo cresci', testo: etichetta }),
    ]);
  }

  async function salvaImpostazioni(cambi) {
    const a = A();
    const r = await Api.post('/locale/impostazioni', cambi);
    if (!r.ok) { a.avviso(r.errore, true); return; }
    a.stato.impostazioni = r.dati.impostazioni;
    a.disegna();
    a.avviso('Impostazioni salvate.');
  }

  async function esporta() {
    const a = A();
    const r = await Api.post('/locale/esporta');
    if (!r.ok) { a.avviso(r.errore, true); return; }
    if (r.dati.annullato) return;
    a.avviso('Copia salvata.');
  }

  async function importa() {
    const a = A();
    const r = await Api.post('/locale/importa');
    if (!r.ok) { a.avviso(r.errore, true); return; }
    if (r.dati.annullato) return;
    const dati = r.dati.dati;
    a.apriFinestrella('Ripristinare questo backup?', [
      a.el('p', { class: 'testo', testo: 'Il diario di questo computer verrà sostituito da quello del file: ' + (dati.entries || []).length + ' voci e ' + (dati.workouts || []).length + ' allenamenti.' }),
    ], [
      a.bottoneChiudi('Annulla'),
      a.el('button', {
        class: 'pulsante piatto', type: 'button', testo: 'Ripristina',
        click: async () => {
          a.chiudiFinestrella();
          const esito = await Api.post('/locale/salva', { dati: dati });
          if (!esito.ok) { a.avviso(esito.errore, true); return; }
          a.stato.dati = dati;
          a.disegna();
          a.avviso('Diario ripristinato.');
        },
      }),
    ]);
  }

  radice.SurplusSchermate = { allenamento: allenamento, storico: storico, impostazioni: impostazioni };
})(window);
