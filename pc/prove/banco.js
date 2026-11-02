/*
 * Banco di prova dell'interfaccia: serve la cartella pc/ui su http e finge di
 * essere il programma, rispondendo a /locale/* con un diario di esempio.
 *
 * Serve a guardare le schermate in un browser mentre si lavora, senza dover
 * ricompilare e riaprire la finestra vera a ogni modifica. Non fa parte del
 * programma: è uno strumento di sviluppo.
 *
 *   node pc/prove/banco.js        → http://127.0.0.1:8777
 */
'use strict';

const http = require('http');
const fs = require('fs');
const path = require('path');

const PORTA = 8777;
const CARTELLA_UI = path.join(__dirname, '..', 'ui');

function iso(giorniFa) {
  const d = new Date();
  d.setDate(d.getDate() - giorniFa);
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
}

function ora(giorniFa, h, m) {
  const d = new Date();
  d.setDate(d.getDate() - giorniFa);
  d.setHours(h, m, 0, 0);
  return d.getTime();
}

function voce(momento, nome, g, kcal, prot, fibra, nova, gruppo, h, m, bevanda) {
  return {
    id: nome + '-' + h + m, date: iso(0), moment: momento, nome: nome, grams: g,
    kcal: kcal, prot: prot, fibra: fibra, nova: nova, gruppo: gruppo,
    isDrink: !!bevanda, createdAt: ora(0, h, m),
  };
}

function esercizio(id, nome, gruppo, serie, repMin, repMax, nota) {
  return { id: id, nome: nome, gruppo: gruppo, spinta: true, trazione: false, serie: serie, repMin: repMin, repMax: repMax, nota: nota || null, custom: false, incrementoKg: null };
}

const dati = {
  schemaVersion: 4,
  profile: { userId: 'local', nome: '', annoNascita: 2009, sesso: 'MASCHIO', altezzaCm: 178, pesoKg: 64.5, attivita: 'MODERATA', obiettivo: 'MASSA', isConfigured: true, disclaimerAcceptedAt: ora(3, 9, 0), isMinor: true },
  entries: [
    voce('COLAZIONE', "Fiocchi d'avena", 80, 304, 10.6, 6.8, 1, 'CEREALE', 7, 40),
    voce('COLAZIONE', 'Latte intero', 250, 160, 7.9, 0, 1, 'LATTICINO', 7, 42, true),
    voce('COLAZIONE', 'Banana', 120, 107, 1.3, 3.1, 1, 'FRUTTA', 7, 45),
    voce('META_MATTINA', 'Mandorle', 30, 173, 6.4, 3.8, 1, 'GRASSI', 10, 30),
    voce('PRANZO', 'Riso basmati', 120, 414, 9.6, 1.7, 1, 'CEREALE', 13, 5),
    voce('PRANZO', 'Petto di pollo', 180, 198, 41.6, 0, 1, 'PROTEICO', 13, 7),
    voce('PRANZO', 'Zucchine', 200, 34, 2.6, 2.2, 1, 'VERDURA', 13, 8),
  ],
  waterByDate: { [iso(0)]: 1750, [iso(1)]: 3100 },
  weights: [{ date: iso(3), kg: 64.2 }, { date: iso(0), kg: 64.5 }],
  customFoods: [
    { id: 'pane', nome: 'Pane', kcal100: 268, prot100: 8, grassi100: 0.5, carb100: null, fibra100: 3.5, grassiSaturi100: null, zuccheri100: null, sale100: null, nova: 3, gruppo: 'CEREALE', isDrink: false, hint: '1 fetta ≈ 30 g', barcode: null, custom: false },
    { id: 'yogurt-greco', nome: 'Yogurt greco intero', kcal100: 95, prot100: 9, grassi100: 5, carb100: null, fibra100: 0, grassiSaturi100: null, zuccheri100: null, sale100: null, nova: 1, gruppo: 'LATTICINO', isDrink: false, hint: '1 vasetto ≈ 170 g', barcode: null, custom: false },
    { id: 'uova', nome: 'Uova', kcal100: 143, prot100: 12.5, grassi100: 9.5, carb100: null, fibra100: 0, grassiSaturi100: null, zuccheri100: null, sale100: null, nova: 1, gruppo: 'PROTEICO', isDrink: false, hint: '1 uovo ≈ 60 g', barcode: null, custom: false },
  ],
  workouts: [
    {
      id: 'w-1', date: iso(0), type: null, routineId: 'spinta', routineName: 'Spinta',
      exercises: [
        { exerciseId: 'shoulder-press', nome: 'Shoulder Press', sets: [{ weightKg: 13, reps: 9 }, { weightKg: 13, reps: 8 }] },
        { exerciseId: 'alzate-laterali', nome: 'Alzate Laterali', sets: [] },
        { exerciseId: 'floor-press', nome: 'Floor Press con manubri', sets: [] },
      ],
      startedAt: ora(0, 16, 40), firstSetAt: ora(0, 16, 46), lastSetAt: ora(0, 16, 58),
    },
    {
      id: 'w-2', date: iso(1), type: null, routineId: 'spinta', routineName: 'Spinta',
      exercises: [
        { exerciseId: 'shoulder-press', nome: 'Shoulder Press', sets: [{ weightKg: 12, reps: 12 }, { weightKg: 12, reps: 12 }, { weightKg: 12, reps: 12 }] },
        { exerciseId: 'alzate-laterali', nome: 'Alzate Laterali', sets: [{ weightKg: 5, reps: 16 }, { weightKg: 5, reps: 15 }] },
      ],
      startedAt: ora(1, 17, 25), firstSetAt: ora(1, 17, 31), lastSetAt: ora(1, 18, 19),
    },
  ],
  customExercises: [],
  exercises: [
    esercizio('shoulder-press', 'Shoulder Press', 'Spalle', '3-4', 8, 12),
    esercizio('alzate-laterali', 'Alzate Laterali', 'Spalle', '3', 12, 20),
    esercizio('floor-press', 'Floor Press con manubri', 'Petto', '3', 8, 12),
    esercizio('curl-manubri', 'Curl con manubri', 'Bicipiti', '3', 8, 12),
    esercizio('face-pull', 'Face Pull', 'Salute spalle', '2-3', 12, 15, 'con elastico'),
  ],
  routines: [
    { id: 'spinta', nome: 'Spinta', exerciseIds: ['shoulder-press', 'alzate-laterali', 'floor-press'] },
    { id: 'trazione', nome: 'Trazione', exerciseIds: ['curl-manubri', 'face-pull'] },
  ],
  schedeInizializzate: true,
};

