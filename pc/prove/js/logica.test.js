// I test della logica di Surplus (pc/ui/logica.js). Portano tutti i casi dei test Kotlin dell'app Android
// (AdvisorEngineTest, DietQualityTest, SchedaNutrizionaleTest, WorkoutQueriesTest, CompilaEsercizioTest,
// WeightRuleTest, NextOccurrenceTest, FoodMathTest, TargetCalculatorTest) e aggiungono i casi limite del porting.
// In fondo, i "riferimenti Kotlin": risultati prodotti dal codice Kotlin vero, congelati in
// riferimenti-kotlin.json. Si lanciano con: node --test pc/prove/js
'use strict';

const { test } = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const FILE_LOGICA = path.join(__dirname, '..', '..', 'ui', 'logica.js');
const L = require(FILE_LOGICA);

/** Come assertEquals(atteso, valore, delta) di JUnit. */
function vicino(valore, atteso, delta, messaggio) {
  assert.ok(Math.abs(valore - atteso) <= delta, (messaggio || 'valore') + ': atteso ' + atteso + ' ±' + delta + ', trovato ' + valore);
}

// --- Il contratto: i nomi con cui l'interfaccia chiama la logica -------------------------------------------------

const FUNZIONI_ATTESE = [
  'fmtInt', 'fmtKcal', 'fmt1', 'fmtLitri', 'fmtSerie', 'fmtOra', 'etichettaData', 'etichettaDataBreve',
  'totaliGiorno', 'perGrammi', 'qualitaGiorno', 'mediaQualita', 'consigli',
  'serieProposta', 'ultimeSerie', 'storicoEsercizio', 'allenatoIl', 'tutteAlMassimo', 'arrotondaKg',
  'ultimaVoltaScheda', 'compilaEsercizio', 'saltoAbituale', 'schedaNutrizionale', 'contributoScheda',
  'regolaPeso', 'calcolaObiettivi', 'prossimaOccorrenza',
];

test('contratto: espone tutte le 27 funzioni che l\'interfaccia chiama, e solo funzioni', () => {
  for (const nome of FUNZIONI_ATTESE) assert.strictEqual(typeof L[nome], 'function', nome);
  for (const [nome, valore] of Object.entries(L)) assert.strictEqual(typeof valore, 'function', nome + ' non è una funzione');
  assert.deepStrictEqual(FUNZIONI_ATTESE.filter((n) => !(n in L)), []);
  assert.strictEqual(FUNZIONI_ATTESE.length, 27);
});

test('contratto: oltre alle 27 ci sono solo due funzioni in più, dichiarate', () => {
  const inPiu = Object.keys(L).filter((n) => !FUNZIONI_ATTESE.includes(n)).sort();
  assert.deepStrictEqual(inPiu, ['leggiRispostaAiEsercizio', 'metabolismoBasale']);
});

test('script classico: nel browser mette window.SurplusLogica e non sporca lo spazio globale', () => {
  const codice = fs.readFileSync(FILE_LOGICA, 'utf8');
  const finestra = {};
  const contesto = vm.createContext({ window: finestra });
  vm.runInContext(codice, contesto);
  assert.strictEqual(typeof finestra.SurplusLogica, 'object');
  assert.deepStrictEqual(Object.keys(finestra.SurplusLogica), Object.keys(L));
  assert.deepStrictEqual(Object.keys(finestra), ['SurplusLogica'], 'nessun altro nome su window');
  // niente const/let/function globali che resterebbero nello script della pagina
  assert.strictEqual(vm.runInContext('typeof radice + typeof numero + typeof fmt1', contesto), 'undefinedundefinedundefined');
  assert.strictEqual(finestra.SurplusLogica.fmt1(3.25), '3,2');
});

test('script classico: sotto Node (senza window) esporta gli stessi nomi', () => {
  assert.strictEqual(typeof window, 'undefined');
  assert.strictEqual(globalThis.SurplusLogica, L);
});

test('solo funzioni pure: nessun DOM, nessuna rete, nessun modulo', () => {
  const codice = fs.readFileSync(FILE_LOGICA, 'utf8');
  for (const vietato of ['document.', 'fetch(', 'XMLHttpRequest', 'localStorage', 'sessionStorage', 'WebSocket', 'require(', 'import ', 'export ']) {
    assert.ok(!codice.includes(vietato), 'non deve contenere ' + vietato);
  }
});

// --- Strumenti per costruire i casi, come nei test Kotlin ---------------------------------------------------------

const obiettiviPiano = { kcalMin: 3300, kcalMax: 3700, protMin: 130, protMax: 150, acquaMl: 3250 };

/** Un giorno come lo vuole consigli(): i valori non indicati sono quelli di partenza del Kotlin. */
function giorno(o) {
  return Object.assign({ kcal: 0, prot: 0, nonNoti: 0, numeroPerMomento: {}, acquaMl: 2000, allenato: false, qualita: null }, o || {});
}

/** La qualità del cibo con i suoi valori derivati, come la restituisce qualitaGiorno(). */
function qualita(o) {
  const q = Object.assign({ kcalTotali: 3000, kcalConGrado: 0, kcalUltraProcessati: 0, fibraG: 0, porzioniFruttaVerdura: 0 }, o || {});
  q.quotaUltraProcessati = q.kcalConGrado >= 500 ? q.kcalUltraProcessati / q.kcalConGrado : null;
  q.copertura = q.kcalTotali > 0 ? q.kcalConGrado / q.kcalTotali : 0;
  return q;
}

/** 30 luglio 2026, all'ora indicata. */
function alle(ore, minuti) {
  return new Date(2026, 6, 30, ore, minuti || 0);
}

function consigliAlle(ore, minuti, d, ieri) {
  return L.consigli(alle(ore, minuti), d, ieri === undefined ? null : ieri, obiettiviPiano);
}
const testi = (lista) => lista.map((c) => c.testo);

// --- AdvisorEngineTest -------------------------------------------------------------------------------------------

test('consigli: mattina presto senza dati - nessun allarme', () => {
  assert.deepStrictEqual(consigliAlle(8, 0, giorno()), []);
});

test('consigli: pomeriggio indietro - consiglio concreto con kcal', () => {
  const d = giorno({ kcal: 700, prot: 25, numeroPerMomento: { COLAZIONE: 1 } });
  const c = consigliAlle(16, 0, d);
  assert.ok(c.some((x) => x.testo.includes('sotto il ritmo')));
  assert.ok(c.some((x) => x.testo.includes('kcal')));
});

test('consigli: pasto saltato a finestra chiusa - consiglio per domani', () => {
  const d = giorno({ kcal: 1500, prot: 60, numeroPerMomento: { COLAZIONE: 1, PRANZO: 1 } });
  const c = consigliAlle(13, 0, d);
  assert.ok(c.some((x) => x.testo.includes('domani prova') && x.testo.includes('metà mattina')), 'atteso consiglio per domani sullo spuntino saltato: ' + JSON.stringify(c));
});

test('consigli: sera sotto il minimo - proposta prima di dormire', () => {
  const c = consigliAlle(21, 30, giorno({ kcal: 2400, prot: 100, acquaMl: 3000 }));
  assert.ok(c[0].testo.includes('Ti mancano'));
  assert.ok(c[0].testo.includes('prima di dormire'));
});

test('consigli: sera a target - conferma senza allarmi', () => {
  const c = consigliAlle(22, 0, giorno({ kcal: 3450, prot: 140, acquaMl: 3300 }));
  assert.ok(c[0].testo.includes('a target'));
});

test('consigli: sera calorie ok ma proteine basse - spinta proteica', () => {
  const c = consigliAlle(22, 0, giorno({ kcal: 3400, prot: 110, acquaMl: 3300 }));
  assert.ok(c[0].testo.includes('yogurt greco'));
});

test('consigli: sera sopra il range - tono tranquillo', () => {
  const c = consigliAlle(22, 0, giorno({ kcal: 4300, prot: 150, acquaMl: 3300 }));
  assert.ok(c[0].testo.includes('sopra il range'));
});

test('consigli: mattina dopo giornata scarsa - recupero da ieri', () => {
  const c = consigliAlle(8, 30, giorno(), giorno({ kcal: 2500, prot: 90 }));
  assert.ok(c.some((x) => x.testo.includes('Ieri')));
});

test('consigli: voci senza valori - nota di sottostima', () => {
  const d = giorno({ kcal: 2000, prot: 80, nonNoti: 2, numeroPerMomento: { PRANZO: 3 } });
  assert.ok(consigliAlle(14, 0, d).some((x) => x.testo.includes('senza valori')));
});

test('consigli: acqua indietro nel pomeriggio - promemoria', () => {
  const d = giorno({ kcal: 2200, prot: 90, acquaMl: 1000, numeroPerMomento: { PRANZO: 1 } });
  assert.ok(consigliAlle(16, 0, d).some((x) => x.testo.includes('Acqua')));
});

test('consigli: acqua a posto - nessun promemoria acqua', () => {
  const d = giorno({ kcal: 2200, prot: 90, acquaMl: 2500, numeroPerMomento: { PRANZO: 1 } });
  assert.ok(!consigliAlle(16, 0, d).some((x) => x.testo.includes('Acqua')));
});

test('consigli: sera giorno di allenamento - soglia alzata e dichiarata', () => {
  const top = consigliAlle(22, 0, giorno({ kcal: 3100, prot: 135, acquaMl: 3300, allenato: true }))[0];
  assert.ok(top.testo.includes('3.450'));
  assert.ok(top.testo.includes('allenamento'));
});

test('consigli: sera stesso introito senza allenamento - soglia normale', () => {
  const top = consigliAlle(22, 0, giorno({ kcal: 3100, prot: 135, acquaMl: 3300 }))[0];
  assert.ok(top.testo.includes('3.300'));
  assert.ok(!top.testo.includes('allenamento'));
});

test('consigli: pomeriggio - ritmo più esigente nei giorni di allenamento', () => {
  const base = giorno({ kcal: 1800, prot: 70, numeroPerMomento: { COLAZIONE: 1, PRANZO: 1 } });
  const senza = consigliAlle(16, 0, base);
  const con = consigliAlle(16, 0, Object.assign({}, base, { allenato: true }));
  assert.ok(!senza.some((x) => x.testo.includes('sotto il ritmo')));
  assert.ok(con.some((x) => x.testo.includes('sotto il ritmo')));
});

test('consigli: ogni consiglio porta la sua spiegazione', () => {
  const scenari = [
    [alle(16), giorno({ kcal: 700, prot: 25, numeroPerMomento: { COLAZIONE: 1 }, acquaMl: 1000 }), null],
    [alle(22), giorno({ kcal: 2400, prot: 100, nonNoti: 1, numeroPerMomento: { PRANZO: 2 }, acquaMl: 2000 }), null],
    [alle(8, 30), giorno(), giorno({ kcal: 2500, prot: 90 })],
  ];
  for (const [adesso, d, ieri] of scenari) {
    const c = L.consigli(adesso, d, ieri, obiettiviPiano);
    assert.ok(c.length > 0, 'scenario senza consigli: ' + adesso);
    for (const a of c) assert.ok(a.perche.trim() !== '', 'spiegazione vuota per: ' + a.testo);
  }
});

test('consigli: ritmo ok - conferma esplicita invece del silenzio', () => {
  const d = giorno({ kcal: 2200, prot: 90, acquaMl: 2500, numeroPerMomento: { PRANZO: 1 } });
  assert.ok(consigliAlle(16, 0, d).some((x) => x.testo.includes('In linea col ritmo')));
});

test('consigli: poca frutta e verdura - suggerisce di aggiungerne', () => {
  const d = giorno({ kcal: 2000, prot: 90, numeroPerMomento: { PRANZO: 1 }, qualita: qualita({ porzioniFruttaVerdura: 1 }) });
  const a = consigliAlle(16, 0, d).find((x) => x.testo.includes('Frutta e verdura'));
  assert.ok(a, 'atteso il consiglio su frutta e verdura');
  assert.ok(a.testo.includes('Aggiungine'));
  assert.ok(a.perche.includes('100 g'));
});

test('consigli: qualità buona - riconoscimento esplicito, non silenzio', () => {
  const d = giorno({
    kcal: 3400, prot: 140, acquaMl: 3300,
    qualita: qualita({ porzioniFruttaVerdura: 4, fibraG: 32, kcalConGrado: 3000, kcalUltraProcessati: 300 }),
  });
  assert.ok(consigliAlle(22, 0, d).some((x) => x.testo.includes('Qualità della giornata buona')));
});

test('consigli: grado di lavorazione - avviso solo con campione sufficiente', () => {
  const piccolo = giorno({
    kcal: 3400, prot: 140, acquaMl: 3300,
    qualita: qualita({ porzioniFruttaVerdura: 4, fibraG: 32, kcalConGrado: 300, kcalUltraProcessati: 300 }),
  });
  assert.ok(!consigliAlle(22, 0, piccolo).some((x) => x.testo.includes('prodotti industriali')));
  const ampio = giorno({
    kcal: 3400, prot: 140, acquaMl: 3300,
    qualita: qualita({ porzioniFruttaVerdura: 4, fibraG: 32, kcalConGrado: 3000, kcalUltraProcessati: 2400 }),
  });
  assert.ok(consigliAlle(22, 0, ampio).some((x) => x.testo.includes('prodotti industriali')));
});

test('consigli: nessun consiglio sulla qualità senza dati', () => {
  const d = giorno({ kcal: 3400, prot: 140, acquaMl: 3300, qualita: null });
  assert.ok(!consigliAlle(22, 0, d).some((x) => x.testo.includes('Frutta e verdura') || x.testo.includes('Fibra')));
});

