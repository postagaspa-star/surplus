#!/usr/bin/env node
/**
 * Rimette insieme il diario del telefono partendo da quello che il telefono ha
 * lasciato fuori dalla sua memoria privata: l'esportazione JSON (completa ma
 * ferma al giorno in cui è stata fatta) e l'esportazione CSV del diario (più
 * recente, ma solo i pasti: niente allenamenti, acqua, pesi, orari).
 *
 * Serve quando il file vero (`data-<utente>.json` nella memoria privata
 * dell'app) non è raggiungibile: su un telefono senza root nessun backup lo
 * copia, quindi le esportazioni in Download sono tutto ciò che resta.
 *
 * Uso:
 *   node pc/strumenti/recupera-dal-telefono.js --json <export.json> --csv <diario.csv> --uscita <data-local.json>
 *
 * Regole:
 *  - il JSON è la base: profilo, dispensa, allenamenti, acqua, pesi e le voci
 *    con id e orario veri;
 *  - dal CSV si aggiungono solo le voci che nel JSON non ci sono, contate per
 *    (giorno, pasto, nome, grammi): se quel giorno ha due caffè uguali ne
 *    restano due;
 *  - le voci aggiunte non hanno l'orario (createdAt = 0): le due app lo sanno e
 *    non mostrano l'ora quando non c'è. Niente orari inventati;
 *  - gruppo, nova e "è una bevanda" si copiano dall'alimento con lo stesso nome
 *    (dispensa dell'utente o dispensa di serie), altrimenti restano neutri;
 *  - con --dispensa (acceso di serie) i nomi che non stanno in nessuna dispensa
 *    tornano in dispensa coi valori per 100 g ricavati dalla voce stessa
 *    (kcal / grammi * 100): sono i suoi numeri, non stime.
 *
 * Il file che esce ha `schemaVersion` del JSON di partenza: la migrazione
 * dell'app lo porta all'ultima versione da sola, al primo avvio.
 */
'use strict';

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const MOMENTI = {
  'colazione': 'COLAZIONE',
  'metà mattina': 'META_MATTINA',
  'meta mattina': 'META_MATTINA',
  'pranzo': 'PRANZO',
  'post allenamento': 'POST_ALLENAMENTO',
  'cena': 'CENA',
  'pre nanna': 'PRE_NANNA',
  'bevande': 'BEVANDE',
};

function argomenti(argv) {
  const a = {};
  for (let i = 2; i < argv.length; i++) {
    const k = argv[i];
    if (!k.startsWith('--')) continue;
    const nome = k.slice(2);
    if (nome === 'dispensa' || nome === 'no-dispensa') { a.dispensa = nome === 'dispensa'; continue; }
    a[nome] = argv[++i];
  }
  if (a.dispensa === undefined) a.dispensa = true;
  return a;
}

/** CSV secondo RFC 4180: virgolette doppie per i campi con virgole dentro. */
function leggiCsv(testo) {
  const righe = [];
  let campo = '';
  let riga = [];
  let dentroVirgolette = false;
  for (let i = 0; i < testo.length; i++) {
    const c = testo[i];
    if (dentroVirgolette) {
      if (c === '"') {
        if (testo[i + 1] === '"') { campo += '"'; i++; }
        else dentroVirgolette = false;
      } else campo += c;
      continue;
    }
    if (c === '"') { dentroVirgolette = true; continue; }
    if (c === ',') { riga.push(campo); campo = ''; continue; }
    if (c === '\r') continue;
    if (c === '\n') { riga.push(campo); righe.push(riga); riga = []; campo = ''; continue; }
    campo += c;
  }
  if (campo !== '' || riga.length) { riga.push(campo); righe.push(riga); }
  return righe.filter((r) => r.some((x) => x !== ''));
}

function numero(s) {
  if (s === undefined || s === null) return null;
  const t = String(s).trim().replace(',', '.');
  if (t === '') return null;
  const n = Number(t);
  return Number.isFinite(n) ? n : null;
}

