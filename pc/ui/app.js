/*
 * Surplus per il computer: le schermate.
 *
 * Stesse schermate dell'app Android, stessi testi, stessi colori. Qui si disegna
 * e si raccolgono i tocchi; i conti (consigli, qualità, regole dell'allenamento)
 * stanno in logica.js, portati dal telefono con gli stessi test.
 *
 * Il diario è un unico documento JSON, identico a quello del telefono: la pagina
 * lo modifica e lo rimanda intero al programma, che lo scrive sul disco.
 */
(function (radice) {
  'use strict';

  const L = radice.SurplusLogica;
  const Api = radice.SurplusApi;

  const MOMENTI = [
    ['COLAZIONE', 'Colazione'],
    ['META_MATTINA', 'Metà mattina'],
    ['PRANZO', 'Pranzo'],
    ['POST_ALLENAMENTO', 'Dopo allenamento'],
    ['CENA', 'Cena'],
    ['PRE_NANNA', 'Prima di dormire'],
    ['BEVANDE', 'Bevande'],
  ];

  const GRUPPI = [
    ['VERDURA', 'Verdura'], ['FRUTTA', 'Frutta'], ['PROTEICO', 'Proteico'], ['CEREALE', 'Cereali'],
    ['LATTICINO', 'Latticini'], ['GRASSI', 'Grassi'], ['DOLCE', 'Dolci'], ['ALTRO', 'Altro'],
  ];

  const NOVA = [
    [1, 'Cibo vero', 'Frutta, verdura, uova, carne e pesce freschi, riso, pasta secca, latte.'],
    [2, 'Ingrediente', 'Usato per cucinare: olio, burro, zucchero, sale.'],
    [3, 'Lavorato', 'Pane, formaggi, prosciutto, cibo in scatola, conserve.'],
    [4, 'Industriale', 'Merendine, bibite, snack confezionati, salse pronte, cereali da colazione.'],
  ];

  const stato = {
    dati: null,
    impostazioni: null,
    schermata: 'oggi',
    giorno: isoDiOggi(),
    sessione: null,      // id della sessione di allenamento aperta
    schedaBozza: null,   // scheda in modifica { id, nome, exerciseIds, esistente }
    esercizioAperto: null,
    finestrella: null,   // dialogo aperto
  };

  // ---- strumenti ----

  function el(tag, attributi, figli) {
    const n = document.createElement(tag);
    if (attributi) {
      for (const k in attributi) {
        const v = attributi[k];
        if (v === null || v === undefined || v === false) continue;
        if (k === 'class') n.className = v;
        else if (k === 'testo') n.textContent = v;
        else if (k === 'click') n.addEventListener('click', v);
        else if (k === 'input') n.addEventListener('input', v);
        // Lo stile si applica dal codice, non come attributo: le regole di sicurezza
        // della pagina vietano gli stili scritti dentro l'HTML, e un attributo bloccato
        // sparirebbe in silenzio (è successo: tutte le barre sembravano piene).
        else if (k === 'style') n.style.cssText = v;
        else n.setAttribute(k, v === true ? '' : v);
      }
    }
    (figli || []).forEach((f) => { if (f) n.appendChild(typeof f === 'string' ? document.createTextNode(f) : f); });
    return n;
  }

  function icona(percorso, classe) {
    const s = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    s.setAttribute('viewBox', '0 0 24 24');
    s.setAttribute('aria-hidden', 'true');
    s.setAttribute('class', classe || 'icona');
    const p = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    p.setAttribute('d', percorso);
    s.appendChild(p);
    return s;
  }

  const ICONE = {
    casa: 'M12 3l9 8h-3v9h-5v-6h-2v6H6v-9H3z',
    manubrio: 'M4 9h2v6H4zm3-2h2v10H7zm8 0h2v10h-2zm3 2h2v6h-2zM10 11h4v2h-4z',
    calendario: 'M5 4h14a1 1 0 011 1v14a1 1 0 01-1 1H5a1 1 0 01-1-1V5a1 1 0 011-1zm1 5v9h12V9zm2 2h2v2H8zm4 0h2v2h-2z',
    ingranaggio: 'M12 8a4 4 0 104 4 4 4 0 00-4-4zm8.9 4a7 7 0 00-.1-1.2l2-1.6-2-3.4-2.4 1a7 7 0 00-2-1.2L16 3H8l-.4 2.6a7 7 0 00-2 1.2l-2.4-1-2 3.4 2 1.6a7 7 0 000 2.4l-2 1.6 2 3.4 2.4-1a7 7 0 002 1.2L8 21h8l.4-2.6a7 7 0 002-1.2l2.4 1 2-3.4-2-1.6c0-.4.1-.8.1-1.2z',
    piu: 'M11 5h2v6h6v2h-6v6h-2v-6H5v-2h6z',
    foto: 'M4 7h3l2-2h6l2 2h3v12H4zm8 2.5a4 4 0 104 4 4 4 0 00-4-4z',
    info: 'M12 2a10 10 0 100 20 10 10 0 000-20zm1 15h-2v-6h2zm0-8h-2V7h2z',
    matita: 'M3 17.2V21h3.8l11-11-3.8-3.8zM20.7 7.3a1 1 0 000-1.4l-2.6-2.6a1 1 0 00-1.4 0l-1.8 1.8L18.9 9z',
    cestino: 'M6 7h12l-1 14H7zM9 4h6l1 2H8z',
    freccia: 'M9 6l6 6-6 6z',
    indietro: 'M15 6l-6 6 6 6z',
    chiudi: 'M18.3 5.7L12 12l6.3 6.3-1.4 1.4L10.6 13.4 4.3 19.7 2.9 18.3 9.2 12 2.9 5.7 4.3 4.3l6.3 6.3 6.3-6.3z',
  };

  function isoDiOggi() {
    const d = new Date();
    return isoDi(d);
  }

  function isoDi(d) {
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const g = String(d.getDate()).padStart(2, '0');
    return d.getFullYear() + '-' + m + '-' + g;
  }

  function spostaGiorno(iso, giorni) {
    const [a, m, g] = iso.split('-').map(Number);
    const d = new Date(a, m - 1, g);
    d.setDate(d.getDate() + giorni);
    return isoDi(d);
  }

  function idNuovo(prefisso) {
    const c = radice.crypto;
    const u = c && c.randomUUID ? c.randomUUID() : String(Date.now()) + Math.random().toString(16).slice(2);
    return prefisso ? prefisso + '-' + u : u;
  }

  function numero(testo) {
    if (typeof testo !== 'string') return null;
    const v = parseFloat(testo.replace(',', '.'));
    return Number.isFinite(v) ? v : null;
  }

  function avviso(testo, male) {
    const box = document.getElementById('avvisi');
    const a = el('div', { class: 'avviso' + (male ? ' male' : ''), testo: testo });
    box.appendChild(a);
    setTimeout(() => a.remove(), male ? 6000 : 3500);
  }

  // ---- dati ----

  function voci(giorno) {
    return stato.dati.entries.filter((e) => e.date === giorno);
  }

  function alimenti() {
    return (stato.dati.customFoods || []);
  }

  function obiettivi() {
    const i = stato.impostazioni;
    return { kcalMin: i.kcalMin, kcalMax: i.kcalMax, protMin: i.protMin, protMax: i.protMax, acquaMl: i.acquaMl };
  }

  function statoGiorno(giorno) {
    const v = voci(giorno);
    const t = L.totaliGiorno(v);
    return {
      kcal: t.kcal,
      prot: t.prot,
      nonNoti: t.nonNoti,
      numeroPerMomento: t.numeroPerMomento,
      acquaMl: (stato.dati.waterByDate || {})[giorno] || 0,
      allenato: L.allenatoIl(stato.dati.workouts, giorno),
      qualita: L.qualitaGiorno(v),
    };
  }

  /** Modifica il documento e lo manda al programma: se il salvataggio fallisce si torna indietro. */
  async function salva(cambia) {
    const prima = JSON.stringify(stato.dati);
    cambia(stato.dati);
    disegna();
    const r = await Api.post('/locale/salva', { dati: stato.dati });
    if (!r.ok) {
      stato.dati = JSON.parse(prima);
      disegna();
      avviso(r.errore, true);
      return false;
    }
    return true;
  }

  // ---- avvio ----

  async function avvia() {
    const r = await Api.get('/locale/stato');
    if (!r.ok) {
      document.getElementById('app').textContent = r.errore;
      return;
    }
    stato.dati = r.dati.dati;
    stato.impostazioni = r.dati.impostazioni;
    stato.versioneProgramma = r.dati.versioneProgramma;
    document.getElementById('app').removeAttribute('aria-busy');
    orologio();
    setInterval(orologio, 20000);
    adattaZoom();
    radice.addEventListener('resize', adattaZoom);
    disegnaBarra();
    disegna();
  }

  function orologio() {
    const d = new Date();
    document.getElementById('orologio').textContent =
      String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
  }

  /** Il telefono disegnato si adatta alla finestra, come fa lo zoom del simulatore. */
  function adattaZoom() {
    const largo = 412 + 28;
    const alto = 915 + 28;
    const z = Math.min(radice.innerWidth / largo, radice.innerHeight / alto);
    document.documentElement.style.setProperty('--zoom', String(Math.min(1.6, Math.max(0.4, z))));
  }

  function disegnaBarra() {
    const barra = document.getElementById('barra');
    barra.textContent = '';
    [['oggi', 'Oggi', ICONE.casa], ['allenamento', 'Allenamento', ICONE.manubrio],
     ['storico', 'Storico', ICONE.calendario], ['impostazioni', 'Impostazioni', ICONE.ingranaggio]]
      .forEach(([id, nome, d]) => {
        const b = el('button', {
          class: 'voce-barra',
          type: 'button',
          click: () => { stato.schermata = id; stato.sessione = null; stato.schedaBozza = null; disegnaBarra(); disegna(); },
        }, [
          el('span', { class: 'pillola' }, [icona(d)]),
          el('span', { class: 'etichetta', testo: nome }),
        ]);
        if (stato.schermata === id) b.setAttribute('aria-current', 'page');
        barra.appendChild(b);
      });
  }

  function disegna() {
    const app = document.getElementById('app');
    app.textContent = '';
    app.scrollTop = app.scrollTop; // la posizione resta dov'è tra un disegno e l'altro
    const altre = radice.SurplusSchermate;
    if (stato.schermata === 'oggi') schermataOggi(app);
    else if (stato.schermata === 'allenamento') altre.allenamento(app);
    else if (stato.schermata === 'storico') altre.storico(app);
    else altre.impostazioni(app);
    if (stato.finestrella) app.parentElement.appendChild(stato.finestrella);
  }

  // ---- finestrelle ----

  function apriFinestrella(titolo, contenuto, azioni) {
    chiudiFinestrella();
    const velo = el('div', { class: 'velo' }, [
      el('div', { class: 'finestrella' }, [
        titolo ? el('h2', { testo: titolo }) : null,
        el('div', { class: 'contenuto' }, contenuto),
        el('div', { class: 'azioni' }, azioni),
      ]),
    ]);
    velo.addEventListener('click', (e) => { if (e.target === velo) chiudiFinestrella(); });
    stato.finestrella = velo;
    document.querySelector('.schermo').appendChild(velo);
    const primo = velo.querySelector('input, button');
    if (primo) primo.focus();
    return velo;
  }

  function chiudiFinestrella() {
    if (stato.finestrella) stato.finestrella.remove();
    stato.finestrella = null;
  }

  function bottoneChiudi(testo) {
    return el('button', { class: 'pulsante piatto', type: 'button', testo: testo || 'Chiudi', click: chiudiFinestrella });
  }

  // ---- schermata Oggi ----

  function schermataOggi(app) {
    const g = stato.giorno;
    const s = statoGiorno(g);
    const o = obiettivi();

    const intestazione = el('div', { class: 'intestazione-giorno' }, [
      el('button', { class: 'freccia', type: 'button', 'aria-label': 'Giorno precedente', click: () => { stato.giorno = spostaGiorno(g, -1); disegna(); } }, [icona(ICONE.indietro)]),
      el('div', { class: 'giorno', testo: L.etichettaData(g) }),
      el('button', {
        class: 'freccia', type: 'button', 'aria-label': 'Giorno successivo',
        disabled: g >= isoDiOggi(),
        click: () => { if (g < isoDiOggi()) { stato.giorno = spostaGiorno(g, 1); disegna(); } },
      }, [icona(ICONE.freccia)]),
    ]);
    app.appendChild(intestazione);

    // Obiettivi
    app.appendChild(el('div', { class: 'riquadro' }, [
      barraObiettivo('Calorie', s.kcal, o.kcalMin, o.kcalMax, 'kcal'),
      barraObiettivo('Proteine', s.prot, o.protMin, o.protMax, 'g'),
      s.nonNoti > 0 ? el('div', { class: 'minuscolo', testo: s.nonNoti + (s.nonNoti === 1 ? ' voce senza valori' : ' voci senza valori') }) : null,
    ]));

    // Consigli
    const lista = L.consigli(new Date(), s, scorsoGiornoStato(g), o);
    if (lista.length > 0) {
      const riquadro = el('div', { class: 'riquadro consigli' }, [el('div', { class: 'grassetto', testo: 'Consigli' })]);
      lista.forEach((c) => {
        riquadro.appendChild(el('div', {
          class: 'riga riga-alto toccabile',
          style: 'cursor:pointer;padding:6px 0',
          click: () => apriFinestrella('Perché questo consiglio', [el('p', { class: 'testo', testo: c.perche })], [bottoneChiudi('Ok')]),
        }, [
          el('div', { class: 'testo cresci', testo: '• ' + c.testo }),
          icona(ICONE.info, 'icona-piccola'),
        ]));
      });
      app.appendChild(riquadro);
    }

    // Acqua
    const acqua = s.acquaMl;
    const daBevande = voci(g).filter((v) => v.isDrink).reduce((t, v) => t + (v.grams || 0), 0);
    app.appendChild(el('div', { class: 'riquadro' }, [
      el('div', { class: 'riga' }, [
        el('span', { class: 'testo', testo: 'Acqua' }),
        el('span', { class: 'piccolo', testo: L.fmtLitri(acqua) + ' / ' + L.fmtLitri(o.acquaMl) + ' L' }),
      ]),
      el('div', { class: 'barra' }, [el('span', { class: 'acqua', style: 'width:' + percento(acqua, o.acquaMl) + '%' })]),
      el('div', { class: 'pulsanti', style: 'margin-top:12px' }, [
        el('button', { class: 'pulsante contorno', type: 'button', testo: '+250 ml', click: () => cambiaAcqua(g, 250) }),
        el('button', { class: 'pulsante contorno', type: 'button', testo: '+500 ml', click: () => cambiaAcqua(g, 500) }),
        el('button', { class: 'pulsante piatto', type: 'button', testo: '−250', click: () => cambiaAcqua(g, -250) }),
      ]),
      daBevande > 0 ? el('div', { class: 'minuscolo', style: 'margin-top:10px', testo: 'Di cui ' + Math.round(daBevande) + ' ml da bevande registrate nel diario: contano tutte come liquidi. I tasti servono per l\'acqua pura.' }) : null,
    ]));

    // I momenti della giornata
    MOMENTI.forEach(([id, nome]) => {
      const mie = voci(g).filter((v) => v.moment === id);
      const kcal = mie.reduce((t, v) => t + (v.kcal || 0), 0);
      const prot = mie.reduce((t, v) => t + (v.prot || 0), 0);
      const riquadro = el('div', { class: 'riquadro' }, [
        el('div', { class: 'riga' }, [
          el('span', { class: 'titolo-riquadro', testo: nome }),
          mie.length > 0 ? el('span', { class: 'piccolo', testo: L.fmtKcal(kcal) + ' kcal · ' + L.fmt1(prot) + ' g prot' }) : null,
        ]),
      ]);
      mie.forEach((v) => {
        const unita = v.isDrink ? ' ml' : ' g';
        const pezzi = [L.fmtOra(v.createdAt), L.fmt1(v.grams) + unita];
        if (v.kcal != null) pezzi.push('≈ ' + L.fmtKcal(v.kcal) + ' kcal, ' + L.fmt1(v.prot || 0) + ' g prot');
        riquadro.appendChild(el('div', { class: 'voce' }, [
          el('div', { class: 'cresci' }, [
            el('div', { class: 'nome', testo: v.nome }),
            el('div', { class: 'dettaglio', testo: pezzi.join(' · ') }),
          ]),
          el('button', { class: 'togli', type: 'button', 'aria-label': 'Togli ' + v.nome, click: () => togliVoce(v.id) }, [icona(ICONE.chiudi, 'icona-piccola')]),
        ]));
      });
      riquadro.appendChild(el('div', { style: 'display:flex;gap:8px;margin-top:4px' }, [
        el('button', { class: 'pulsante piatto', type: 'button', click: () => dialogoAggiungi(id) }, [icona(ICONE.piu), 'Aggiungi']),
        el('button', { class: 'pulsante piatto', type: 'button', click: () => dialogoFoto(id) }, [icona(ICONE.foto), 'Foto']),
      ]));
      app.appendChild(riquadro);
    });

    // Peso
    const pesi = stato.dati.weights || [];
    const diOggi = pesi.find((p) => p.date === g);
    app.appendChild(el('div', { class: 'riquadro' }, [
      el('div', { class: 'riga' }, [
        el('span', { class: 'testo', testo: 'Peso' }),
        el('span', { class: 'piccolo', testo: diOggi ? L.fmt1(diOggi.kg) + ' kg' : 'non registrato' }),
      ]),
      el('button', { class: 'pulsante piatto', style: 'margin-top:6px', type: 'button', testo: diOggi ? 'Correggi il peso' : 'Segna il peso di oggi', click: dialogoPeso }),
      regolaPesoTesto(),
    ]));
  }

  function regolaPesoTesto() {
    const r = L.regolaPeso(stato.dati.weights || []);
    if (!r || !r.testo) return null;
    return el('div', { class: 'minuscolo', style: 'margin-top:6px', testo: r.testo });
  }

  function scorsoGiornoStato(g) {
    const ieri = spostaGiorno(g, -1);
    return voci(ieri).length > 0 ? statoGiorno(ieri) : null;
  }

  function barraObiettivo(nome, valore, min, max, unita) {
    const dentro = valore >= min && valore <= max;
    const oltre = valore > max + max * 0.08;
    return el('div', { class: 'obiettivo' }, [
      el('div', { class: 'riga' }, [
        el('span', { class: 'nome', testo: nome }),
        el('span', { class: 'valore' + (dentro ? ' ok' : ''), testo: L.fmtKcal(valore) + ' / ' + L.fmtInt(min) + '–' + L.fmtInt(max) + ' ' + unita + (dentro ? '  ✓' : '') }),
      ]),
      el('div', { class: 'barra' }, [el('span', { class: oltre ? 'oltre' : '', style: 'width:' + percento(valore, max) + '%' })]),
    ]);
  }

  function percento(valore, massimo) {
    if (!massimo) return 0;
    return Math.max(0, Math.min(100, Math.round((valore / massimo) * 100)));
  }

  function cambiaAcqua(giorno, delta) {
    salva((d) => {
      const attuale = (d.waterByDate || {})[giorno] || 0;
      d.waterByDate = Object.assign({}, d.waterByDate);
      d.waterByDate[giorno] = Math.max(0, attuale + delta);
    });
  }

  function togliVoce(id) {
    salva((d) => {
      const v = d.entries.find((x) => x.id === id);
      d.entries = d.entries.filter((x) => x.id !== id);
      // Togliere una bevanda toglie anche i suoi ml dal contatore: simmetrico all'inserimento.
      if (v && v.isDrink) {
        const attuale = (d.waterByDate || {})[v.date] || 0;
        d.waterByDate = Object.assign({}, d.waterByDate);
        d.waterByDate[v.date] = Math.max(0, attuale - Math.round(v.grams || 0));
      }
    });
  }

  function dialogoPeso() {
    const campo = el('input', { type: 'text', inputmode: 'decimal', placeholder: 'kg' });
    const diOggi = (stato.dati.weights || []).find((p) => p.date === stato.giorno);
    if (diOggi) campo.value = L.fmt1(diOggi.kg);
    apriFinestrella('Peso di ' + L.etichettaData(stato.giorno).toLowerCase(), [
      el('label', { class: 'campo' }, [el('span', { testo: 'Chilogrammi' }), campo]),
      el('div', { class: 'minuscolo', testo: 'Pesati sempre nello stesso momento: appena sveglio, prima di mangiare.' }),
    ], [
      bottoneChiudi('Annulla'),
      el('button', {
        class: 'pulsante piatto', type: 'button', testo: 'Salva',
        click: () => {
          const kg = numero(campo.value);
          if (!kg || kg <= 0) { avviso('Scrivi un peso valido.', true); return; }
          chiudiFinestrella();
          salva((d) => {
            d.weights = (d.weights || []).filter((p) => p.date !== stato.giorno).concat([{ date: stato.giorno, kg: kg }]);
          });
        },
      }),
    ]);
  }

  // ---- aggiungere un alimento ----

  function dialogoAggiungi(momento) {
    let scelto = null;
    let modoManuale = false;

    const cerca = el('input', { type: 'text', placeholder: 'Cerca in dispensa o scrivi un codice a barre' });
    const risultati = el('div');
    const grammi = el('input', { type: 'text', inputmode: 'decimal', placeholder: 'quantità' });
    const sceltaBox = el('div');

    function mostraDispensa() {
      risultati.textContent = '';
      const q = cerca.value.trim().toLowerCase();
      const trovati = alimenti().filter((a) => !q || a.nome.toLowerCase().indexOf(q) >= 0).slice(0, 8);
      trovati.forEach((a) => {
        risultati.appendChild(el('button', {
          class: 'riquadro toccabile', type: 'button', style: 'display:block;width:100%;text-align:left;border:0;font:inherit;color:inherit;margin-bottom:6px',
          click: () => { scelto = a; modoManuale = false; aggiorna(); },
        }, [
          el('div', { class: 'testo', testo: a.nome }),
          el('div', { class: 'piccolo', testo: descriviAlimento(a) }),
        ]));
      });
      if (q.length >= 2) {
        risultati.appendChild(el('button', {
          class: 'pulsante piatto', type: 'button', testo: 'Cerca «' + cerca.value.trim() + '» negli archivi online',
          click: () => cercaOnline(cerca.value.trim()),
        }));
      }
    }

    async function cercaOnline(q) {
      risultati.textContent = '';
      risultati.appendChild(el('div', { class: 'piccolo', testo: 'Cerco…' }));
      const r = await Api.get('/locale/cerca?q=' + encodeURIComponent(q));
      risultati.textContent = '';
      if (!r.ok) { risultati.appendChild(el('div', { class: 'piccolo rosso', testo: r.errore })); return; }
      const trovati = r.dati.risultati || [];
      if (trovati.length === 0) {
        risultati.appendChild(el('div', { class: 'piccolo', testo: 'Nessun prodotto trovato. Puoi inserirlo a mano.' }));
        return;
      }
      trovati.forEach((a) => {
        risultati.appendChild(el('button', {
          class: 'riquadro toccabile', type: 'button', style: 'display:block;width:100%;text-align:left;border:0;font:inherit;color:inherit;margin-bottom:6px',
          click: () => { scelto = Object.assign({ id: idNuovo('ai') }, a); modoManuale = false; aggiorna(); },
        }, [
          el('div', { class: 'testo', testo: a.nome }),
          el('div', { class: 'piccolo', testo: descriviAlimento(a) }),
        ]));
      });
    }

    function aggiorna() {
      sceltaBox.textContent = '';
      if (scelto) {
        sceltaBox.appendChild(el('div', { class: 'riquadro' }, [
          el('div', { class: 'testo', testo: scelto.nome }),
          el('div', { class: 'piccolo', testo: descriviAlimento(scelto) }),
          scelto.hint ? el('div', { class: 'minuscolo', testo: scelto.hint }) : null,
        ]));
      }
      grammi.placeholder = scelto && scelto.isDrink ? 'millilitri' : 'grammi';
    }

    const manuale = campiManuali();

    cerca.addEventListener('input', () => { if (!modoManuale) mostraDispensa(); });
    mostraDispensa();
    aggiorna();

    const corpo = [
      el('label', { class: 'campo' }, [el('span', { testo: 'Alimento' }), cerca]),
      risultati,
      sceltaBox,
      el('label', { class: 'campo' }, [el('span', { testo: 'Quantità' }), grammi]),
      el('button', {
        class: 'pulsante piatto', type: 'button', testo: 'Scrivilo a mano',
        click: (e) => {
          modoManuale = !modoManuale;
          scelto = null;
          manuale.box.style.display = modoManuale ? '' : 'none';
          risultati.style.display = modoManuale ? 'none' : '';
          e.target.textContent = modoManuale ? 'Torna alla ricerca' : 'Scrivilo a mano';
          aggiorna();
        },
      }),
      manuale.box,
    ];
    manuale.box.style.display = 'none';

    apriFinestrella('Aggiungi a ' + nomeMomento(momento), corpo, [
      bottoneChiudi('Annulla'),
      el('button', {
        class: 'pulsante piatto', type: 'button', testo: 'Aggiungi',
        click: () => {
          const q = numero(grammi.value);
          if (!q || q <= 0) { avviso('Scrivi quanto ne hai mangiato.', true); return; }
          const alimento = modoManuale ? manuale.leggi() : scelto;
          if (!alimento) { avviso('Scegli un alimento o scrivilo a mano.', true); return; }
          chiudiFinestrella();
          aggiungiVoce(momento, alimento, q, modoManuale || !alimentoInDispensa(alimento));
        },
      }),
    ]);
  }

  function alimentoInDispensa(a) {
    return alimenti().some((x) => x.id === a.id);
  }

  function nomeMomento(id) {
    const m = MOMENTI.find((x) => x[0] === id);
    return m ? m[1].toLowerCase() : '';
  }

  function descriviAlimento(a) {
    const pezzi = [];
    if (a.kcal100 != null) pezzi.push(L.fmtKcal(a.kcal100) + ' kcal');
    if (a.prot100 != null) pezzi.push(L.fmt1(a.prot100) + ' g prot');
    const unita = a.isDrink ? '/100 ml' : '/100 g';
    if (a.nova) pezzi.push('NOVA ' + a.nova);
    return pezzi.length ? pezzi.join(' · ') + ' ' + unita : 'senza valori';
  }

  /** I campi completi dell'alimento scritto a mano: senza, i conti su fibre e lavorazione sarebbero ciechi. */
  function campiManuali() {
    const nome = el('input', { type: 'text', placeholder: 'nome' });
    const kcal = el('input', { type: 'text', inputmode: 'decimal', placeholder: 'kcal/100' });
    const prot = el('input', { type: 'text', inputmode: 'decimal', placeholder: 'prot/100' });
    const grassi = el('input', { type: 'text', inputmode: 'decimal' });
    const carb = el('input', { type: 'text', inputmode: 'decimal' });
    const fibra = el('input', { type: 'text', inputmode: 'decimal' });
    const saturi = el('input', { type: 'text', inputmode: 'decimal' });
    const zuccheri = el('input', { type: 'text', inputmode: 'decimal' });
    const sale = el('input', { type: 'text', inputmode: 'decimal' });
    const bevanda = el('input', { type: 'checkbox' });
    let nova = null;
    let gruppo = 'ALTRO';

    const scelteNova = el('div', { class: 'scelte' });
    const spiega = el('div', { class: 'minuscolo' });
    NOVA.forEach(([livello, etichetta, descrizione]) => {
      const b = el('button', { class: 'scelta', type: 'button', testo: etichetta, 'aria-pressed': 'false' });
      b.addEventListener('click', () => {
        nova = nova === livello ? null : livello;
        Array.prototype.forEach.call(scelteNova.children, (c) => c.setAttribute('aria-pressed', 'false'));
        if (nova) b.setAttribute('aria-pressed', 'true');
        spiega.textContent = nova ? descrizione : '';
      });
      scelteNova.appendChild(b);
    });

    const scelteGruppo = el('div', { class: 'scelte' });
    GRUPPI.forEach(([id, etichetta]) => {
      const b = el('button', { class: 'scelta', type: 'button', testo: etichetta, 'aria-pressed': id === 'ALTRO' ? 'true' : 'false' });
      b.addEventListener('click', () => {
        gruppo = id;
        Array.prototype.forEach.call(scelteGruppo.children, (c) => c.setAttribute('aria-pressed', 'false'));
        b.setAttribute('aria-pressed', 'true');
      });
      scelteGruppo.appendChild(b);
    });

    const altri = el('div', { style: 'display:none' }, [
      el('div', { class: 'campi' }, [
        el('label', { class: 'campo' }, [el('span', { testo: 'grassi/100' }), grassi]),
        el('label', { class: 'campo' }, [el('span', { testo: 'carb/100' }), carb]),
      ]),
      el('div', { class: 'campi' }, [
        el('label', { class: 'campo' }, [el('span', { testo: 'fibre/100' }), fibra]),
        el('label', { class: 'campo' }, [el('span', { testo: 'saturi/100' }), saturi]),
      ]),
      el('div', { class: 'campi' }, [
        el('label', { class: 'campo' }, [el('span', { testo: 'zuccheri/100' }), zuccheri]),
        el('label', { class: 'campo' }, [el('span', { testo: 'sale/100' }), sale]),
      ]),
      el('div', { class: 'minuscolo', testo: 'Trovi tutti questi numeri nella tabella nutrizionale della confezione.' }),
    ]);

    const box = el('div', {}, [
      el('label', { class: 'campo' }, [el('span', { testo: 'Nome' }), nome]),
      el('div', { class: 'campi' }, [
        el('label', { class: 'campo' }, [el('span', { testo: 'kcal/100' }), kcal]),
        el('label', { class: 'campo' }, [el('span', { testo: 'proteine/100' }), prot]),
      ]),
      el('div', { class: 'minuscolo', testo: 'Valori facoltativi: senza, la voce conta come «?»' }),
      el('div', { class: 'piccolo', style: 'margin-top:10px', testo: 'Quanto è lavorato' }),
      scelteNova,
      spiega,
      el('div', { class: 'piccolo', testo: 'Gruppo' }),
      scelteGruppo,
      el('button', {
        class: 'pulsante piatto', type: 'button', testo: 'Altri valori (fibre, grassi, sale…)',
        click: (e) => {
          const aperto = altri.style.display !== 'none';
          altri.style.display = aperto ? 'none' : '';
          e.target.textContent = aperto ? 'Altri valori (fibre, grassi, sale…)' : 'Nascondi altri valori';
        },
      }),
      altri,
      el('label', { class: 'riga', style: 'justify-content:flex-start;gap:8px;margin-top:6px' }, [bevanda, el('span', { class: 'piccolo', testo: 'È una bevanda (valori per 100 ml)' })]),
    ]);

    return {
      box: box,
      leggi: function () {
        const n = nome.value.trim();
        if (!n) return null;
        return {
          id: idNuovo('manuale'),
          nome: n,
          kcal100: numero(kcal.value),
          prot100: numero(prot.value),
          grassi100: numero(grassi.value),
          carb100: numero(carb.value),
          fibra100: numero(fibra.value),
          grassiSaturi100: numero(saturi.value),
          zuccheri100: numero(zuccheri.value),
          sale100: numero(sale.value),
          nova: nova,
          gruppo: gruppo,
          isDrink: bevanda.checked,
          hint: null,
          barcode: null,
          custom: true,
        };
      },
    };
  }

  async function aggiungiVoce(momento, alimento, quantita, inDispensa) {
    const voce = {
      id: idNuovo(),
      date: stato.giorno,
      moment: momento,
      nome: alimento.nome,
      grams: quantita,
      kcal: L.perGrammi(quantita, alimento.kcal100),
      prot: L.perGrammi(quantita, alimento.prot100),
      fibra: L.perGrammi(quantita, alimento.fibra100),
      nova: alimento.nova != null ? alimento.nova : null,
      gruppo: alimento.gruppo || 'ALTRO',
      isDrink: !!alimento.isDrink,
      createdAt: Date.now(),
    };
    await salva((d) => {
      d.entries = d.entries.concat([voce]);
      if (voce.isDrink) {
        const attuale = (d.waterByDate || {})[voce.date] || 0;
        d.waterByDate = Object.assign({}, d.waterByDate);
        d.waterByDate[voce.date] = attuale + Math.round(quantita);
      }
      // L'alimento nuovo resta in dispensa: la prossima volta si trova cercando.
      if (inDispensa && !d.customFoods.some((x) => x.nome.toLowerCase() === alimento.nome.toLowerCase())) {
        d.customFoods = d.customFoods.concat([Object.assign({}, alimento, { custom: true })]);
      }
    });
    if (stato.impostazioni.mostraScheda && alimento.kcal100 != null) schedaProdotto(alimento, quantita);
  }

  /** La scheda dopo l'inserimento, come sul telefono: cinque secondi, si ferma al tocco. */
  function schedaProdotto(alimento, quantita) {
    const scheda = L.schedaNutrizionale(alimento);
    const contributo = L.contributoScheda(alimento, quantita);
    const avanzamento = el('span', { style: 'width:0%' });
    const corpo = el('div', { class: 'scheda-prodotto', style: 'padding:16px' }, [
      el('div', { class: 'titolo-riquadro', testo: alimento.nome }),
      el('div', { class: 'piccolo', style: 'color:#5B5B5B', testo: contributo }),
      el('div', { style: 'margin-top:10px' }, scheda.righe.map((r) => el('div', { class: 'riga-scheda' }, [
        el('span', { class: 'pallino ' + (r.ok ? 'buono' : 'neutro') }),
        el('div', { class: 'cresci' }, [
          el('div', { class: 'testo', testo: r.titolo }),
          r.nota ? el('div', { class: 'piccolo', style: 'color:#5B5B5B', testo: r.nota }) : null,
        ]),
        el('div', { class: 'grassetto', testo: r.valore }),
      ]))),
      el('div', { class: 'tempo' }, [avanzamento]),
    ]);

    const velo = apriFinestrella(null, [corpo], [bottoneChiudi('Chiudi')]);
    let passato = 0;
    let fermo = false;
    const passo = 100;
    const durata = 5000;
    const timer = setInterval(() => {
      if (fermo) return;
      passato += passo;
      avanzamento.style.width = Math.min(100, (passato / durata) * 100) + '%';
      if (passato >= durata) { clearInterval(timer); if (stato.finestrella === velo) chiudiFinestrella(); }
    }, passo);
    corpo.addEventListener('click', () => { fermo = !fermo; });
    velo.addEventListener('remove', () => clearInterval(timer));
  }

  // ---- foto ----

  function dialogoFoto(momento) {
    let scatti = [];
    let unico = true;
    let esito = null;

    const elenco = el('div');
    const risultato = el('div');

    function disegnaScatti() {
      elenco.textContent = '';
      if (scatti.length === 0) return;
      elenco.appendChild(el('div', { class: 'scelte' }, [
        el('button', { class: 'scelta', type: 'button', testo: 'Un solo alimento', 'aria-pressed': String(unico), click: () => { unico = true; disegnaScatti(); } }),
        el('button', { class: 'scelta', type: 'button', testo: 'Alimenti diversi', 'aria-pressed': String(!unico), click: () => { unico = false; disegnaScatti(); } }),
      ]));
      const riga = el('div', { style: 'display:flex;gap:8px;overflow-x:auto;padding-bottom:6px' });
      scatti.forEach((s, i) => {
        riga.appendChild(el('div', { style: 'text-align:center;flex-shrink:0' }, [
          el('img', { src: s.anteprima, alt: '', style: 'width:84px;height:84px;object-fit:cover;border-radius:10px' }),
          el('button', { class: 'pulsante piatto', style: 'padding:4px', type: 'button', testo: 'Togli', click: () => { scatti.splice(i, 1); disegnaScatti(); } }),
        ]));
      });
      elenco.appendChild(riga);
    }

    async function scegli() {
      const r = await Api.post('/locale/scegli-immagini');
      if (!r.ok) { avviso(r.errore, true); return; }
      const nuove = (r.dati.immagini || []);
      if (nuove.length === 0) return;
      scatti = scatti.concat(nuove);
      disegnaScatti();
      // Il costo di una chiamata cresce coi pixel: meglio saperlo prima di spendere.
      const token = scatti.reduce((t, s) => t + (s.token || 0), 0);
      elenco.appendChild(el('div', { class: 'minuscolo', testo: scatti.length + (scatti.length === 1 ? ' foto' : ' foto') + ' · ~' + token + ' token immagine da inviare' }));
    }

    async function analizza() {
      risultato.textContent = '';
      risultato.appendChild(el('div', { class: 'piccolo', testo: 'Sto guardando le foto…' }));
      const r = await Api.post('/locale/foto', { immagini: scatti.map((s) => s.file), unico: unico, turni: [] });
      risultato.textContent = '';
      if (!r.ok) { risultato.appendChild(el('div', { class: 'piccolo rosso', testo: r.errore })); return; }
      esito = r.dati;
      mostraEsito();
    }

    function mostraEsito() {
      risultato.textContent = '';
      const scelti = {};
      (esito.alimenti || []).forEach((a, i) => { scelti[i] = true; });
      (esito.alimenti || []).forEach((a, i) => {
        const nome = el('input', { type: 'text', value: a.nome });
        const grammi = el('input', { type: 'text', inputmode: 'decimal', value: String(Math.round(a.grammi)) });
        const spunta = el('input', { type: 'checkbox', checked: true });
        spunta.addEventListener('change', () => { scelti[i] = spunta.checked; });
        a._nome = nome; a._grammi = grammi; a._scelti = () => spunta.checked;
        risultato.appendChild(el('div', { class: 'riquadro' }, [
          el('div', { class: 'riga' }, [
            spunta,
            el('div', { class: 'cresci piccolo', testo: descriviAlimento(a) }),
            el('span', { class: 'minuscolo', testo: a.certezza === 'alta' ? 'sicuro' : a.certezza === 'bassa' ? 'incerto' : 'stimato' }),
          ]),
          el('label', { class: 'campo' }, [el('span', { testo: 'Nome' }), nome]),
          el('label', { class: 'campo' }, [el('span', { testo: 'grammi' }), grammi]),
        ]));
      });
      if (esito.note) risultato.appendChild(el('div', { class: 'minuscolo', testo: esito.note }));
      if (esito.domanda) {
        const risposta = el('input', { type: 'text', placeholder: 'la tua risposta' });
        risultato.appendChild(el('div', { class: 'riquadro consigli' }, [
          el('div', { class: 'testo grassetto', testo: esito.domanda }),
          el('label', { class: 'campo' }, [el('span', { testo: 'Rispondi (facoltativo)' }), risposta]),
          el('button', {
            class: 'pulsante piatto', type: 'button', testo: 'Rispondi e ricalcola',
            click: async () => {
              if (!risposta.value.trim()) return;
              risultato.textContent = '';
              risultato.appendChild(el('div', { class: 'piccolo', testo: 'Ricalcolo…' }));
              const r = await Api.post('/locale/foto', {
                immagini: scatti.map((s) => s.file), unico: unico,
                turni: [{ utente: false, testo: esito.domanda }, { utente: true, testo: risposta.value.trim() }],
              });
              if (!r.ok) { risultato.textContent = ''; risultato.appendChild(el('div', { class: 'piccolo rosso', testo: r.errore })); return; }
              esito = r.dati;
              mostraEsito();
            },
          }),
        ]));
      }
    }

    apriFinestrella('Foto · ' + nomeMomento(momento), [
      el('div', { class: 'piccolo', testo: 'Scegli le foto del piatto o dell\'etichetta: ti propongo gli alimenti con le quantità stimate, poi confermi tu.' }),
      el('button', { class: 'pulsante contorno', style: 'width:100%;margin:10px 0', type: 'button', testo: 'Scegli le foto', click: scegli }),
      elenco,
      el('button', { class: 'pulsante pieno', style: 'width:100%;margin:10px 0', type: 'button', testo: 'Analizza', click: () => { if (scatti.length === 0) { avviso('Scegli almeno una foto.', true); return; } analizza(); } }),
      risultato,
    ], [
      bottoneChiudi('Chiudi'),
      el('button', {
        class: 'pulsante piatto', type: 'button', testo: 'Aggiungi',
        click: async () => {
          if (!esito || !esito.alimenti) { avviso('Prima analizza le foto.', true); return; }
          const scelti = esito.alimenti.filter((a) => a._scelti && a._scelti());
          if (scelti.length === 0) { avviso('Scegli almeno un alimento.', true); return; }
          chiudiFinestrella();
          for (const a of scelti) {
            const q = numero(a._grammi.value) || a.grammi;
            await aggiungiVoce(momento, {
              id: idNuovo('ai'),
              nome: (a._nome.value || a.nome).trim(),
              kcal100: a.kcal100, prot100: a.prot100, grassi100: a.grassi100, carb100: a.carb100,
              fibra100: a.fibra100, nova: a.nova, gruppo: a.gruppo, isDrink: false,
              hint: 'riconosciuto da una foto · certezza ' + a.certezza, custom: true,
            }, q, true);
          }
        },
      }),
    ]);
  }

  radice.SurplusApp = { avvia: avvia, stato: stato, el: el, icona: icona, ICONE: ICONE, apriFinestrella: apriFinestrella, chiudiFinestrella: chiudiFinestrella, bottoneChiudi: bottoneChiudi, salva: salva, avviso: avviso, numero: numero, idNuovo: idNuovo, disegna: disegna, percento: percento, isoDiOggi: isoDiOggi, spostaGiorno: spostaGiorno, statoGiorno: statoGiorno, obiettivi: obiettivi, voci: voci, barraObiettivo: barraObiettivo, MOMENTI: MOMENTI };

  document.addEventListener('DOMContentLoaded', avvia);
})(window);