/** Regola di progetto: si suggerisce cosa aggiungere, mai si colpevolizza. */
test('consigli: non colpevolizzano mai', () => {
  const proibite = ['cattiv', 'sbagliat', 'male', 'schifezz', 'colpa', 'evita', 'smetti', 'non mangiare'];
  const scenari = [
    [alle(16), giorno({ kcal: 2000, prot: 90, numeroPerMomento: { PRANZO: 1 }, qualita: qualita({ porzioniFruttaVerdura: 0 }) })],
    [alle(22), giorno({ kcal: 3400, prot: 140, acquaMl: 3300, qualita: qualita({ porzioniFruttaVerdura: 0, fibraG: 8, kcalConGrado: 3000, kcalUltraProcessati: 2700 }) })],
    [alle(22), giorno({ kcal: 4500, prot: 150, acquaMl: 3300, qualita: qualita({ porzioniFruttaVerdura: 1 }) })],
  ];
  for (const [adesso, d] of scenari) {
    for (const a of L.consigli(adesso, d, null, obiettiviPiano)) {
      const testo = (a.testo + ' ' + a.perche).toLowerCase();
      for (const parola of proibite) assert.ok(!testo.includes(parola), '«' + parola + '» in: ' + a.testo);
    }
  }
});

test('consigli: spiegazione serale - contiene i numeri del calcolo', () => {
  const top = consigliAlle(22, 0, giorno({ kcal: 3100, prot: 135, acquaMl: 3300, allenato: true }))[0];
  assert.ok(top.perche.includes('3.450'));
  assert.ok(top.perche.includes('allenamento'));
  assert.ok(top.perche.includes('3.100'));
});

// --- I testi dei consigli, parola per parola, come li produce il Kotlin --------------------------------------------
// Attesi presi dall'esecuzione del codice Kotlin vero (AdvisorEngine.advise), non scritti a mano.

const QUOTE = "colazione 20%, metà mattina 8%, pranzo 28%, dopo allenamento 12%, cena 25%, prima di dormire 7%";
const CENA_SALTATA = [2, "Oggi niente cena: domani prova frittata di 4 uova + asiago 50 g + pane 100 g (≈ 800 kcal in più).",
  "La finestra di cena (18:30–21:15) si è chiusa senza voci registrate. Nel piano quel momento vale ~875 kcal (25% della giornata): il consiglio per domani serve a non perdere quella quota."];

function comeKotlin(righe) {
  return righe.map(([priorita, testo, perche]) => ({ priorita, testo, perche }));
}

test('testi: sera sotto il minimo, con il buco in kcal e proteine', () => {
  const c = consigliAlle(21, 30, giorno({ kcal: 2400, prot: 100, acquaMl: 3000 }));
  assert.deepStrictEqual(c, comeKotlin([
    [0, "Ti mancano ~900 kcal e ~30 g di proteine per il minimo di 3.300: prima di dormire yogurt greco 200 g + frutta secca 30 g (≈ 370 kcal, 24 g proteine).",
      "Bilancio serale (dopo le 21:00). Minimo del giorno: 3.300 kcal. Finora: 2.400 kcal e 100 g di proteine (minimo 130 g). Mancano ~900 kcal e ~30 g di proteine: propongo l'opzione «prima di dormire» del piano più vicina al buco, scelta tra quelle ricche di proteine."],
    CENA_SALTATA,
  ]));
});

test('testi: sera a target', () => {
  const c = consigliAlle(22, 0, giorno({ kcal: 3450, prot: 140, acquaMl: 3300 }));
  assert.deepStrictEqual(c, comeKotlin([
    [0, "Giornata a target: 3.450 kcal e 140 g di proteine. Continua così.",
      "Sei a 3.450 kcal — dentro il minimo utile (3.300, con tolleranza di 150) e sotto il massimo 3.700 (+250 di margine) — con 140 g di proteine ≥ 130. Nessun intervento: come dice il piano, conta la costanza."],
    CENA_SALTATA,
  ]));
});

test('testi: sera con calorie ok ma proteine basse', () => {
  const c = consigliAlle(22, 0, giorno({ kcal: 3400, prot: 110, acquaMl: 3300 }));
  assert.deepStrictEqual(c, comeKotlin([
    [0, "Calorie ok (3.400), ma proteine a 110 g su 130: prima di dormire yogurt greco 200 g (+18 g di proteine).",
      "Calorie nel range utile (3.400 su 3.300–3.700), ma proteine a 110 g contro il minimo di 130: per la crescita muscolare sono la parte non negoziabile del piano. Lo yogurt greco è l'opzione serale con più proteine per caloria."],
    CENA_SALTATA,
  ]));
});

test('testi: sera sopra il range', () => {
  const c = consigliAlle(22, 0, giorno({ kcal: 4300, prot: 150, acquaMl: 3300 }));
  assert.deepStrictEqual(c, comeKotlin([
    [0, "Oggi 4.300 kcal, sopra il range: in fase di massa non è un dramma, domani torna nel 3.300–3.700.",
      "Registrate 4.300 kcal, oltre il massimo 3.700 più il margine di 250. In surplus un giorno alto non rovina nulla — conta la media della settimana — ma meglio non farlo diventare l'abitudine."],
    CENA_SALTATA,
  ]));
});

test('testi: sera di allenamento, la soglia sale di 150 e lo dice', () => {
  const c = consigliAlle(22, 0, giorno({ kcal: 3100, prot: 135, acquaMl: 3300, allenato: true }));
  assert.deepStrictEqual(c, comeKotlin([
    [0, "Ti mancano ~350 kcal per il minimo di 3.450 (giorno di allenamento): prima di dormire yogurt greco 200 g + frutta secca 30 g (≈ 370 kcal, 24 g proteine).",
      "Bilancio serale (dopo le 21:00). Minimo del giorno: 3.300 kcal +150 per l'allenamento registrato oggi → 3.450. Finora: 3.100 kcal e 135 g di proteine (minimo 130 g). Mancano ~350 kcal: propongo l'opzione «prima di dormire» del piano più vicina al buco."],
    [2, "Oggi niente cena: domani prova frittata di 4 uova + asiago 50 g + pane 100 g (≈ 800 kcal in più).",
      "La finestra di cena (18:30–21:15) si è chiusa senza voci registrate. Nel piano quel momento vale ~913 kcal (25% della giornata): il consiglio per domani serve a non perdere quella quota."],
  ]));
});

test('testi: pomeriggio indietro, la proposta ORA e il pasto saltato per domani', () => {
  const c = consigliAlle(16, 0, giorno({ kcal: 700, prot: 25, numeroPerMomento: { COLAZIONE: 1 } }));
  assert.deepStrictEqual(c, comeKotlin([
    [1, "Sei ~1400 kcal sotto il ritmo della giornata. dopo l'allenamento: panino (pane 100 g) con cotto 50 g + frutta secca 30 g (≈ 530 kcal, 21 g proteine).",
      "Alle 16:00 la quota attesa è ~2.118 kcal: fette dei momenti già trascorsi (" + QUOTE + ") applicate al punto medio del range (3.500). Registrate 700 kcal → ritardo ~1400, sopra la soglia di avviso (350). Propongo il prossimo momento libero con l'opzione del piano più vicina al buco."],
    [2, "Oggi niente pranzo: domani prova pasta 150 g + carne 180 g + pane 80 g (≈ 1000 kcal in più).",
      "La finestra di pranzo (11:45–14:30) si è chiusa senza voci registrate. Nel piano quel momento vale ~980 kcal (28% della giornata): il consiglio per domani serve a non perdere quella quota."],
  ]));
});

test('testi: mattina dopo una giornata scarsa', () => {
  const c = consigliAlle(8, 30, giorno(), giorno({ kcal: 2500, prot: 90 }));
  assert.deepStrictEqual(c, comeKotlin([
    [1, "Ieri ~2.500 kcal, sotto il minimo: oggi parti pieno. Colazione: latte 300 ml + 5 fette biscottate con miele + 3 uova (≈ 720 kcal).",
      "Ieri hai chiuso a 2.500 kcal contro un minimo di 3.300 (l'avviso scatta quando manca più di 200). Il modo più semplice di recuperare è partire con la colazione più completa del piano, senza stravolgere il resto della giornata."],
    [1, "Sei ~450 kcal sotto il ritmo della giornata. a colazione: yogurt greco 170 g + cereali 60 g + frutta secca 30 g (≈ 570 kcal, 22 g proteine).",
      "Alle 8:30 la quota attesa è ~467 kcal: fette dei momenti già trascorsi (" + QUOTE + ") applicate al punto medio del range (3.500). Registrate 0 kcal → ritardo ~450, sopra la soglia di avviso (350). Propongo il prossimo momento libero con l'opzione del piano più vicina al buco."],
  ]));
});

test('testi: ritmo in linea, spuntino saltato e voci senza valori', () => {
  const c = consigliAlle(14, 0, giorno({ kcal: 2000, prot: 80, nonNoti: 2, numeroPerMomento: { PRANZO: 3 } }));
  assert.deepStrictEqual(c, comeKotlin([
    [2, "Oggi niente spuntino di metà mattina: domani prova yogurt bianco 125 g + 4-5 taralli (≈ 280 kcal in più).",
      "La finestra di spuntino di metà mattina (9:30–11:45) si è chiusa senza voci registrate. Nel piano quel momento vale ~280 kcal (8% della giornata): il consiglio per domani serve a non perdere quella quota."],
    [5, "In linea col ritmo: 2.000 kcal su ~1.782 attese a quest'ora.",
      "Alle 14:00 la quota attesa è ~1.782 kcal: fette dei momenti già trascorsi (" + QUOTE + ") sul punto medio del range (3.500). Hai registrato 2.000 kcal: lo scarto è sotto la soglia di avviso (350), nessuna correzione necessaria."],
    [9, "2 voci senza valori nutrizionali: i totali reali sono più alti di quelli mostrati.",
      "2 voci della giornata sono state inserite senza kcal/proteine: i totali mostrati sono quindi un minimo garantito, non il valore reale. Per contarle, aggiungi i valori all'alimento in dispensa (bastano kcal e proteine per 100 g)."],
  ]));
});

test('testi: acqua indietro, con il target da 3250 ml che il Kotlin scrive "3,2 L"', () => {
  const d = giorno({ kcal: 2200, prot: 90, acquaMl: 1000, numeroPerMomento: { PRANZO: 1 } });
  const acqua = consigliAlle(16, 0, d).find((x) => x.priorita === 4);
  assert.deepStrictEqual(acqua, {
    priorita: 4,
    testo: "Acqua: sei a 1 su 3,2 L. Tieni la borraccia a portata.",
    perche: "Regola: metà del target entro metà pomeriggio. Target 3,2 L → soglia 1,6 L alle 15:00; registrati 1 L.",
  });
});

test('testi: frutta e verdura (traguardo, mai rimprovero)', () => {
  const d = giorno({ kcal: 2000, prot: 90, numeroPerMomento: { PRANZO: 1 }, qualita: qualita({ porzioniFruttaVerdura: 1 }) });
  const a = consigliAlle(16, 0, d).find((x) => x.priorita === 6);
  assert.deepStrictEqual(a, {
    priorita: 6,
    testo: "Frutta e verdura: 1 porzioni oggi. Aggiungine 2 — un frutto a metà mattina e verdura a cena bastano.",
    perche: "Conto una porzione ogni 100 g di frutta o verdura registrata: oggi 1. L'obiettivo è 3-5 al giorno. In un surplus contano più del solito: mangi tanto, e fibra e micronutrienti sono ciò che tiene in ordine digestione e recupero mentre il volume di cibo è alto. È un'aggiunta al piano, non toglie nulla alle calorie o alle proteine.",
  });
});

test('testi: qualità buona, grado di lavorazione e fibra', () => {
  const buona = giorno({ kcal: 3400, prot: 140, acquaMl: 3300, qualita: qualita({ porzioniFruttaVerdura: 4, fibraG: 32, kcalConGrado: 3000, kcalUltraProcessati: 300 }) });
  assert.deepStrictEqual(consigliAlle(22, 0, buona).find((x) => x.priorita === 6), {
    priorita: 6,
    testo: "Qualità della giornata buona: 4 porzioni di frutta e verdura e poca roba industriale.",
    perche: "Frutta e verdura a 4 porzioni (obiettivo 3-5), e solo il 10% delle calorie da prodotti industriali. È esattamente la differenza tra mettere su peso e mettere su peso buono.",
  });
  const scarsa = giorno({ kcal: 3400, prot: 140, acquaMl: 3300, qualita: qualita({ porzioniFruttaVerdura: 0, fibraG: 8, kcalConGrado: 3000, kcalUltraProcessati: 2700 }) });
  const c = consigliAlle(22, 0, scarsa);
  assert.deepStrictEqual(c.filter((x) => x.priorita >= 6).map((x) => x.testo), [
    "Frutta e verdura: nessuna porzione oggi. Aggiungine 3 — un frutto a metà mattina e verdura a cena bastano.",
    "Circa il 90% delle calorie di oggi viene da prodotti industriali. Il piano regge lo stesso: domani prova a sostituirne uno con cibo fresco — uova, riso, carne, frutta secca.",
    "Fibra: 8 g oggi. Con una dieta da 3.400 kcal punta a 30 g: pane e pasta integrali, verdura e frutta secca la coprono senza sforzo.",
  ]);
  assert.strictEqual(c.find((x) => x.priorita === 7).perche,
    "Registrati 8 g di fibra contro un obiettivo di ~30 g. Con introiti alti la fibra è il marcatore più semplice di una dieta fatta di cibo vero, e aiuta la digestione di pasti abbondanti. Conto solo gli alimenti che riportano il valore: quelli senza dato non li indovino.");
});