/** La dispensa di serie sta nel Kotlin: ne basta nome, gruppo, nova e bevanda. */
function dispensaDiSerie(radice) {
  const file = path.join(radice, 'app/src/main/kotlin/it/gaspa/surplus/data/SeedFoods.kt');
  if (!fs.existsSync(file)) return [];
  const testo = fs.readFileSync(file, 'utf8');
  const fuori = [];
  const riga = /f\(\s*"([^"]+)"\s*,\s*"((?:[^"\\]|\\.)*)"([^\n]*)/g;
  let m;
  while ((m = riga.exec(testo)) !== null) {
    const coda = m[3];
    const nova = /nova\s*=\s*(\d+)/.exec(coda);
    const gruppo = /gruppo\s*=\s*FoodGroup\.(\w+)/.exec(coda);
    fuori.push({
      id: m[1],
      nome: m[2].replace(/\\"/g, '"'),
      nova: nova ? Number(nova[1]) : null,
      gruppo: gruppo ? gruppo[1] : 'ALTRO',
      isDrink: /drink\s*=\s*true/.test(coda),
    });
  }
  return fuori;
}

function chiave(giorno, momento, nome, grammi) {
  return [giorno, momento, String(nome).trim().toLowerCase(), Number(grammi).toFixed(1)].join('|');
}

function principale() {
  const a = argomenti(process.argv);
  if (!a.json && !a.csv) {
    console.error('Serve almeno --json <export.json> o --csv <diario.csv>.');
    process.exit(2);
  }
  const radice = path.resolve(__dirname, '..', '..');

  const dati = a.json
    ? JSON.parse(fs.readFileSync(a.json, 'utf8'))
    : { schemaVersion: 3, profile: {}, entries: [], waterByDate: {}, weights: [], customFoods: [], workouts: [], customExercises: [] };
  dati.entries = Array.isArray(dati.entries) ? dati.entries : [];
  dati.customFoods = Array.isArray(dati.customFoods) ? dati.customFoods : [];

  // Nome -> caratteristiche, dalla dispensa dell'utente e da quella di serie.
  const perNome = new Map();
  for (const f of dispensaDiSerie(radice)) perNome.set(f.nome.trim().toLowerCase(), f);
  for (const f of dati.customFoods) if (f && f.nome) perNome.set(String(f.nome).trim().toLowerCase(), f);

  const conteggioJson = new Map();
  for (const e of dati.entries) {
    const k = chiave(e.date, e.moment, e.nome, e.grams);
    conteggioJson.set(k, (conteggioJson.get(k) || 0) + 1);
  }

  const rapporto = { csv: 0, aggiunte: 0, giaPresenti: 0, scartate: 0, senzaDispensa: new Set(), nuoviInDispensa: 0 };
  const nuove = [];

  if (a.csv) {
    const righe = leggiCsv(fs.readFileSync(a.csv, 'utf8'));
    const intestazione = righe.shift() || [];
    const col = {};
    intestazione.forEach((n, i) => { col[n.trim()] = i; });
    for (const r of righe) {
      const giorno = (r[col['data']] || '').trim();
      const pasto = (r[col['pasto']] || '').trim().toLowerCase();
      const nome = (r[col['alimento']] || '').trim();
      const grammi = numero(r[col['grammi']]);
      const momento = MOMENTI[pasto];
      if (!/^\d{4}-\d{2}-\d{2}$/.test(giorno) || !momento || !nome || grammi === null) { rapporto.scartate++; continue; }
      rapporto.csv++;
      const k = chiave(giorno, momento, nome, grammi);
      const resta = conteggioJson.get(k) || 0;
      if (resta > 0) { conteggioJson.set(k, resta - 1); rapporto.giaPresenti++; continue; }
      const noto = perNome.get(nome.toLowerCase());
      if (!noto) rapporto.senzaDispensa.add(nome);
      nuove.push({
        id: 'rec-' + crypto.randomUUID(),
        date: giorno,
        moment: momento,
        nome,
        grams: grammi,
        kcal: numero(r[col['kcal']]),
        prot: numero(r[col['proteine_g']]),
        fibra: numero(r[col['fibre_g']]),
        nova: noto && noto.nova !== undefined ? noto.nova : null,
        gruppo: noto && noto.gruppo ? noto.gruppo : 'ALTRO',
        isDrink: noto ? !!noto.isDrink : momento === 'BEVANDE',
        createdAt: 0,
      });
      rapporto.aggiunte++;
    }
  }

  dati.entries = dati.entries.concat(nuove);
  dati.entries.sort((x, y) => (x.date < y.date ? -1 : x.date > y.date ? 1 : (x.createdAt || 0) - (y.createdAt || 0)));

  // Dispensa: i nomi nuovi tornano con i valori per 100 g ricavati dalla voce più recente.
  if (a.dispensa && rapporto.senzaDispensa.size) {
    const ultima = new Map();
    for (const e of nuove) {
      if (!rapporto.senzaDispensa.has(e.nome)) continue;
      const k = e.nome.trim().toLowerCase();
      const prima = ultima.get(k);
      if (!prima || e.date >= prima.date) ultima.set(k, e);
    }
    for (const e of ultima.values()) {
      if (!e.grams || e.grams <= 0 || e.kcal === null) continue;
      const per100 = (v) => (v === null || v === undefined ? null : Math.round((v / e.grams) * 100 * 10) / 10);
      dati.customFoods.push({
        id: 'custom-' + crypto.randomUUID(),
        nome: e.nome,
        kcal100: per100(e.kcal),
        prot100: per100(e.prot),
        grassi100: null,
        carb100: null,
        fibra100: per100(e.fibra),
        grassiSaturi100: null,
        zuccheri100: null,
        sale100: null,
        nova: null,
        gruppo: e.gruppo || 'ALTRO',
        isDrink: !!e.isDrink,
        hint: 'valori ricavati dal diario del telefono',
        barcode: null,
        custom: true,
      });
      rapporto.nuoviInDispensa++;
    }
  }

  const giorni = new Set(dati.entries.map((e) => e.date));
  const ordinati = [...giorni].sort();
  const uscita = a.uscita || path.join(radice, 'pc', 'dist', 'data-local.json');
  fs.mkdirSync(path.dirname(uscita), { recursive: true });
  fs.writeFileSync(uscita, JSON.stringify(dati, null, 2), 'utf8');

  console.log('Diario rimesso insieme');
  console.log('  righe lette dal CSV:       ' + rapporto.csv + (rapporto.scartate ? ' (' + rapporto.scartate + ' scartate)' : ''));
  console.log('  voci già nel JSON:         ' + rapporto.giaPresenti);
  console.log('  voci aggiunte dal CSV:     ' + rapporto.aggiunte);
  console.log('  voci in tutto:             ' + dati.entries.length);
  console.log('  giorni coperti:            ' + giorni.size + (ordinati.length ? ' (dal ' + ordinati[0] + ' al ' + ordinati[ordinati.length - 1] + ')' : ''));
  console.log('  nomi fuori dalla dispensa: ' + rapporto.senzaDispensa.size + ', rimessi in dispensa: ' + rapporto.nuoviInDispensa);
  console.log('  allenamenti:               ' + (dati.workouts || []).length + '   acqua: ' + Object.keys(dati.waterByDate || {}).length + ' giorni   pesi: ' + (dati.weights || []).length);
  console.log('  scritto in:                ' + uscita);
}

principale();