const impostazioni = {
  kcalMin: 3300, kcalMax: 3700, protMin: 130, protMax: 150, acquaMl: 3250,
  mostraScheda: true, schedaTutorialVisto: false, avvioAlLogin: false,
  aiFornitore: 'GEMINI', aiModello: '', aiAttiva: false,
};

const TIPI = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8' };

http.createServer((req, res) => {
  const url = new URL(req.url, 'http://127.0.0.1');
  if (url.pathname.startsWith('/locale/')) {
    let corpo = '';
    req.on('data', (c) => { corpo += c; });
    req.on('end', () => {
      let risposta = { ok: true };
      if (url.pathname === '/locale/stato') risposta = { versioneProgramma: 'banco', versione: 1, dati: dati, impostazioni: impostazioni };
      else if (url.pathname === '/locale/salva') risposta = { ok: true, versione: 2 };
      else if (url.pathname === '/locale/cerca') risposta = { ok: true, risultati: [] };
      else if (url.pathname === '/locale/impostazioni') risposta = { ok: true, impostazioni: impostazioni };
      else risposta = { errore: 'Nel banco di prova questa funzione non c\'è.' };
      res.writeHead(risposta.errore ? 422 : 200, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify(risposta));
    });
    return;
  }
  const nome = url.pathname === '/' ? '/index.html' : url.pathname;
  const file = path.join(CARTELLA_UI, nome.replace(/^\//, ''));
  if (!file.startsWith(CARTELLA_UI) || !fs.existsSync(file)) {
    res.writeHead(404, { 'Content-Type': 'text/plain' });
    res.end('non trovato');
    return;
  }
  res.writeHead(200, { 'Content-Type': TIPI[path.extname(file)] || 'application/octet-stream' });
  res.end(fs.readFileSync(file));
}).listen(PORTA, '127.0.0.1', () => {
  console.log('banco di prova su http://127.0.0.1:' + PORTA);
});