// --- Casi limite dei consigli (aggiunti col porting) ---------------------------------------------------------------

test('consigli: senza giorno o senza obiettivi non inventano nulla', () => {
  assert.deepStrictEqual(L.consigli(alle(22), null, null, obiettiviPiano), []);
  assert.deepStrictEqual(L.consigli(alle(22), giorno({ kcal: 100 }), null, null), []);
  assert.deepStrictEqual(L.consigli(alle(22), undefined, undefined, undefined), []);
});

test('consigli: ordinati per priorità, a parità nell\'ordine in cui nascono', () => {
  const d = giorno({ kcal: 3400, prot: 140, acquaMl: 3300, nonNoti: 1, qualita: qualita({ porzioniFruttaVerdura: 0, fibraG: 8, kcalConGrado: 3000, kcalUltraProcessati: 2700 }) });
  const c = consigliAlle(22, 0, d);
  assert.deepStrictEqual(c.map((x) => x.priorita), [0, 2, 6, 6, 7, 9]);
  assert.ok(c[2].testo.startsWith('Frutta e verdura'), 'frutta prima del grado di lavorazione, come nel Kotlin');
  assert.ok(c[3].testo.startsWith('Circa il'));
});

test('consigli: non modificano né il giorno, né ieri, né gli obiettivi', () => {
  const profondoGelato = (o) => { Object.values(o).forEach((v) => { if (v && typeof v === 'object') profondoGelato(v); }); return Object.freeze(o); };
  const d = profondoGelato(giorno({ kcal: 3400, prot: 110, nonNoti: 2, numeroPerMomento: { PRANZO: 1 }, qualita: qualita({ porzioniFruttaVerdura: 1, kcalConGrado: 900, kcalUltraProcessati: 800 }) }));
  const ieri = profondoGelato(giorno({ kcal: 2000, prot: 90 }));
  const ob = profondoGelato(Object.assign({}, obiettiviPiano));
  const adesso = alle(22);
  const prima = adesso.getTime();
  assert.ok(L.consigli(adesso, d, ieri, ob).length > 0);
  assert.strictEqual(adesso.getTime(), prima);
});

test('consigli: la qualità senza i valori derivati dà gli stessi consigli', () => {
  const grezza = { kcalTotali: 3000, kcalConGrado: 3000, kcalUltraProcessati: 2400, fibraG: 12, porzioniFruttaVerdura: 1 };
  const conDerivati = qualita(grezza);
  const a = consigliAlle(22, 0, giorno({ kcal: 3400, prot: 140, acquaMl: 3300, qualita: grezza }));
  const b = consigliAlle(22, 0, giorno({ kcal: 3400, prot: 140, acquaMl: 3300, qualita: conDerivati }));
  assert.deepStrictEqual(a, b);
  assert.ok(a.some((x) => x.testo.includes('prodotti industriali')));
});

test('consigli: qualitaGiorno() di voci vere alimenta i consigli senza adattamenti', () => {
  const voci = [
    { moment: 'PRANZO', grams: 130, kcal: 455, prot: 16, fibra: 3, nova: 4, gruppo: 'CEREALE' },
    { moment: 'CENA', grams: 300, kcal: 2900, prot: 100, fibra: 2, nova: 4, gruppo: 'DOLCE' },
  ];
  const t = L.totaliGiorno(voci);
  const q = L.qualitaGiorno(voci);
  const c = L.consigli(alle(22), { kcal: t.kcal, prot: t.prot, nonNoti: t.nonNoti, numeroPerMomento: t.numeroPerMomento, acquaMl: 3300, allenato: false, qualita: q }, null, obiettiviPiano);
  assert.ok(c.some((x) => x.testo.startsWith('Circa il 100% delle calorie')), JSON.stringify(c.map((x) => x.testo)));
});

test('consigli: ai confini delle fasce orarie gli avvisi cambiano nel minuto giusto', () => {
  // 15:00 esatte: dal pomeriggio l'acqua conta; un minuto prima no
  const d = giorno({ kcal: 2200, prot: 90, acquaMl: 1000, numeroPerMomento: { PRANZO: 1 } });
  assert.ok(!consigliAlle(14, 59, d).some((x) => x.testo.startsWith('Acqua')));
  assert.ok(consigliAlle(15, 0, d).some((x) => x.testo.startsWith('Acqua')));
  // 21:00: scatta il bilancio serale
  const s = giorno({ kcal: 2400, prot: 100, acquaMl: 3000 });
  assert.ok(!consigliAlle(20, 59, s).some((x) => x.testo.startsWith('Ti mancano')));
  assert.ok(consigliAlle(21, 0, s).some((x) => x.testo.startsWith('Ti mancano')));
  // 11:00: il recupero da ieri vale solo prima delle 11
  const ieri = giorno({ kcal: 2500, prot: 90 });
  assert.ok(consigliAlle(10, 59, giorno(), ieri).some((x) => x.testo.startsWith('Ieri')));
  assert.ok(!consigliAlle(11, 0, giorno(), ieri).some((x) => x.testo.startsWith('Ieri')));
});

test('consigli: il minimo di ieri ha una tolleranza di 200 kcal, il bilancio serale di 150', () => {
  const ieri = (kcal) => giorno({ kcal, prot: 90 });
  assert.ok(consigliAlle(8, 0, giorno(), ieri(3099.5)).some((x) => x.testo.startsWith('Ieri')));
  assert.ok(!consigliAlle(8, 0, giorno(), ieri(3100)).some((x) => x.testo.startsWith('Ieri')), 'a 3100 esatti non scatta: il confronto è stretto');
  assert.ok(!consigliAlle(8, 0, giorno(), ieri(0)).some((x) => x.testo.startsWith('Ieri')), 'ieri vuoto: niente da recuperare');
  assert.ok(consigliAlle(22, 0, giorno({ kcal: 3150, prot: 140, acquaMl: 3300 }))[0].testo.startsWith('Giornata a target'));
  assert.ok(consigliAlle(22, 0, giorno({ kcal: 3149, prot: 140, acquaMl: 3300 }))[0].testo.startsWith('Ti mancano'));
});

// --- DietQualityTest ----------------------------------------------------------------------------------------------

/** Una voce del diario come nel file dell'app: e(nome, grammi, kcal, fibra, nova, gruppo) dei test Kotlin. */
function voce(nome, grams, kcal, extra) {
  return Object.assign({
    id: 'e-' + nome, date: '2026-08-01', moment: 'PRANZO', nome, grams, kcal,
    prot: null, fibra: null, nova: null, gruppo: 'ALTRO', isDrink: false, createdAt: 0,
  }, extra || {});
}

test('qualità: porzioni frutta e verdura - 100 g = 1 porzione', () => {
  const q = L.qualitaGiorno([
    voce('Mela', 180, 94, { gruppo: 'FRUTTA' }),
    voce('Insalata', 120, 24, { gruppo: 'VERDURA' }),
    voce('Pasta', 130, 455, { gruppo: 'CEREALE' }),
  ]);
  vicino(q.porzioniFruttaVerdura, 3.0, 0.001);
});

test('qualità: frutta secca non conta come porzione di frutta', () => {
  const q = L.qualitaGiorno([voce('Mandorle', 300, 1800, { gruppo: 'GRASSI' })]);
  vicino(q.porzioniFruttaVerdura, 0.0, 0.001);
});

test('qualità: quota ultra-processati calcolata solo sulle voci con grado noto', () => {
  const q = L.qualitaGiorno([
    voce('Cereali', 100, 400, { nova: 4 }),
    voce('Pasta', 130, 400, { nova: 1 }),
    voce('Roba senza dato', 100, 1000), // nova null: fuori dal calcolo
  ]);
  vicino(q.kcalConGrado, 800.0, 0.001);
  vicino(q.quotaUltraProcessati, 0.5, 0.001);
  assert.ok(q.copertura < 0.5, 'copertura bassa: 800 kcal su 1800 totali');
});

test('qualità: campione troppo piccolo - nessuna conclusione sul grado', () => {
  const q = L.qualitaGiorno([voce('Merendina', 50, 200, { nova: 4 })]);
  assert.ok(q.kcalConGrado < 500);
  assert.strictEqual(q.quotaUltraProcessati, null);
});

test('qualità: fibra sommata solo dove il dato esiste', () => {
  const q = L.qualitaGiorno([
    voce('Pane', 100, 268, { fibra: 3.5 }),
    voce('Verdura', 200, 60, { fibra: 5.0 }),
    voce('Formaggio', 100, 370), // niente fibra dichiarata
  ]);
  vicino(q.fibraG, 8.5, 0.001);
});

test('qualità: giornata vuota - tutto a zero, nessuna divisione per zero', () => {
  const q = L.qualitaGiorno([]);
  vicino(q.kcalTotali, 0.0, 0.001);
  vicino(q.copertura, 0.0, 0.001);
  assert.strictEqual(q.quotaUltraProcessati, null);
});

test('qualità: media settimanale - ignora i giorni senza dati', () => {
  const pieno = L.qualitaGiorno([voce('Mela', 200, 100, { fibra: 4.0, gruppo: 'FRUTTA' })]);
  const vuoto = L.qualitaGiorno([]);
  const media = L.mediaQualita([pieno, vuoto, vuoto]);
  vicino(media.porzioniFruttaVerdura, 2.0, 0.001);
  vicino(media.fibraG, 4.0, 0.001);
});

test('qualità: media di zero giorni utili - null', () => {
  assert.strictEqual(L.mediaQualita([L.qualitaGiorno([])]), null);
});

// --- FoodMathTest -------------------------------------------------------------------------------------------------

test('quantità: pasta 130 g a 350 kcal per 100 - 455 kcal', () => {
  vicino(L.perGrammi(130, 350), 455.0, 0.001);
});

test('quantità: valori nulli restano nulli', () => {
  assert.strictEqual(L.perGrammi(130, null), null);
  assert.strictEqual(L.perGrammi(130, undefined), null);
});

test('quantità: arrotondamento a una cifra decimale', () => {
  vicino(L.perGrammi(150, 0.8), 1.2, 0.001);
});

test('totali giornata - kcal sommate e voci sconosciute contate', () => {
  const e = (moment, kcal, prot) => ({ id: 'x', date: '2026-07-30', moment, nome: 'test', grams: 100, kcal, prot });
  const t = L.totaliGiorno([
    e('COLAZIONE', 500, 20),
    e('PRANZO', 900, 40),
    e('PRANZO', null, null),
    e('BEVANDE', 130, 6.6),
  ]);
  vicino(t.kcal, 1530.0, 0.001);
  vicino(t.prot, 66.6, 0.001);
  assert.strictEqual(t.nonNoti, 1);
  assert.strictEqual(t.numeroPerMomento.PRANZO, 2);
  assert.deepStrictEqual(t.numeroPerMomento, { COLAZIONE: 1, PRANZO: 2, BEVANDE: 1 });
  assert.deepStrictEqual(t.kcalPerMomento, { COLAZIONE: 500, PRANZO: 900, BEVANDE: 130 });
});

// --- Casi limite del diario e della qualità -------------------------------------------------------------------------

test('totali: lista vuota o mancante - tutto a zero', () => {
  const vuoto = { kcal: 0, prot: 0, nonNoti: 0, kcalPerMomento: {}, numeroPerMomento: {} };
  assert.deepStrictEqual(L.totaliGiorno([]), vuoto);
  assert.deepStrictEqual(L.totaliGiorno(undefined), vuoto);
  assert.deepStrictEqual(L.totaliGiorno(null), vuoto);
});

test('totali: 0 kcal è un valore noto, null no; le proteine mancanti non contano', () => {
  const t = L.totaliGiorno([
    { moment: 'CENA', kcal: 0, prot: 0 },
    { moment: 'CENA', kcal: null, prot: 12 }, // kcal ignote ma proteine note: le proteine si sommano comunque
    { moment: 'CENA', kcal: 100 },
  ]);
  assert.strictEqual(t.kcal, 100);
  assert.strictEqual(t.prot, 12);
  assert.strictEqual(t.nonNoti, 1);
  assert.deepStrictEqual(t.numeroPerMomento, { CENA: 3 });
  assert.deepStrictEqual(t.kcalPerMomento, { CENA: 100 });
});

test('quantità: la metà strada va al pari, come nel Kotlin (130 g x 14,5 / 100 = 18,85 → 18,8)', () => {
  assert.strictEqual(L.perGrammi(130, 14.5), 18.8);
  assert.strictEqual(L.perGrammi(0, 100), 0);
  assert.strictEqual(L.perGrammi(100, 0), 0, 'zero per 100 g è un valore noto, non null');
  assert.strictEqual(L.perGrammi(250, 64), 160);
});

test('qualità: la quota di ultra-processati si calcola da 500 kcal di grado noto in su', () => {
  const con = (kcal) => L.qualitaGiorno([voce('x', 100, kcal, { nova: 4 })]);
  assert.strictEqual(con(499.9).quotaUltraProcessati, null);
  assert.strictEqual(con(500).quotaUltraProcessati, 1);
  assert.strictEqual(L.qualitaGiorno([voce('a', 100, 300, { nova: 4 }), voce('b', 100, 300, { nova: 2 })]).quotaUltraProcessati, 0.5);
});

test('qualità: solo il grado 4 è industriale; i valori mancanti valgono zero', () => {
  const q = L.qualitaGiorno([
    voce('a', 100, 200, { nova: 3 }), voce('b', 100, 300, { nova: 4 }), voce('c', 100, null, { nova: 4 }), voce('d', 100, 500, { nova: 1 }),
  ]);
  assert.strictEqual(q.kcalTotali, 1000);
  assert.strictEqual(q.kcalConGrado, 1000);
  assert.strictEqual(q.kcalUltraProcessati, 300);
  assert.strictEqual(q.quotaUltraProcessati, 0.3);
  assert.strictEqual(q.copertura, 1);
});

test('qualità: fibra e porzioni a una cifra, la metà strada va al pari (0,25 → 0,2; 0,75 → 0,8)', () => {
  const a = L.qualitaGiorno([voce('a', 25, 50, { fibra: 0.25, gruppo: 'FRUTTA' })]);
  assert.strictEqual(a.fibraG, 0.2);
  assert.strictEqual(a.porzioniFruttaVerdura, 0.2);
  const b = L.qualitaGiorno([voce('b', 75, 50, { fibra: 0.75, gruppo: 'VERDURA' })]);
  assert.strictEqual(b.fibraG, 0.8);
  assert.strictEqual(b.porzioniFruttaVerdura, 0.8);
});

test('qualità: la media ricalcola quota e copertura sui valori medi', () => {
  const g1 = L.qualitaGiorno([voce('a', 100, 1000, { nova: 4 })]);
  const g2 = L.qualitaGiorno([voce('b', 100, 1000, { nova: 1 })]);
  const media = L.mediaQualita([g1, g2]);
  assert.strictEqual(media.kcalTotali, 1000);
  assert.strictEqual(media.kcalUltraProcessati, 500);
  assert.strictEqual(media.quotaUltraProcessati, 0.5);
  assert.strictEqual(media.copertura, 1);
  assert.strictEqual(L.mediaQualita([]), null);
  assert.strictEqual(L.mediaQualita(undefined), null);
});

test('qualità: la lista in ingresso non viene toccata', () => {
  const voci = [voce('a', 100, 200, { nova: 4, fibra: 2, gruppo: 'FRUTTA' })];
  const copia = JSON.stringify(voci);
  L.qualitaGiorno(voci);
  L.totaliGiorno(voci);
  assert.strictEqual(JSON.stringify(voci), copia);
});

// --- SchedaNutrizionaleTest ---------------------------------------------------------------------------------------

/** Un alimento come nel file: valori per 100 g, quelli non indicati sono null. */
function alimento(o) {
  const x = o || {};
  const v = (k) => (x[k] === undefined ? null : x[k]);
  return {
    id: 't', nome: x.nome || 'Test', kcal100: v('kcal'), prot100: v('prot'), grassi100: v('grassi'), carb100: v('carb'),
    fibra100: v('fibra'), grassiSaturi100: v('saturi'), zuccheri100: v('zuccheri'), sale100: v('sale'), nova: v('nova'),
    gruppo: 'ALTRO', isDrink: !!x.drink,
  };
}
const titoli = (a) => L.schedaNutrizionale(a).righe.map((r) => r.titolo);

test('scheda: mostra solo le righe di cui esiste il dato', () => {
  assert.deepStrictEqual(titoli(alimento({ kcal: 356, prot: 14.5 })), ['Energia', 'Proteine']);
});

test('scheda: alimento senza valori - nessuna riga, nessuna invenzione', () => {
  assert.deepStrictEqual(L.schedaNutrizionale(alimento()).righe, []);
});

/** Il metro è la massa: un cibo calorico è un pregio, non un difetto. */
test('scheda: cibo calorico giudicato positivamente', () => {
  const righe = L.schedaNutrizionale(alimento({ kcal: 600 })).righe;
  assert.strictEqual(righe.length, 1);
  assert.strictEqual(righe[0].ok, true);
  assert.strictEqual(righe[0].livello, 'BUONO');
  assert.ok(righe[0].nota.includes('surplus'));
});

test('scheda: cibo poco calorico - neutro, mai colpevolizzato', () => {
  const righe = L.schedaNutrizionale(alimento({ kcal: 30 })).righe;
  assert.strictEqual(righe[0].ok, false);
  assert.strictEqual(righe[0].livello, 'NEUTRO');
});

test('scheda: proteine alte sono verdi', () => {
  const righe = L.schedaNutrizionale(alimento({ prot: 25 })).righe;
  assert.strictEqual(righe.length, 1);
  assert.strictEqual(righe[0].livello, 'BUONO');
  assert.ok(righe[0].nota.includes('Ottima'));
});

test('scheda: prodotto industriale - nessun giudizio negativo', () => {
  const righe = L.schedaNutrizionale(alimento({ nova: 4 })).righe;
  assert.strictEqual(righe.length, 1);
  assert.strictEqual(righe[0].valore, 'NOVA 4');
  assert.strictEqual(righe[0].livello, 'NEUTRO');
});

/** Stessa regola dei consigli: nessuna parola che colpevolizza. */
test('scheda: nessuna riga usa parole colpevolizzanti', () => {
  const proibite = ['cattiv', 'sbagliat', 'male', 'schifezz', 'evita', 'dannos', 'nocivo'];
  const completo = alimento({ kcal: 539, prot: 6, grassi: 30.5, carb: 57.5, fibra: 3.4, saturi: 10.6, zuccheri: 56.3, sale: 0.107, nova: 4 });
  for (const r of L.schedaNutrizionale(completo).righe) {
    const testo = (r.titolo + ' ' + r.nota).toLowerCase();
    for (const p of proibite) assert.ok(!testo.includes(p), '«' + p + '» in: ' + r.nota);
  }
});

test('scheda: contributo - riporta la quantità reale della voce', () => {
  const c = L.contributoScheda(alimento({ kcal: 356, prot: 14.5 }), 130);
  assert.ok(c.includes('130 g'));
  assert.ok(c.includes('462,8 kcal'));
  // 14,5 x 1,3 = 18,85 → una cifra decimale, come ovunque nell'app (e a metà strada si va al pari: 18,8).
  assert.ok(c.includes('18,8 g di proteine'), 'trovato invece: ' + c);
});

test('scheda: contributo di una bevanda - in millilitri', () => {
  const c = L.contributoScheda(alimento({ kcal: 64, drink: true }), 300);
  assert.ok(c.includes('300 ml'));
});

test('scheda: scheda completa - ordine stabile e tutte le voci presenti', () => {
  const completo = alimento({ kcal: 356, prot: 14.5, grassi: 1.5, carb: 70, fibra: 2.9, saturi: 0.3, zuccheri: 3.1, sale: 0.0, nova: 1 });
  assert.deepStrictEqual(titoli(completo), ['Energia', 'Proteine', 'Carboidrati', 'Fibre', 'Grassi', 'Grassi saturi', 'Zuccheri', 'Sale', 'Lavorazione']);
});

// --- Casi limite della scheda (attesi presi dal Kotlin vero) -------------------------------------------------------

const righeComeKotlin = (a) => L.schedaNutrizionale(a).righe.map((r) => [r.titolo, r.nota, r.valore, r.ok]);

test('scheda: un prodotto industriale e calorico, riga per riga', () => {
  const a = alimento({ kcal: 539, prot: 6, grassi: 30.5, carb: 57.5, fibra: 3.4, saturi: 10.6, zuccheri: 56.3, sale: 0.107, nova: 4 });
  assert.deepStrictEqual(righeComeKotlin(a), [
    ['Energia', 'Molto calorico: comodo per il surplus', '539 kcal', true],
    ['Proteine', 'Poche proteine: abbinalo a una fonte proteica', '6 g', false],
    ['Carboidrati', "Energia per l'allenamento", '57,5 g', false],
    ['Fibre', "Un po' di fibre", '3,4 g', true],
    ['Grassi', 'Grassi abbondanti: molte calorie in poco volume', '30,5 g', false],
    ['Grassi saturi', 'Grassi saturi presenti', '10,6 g', false],
    ['Zuccheri', 'Zuccheri presenti', '56,3 g', false],
    ['Sale', 'Povero di sale', '0,1 g', true],
    ['Lavorazione', 'Prodotto industriale', 'NOVA 4', false],
  ]);
});

test('scheda: un cibo vero, riga per riga', () => {
  const a = alimento({ kcal: 356, prot: 14.5, grassi: 1.5, carb: 70, fibra: 2.9, saturi: 0.3, zuccheri: 3.1, sale: 0.0, nova: 1 });
  assert.deepStrictEqual(righeComeKotlin(a), [
    ['Energia', 'Molto calorico: comodo per il surplus', '356 kcal', true],
    ['Proteine', 'Buon contenuto di proteine', '14,5 g', true],
    ['Carboidrati', "Energia per l'allenamento", '70 g', false],
    ['Fibre', 'Poche fibre', '2,9 g', false],
    ['Grassi', 'Grassi moderati', '1,5 g', false],
    ['Grassi saturi', 'Povero di grassi saturi', '0,3 g', true],
    ['Zuccheri', 'Povero di zuccheri', '3,1 g', true],
    ['Sale', 'Povero di sale', '0 g', true],
    ['Lavorazione', 'Cibo non lavorato', 'NOVA 1', true],
  ]);
});

test('scheda: lo zero è un dato (la riga c\'è), un NOVA fuori scala resta "industriale"', () => {
  const a = alimento({ kcal: 0, prot: 0, fibra: 6, grassi: 20, nova: 5, drink: true });
  assert.deepStrictEqual(righeComeKotlin(a), [
    ['Energia', 'Leggero: ne serve una porzione generosa', '0 kcal', false],
    ['Proteine', 'Poche proteine: abbinalo a una fonte proteica', '0 g', false],
    ['Fibre', 'Ricco di fibre', '6 g', true],
    ['Grassi', 'Grassi abbondanti: molte calorie in poco volume', '20 g', false],
    ['Lavorazione', 'Prodotto industriale', 'NOVA 5', false],
  ]);
});

test('scheda: le soglie esatte (150, 300, 8, 15, 3, 6, 1,5, 5, 0,3, 20)', () => {
  const nota = (campo, valore) => L.schedaNutrizionale(alimento({ [campo]: valore })).righe[0].nota;
  assert.strictEqual(nota('kcal', 149.9), 'Leggero: ne serve una porzione generosa');
  assert.strictEqual(nota('kcal', 150), 'Buon apporto di energia');
  assert.strictEqual(nota('kcal', 299.9), 'Buon apporto di energia');
  assert.strictEqual(nota('kcal', 300), 'Molto calorico: comodo per il surplus');
  assert.strictEqual(nota('prot', 7.9), 'Poche proteine: abbinalo a una fonte proteica');
  assert.strictEqual(nota('prot', 8), 'Buon contenuto di proteine');
  assert.strictEqual(nota('prot', 15), 'Ottima fonte di proteine');
  assert.strictEqual(nota('fibra', 2.9), 'Poche fibre');
  assert.strictEqual(nota('fibra', 3), "Un po' di fibre");
  assert.strictEqual(nota('fibra', 6), 'Ricco di fibre');
  assert.strictEqual(nota('saturi', 1.5), 'Povero di grassi saturi');
  assert.strictEqual(nota('saturi', 1.6), 'Grassi saturi presenti');
  assert.strictEqual(nota('zuccheri', 5), 'Povero di zuccheri');
  assert.strictEqual(nota('zuccheri', 5.1), 'Zuccheri presenti');
  assert.strictEqual(nota('sale', 0.3), 'Povero di sale');
  assert.strictEqual(nota('sale', 0.31), 'Sale presente');
  assert.strictEqual(nota('grassi', 19.9), 'Grassi moderati');
  assert.strictEqual(nota('grassi', 20), 'Grassi abbondanti: molte calorie in poco volume');
});

test('scheda: il livello della scheda è il migliore tra le righe e non è mai negativo', () => {
  assert.strictEqual(L.schedaNutrizionale(alimento()).livello, 'NEUTRO');
  assert.strictEqual(L.schedaNutrizionale(alimento({ kcal: 30, nova: 4 })).livello, 'NEUTRO');
  assert.strictEqual(L.schedaNutrizionale(alimento({ kcal: 30, prot: 25 })).livello, 'BUONO');
  for (const a of [alimento(), alimento({ kcal: 30 }), alimento({ nova: 4 }), alimento({ kcal: 900, prot: 30 })]) {
    const s = L.schedaNutrizionale(a);
    assert.ok(['BUONO', 'NEUTRO'].includes(s.livello));
    for (const r of s.righe) assert.strictEqual(r.livello, r.ok ? 'BUONO' : 'NEUTRO');
  }
});

test('scheda: il contributo con i soli dati che ci sono, e un alimento mancante non fa saltare nulla', () => {
  assert.strictEqual(L.contributoScheda(alimento({ prot: 14.5 }), 130), '130 g, 18,8 g di proteine');
  assert.strictEqual(L.contributoScheda(alimento(), 99.95), '100 g');
  assert.strictEqual(L.contributoScheda(alimento({ kcal: 64, drink: true }), 300), '300 ml = 192 kcal');
  assert.strictEqual(L.contributoScheda(alimento({ kcal: 356, prot: 14.5 }), 130), '130 g = 462,8 kcal, 18,8 g di proteine');
  assert.deepStrictEqual(L.schedaNutrizionale(null).righe, []);
  assert.strictEqual(L.contributoScheda(undefined, 50), '50 g');
});

// --- WorkoutQueriesTest -------------------------------------------------------------------------------------------
// Le sessioni e le serie hanno la forma del file dell'app ({ weightKg, reps }); le risposte sono sempre { kg, rip }.

const serieFile = (weightKg, reps) => ({ weightKg, reps });
const kr = (kg, rip) => ({ kg, rip });
const logEs = (exerciseId, ...sets) => ({ exerciseId, nome: exerciseId, sets });
function sessione(id, date, startedAt, ...logs) {
  return { id, date, type: 'SPINTA', exercises: logs, startedAt, firstSetAt: 0, lastSetAt: 0, routineId: null, routineName: '' };
}

const s1 = sessione('a', '2026-07-20', 1, logEs('sp', serieFile(10, 8), serieFile(10, 8)));
const s2 = sessione('b', '2026-07-24', 2, logEs('sp', serieFile(10, 10), serieFile(10, 9)));
const s3 = sessione('c', '2026-07-28', 3, logEs('sp')); // sessione senza serie
const corrente = sessione('d', '2026-07-31', 4, logEs('sp'));
const tutte = [s1, s2, s3, corrente];

test('allenamento: ultima volta - sessione precedente più recente con serie', () => {
  const prev = L.ultimeSerie(tutte, 'sp', 'd');
  assert.notStrictEqual(prev, null);
  assert.strictEqual(prev.data, '2026-07-24');
  assert.deepStrictEqual(prev.serie, [kr(10, 10), kr(10, 9)]);
});

test('allenamento: ultima volta - stesso giorno ordina per orario di inizio', () => {
  const m1 = sessione('m1', '2026-07-30', 5, logEs('sp', serieFile(8, 8)));
  const m2 = sessione('m2', '2026-07-30', 9, logEs('sp', serieFile(9, 8)));
  const cur = sessione('cur', '2026-07-31', 1, logEs('sp'));
  assert.deepStrictEqual(L.ultimeSerie([m1, m2, cur], 'sp', 'cur').serie, [kr(9, 8)]);
});

test('allenamento: ultima volta - null se mai fatto prima', () => {
  assert.strictEqual(L.ultimeSerie(tutte, 'mai-fatto', 'd'), null);
});

test('allenamento: storico - dal più recente, senza sessioni vuote', () => {
  const h = L.storicoEsercizio(tutte, 'sp');
  assert.deepStrictEqual(h.map((r) => r.data), ['2026-07-24', '2026-07-20']);
});

test('allenamento: giorno di allenamento - richiede almeno una serie', () => {
  assert.strictEqual(L.allenatoIl(tutte, '2026-07-24'), true);
  assert.strictEqual(L.allenatoIl(tutte, '2026-07-28'), false); // sessione vuota
  assert.strictEqual(L.allenatoIl(tutte, '2026-07-25'), false); // giorno senza sessioni
});

test('allenamento: progressione - tutte le serie al massimo del range', () => {
  assert.strictEqual(L.tutteAlMassimo([serieFile(12, 12), serieFile(12, 13)], 12), true);
  assert.strictEqual(L.tutteAlMassimo([serieFile(12, 12), serieFile(12, 10)], 12), false);
  assert.strictEqual(L.tutteAlMassimo([serieFile(12, 12)], null), false);
  assert.strictEqual(L.tutteAlMassimo([], 12), false);
});

test('allenamento: storico - ogni riga porta il nome della scheda di allora', () => {
  const vecchia = sessione('v', '2026-07-20', 1, logEs('sp', serieFile(10, 8)));
  const nuova = { id: 'n', date: '2026-08-10', exercises: [logEs('sp', serieFile(11, 8))], startedAt: 2, routineId: 'r-1', routineName: 'Upper' };
  const h = L.storicoEsercizio([vecchia, nuova], 'sp');
  assert.deepStrictEqual(h.map((r) => r.etichetta), ['Upper', 'Spinta']);
});

// ---- proposta della serie: un tocco su "Segna" nel caso normale ----

const press = { id: 'sp', nome: 'Shoulder Press', gruppo: 'Spalle', repMin: 8, repMax: 12 };

test('allenamento: proposta - di norma l\'ultima serie della volta scorsa', () => {
  const p = L.serieProposta([serieFile(12, 10), serieFile(12, 9)], press);
  assert.deepStrictEqual(p.set, kr(12, 9));
  assert.strictEqual(p.progressione, false);
});

test('allenamento: proposta - tutte al massimo del range, un disco in più ripartendo dal minimo', () => {
  const p = L.serieProposta([serieFile(12, 12), serieFile(12, 12), serieFile(12, 13)], press);
  assert.deepStrictEqual(p.set, kr(13, 8));
  assert.strictEqual(p.progressione, true);
});

test('allenamento: proposta - il salto di peso è quello dell\'esercizio', () => {
  const conDischi = Object.assign({}, press, { incrementoKg: 2.5 });
  const p = L.serieProposta([serieFile(10, 12)], conDischi);
  vicino(p.set.kg, 12.5, 0.001);
});

test('allenamento: proposta - a corpo libero non c\'è disco da aggiungere', () => {
  const pushup = { id: 'pu', nome: 'Push-up', gruppo: 'Petto', repMin: 10, repMax: 15 };
  const p = L.serieProposta([serieFile(0, 15), serieFile(0, 15)], pushup);
  assert.deepStrictEqual(p.set, kr(0, 15));
  assert.strictEqual(p.progressione, false);
});

test('allenamento: proposta - senza range niente progressione automatica', () => {
  const libero = { id: 'x', nome: 'X', gruppo: 'Altro' };
  assert.strictEqual(L.serieProposta([serieFile(10, 30)], libero).progressione, false);
});

test('allenamento: proposta - mai fatto, nessuna proposta', () => {
  assert.strictEqual(L.serieProposta(null, press), null);
  assert.strictEqual(L.serieProposta([], press), null);
});

test('allenamento: arrotonda - niente pesi come 12,500000001', () => {
  let peso = 10.0;
  for (let i = 0; i < 5; i++) peso = L.arrotondaKg(peso + 0.5);
  assert.strictEqual(peso, 12.5);
});

test('allenamento: ultima volta di una scheda - solo sessioni con serie', () => {
  const a = { id: 'a', date: '2026-08-01', exercises: [logEs('sp', serieFile(10, 8))], routineId: 'r1' };
  const b = { id: 'b', date: '2026-08-05', exercises: [logEs('sp')], routineId: 'r1' }; // vuota
  const c = { id: 'c', date: '2026-08-07', exercises: [logEs('sp', serieFile(10, 8))], routineId: 'r2' };
  assert.strictEqual(L.ultimaVoltaScheda([a, b, c], 'r1'), '2026-08-01');
  assert.strictEqual(L.ultimaVoltaScheda([a, b, c], 'r3'), null);
});

// --- Casi limite dell'allenamento -------------------------------------------------------------------------------------

test('allenamento: le serie si leggono sia {weightKg, reps} sia {kg, rip}, e escono sempre {kg, rip}', () => {
  const comeFile = sessione('a', '2026-07-20', 1, logEs('sp', serieFile(10, 8), serieFile(12.5, 6)));
  const comePc = sessione('a', '2026-07-20', 1, logEs('sp', kr(10, 8), kr(12.5, 6)));
  assert.deepStrictEqual(L.storicoEsercizio([comeFile], 'sp'), L.storicoEsercizio([comePc], 'sp'));
  assert.deepStrictEqual(L.storicoEsercizio([comeFile], 'sp')[0].serie, [kr(10, 8), kr(12.5, 6)]);
  assert.strictEqual(L.fmtSerie(serieFile(12.5, 6)), L.fmtSerie(kr(12.5, 6)));
  assert.strictEqual(L.tutteAlMassimo([kr(10, 12), kr(10, 12)], 12), true);
  assert.deepStrictEqual(L.serieProposta([kr(12, 10), kr(12, 9)], press).set, kr(12, 9));
});

test('allenamento: senza sessione in corso contano tutte; con un id sconosciuto nessun filtro sulle date', () => {
  assert.strictEqual(L.ultimeSerie(tutte, 'sp').data, '2026-07-24');
  assert.strictEqual(L.ultimeSerie(tutte, 'sp', null).data, '2026-07-24');
  assert.strictEqual(L.ultimeSerie(tutte, 'sp', 'non-esiste').data, '2026-07-24');
  const dopo = sessione('z', '2026-08-30', 1, logEs('sp', serieFile(30, 5)));
  assert.strictEqual(L.ultimeSerie(tutte.concat([dopo]), 'sp', 'd').data, '2026-07-24', 'una sessione successiva alla corrente non conta');
  assert.strictEqual(L.ultimeSerie(tutte.concat([dopo]), 'sp', 'non-esiste').data, '2026-08-30');
});

test('allenamento: la sessione in corso non è mai la "volta scorsa" di sé stessa', () => {
  const unica = sessione('solo', '2026-07-31', 1, logEs('sp', serieFile(10, 8)));
  assert.strictEqual(L.ultimeSerie([unica], 'sp', 'solo'), null);
});

test('allenamento: a parità di giorno e di orario vince la prima della lista (ordinamento stabile)', () => {
  const a = sessione('a', '2026-07-30', 5, logEs('sp', serieFile(8, 8)));
  const b = sessione('b', '2026-07-30', 5, logEs('sp', serieFile(9, 8)));
  assert.deepStrictEqual(L.ultimeSerie([a, b], 'sp', 'cur').serie, [kr(8, 8)]);
  assert.deepStrictEqual(L.ultimeSerie([b, a], 'sp', 'cur').serie, [kr(9, 8)]);
});

test('allenamento: conta il primo esercizio con quell\'id nella sessione, come nel Kotlin', () => {
  const doppia = sessione('d', '2026-07-24', 1, logEs('sp'), logEs('sp', serieFile(50, 5)));
  assert.strictEqual(L.ultimeSerie([doppia], 'sp', 'x'), null);
  assert.deepStrictEqual(L.storicoEsercizio([doppia], 'sp'), []);
});

test('allenamento: le liste in ingresso non vengono riordinate né modificate', () => {
  const lista = [s2, s1, corrente, s3];
  const prima = JSON.stringify(lista);
  L.ultimeSerie(lista, 'sp', 'd');
  L.storicoEsercizio(lista, 'sp');
  L.allenatoIl(lista, '2026-07-24');
  L.ultimaVoltaScheda(lista, 'r1');
  assert.strictEqual(JSON.stringify(lista), prima);
  assert.deepStrictEqual(L.ultimeSerie(undefined, 'sp'), null);
  assert.deepStrictEqual(L.storicoEsercizio(null, 'sp'), []);
  assert.strictEqual(L.allenatoIl(undefined, '2026-07-24'), false);
  assert.strictEqual(L.ultimaVoltaScheda(null, 'r1'), null);
});

test('allenamento: l\'etichetta della sessione - scheda, poi il tipo delle prime versioni, poi "Allenamento"', () => {
  const con = (extra) => Object.assign({ id: 'x', date: '2026-07-20', exercises: [logEs('sp', serieFile(1, 1))], startedAt: 1 }, extra);
  const et = (s) => L.storicoEsercizio([s], 'sp')[0].etichetta;
  assert.strictEqual(et(con({ routineName: 'Upper', type: 'SPINTA' })), 'Upper');
  assert.strictEqual(et(con({ routineName: '   ', type: 'TRAZIONE' })), 'Trazione');
  assert.strictEqual(et(con({ routineName: '', type: 'SPINTA' })), 'Spinta');
  assert.strictEqual(et(con({ type: null })), 'Allenamento');
  assert.strictEqual(et(con({})), 'Allenamento');
});

test('allenamento: la proposta parte dal peso massimo (non dall\'ultima serie) e rispetta i campi mancanti', () => {
  const p = L.serieProposta([serieFile(20, 12), serieFile(10, 12)], { repMax: 12 });
  assert.deepStrictEqual(p, { set: kr(21, 12), progressione: true }, 'senza repMin si riparte dalle ripetizioni dell\'ultima serie; senza salto +1 kg');
  assert.deepStrictEqual(L.serieProposta([serieFile(20, 12), serieFile(10, 12)], { repMin: 6, repMax: 12, incrementoKg: 0 }),
    { set: kr(21, 6), progressione: true }, 'un salto di 0 vale 1 kg');
  assert.deepStrictEqual(L.serieProposta([serieFile(22.5, 15)], { repMin: 8, repMax: 15, incrementoKg: -2 }),
    { set: kr(23.5, 8), progressione: true }, 'un salto negativo vale 1 kg');
  assert.deepStrictEqual(L.serieProposta([serieFile(22.5, 15)], null), { set: kr(22.5, 15), progressione: false });
});

test('allenamento: arrotondaKg - centesimi, la metà strada va al pari (valori del Kotlin)', () => {
  const attesi = [[0.125, 0.12], [0.375, 0.38], [1.005, 1], [2.675, 2.68], [12.345, 12.34], [10.005, 10.01], [33.335, 33.34], [99.995, 100], [12.500000001, 12.5], [0.005, 0], [7.5, 7.5], [80.125, 80.12]];
  for (const [kg, atteso] of attesi) assert.strictEqual(L.arrotondaKg(kg), atteso, String(kg));
});

test('allenamento: fmtSerie - peso con una cifra e "corpo" quando non c\'è peso', () => {
  assert.strictEqual(L.fmtSerie(kr(10, 8)), '10 kg×8');
  assert.strictEqual(L.fmtSerie(kr(12.5, 6)), '12,5 kg×6');
  assert.strictEqual(L.fmtSerie(kr(12.25, 6)), '12,2 kg×6', 'la metà strada va al pari');
  assert.strictEqual(L.fmtSerie(kr(0, 12)), 'corpo×12');
  assert.strictEqual(L.fmtSerie({ weightKg: 0.04, reps: 3 }), '0 kg×3', 'ogni peso sopra zero è un peso, anche se si arrotonda a 0');
});

// --- CompilaEsercizioTest -----------------------------------------------------------------------------------------

const r = (nome, salto) => L.compilaEsercizio(nome, salto === undefined ? null : salto);

/** Le regole devono ridare gli stessi valori della scheda di partenza. */
test('compila: gli esercizi della scheda escono come nella scheda', () => {
  const attesi = {
    'Shoulder Press': ['Spalle', '3-4', 8, 12],
    'Alzate Laterali': ['Spalle', '3', 12, 20],
    'French Press': ['Tricipiti', '3', 10, 15],
    'Curl con manubri': ['Bicipiti', '3', 8, 12],
    'Reverse Fly': ['Salute spalle', '2-3', 12, 15],
    'Face Pull': ['Salute spalle', '2-3', 12, 15],
    'Leg Raises a terra': ['Addominali', '3', 12, 20],
    'Alzate polpacci a una gamba': ['Polpacci', '3', 15, 25],
  };
  for (const [nome, atteso] of Object.entries(attesi)) {
    const p = r(nome);
    assert.notStrictEqual(p, null, 'non riconosciuto: ' + nome);
    assert.deepStrictEqual([p.gruppo, p.serie, p.repMin, p.repMax], atteso, nome);
  }
});

test('compila: tutti gli esercizi di partenza vengono riconosciuti', () => {
  const partenza = ['Shoulder Press', 'Alzate Laterali', 'French Press', 'Diamond Push-up', 'Floor Press con manubri',
    'Rematore a un braccio', 'Superman Row', 'Curl con manubri', 'Hammer Curl', 'Reverse Fly', 'Face Pull',
    'Leg Raises a terra', 'Crunch', 'Alzate polpacci a una gamba'];
  for (const nome of partenza) assert.notStrictEqual(r(nome), null, 'non riconosciuto: ' + nome);
});

test('compila: nomi italiani comuni in palestra', () => {
  assert.strictEqual(r('Squat con manubri').gruppo, 'Gambe');
  assert.strictEqual(r('Stacco da terra').gruppo, 'Gambe');
  assert.strictEqual(r('Panca piana').gruppo, 'Petto');
  assert.strictEqual(r('Croci ai cavi').gruppo, 'Petto');
  assert.strictEqual(r('Piegamenti').gruppo, 'Petto');
  assert.strictEqual(r('Trazioni alla sbarra').gruppo, 'Schiena');
  assert.strictEqual(r('Lat machine').gruppo, 'Schiena');
  assert.strictEqual(r('Pulley basso').gruppo, 'Schiena');
  assert.strictEqual(r('Military press').gruppo, 'Spalle');
  assert.strictEqual(r('Push down al cavo').gruppo, 'Tricipiti');
  assert.strictEqual(r('Hip thrust').gruppo, 'Glutei');
  assert.strictEqual(r('Crunch inverso').gruppo, 'Addominali');
});

test('compila: quelli specifici prima dei generici', () => {
  assert.strictEqual(r('Leg curl').gruppo, 'Gambe'); // non Bicipiti per via di "curl"
  assert.strictEqual(r('Diamond push-up').gruppo, 'Tricipiti'); // non Petto per via di "push-up"
  assert.strictEqual(r('Alzate posteriori').gruppo, 'Salute spalle'); // non Spalle generico
  assert.strictEqual(r('Alzate le gambe').gruppo, 'Addominali'); // non Spalle per via di "alzate"
  assert.strictEqual(r('Alzate laterali').gruppo, 'Spalle');
});

test('compila: maiuscole, accenti e trattini non contano', () => {
  assert.strictEqual(r('push up').gruppo, r('PUSH-UP').gruppo);
  assert.strictEqual(r('military press').gruppo, r('  Military   Press ').gruppo);
  assert.strictEqual(r('Affondi all\'indietro').gruppo, 'Gambe');
});

test('compila: "lat" non scatta dentro "laterali"', () => {
  assert.strictEqual(r('Alzate laterali con elastico').gruppo, 'Spalle');
});

// ---- salto di peso: dipende dall'attrezzo, non dall'esercizio ----

test('compila: bilanciere 2,5 kg, macchine e cavi 5 kg', () => {
  assert.strictEqual(r('Panca piana con bilanciere').incrementoKg, 2.5);
  assert.strictEqual(r('Lat machine').incrementoKg, 5.0);
  assert.strictEqual(r('Croci ai cavi').incrementoKg, 5.0);
});

test('compila: manubri - il salto che l\'utente usa già', () => {
  assert.strictEqual(r('Curl con manubri', 2.0).incrementoKg, 2.0);
  assert.strictEqual(r('Curl con manubri', null).incrementoKg, null); // vuoto = 1 kg di serie
});

test('compila: corpo libero - nessun salto, a meno che sia zavorrato', () => {
  assert.strictEqual(r('Trazioni', 2.0).incrementoKg, null);
  assert.strictEqual(r('Piegamenti', 2.0).incrementoKg, null);
  assert.strictEqual(r('Trazioni zavorrate', 2.0).incrementoKg, 2.0);
});

test('compila: note utili - per lato e plank a tempo', () => {
  assert.strictEqual(r('Rematore a un braccio').nota, 'per lato');
  assert.strictEqual(r('Affondi con manubri').nota, 'per lato');
  assert.strictEqual(r('Plank').nota, 'a tempo: segna i secondi come ripetizioni');
  assert.strictEqual(r('Panca piana').nota, null);
});

test('compila: nomi sconosciuti - nessuna invenzione, passa all\'AI', () => {
  assert.strictEqual(r('Farmer walk'), null);
  assert.strictEqual(r('Burpees'), null);
  assert.strictEqual(r(''), null);
});

test('compila: salto abituale - il più usato tra quelli impostati', () => {
  assert.strictEqual(L.saltoAbituale([2.0, null, 2.0, 1.0]), 2.0);
  assert.strictEqual(L.saltoAbituale([null, null]), null);
});

// ---- risposta dell'AI ----

const ai = (oggetto) => L.leggiRispostaAiEsercizio(JSON.stringify(oggetto));

test('compila: AI - risposta normale', () => {
  const p = ai({ gruppo: 'Gambe', serie: '3', repMin: 10, repMax: 15, incrementoKg: 2, nota: 'schiena dritta' });
  assert.strictEqual(p.gruppo, 'Gambe');
  assert.deepStrictEqual([p.repMin, p.repMax], [10, 15]);
  assert.strictEqual(p.incrementoKg, 2.0);
  assert.strictEqual(p.nota, 'schiena dritta');
});

test('compila: AI - corpo libero e range scritto al contrario', () => {
  const p = ai({ gruppo: 'Petto', serie: '3', repMin: 15, repMax: 8, incrementoKg: 0, nota: '' });
  assert.deepStrictEqual([p.repMin, p.repMax], [8, 15]);
  assert.strictEqual(p.incrementoKg, null);
  assert.strictEqual(p.nota, null);
});

test('compila: AI - valori assurdi vengono scartati invece di finire nella scheda', () => {
  const p = ai({ gruppo: 'Muscoli vari', serie: 'tante', repMin: 500, repMax: 12, incrementoKg: 80, nota: 'ok' });
  assert.strictEqual(p.gruppo, 'Altro');
  assert.strictEqual(p.serie, '3');
  assert.deepStrictEqual([p.repMin, p.repMax], [12, 12]);
  assert.strictEqual(p.incrementoKg, null);
});

test('compila: AI - nome non riconosciuto', () => {
  assert.strictEqual(ai({ gruppo: 'Altro', serie: '3', repMin: 8, repMax: 12, incrementoKg: 0, nota: 'nome non riconosciuto' }), null);
  assert.strictEqual(ai({ nota: 'NOME NON RICONOSCIUTO, scusa' }), null);
});

test('compila: AI - la risposta può arrivare avvolta nelle recinzioni o in qualche parola', () => {
  const corpo = '{"gruppo":"Glutei","serie":"3-4","repMin":8,"repMax":12,"incrementoKg":5,"nota":"spingi coi talloni"}';
  const atteso = { gruppo: 'Glutei', serie: '3-4', repMin: 8, repMax: 12, incrementoKg: 5, nota: 'spingi coi talloni' };
  const recinzione = '`'.repeat(3);
  for (const testo of [corpo, '  ' + corpo + '\n', recinzione + 'json\n' + corpo + '\n' + recinzione, recinzione + corpo + recinzione, 'Ecco la scheda:\n' + corpo + '\nSpero sia utile.']) {
    assert.deepStrictEqual(L.leggiRispostaAiEsercizio(testo), atteso, testo);
  }
});

test('compila: AI - numeri scritti male: virgola decimale, testo, mancanti', () => {
  const p = ai({ gruppo: 'gambe', serie: ' 4 ', repMin: '10', repMax: '12,9', incrementoKg: '2,5', nota: 'x'.repeat(100) });
  assert.strictEqual(p.gruppo, 'Gambe', 'maiuscole del gruppo ignorate');
  assert.strictEqual(p.serie, '4');
  assert.deepStrictEqual([p.repMin, p.repMax], [10, 12], 'le ripetizioni sono intere');
  assert.strictEqual(p.incrementoKg, 2.5);
  assert.strictEqual(p.nota.length, 80, 'la nota si ferma a 80 caratteri');
  assert.deepStrictEqual(ai({}), { gruppo: 'Altro', serie: '3', repMin: 8, repMax: 12, incrementoKg: null, nota: null });
  assert.deepStrictEqual([ai({ repMin: 7 }).repMin, ai({ repMin: 7 }).repMax], [7, 7]);
  assert.deepStrictEqual([ai({ repMax: 9 }).repMin, ai({ repMax: 9 }).repMax], [9, 9]);
  assert.strictEqual(ai({ incrementoKg: 20 }).incrementoKg, 20);
  assert.strictEqual(ai({ incrementoKg: 20.5 }).incrementoKg, null);
  assert.strictEqual(ai({ serie: '3-4' }).serie, '3-4');
  assert.strictEqual(ai({ serie: '123' }).serie, '3');
});

test('compila: AI - se non è un oggetto JSON lancia un errore (decide chi chiama)', () => {
  assert.throws(() => L.leggiRispostaAiEsercizio('non è json'));
  assert.throws(() => L.leggiRispostaAiEsercizio('[1,2]'));
  assert.throws(() => L.leggiRispostaAiEsercizio(''));
});

// ---- casi limite delle regole ----

test('compila: nomi con accenti, maiuscole e punteggiatura vengono letti allo stesso modo', () => {
  assert.strictEqual(r('ÀLZATE LATERALI').gruppo, 'Spalle');
  assert.strictEqual(r('Alzate_laterali').gruppo, 'Spalle');
  assert.strictEqual(r('Alzate.laterali (manubri)').gruppo, 'Spalle');
  assert.strictEqual(r('Pull-up').gruppo, 'Schiena');
  assert.strictEqual(r('Rematore, a un braccio').nota, 'per lato');
  assert.strictEqual(r('Estensione sopra la testa').gruppo, 'Tricipiti');
  assert.strictEqual(r('Accosciata').gruppo, 'Gambe');
});

test('compila: input strani non fanno saltare nulla', () => {
  assert.strictEqual(L.compilaEsercizio(null, null), null);
  assert.strictEqual(L.compilaEsercizio(undefined), null);
  assert.strictEqual(L.compilaEsercizio(42, null), null);
  assert.strictEqual(L.compilaEsercizio('   ', null), null);
  assert.strictEqual(L.compilaEsercizio('Curl con manubri', undefined).incrementoKg, null);
  assert.strictEqual(L.compilaEsercizio('Curl con manubri', NaN).incrementoKg, null);
  assert.strictEqual(L.compilaEsercizio('Curl con manubri', '2').incrementoKg, null, 'il salto deve essere un numero');
});

test('compila: la proposta è un oggetto nuovo ogni volta', () => {
  const a = r('Plank');
  a.repMin = 999;
  assert.strictEqual(r('Plank').repMin, 30);
});

test('compila: salto abituale - parità, zeri e valori strani', () => {
  assert.strictEqual(L.saltoAbituale([1, 2, 2, 1]), 1, 'a parità vince il primo incontrato');
  assert.strictEqual(L.saltoAbituale([0, -1, null, 2.5]), 2.5);
  assert.strictEqual(L.saltoAbituale([0, -1]), null);
  assert.strictEqual(L.saltoAbituale([]), null);
  assert.strictEqual(L.saltoAbituale(undefined), null);
});

// --- WeightRuleTest -----------------------------------------------------------------------------------------------

const OGGI = '2026-07-30';

/** La data di `giorniFa` giorni prima di oggi, aaaa-mm-gg (aritmetica in UTC: niente fusi). */
function isoFa(giorniFa, da) {
  const base = da || OGGI;
  const d = new Date(Date.UTC(Number(base.slice(0, 4)), Number(base.slice(5, 7)) - 1, Number(base.slice(8, 10)) - giorniFa));
  return d.toISOString().slice(0, 10);
}
const peso = (giorniFa, kg) => ({ date: isoFa(giorniFa), kg });

test('peso: fermo da più di 3 settimane - consiglio di aumentare', () => {
  const advice = L.regolaPeso([peso(28, 70.0), peso(14, 70.2), peso(0, 70.1)], OGGI);
  assert.notStrictEqual(advice, null);
  assert.ok(advice.testo.includes('aumentare le porzioni'));
});

test('peso: in salita - nessun avviso', () => {
  assert.strictEqual(L.regolaPeso([peso(28, 70.0), peso(0, 71.5)], OGGI), null);
});

test('peso: dati per meno di 3 settimane - nessun avviso', () => {
  assert.strictEqual(L.regolaPeso([peso(10, 70.0), peso(0, 70.0)], OGGI), null);
});

test('peso: pesate troppo vecchie escluse dal calcolo', () => {
  assert.strictEqual(L.regolaPeso([peso(60, 65.0), peso(3, 70.0)], OGGI), null);
});

test('peso: nessuna pesata - nessun avviso', () => {
  assert.strictEqual(L.regolaPeso([], OGGI), null);
});

// --- Casi limite del peso (attesi presi dal Kotlin vero) ---------------------------------------------------------------

test('peso: il testo, parola per parola, con giorni e differenza', () => {
  const a = L.regolaPeso([peso(28, 70.0), peso(14, 70.2), peso(0, 70.1)], OGGI);
  assert.strictEqual(a.testo, 'Peso fermo da 28 giorni (+0,1 kg): il piano dice di aumentare le porzioni. Parti da +50 g di pasta o +1 fetta di pane al giorno.');
  assert.strictEqual(a.giorni, 28);
  vicino(a.deltaKg, 0.1, 1e-9);
  assert.strictEqual(String(a), a.testo, 'scritto come testo dà il testo (il Kotlin restituisce una stringa)');
  assert.strictEqual(a + '', a.testo);
  assert.deepStrictEqual(Object.keys(a), ['testo', 'giorni', 'deltaKg'], 'toString non entra nei confronti');
});

test('peso: se cala il segno è meno, e il decimale va col "mezzo in su" di Java (0,25 → 0,3)', () => {
  const calo = L.regolaPeso([peso(28, 71.0), peso(0, 70.0)], OGGI);
  assert.ok(calo.testo.includes('(-1,0 kg)'), calo.testo);
  assert.ok(L.regolaPeso([peso(28, 70.0), peso(0, 70.25)], OGGI).testo.includes('(+0,3 kg)'));
  assert.ok(L.regolaPeso([peso(28, 70.25), peso(0, 70.0)], OGGI).testo.includes('(-0,3 kg)'));
  assert.ok(L.regolaPeso([peso(28, 70.0), peso(0, 70.0)], OGGI).testo.includes('(+0,0 kg)'));
  assert.ok(L.regolaPeso([peso(30, 70.0), peso(0, 69.96)], OGGI).testo.includes('(-0,0 kg)'), 'un calo minimo resta un calo');
  assert.ok(L.regolaPeso([peso(30, 70.0), peso(0, 70.35)], OGGI).testo.includes('(+0,3 kg)'), '70,35 - 70 non è esattamente 0,35');
  // Pesi fuori scala, solo per provare l'arrotondamento: 0,45 - 0,1 vale proprio 0,35 e Java scrive +0,4 (toFixed direbbe 0,3).
  assert.ok(L.regolaPeso([peso(30, 0.1), peso(0, 0.45)], OGGI).testo.includes('(+0,4 kg)'));
  assert.ok(L.regolaPeso([peso(30, 0.5), peso(0, 0.35)], OGGI).testo.includes('(-0,2 kg)'));
});

test('peso: soglie esatte - 21 giorni sì, 20 no; 0,4 kg no, 0,39 sì', () => {
  assert.notStrictEqual(L.regolaPeso([peso(21, 70.0), peso(0, 70.1)], OGGI), null);
  assert.strictEqual(L.regolaPeso([peso(20, 70.0), peso(0, 70.1)], OGGI), null);
  assert.strictEqual(L.regolaPeso([peso(28, 70.0), peso(0, 70.4)], OGGI), null, '+0,4 è già una crescita');
  assert.notStrictEqual(L.regolaPeso([peso(28, 70.0), peso(0, 70.39)], OGGI), null);
});

test('peso: la finestra è di 35 giorni - la pesata di 35 giorni fa conta, quella di 36 no', () => {
  assert.strictEqual(L.regolaPeso([peso(35, 70.0), peso(0, 70.1)], OGGI).giorni, 35);
  assert.strictEqual(L.regolaPeso([peso(36, 70.0), peso(0, 70.1)], OGGI), null);
});

test('peso: date illeggibili o future vengono saltate, un\'ordine qualunque va bene', () => {
  const pesi = [peso(0, 70.1), { date: 'ieri', kg: 99 }, { date: '2026-02-30', kg: 99 }, { date: isoFa(-3), kg: 99 }, peso(28, 70.0), { date: '', kg: 99 }, null, { kg: 70 }, { date: isoFa(5), kg: 'x' }];
  const a = L.regolaPeso(pesi, OGGI);
  assert.strictEqual(a.giorni, 28);
  assert.ok(a.testo.includes('(+0,1 kg)'));
});

test('peso: "oggi" può essere una Date o mancare; senza pesate vicine a oggi non scatta nulla', () => {
  assert.notStrictEqual(L.regolaPeso([peso(28, 70.0), peso(0, 70.1)], new Date(2026, 6, 30, 23, 59)), null);
  assert.strictEqual(L.regolaPeso([peso(28, 70.0), peso(0, 70.1)]), null, 'senza "oggi" si usa il giorno di adesso: quelle pesate sono vecchie');
  assert.strictEqual(L.regolaPeso(undefined, OGGI), null);
  assert.strictEqual(L.regolaPeso([{ date: OGGI, kg: 70 }], OGGI), null);
});

test('peso: due pesate nello stesso giorno restano nell\'ordine in cui sono state date', () => {
  const a = L.regolaPeso([peso(28, 70.0), peso(0, 70.3), peso(0, 70.1)], OGGI);
  assert.ok(a.testo.includes('(+0,1 kg)'), 'conta l\'ultima della lista, come nel Kotlin');
});

// --- NextOccurrenceTest -------------------------------------------------------------------------------------------

// 2026-07-29 è un mercoledì.
const mercoledi10 = () => new Date(2026, 6, 29, 10, 0);
const TUTTI_I_GIORNI = 0b1111111;
const LUNEDI = 1 << 0;
const MERCOLEDI = 1 << 2;
const DOMENICA = 1 << 6;
const dueCifre = (n) => String(n).padStart(2, '0');
/** "2026-07-29 10:30" dalle ore locali; null resta null. */
function orologio(d) {
  if (d === null) return null;
  return d.getFullYear() + '-' + dueCifre(d.getMonth() + 1) + '-' + dueCifre(d.getDate()) + ' ' + dueCifre(d.getHours()) + ':' + dueCifre(d.getMinutes());
}

test('promemoria: stesso giorno se l\'orario deve ancora arrivare', () => {
  assert.strictEqual(orologio(L.prossimaOccorrenza(mercoledi10(), 10, 30, TUTTI_I_GIORNI)), '2026-07-29 10:30');
});

test('promemoria: giorno dopo se l\'orario è già passato', () => {
  assert.strictEqual(orologio(L.prossimaOccorrenza(mercoledi10(), 9, 0, TUTTI_I_GIORNI)), '2026-07-30 09:00');
});

test('promemoria: orario identico ad adesso - va a domani', () => {
  assert.strictEqual(orologio(L.prossimaOccorrenza(mercoledi10(), 10, 0, TUTTI_I_GIORNI)), '2026-07-30 10:00');
});

test('promemoria: solo domenica - salta alla prossima domenica', () => {
  const next = L.prossimaOccorrenza(mercoledi10(), 17, 0, DOMENICA);
  assert.strictEqual(orologio(next), '2026-08-02 17:00');
  assert.strictEqual(next.getDay(), 0, 'è una domenica');
});

test('promemoria: nessun giorno attivo - null', () => {
  assert.strictEqual(L.prossimaOccorrenza(mercoledi10(), 10, 30, 0), null);
});

// --- Casi limite dei promemoria -----------------------------------------------------------------------------------

test('promemoria: il bit 0 è lunedì e il bit 6 domenica', () => {
  assert.strictEqual(orologio(L.prossimaOccorrenza(mercoledi10(), 8, 0, LUNEDI)), '2026-08-03 08:00');
  assert.strictEqual(orologio(L.prossimaOccorrenza(mercoledi10(), 10, 30, MERCOLEDI)), '2026-07-29 10:30');
  assert.strictEqual(orologio(L.prossimaOccorrenza(mercoledi10(), 9, 0, MERCOLEDI)), '2026-08-05 09:00', 'stesso giorno ma ormai passato: mercoledì prossimo');
  assert.strictEqual(orologio(L.prossimaOccorrenza(mercoledi10(), 10, 0, MERCOLEDI)), '2026-08-05 10:00');
  assert.strictEqual(orologio(L.prossimaOccorrenza(mercoledi10(), 7, 0, LUNEDI | DOMENICA)), '2026-08-02 07:00');
});

test('promemoria: contano solo i sette bit bassi', () => {
  assert.strictEqual(L.prossimaOccorrenza(mercoledi10(), 10, 30, 128), null);
  assert.strictEqual(orologio(L.prossimaOccorrenza(mercoledi10(), 8, 0, 128 | LUNEDI)), '2026-08-03 08:00');
});

test('promemoria: secondi e millisecondi di adesso contano', () => {
  assert.strictEqual(orologio(L.prossimaOccorrenza(new Date(2026, 6, 29, 10, 0, 30), 10, 0, TUTTI_I_GIORNI)), '2026-07-30 10:00');
  assert.strictEqual(orologio(L.prossimaOccorrenza(new Date(2026, 6, 29, 9, 59, 59, 999), 10, 0, TUTTI_I_GIORNI)), '2026-07-29 10:00');
  assert.strictEqual(orologio(L.prossimaOccorrenza(new Date(2026, 6, 29, 23, 59), 0, 0, TUTTI_I_GIORNI)), '2026-07-30 00:00');
});

test('promemoria: a fine mese e a fine anno', () => {
  assert.strictEqual(orologio(L.prossimaOccorrenza(new Date(2026, 11, 31, 23, 0), 7, 30, TUTTI_I_GIORNI)), '2027-01-01 07:30');
  assert.strictEqual(orologio(L.prossimaOccorrenza(new Date(2026, 6, 31, 12, 0), 9, 0, TUTTI_I_GIORNI)), '2026-08-01 09:00');
  assert.strictEqual(orologio(L.prossimaOccorrenza(new Date(2028, 1, 28, 12, 0), 9, 0, TUTTI_I_GIORNI)), '2028-02-29 09:00', 'anno bisestile');
});

test('promemoria: orario che non esiste o input strani - null, e adesso non viene toccato', () => {
  const adesso = mercoledi10();
  const prima = adesso.getTime();
  assert.strictEqual(L.prossimaOccorrenza(adesso, 24, 0, TUTTI_I_GIORNI), null);
  assert.strictEqual(L.prossimaOccorrenza(adesso, -1, 0, TUTTI_I_GIORNI), null);
  assert.strictEqual(L.prossimaOccorrenza(adesso, 10, 60, TUTTI_I_GIORNI), null);
  assert.strictEqual(L.prossimaOccorrenza(adesso, 10.5, 0, TUTTI_I_GIORNI), null);
  assert.strictEqual(L.prossimaOccorrenza(adesso, '10', 0, TUTTI_I_GIORNI), null);
  assert.ok(L.prossimaOccorrenza(adesso, 11, 0, TUTTI_I_GIORNI) instanceof Date);
  assert.strictEqual(adesso.getTime(), prima);
});

// --- TargetCalculatorTest -----------------------------------------------------------------------------------------

const ANNO = 2026;

/** Un profilo come nel file: i valori non indicati sono quelli del test Kotlin (70 kg, 178 cm, nato nel 2010...). */
function profilo(o) {
  return Object.assign({ annoNascita: 2010, sesso: 'MASCHIO', altezzaCm: 178, pesoKg: 70, attivita: 'MODERATA', obiettivo: 'MASSA' }, o || {});
}

test('obiettivi: bmr maschio - formula di Mifflin-St Jeor', () => {
  // 10*70 + 6.25*178 - 5*16 + 5 = 700 + 1112.5 - 80 + 5 = 1737.5
  vicino(L.metabolismoBasale(profilo(), ANNO), 1737.5, 0.01);
});

test('obiettivi: bmr femmina - 166 kcal sotto il maschio equivalente', () => {
  const m = L.metabolismoBasale(profilo({ sesso: 'MASCHIO' }), ANNO);
  const f = L.metabolismoBasale(profilo({ sesso: 'FEMMINA' }), ANNO);
  vicino(m - f, 166.0, 0.01);
});

// Gli estremi sono arrotondati a 50, quindi il centro può scostarsi fino a 25 kcal.
test('obiettivi: massa - surplus sopra il consumo stimato', () => {
  const t = L.calcolaObiettivi(profilo(), ANNO);
  const centro = Math.trunc((t.kcalMin + t.kcalMax) / 2);
  assert.ok(centro > t.tdee, 'centro ' + centro + ' non è in surplus su TDEE ' + t.tdee);
  vicino(centro - t.tdee, 400.0, 25.0);
});

test('obiettivi: definizione - deficit sotto il consumo stimato', () => {
  const t = L.calcolaObiettivi(profilo({ obiettivo: 'DEFINIZIONE' }), ANNO);
  const centro = Math.trunc((t.kcalMin + t.kcalMax) / 2);
  assert.ok(centro < t.tdee, 'centro ' + centro + ' non è in deficit su TDEE ' + t.tdee);
  vicino(centro - t.tdee, -400.0, 25.0);
});

test('obiettivi: proteine - proporzionali al peso e all\'obiettivo', () => {
  assert.strictEqual(L.calcolaObiettivi(profilo({ pesoKg: 70 }), ANNO).protMin, 126); // 70 x 1.8
  assert.strictEqual(L.calcolaObiettivi(profilo({ pesoKg: 70, obiettivo: 'DEFINIZIONE' }), ANNO).protMin, 154); // 70 x 2.2
});

test('obiettivi: attività più alta - più calorie', () => {
  const sed = L.calcolaObiettivi(profilo({ attivita: 'SEDENTARIA' }), ANNO).kcalMin;
  const alta = L.calcolaObiettivi(profilo({ attivita: 'ALTA' }), ANNO).kcalMin;
  assert.ok(sed < alta, 'sedentaria ' + sed + ' dovrebbe essere < alta ' + alta);
});

test('obiettivi: acqua - proporzionale al peso, arrotondata e limitata', () => {
  assert.strictEqual(L.calcolaObiettivi(profilo({ pesoKg: 70 }), ANNO).acquaMl, 2750);
  assert.strictEqual(L.calcolaObiettivi(profilo({ pesoKg: 200 }), ANNO).acquaMl, 5000); // tetto
});

test('obiettivi: range calorico - ampio 400 kcal e arrotondato a 50', () => {
  const t = L.calcolaObiettivi(profilo(), ANNO);
  assert.strictEqual(t.kcalMax - t.kcalMin, 400);
  assert.strictEqual(t.kcalMin % 50, 0);
  assert.strictEqual(t.kcalMax % 50, 0);
});

test('obiettivi: età - zero se l\'anno di nascita non è stato inserito', () => {
  // UserProfile().eta(anno) = 0 senza anno di nascita; qui si vede dal metabolismo basale (senza età: 1737.5 + 5 x 16)
  vicino(L.metabolismoBasale(profilo({ annoNascita: 0 }), ANNO), 1737.5 + 5 * 16, 0.01);
  vicino(L.metabolismoBasale(profilo({ annoNascita: 2010 }), ANNO), 1737.5, 0.01);
  assert.strictEqual(L.metabolismoBasale({ pesoKg: 70, altezzaCm: 178, sesso: 'MASCHIO' }, ANNO), 1817.5);
});

// ---- Casi limite: valori presi dal Kotlin vero (le metà strada vanno al pari: 1571,5 → 1572; 1662,5 → 1662) ----

test('obiettivi: una tabella di profili, riga per riga', () => {
  const casi = [
    [{}, { bmr: 1738, tdee: 2693, kcalMin: 2900, kcalMax: 3300, protMin: 126, protMax: 146, acquaMl: 2750 }],
    [{ sesso: 'FEMMINA' }, { bmr: 1572, tdee: 2436, kcalMin: 2650, kcalMax: 3050, protMin: 126, protMax: 146, acquaMl: 2750 }],
    [{ obiettivo: 'DEFINIZIONE' }, { bmr: 1738, tdee: 2693, kcalMin: 2100, kcalMax: 2500, protMin: 154, protMax: 174, acquaMl: 2750 }],
    [{ obiettivo: 'RICOMPOSIZIONE' }, { bmr: 1738, tdee: 2693, kcalMin: 2500, kcalMax: 2900, protMin: 140, protMax: 160, acquaMl: 2750 }],
    [{ attivita: 'SEDENTARIA' }, { bmr: 1738, tdee: 2085, kcalMin: 2300, kcalMax: 2700, protMin: 126, protMax: 146, acquaMl: 2750 }],
    [{ attivita: 'ALTA' }, { bmr: 1738, tdee: 2997, kcalMin: 3200, kcalMax: 3600, protMin: 126, protMax: 146, acquaMl: 2750 }],
    [{ attivita: 'MOLTO_ALTA', pesoKg: 85, altezzaCm: 185, annoNascita: 1990 }, { bmr: 1831, tdee: 3479, kcalMin: 3700, kcalMax: 4100, protMin: 153, protMax: 173, acquaMl: 3500 }],
    [{ pesoKg: 200 }, { bmr: 3038, tdee: 4708, kcalMin: 4900, kcalMax: 5300, protMin: 360, protMax: 380, acquaMl: 5000 }],
    [{ pesoKg: 30 }, { bmr: 1338, tdee: 2073, kcalMin: 2250, kcalMax: 2650, protMin: 54, protMax: 74, acquaMl: 1500 }],
    [{ pesoKg: 55.5, sesso: 'FEMMINA', altezzaCm: 165, annoNascita: 2005 }, { bmr: 1320, tdee: 2046, kcalMin: 2250, kcalMax: 2650, protMin: 100, protMax: 120, acquaMl: 2250 }],
    [{ annoNascita: 0 }, { bmr: 1818, tdee: 2817, kcalMin: 3000, kcalMax: 3400, protMin: 126, protMax: 146, acquaMl: 2750 }],
    [{ pesoKg: 62.5, obiettivo: 'DEFINIZIONE', attivita: 'LEGGERA' }, { bmr: 1662, tdee: 2286, kcalMin: 1700, kcalMax: 2100, protMin: 138, protMax: 158, acquaMl: 2500 }],
    [{ pesoKg: 90.3, annoNascita: 1975 }, { bmr: 1766, tdee: 2737, kcalMin: 2950, kcalMax: 3350, protMin: 163, protMax: 183, acquaMl: 3500 }],
  ];
  for (const [extra, atteso] of casi) assert.deepStrictEqual(L.calcolaObiettivi(profilo(extra), ANNO), atteso, JSON.stringify(extra));
});

test('obiettivi: profilo vuoto o con enum sconosciuti - i valori di partenza del profilo, senza errori', () => {
  const t = L.calcolaObiettivi(profilo({ sesso: '?', attivita: 'boh', obiettivo: undefined }), ANNO);
  assert.deepStrictEqual(t, L.calcolaObiettivi(profilo(), ANNO), 'maschio, moderata, massa');
  assert.deepStrictEqual(L.calcolaObiettivi(profilo({ sesso: 'femmina', attivita: 'alta' }), ANNO), L.calcolaObiettivi(profilo({ sesso: 'FEMMINA', attivita: 'ALTA' }), ANNO));
  const vuoto = L.calcolaObiettivi({}, ANNO);
  assert.strictEqual(vuoto.acquaMl, 1500);
  assert.strictEqual(vuoto.protMin, 0);
  assert.ok(Number.isInteger(vuoto.kcalMin) && Number.isInteger(vuoto.kcalMax));
  assert.doesNotThrow(() => L.calcolaObiettivi(null, ANNO));
  assert.strictEqual(L.calcolaObiettivi(profilo(), undefined).kcalMax - L.calcolaObiettivi(profilo(), undefined).kcalMin, 400, 'senza anno si usa quello di adesso');
});

// --- Formattazione all'italiana (valori presi dal Kotlin vero: Common.kt e AdvisorEngine.grp/lt) --------------------

function tabella(funzione, righe) {
  for (const [ingresso, atteso] of righe) assert.strictEqual(funzione(ingresso), atteso, 'ingresso ' + ingresso);
}

test('formato: fmtInt - migliaia col punto', () => {
  tabella(L.fmtInt, [[0, '0'], [7, '7'], [999, '999'], [1000, '1.000'], [3450, '3.450'], [12345, '12.345'],
    [1234567, '1.234.567'], [2147483647, '2.147.483.647'], [-1234, '-1.234'], [-12345, '-12.345']]);
});

test('formato: fmtInt - un numero con la virgola si arrotonda, il meno non resta orfano', () => {
  tabella(L.fmtInt, [[3449.6, '3.450'], [0.4, '0'], [-0.4, '0'], [1e6 + 0.4, '1.000.000']]);
  // il Kotlin scrive "-.123" e "-.123.456" per i negativi con 3 o 6 cifre: qui il meno resta fuori dai gruppi
  tabella(L.fmtInt, [[-1, '-1'], [-99, '-99'], [-123, '-123'], [-999, '-999'], [-123456, '-123.456']]);
});

test('formato: fmtKcal - interi, a metà strada verso l\'alto (roundToInt)', () => {
  tabella(L.fmtKcal, [[3449.5, '3.450'], [3449.4, '3.449'], [2.5, '3'], [0.5, '1'], [-0.5, '0'], [-2.5, '-2'],
    [999.5, '1.000'], [3300, '3.300'], [0.49999999999999994, '0'], [1000000.5, '1.000.001']]);
});

test('formato: fmt1 - una cifra, virgola italiana, niente ",0", metà strada al pari', () => {
  tabella(L.fmt1, [[3, '3'], [3.04, '3'], [3.05, '3'], [3.06, '3,1'], [3.25, '3,2'], [3.35, '3,4'], [3.45, '3,4'],
    [18.85, '18,8'], [0.04, '0'], [-0.04, '0'], [-0.25, '-0,2'], [-3.5, '-3,5'], [1234.56, '1234,6'],
    [0.1 + 0.2, '0,3'], [12.5, '12,5'], [99.95, '100'], [0.05, '0'], [0.15, '0,2'], [2.675, '2,7']]);
});

test('formato: fmtLitri - la stessa regola, 3250 ml sono "3,2" e 3750 "3,8"', () => {
  tabella(L.fmtLitri, [[0, '0'], [125, '0,1'], [250, '0,2'], [500, '0,5'], [1000, '1'], [1500, '1,5'], [1750, '1,8'],
    [2750, '2,8'], [3000, '3'], [3250, '3,2'], [3300, '3,3'], [3750, '3,8'], [4999, '5']]);
});

test('formato: numeri mancanti o strani non producono NaN né "undefined"', () => {
  for (const f of [L.fmtInt, L.fmtKcal, L.fmt1, L.fmtLitri]) {
    for (const x of [undefined, null, NaN, Infinity, 'abc', {}]) assert.strictEqual(f(x), '0', f.name + '(' + String(x) + ')');
  }
  assert.strictEqual(L.fmt1('12.5'), '12,5', 'una stringa numerica vale come numero');
  assert.strictEqual(L.fmtSerie(undefined), 'corpo×0');
});

test('formato: fmtOra - ore e minuti locali con lo zero davanti', () => {
  assert.strictEqual(L.fmtOra(new Date(2026, 6, 30, 17, 42).getTime()), '17:42');
  assert.strictEqual(L.fmtOra(new Date(2026, 6, 30, 7, 5).getTime()), '07:05');
  assert.strictEqual(L.fmtOra(new Date(2026, 0, 1, 0, 0).getTime()), '00:00');
  assert.strictEqual(L.fmtOra(new Date(2026, 11, 31, 23, 59, 59, 999).getTime()), '23:59');
});

test('formato: fmtOra - le voci vecchie senza ora (0) o un istante mancante danno una stringa vuota', () => {
  for (const x of [0, -5, undefined, null, NaN, Infinity, '1700000000000']) assert.strictEqual(L.fmtOra(x), '', String(x));
});

test('formato: data - Oggi, Ieri, poi il giorno per esteso o abbreviato', () => {
  const oggi = '2026-11-02';
  const attese = [
    ['2026-11-02', 'Oggi', 'Oggi'], ['2026-11-01', 'Ieri', 'Ieri'],
    ['2026-11-03', 'Martedì 3 novembre', 'Mar 3 nov'], ['2026-10-31', 'Sabato 31 ottobre', 'Sab 31 ott'],
    ['2026-12-25', 'Venerdì 25 dicembre', 'Ven 25 dic'], ['2026-01-01', 'Giovedì 1 gennaio', 'Gio 1 gen'],
    ['2025-12-31', 'Mercoledì 31 dicembre', 'Mer 31 dic'], ['2024-02-29', 'Giovedì 29 febbraio', 'Gio 29 feb'],
    ['2026-11-09', 'Lunedì 9 novembre', 'Lun 9 nov'], ['2026-11-08', 'Domenica 8 novembre', 'Dom 8 nov'],
  ];
  for (const [iso, lunga, breve] of attese) {
    assert.strictEqual(L.etichettaData(iso, oggi), lunga, iso);
    assert.strictEqual(L.etichettaDataBreve(iso, oggi), breve, iso);
  }
});

test('formato: data - se non si legge torna com\'è; "oggi" può essere una Date', () => {
  for (const x of ['ciao', '2026-02-30', '2026-13-01', '2026-11-2', '20261102', '2026-11-02T10:00', ' 2026-11-02', '']) {
    assert.strictEqual(L.etichettaData(x, '2026-11-02'), x, x);
    assert.strictEqual(L.etichettaDataBreve(x, '2026-11-02'), x, x);
  }
  assert.strictEqual(L.etichettaData(undefined, '2026-11-02'), '');
  assert.strictEqual(L.etichettaData('2026-11-02', new Date(2026, 10, 2, 23, 59)), 'Oggi');
  assert.strictEqual(L.etichettaData('2026-11-01', new Date(2026, 10, 2, 0, 0)), 'Ieri');
  assert.strictEqual(L.etichettaData('2026-03-01', '2026-03-02'), 'Ieri', 'ieri attraverso la fine del mese');
  assert.strictEqual(L.etichettaData('2025-12-31', '2026-01-01'), 'Ieri', 'ieri attraverso la fine dell\'anno');
  assert.strictEqual(L.etichettaData('2024-02-29', '2024-03-01'), 'Ieri', 'ieri dopo il 29 febbraio');
});

test('formato: data - senza "oggi" si usa il giorno di adesso', () => {
  const n = new Date();
  const iso = (d) => d.getFullYear() + '-' + dueCifre(d.getMonth() + 1) + '-' + dueCifre(d.getDate());
  assert.strictEqual(L.etichettaData(iso(n)), 'Oggi');
  assert.strictEqual(L.etichettaDataBreve(iso(new Date(n.getFullYear(), n.getMonth(), n.getDate() - 1, 12))), 'Ieri');
});
