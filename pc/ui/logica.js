/*
 * Surplus per il computer: la logica pura portata dal Kotlin dell'app Android (AdvisorEngine, DietQuality,
 * SchedaNutrizionale, WorkoutQueries, CompilaEsercizio, WeightRule, NextOccurrence, FoodMath, TargetCalculator
 * e la formattazione di Common.kt). Solo funzioni pure: niente pagina, niente rete, niente stato.
 * La provano i test in pc/prove/js con Node. Nel browser sta in window.SurplusLogica.
 *
 * Regole di prodotto che qui non si toccano (le verificano i test):
 *  - i testi dei consigli e i loro "perché" sono quelli del Kotlin, parola per parola, numeri compresi;
 *  - mai parole che colpevolizzano il cibo: si suggerisce cosa aggiungere, mai cosa togliere;
 *  - numeri all'italiana: migliaia col punto (3.450), virgola decimale, una sola cifra dopo la virgola;
 *  - se il dato non c'è, la logica tace invece di indovinare (null, lista vuota).
 *
 * I dati hanno i nomi del file dell'app Android (date, moment, kcal, exercises, sets, kcal100...) e gli enum
 * sono le stesse stringhe maiuscole ("COLAZIONE", "FRUTTA"...). Una serie (peso x ripetizioni) si legge sia
 * come {weightKg, reps} (file Android) sia come {kg, rip}; quelle restituite sono sempre {kg, rip}.
 */
(function () {
  'use strict';
  const radice = typeof window !== 'undefined' ? window : globalThis;

  // --- Numeri e arrotondamenti ----------------------------------------------------------------
  //
  // Il Kotlin arrotonda in due modi e i testi dell'app dipendono da entrambi:
  //  - kotlin.math.round (cifre decimali, obiettivi): a metà strada va al numero pari, quindi
  //    18,85 diventa 18,8 e 3,25 L diventano "3,2";
  //  - roundToInt (kcal e grammi interi, consigli): a metà strada va verso l'alto.
  // Math.round di JavaScript fa solo il secondo: il primo è scritto a mano.

  /** Un numero finito, altrimenti 0: un campo mancante non deve far saltare i totali. */
  function numero(v) {
    const n = typeof v === 'number' ? v : (typeof v === 'string' && v.trim() !== '' ? Number(v) : NaN);
    return Number.isFinite(n) ? n : 0;
  }

  /** kotlin.math.round: a metà strada va al numero pari (2,5 → 2; 3,5 → 4). */
  function arrotondaPari(x) {
    if (!Number.isFinite(x)) return x;
    const sotto = Math.floor(x);
    const resto = x - sotto; // esatto: sotto e x sono vicini
    if (resto < 0.5) return sotto;
    if (resto > 0.5) return sotto + 1;
    return sotto % 2 === 0 ? sotto : sotto + 1;
  }

  /** roundToInt: a metà strada va verso l'alto (2,5 → 3; -2,5 → -2). Il "+ 0" toglie il -0. */
  function arrotondaIntero(x) {
    return Math.round(numero(x)) + 0;
  }

  /** Una cifra decimale col metodo di kotlin.math.round, senza -0. */
  function arrotondaDecimo(x) {
    return arrotondaPari(x * 10) / 10 + 0;
  }

  // --- Formattazione all'italiana --------------------------------------------------------------

  /**
   * Migliaia col punto (3450 → "3.450"), senza dipendere dal Locale. Il Kotlin sbaglia i negativi con
   * 3 o 6 cifre ("-.123"): qui il segno resta fuori dai gruppi.
   */
  function raggruppa(n) {
    const s = String(Math.trunc(n) + 0);
    const segno = s[0] === '-' ? '-' : '';
    return segno + (segno ? s.slice(1) : s).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  }

  /** Intero con le migliaia col punto. Un numero con la virgola viene arrotondato (3449,6 → "3.450"). */
  function fmtInt(n) {
    return raggruppa(arrotondaIntero(n));
  }

  /** Kcal intere con le migliaia col punto. */
  function fmtKcal(v) {
    return fmtInt(v);
  }

  /** Una cifra decimale, virgola italiana, niente ",0" inutile: 3 → "3"; 3,25 → "3,2"; 3,5 → "3,5". */
  function fmt1(v) {
    const r = arrotondaDecimo(numero(v));
    return r === Math.floor(r) ? String(r) : String(r).replace('.', ',');
  }

  /** Litri da millilitri, con la stessa regola di fmt1: 3250 → "3,2". */
  function fmtLitri(ml) {
    return fmt1(numero(ml) / 1000);
  }

  function chilogrammi(s) {
    return numero(s && (s.kg != null ? s.kg : s.weightKg));
  }

  function ripetizioni(s) {
    return numero(s && (s.rip != null ? s.rip : s.reps));
  }

  function normalizzaSerie(s) {
    return { kg: chilogrammi(s), rip: ripetizioni(s) };
  }

  /** Serie compatta: "10 kg×8", oppure "corpo×12" per gli esercizi a corpo libero. */
  function fmtSerie(s) {
    const kg = chilogrammi(s);
    return kg > 0 ? fmt1(kg) + ' kg×' + ripetizioni(s) : 'corpo×' + ripetizioni(s);
  }

  function due(n) {
    return String(n).padStart(2, '0');
  }

  /**
   * L'ora locale "HH:mm" da un istante in millisecondi. Vuota se l'istante non c'è o è 0: sul telefono
   * l'ora si mostra solo quando createdAt > 0 (le voci vecchie non l'hanno).
   */
  function fmtOra(epochMs) {
    if (typeof epochMs !== 'number' || !Number.isFinite(epochMs) || epochMs <= 0) return '';
    const d = new Date(epochMs);
    return due(d.getHours()) + ':' + due(d.getMinutes());
  }

  // --- Date -------------------------------------------------------------------------------------

  const GIORNI_LUNGHI = ['domenica', 'lunedì', 'martedì', 'mercoledì', 'giovedì', 'venerdì', 'sabato'];
  const GIORNI_BREVI = ['dom', 'lun', 'mar', 'mer', 'gio', 'ven', 'sab'];
  const MESI_LUNGHI = ['gennaio', 'febbraio', 'marzo', 'aprile', 'maggio', 'giugno', 'luglio', 'agosto', 'settembre', 'ottobre', 'novembre', 'dicembre'];
  const MESI_BREVI = ['gen', 'feb', 'mar', 'apr', 'mag', 'giu', 'lug', 'ago', 'set', 'ott', 'nov', 'dic'];

  function maiuscola(t) {
    return t ? t[0].toUpperCase() + t.slice(1) : t;
  }

  function eUnaData(x) {
    return Object.prototype.toString.call(x) === '[object Date]' && !isNaN(x.getTime());
  }

  /** Un istante qualunque come Date; senza un valore valido, adesso. */
  function comeData(x) {
    if (eUnaData(x)) return x;
    if (typeof x === 'number' && Number.isFinite(x)) return new Date(x);
    return new Date();
  }

  /**
   * Un giorno di calendario senza ora né fuso: { anno, mese (1-12), giorno, n (giorni dal 1970), dow (0 = domenica) }.
   * null se la data non esiste (30 febbraio...): LocalDate.parse del Kotlin la rifiuterebbe.
   */
  function datiGiorno(anno, mese, giorno) {
    const t = new Date(0);
    t.setUTCFullYear(anno, mese - 1, giorno); // così l'anno 0-99 non diventa 1900-1999
    if (t.getUTCFullYear() !== anno || t.getUTCMonth() !== mese - 1 || t.getUTCDate() !== giorno) return null;
    return { anno, mese, giorno, n: Math.round(t.getTime() / 86400000), dow: t.getUTCDay() };
  }

  /** "2026-11-02" (solo questa forma, come LocalDate.parse) → giorno di calendario, oppure null. */
  function leggiDataIso(iso) {
    const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(typeof iso === 'string' ? iso : '');
    return m ? datiGiorno(Number(m[1]), Number(m[2]), Number(m[3])) : null;
  }

  /** Il giorno di calendario di una stringa "aaaa-mm-gg" o di una Date (in ora locale); senza valore, oggi. */
  function giornoDa(x) {
    if (typeof x === 'string') return leggiDataIso(x) || giornoDa(undefined);
    const d = comeData(x);
    return datiGiorno(d.getFullYear(), d.getMonth() + 1, d.getDate());
  }

  function etichettaGiorno(iso, oggi, breve) {
    const g = leggiDataIso(iso);
    if (!g) return typeof iso === 'string' ? iso : ''; // come runCatching { ... }.getOrDefault(iso)
    const o = giornoDa(oggi);
    if (g.n === o.n) return 'Oggi';
    if (g.n === o.n - 1) return 'Ieri';
    const nomi = breve ? GIORNI_BREVI : GIORNI_LUNGHI;
    const mesi = breve ? MESI_BREVI : MESI_LUNGHI;
    return maiuscola(nomi[g.dow] + ' ' + g.giorno + ' ' + mesi[g.mese - 1]);
  }

  /**
   * "Oggi", "Ieri", poi "Lunedì 2 novembre". iso è "aaaa-mm-gg"; oggi (Date o "aaaa-mm-gg") serve
   * solo ai test: senza, è il giorno di adesso. Se la data non si legge torna iso così com'è.
   */
  function etichettaData(iso, oggi) {
    return etichettaGiorno(iso, oggi, false);
  }

  /** Come etichettaData ma corta: "Oggi", "Ieri", poi "Lun 2 nov". */
  function etichettaDataBreve(iso, oggi) {
    return etichettaGiorno(iso, oggi, true);
  }

  // --- Diario del giorno -------------------------------------------------------------------------

  /**
   * Totali di una lista di voci del diario (quelle di un giorno). nonNoti = voci senza kcal: i totali
   * sono un minimo garantito, non il valore reale. kcalPerMomento e numeroPerMomento hanno per chiavi i
   * momenti ("COLAZIONE", "PRANZO"...); un momento senza voci non compare.
   */
  function totaliGiorno(voci) {
    let kcal = 0;
    let prot = 0;
    let nonNoti = 0;
    const kcalPerMomento = {};
    const numeroPerMomento = {};
    for (const e of voci || []) {
      numeroPerMomento[e.moment] = (numeroPerMomento[e.moment] || 0) + 1;
      if (e.kcal == null) {
        nonNoti++;
      } else {
        kcal += e.kcal;
        kcalPerMomento[e.moment] = (kcalPerMomento[e.moment] || 0) + e.kcal;
      }
      if (e.prot != null) prot += e.prot;
    }
    return { kcal, prot, nonNoti, kcalPerMomento, numeroPerMomento };
  }

  /** Kcal o proteine di una quantità in grammi, dato il valore per 100 g; null se il valore non c'è. */
  function perGrammi(grammi, per100) {
    if (per100 == null) return null;
    return arrotondaDecimo(numero(grammi) * per100 / 100);
  }

  // --- Qualità del cibo ---------------------------------------------------------------------------

  /** Una porzione di frutta o verdura = 100 g: soglia semplice e verificabile. */
  const PORZIONE_G = 100;
  /** Obiettivo giornaliero di fibra per una dieta ipercalorica. */
  const FIBRA_TARGET = 30;
  /** Sotto queste kcal di voci con grado noto il campione è troppo piccolo per dire qualcosa. */
  const MIN_KCAL_GRADO = 500;

  /** La qualità con i suoi due valori derivati (quota di ultra-processati, copertura del dato). */
  function creaQualita(kcalTotali, kcalConGrado, kcalUltraProcessati, fibraG, porzioniFruttaVerdura) {
    return {
      kcalTotali,
      kcalConGrado,
      kcalUltraProcessati,
      fibraG,
      porzioniFruttaVerdura,
      // Calcolata SOLO sulle voci di grado noto, e solo se sono abbastanza: altrimenti l'app tace.
      quotaUltraProcessati: kcalConGrado >= MIN_KCAL_GRADO ? kcalUltraProcessati / kcalConGrado : null,
      // Quanta parte della giornata è coperta dal dato: sotto il 50% non si conclude nulla.
      copertura: kcalTotali > 0 ? kcalConGrado / kcalTotali : 0,
    };
  }

  /**
   * Qualità del cibo di una giornata: frutta/verdura, fibra e grado di lavorazione (NOVA). Nessun punteggio
   * "salute" e nessun cibo bollato come cattivo; dove il dato manca si tace.
   */
  function qualitaGiorno(voci) {
    let kcalTot = 0;
    let kcalConGrado = 0;
    let kcalUltra = 0;
    let fibra = 0;
    let grammiFruttaVerdura = 0;
    for (const e of voci || []) {
      const kcal = e.kcal != null ? e.kcal : 0;
      kcalTot += kcal;
      if (e.nova != null) {
        kcalConGrado += kcal;
        if (e.nova >= 4) kcalUltra += kcal;
      }
      fibra += e.fibra != null ? e.fibra : 0;
      if (e.gruppo === 'FRUTTA' || e.gruppo === 'VERDURA') grammiFruttaVerdura += numero(e.grams);
    }
    return creaQualita(
      kcalTot,
      kcalConGrado,
      kcalUltra,
      arrotondaDecimo(fibra),
      arrotondaDecimo(grammiFruttaVerdura / PORZIONE_G),
    );
  }

  /** Media giornaliera su più giorni (per il riquadro settimanale); i giorni senza kcal non contano. null se non ce ne sono. */
  function mediaQualita(perGiorno) {
    const giorni = (perGiorno || []).filter((q) => q && q.kcalTotali > 0);
    if (giorni.length === 0) return null;
    const n = giorni.length;
    const somma = (campo) => giorni.reduce((s, q) => s + numero(q[campo]), 0);
    return creaQualita(
      somma('kcalTotali') / n,
      somma('kcalConGrado') / n,
      somma('kcalUltraProcessati') / n,
      arrotondaDecimo(somma('fibraG') / n),
      arrotondaDecimo(somma('porzioniFruttaVerdura') / n),
    );
  }

  // --- Consigli (AdvisorEngine) ---------------------------------------------------------------------
  //
  // Deterministici, basati sul piano: confrontano il mangiato col ritmo atteso della giornata e propongono
  // cibi CONCRETI con grammature, prese dalle alternative del piano stesso. Nessuna AI, nessuna rete.
  // Ogni consiglio porta con sé il proprio "perché", coi numeri usati nel calcolo.

  // Fasce orarie dei momenti (minuti dalla mezzanotte) e quota attesa di kcal (somma = 1).
  const FINESTRE = [
    { momento: 'COLAZIONE', inizio: 390, fine: 570, quota: 0.20 }, // 6:30-9:30
    { momento: 'META_MATTINA', inizio: 570, fine: 705, quota: 0.08 }, // 9:30-11:45
    { momento: 'PRANZO', inizio: 705, fine: 870, quota: 0.28 }, // 11:45-14:30
    { momento: 'POST_ALLENAMENTO', inizio: 870, fine: 1110, quota: 0.12 }, // 14:30-18:30
    { momento: 'CENA', inizio: 1110, fine: 1275, quota: 0.25 }, // 18:30-21:15
    { momento: 'PRE_NANNA', inizio: 1275, fine: 1439, quota: 0.07 }, // 21:15-24:00
  ];

  const QUOTE_DESC = 'colazione 20%, metà mattina 8%, pranzo 28%, dopo allenamento 12%, cena 25%, prima di dormire 7%';

  // Alternative concrete del piano, con kcal e proteine stimate (proteico = ricca di proteine).
  const CATALOGO = {
    COLAZIONE: [
      { testo: 'latte 300 ml + 5 fette biscottate con miele + 3 uova', kcal: 720, prot: 34, proteico: true },
      { testo: 'yogurt greco 170 g + cereali 60 g + frutta secca 30 g', kcal: 570, prot: 22, proteico: true },
    ],
    META_MATTINA: [
      { testo: 'un frutto (150 g) + frutta secca 30 g', kcal: 270, prot: 7, proteico: false },
      { testo: 'yogurt bianco 125 g + 4-5 taralli', kcal: 280, prot: 8, proteico: false },
      { testo: 'panino piccolo (pane 80 g) con asiago 40 g', kcal: 360, prot: 14, proteico: true },
    ],
    PRANZO: [
      { testo: 'pasta 140 g con cubetti di cotto 50 g + 8 taralli', kcal: 950, prot: 35, proteico: true },
      { testo: 'pasta 130 g con asiago 100 g + pane 50 g', kcal: 960, prot: 42, proteico: true },
      { testo: 'pasta 150 g + carne 180 g + pane 80 g', kcal: 1000, prot: 50, proteico: true },
    ],
    POST_ALLENAMENTO: [
      { testo: 'panino (pane 100 g) con cotto 50 g + frutta secca 30 g', kcal: 530, prot: 21, proteico: true },
      { testo: 'yogurt greco 170 g + un frutto + 4 taralli', kcal: 430, prot: 19, proteico: true },
    ],
    CENA: [
      { testo: 'carne 200 g + patate 300 g + pane 100 g', kcal: 780, prot: 48, proteico: true },
      { testo: 'frittata di 4 uova + asiago 50 g + pane 100 g', kcal: 800, prot: 45, proteico: true },
      { testo: 'pesce 200 g + patate 300 g + pane 100 g', kcal: 730, prot: 44, proteico: true },
    ],
    PRE_NANNA: [
      { testo: 'yogurt greco 200 g', kcal: 190, prot: 18, proteico: true },
      { testo: 'un bicchiere grande di latte (300 ml)', kcal: 190, prot: 10, proteico: true },
      { testo: 'frutta secca 30 g', kcal: 180, prot: 6, proteico: false },
      { testo: 'yogurt greco 200 g + frutta secca 30 g', kcal: 370, prot: 24, proteico: true },
    ],
  };

  const NOMI_SALTATI = {
    COLAZIONE: 'colazione',
    META_MATTINA: 'spuntino di metà mattina',
    PRANZO: 'pranzo',
    POST_ALLENAMENTO: 'spuntino post allenamento',
    CENA: 'cena',
    PRE_NANNA: 'spuntino serale',
  };

  const ETICHETTE_FINESTRE = {
    META_MATTINA: 'a metà mattina',
    POST_ALLENAMENTO: "dopo l'allenamento",
    PRE_NANNA: 'prima di dormire',
    COLAZIONE: 'a colazione',
    PRANZO: 'a pranzo',
    CENA: 'a cena',
  };

  /** Quanta parte della fascia è già trascorsa a quest'ora (0-1). */
  function avanzamento(w, minuti) {
    if (minuti >= w.fine) return 1;
    if (minuti <= w.inizio) return 0;
    return (minuti - w.inizio) / (w.fine - w.inizio);
  }

  function conta(numeroPerMomento, momento) {
    return (numeroPerMomento && numeroPerMomento[momento]) || 0;
  }

  /** Il prossimo momento ancora aperto e senza voci; se sono tutti pieni, il primo ancora aperto. */
  function prossimoMomento(minuti, numeroPerMomento) {
    const aperte = FINESTRE.filter((w) => w.fine > minuti).sort((a, b) => a.inizio - b.inizio);
    const libera = aperte.find((w) => conta(numeroPerMomento, w.momento) === 0);
    if (libera) return libera.momento;
    return aperte.length > 0 ? aperte[0].momento : null;
  }

  /** L'opzione del piano più vicina al buco in kcal (a parità, la prima); se serve proteina, tra quelle proteiche. */
  function scegli(momento, buco, serveProteine) {
    const opzioni = CATALOGO[momento];
    const proteiche = opzioni.filter((o) => o.proteico);
    const gruppo = serveProteine && proteiche.length > 0 ? proteiche : opzioni;
    let migliore = gruppo[0];
    for (const o of gruppo) {
      if (Math.abs(o.kcal - buco) < Math.abs(migliore.kcal - buco)) migliore = o;
    }
    // Un buco piccolo si chiude con mezza porzione.
    if (buco >= 1 && buco <= migliore.kcal * 0.6) {
      return Object.assign({}, migliore, { testo: migliore.testo + ' (va bene anche mezza porzione)' });
    }
    return migliore;
  }

  /** "9:05", "14:30". */
  function oreMinuti(min) {
    return Math.floor(min / 60) + ':' + due(min % 60);
  }

  function orariFinestra(w) {
    return oreMinuti(w.inizio) + '–' + oreMinuti(w.fine);
  }

  /** Arrotonda a 50 (a metà strada verso l'alto) per i buchi in kcal: "~450 kcal". */
  function arrotonda50(v) {
    return arrotondaIntero(v / 50) * 50;
  }

  /** Il giorno come lo vuole il motore dei consigli, con i campi mancanti a zero. */
  function statoGiorno(x) {
    return {
      kcal: numero(x.kcal),
      prot: numero(x.prot),
      nonNoti: numero(x.nonNoti),
      numeroPerMomento: x.numeroPerMomento || {},
      acquaMl: numero(x.acquaMl),
      allenato: !!x.allenato,
      qualita: x.qualita || null,
    };
  }

  /** Il bilancio della sera (dopo le 21): il consiglio più importante della giornata. */
  function consigliSerali(c, out) {
    const { t, g } = c;
    const pavimento = t.kcalMin + c.deltaAll;
    const mancano = pavimento - g.kcal;
    const buchiProt = t.protMin - g.prot;
    const kcalOggi = raggruppa(arrotondaIntero(g.kcal));
    const protOggi = arrotondaIntero(g.prot);
    if (mancano > 150) {
      const s = scegli('PRE_NANNA', mancano, buchiProt > 10);
      const parteProt = buchiProt > 10 ? ' e ~' + arrotondaIntero(buchiProt) + ' g di proteine' : '';
      let perche = 'Bilancio serale (dopo le 21:00). Minimo del giorno: ' + raggruppa(t.kcalMin) + ' kcal';
      if (g.allenato) perche += " +150 per l'allenamento registrato oggi → " + raggruppa(pavimento);
      perche += '. Finora: ' + kcalOggi + ' kcal e ' + protOggi + ' g di proteine (minimo ' + t.protMin + ' g). ';
      perche += 'Mancano ~' + arrotonda50(mancano) + ' kcal';
      if (buchiProt > 10) perche += ' e ~' + arrotondaIntero(buchiProt) + ' g di proteine';
      perche += ': propongo l\'opzione «prima di dormire» del piano più vicina al buco';
      if (buchiProt > 10) perche += ', scelta tra quelle ricche di proteine';
      perche += '.';
      out.push({
        priorita: 0,
        testo: 'Ti mancano ~' + arrotonda50(mancano) + ' kcal' + parteProt + ' per il minimo di ' + raggruppa(pavimento) + c.etichettaAll
          + ': prima di dormire ' + s.testo + ' (≈ ' + s.kcal + ' kcal, ' + s.prot + ' g proteine).',
        perche,
      });
    } else if (g.kcal <= t.kcalMax + 250) {
      if (buchiProt > 10) {
        out.push({
          priorita: 0,
          testo: 'Calorie ok (' + kcalOggi + '), ma proteine a ' + protOggi + ' g su ' + t.protMin
            + ': prima di dormire yogurt greco 200 g (+18 g di proteine).',
          perche: 'Calorie nel range utile (' + kcalOggi + ' su ' + raggruppa(t.kcalMin) + '–' + raggruppa(t.kcalMax)
            + '), ma proteine a ' + protOggi + ' g contro il minimo di ' + t.protMin
            + ': per la crescita muscolare sono la parte non negoziabile del piano. '
            + "Lo yogurt greco è l'opzione serale con più proteine per caloria.",
        });
      } else {
        out.push({
          priorita: 0,
          testo: 'Giornata a target: ' + kcalOggi + ' kcal e ' + protOggi + ' g di proteine. Continua così.',
          perche: 'Sei a ' + kcalOggi + ' kcal — dentro il minimo utile (' + raggruppa(pavimento)
            + (g.allenato ? ", alzato di 150 per l'allenamento" : '') + ', con tolleranza di 150) e sotto il massimo '
            + raggruppa(t.kcalMax) + ' (+250 di margine) — con ' + protOggi + ' g di proteine ≥ ' + t.protMin
            + '. Nessun intervento: come dice il piano, conta la costanza.',
        });
      }
    } else {
      out.push({
        priorita: 0,
        testo: 'Oggi ' + kcalOggi + ' kcal, sopra il range: in fase di massa non è un dramma, domani torna nel '
          + raggruppa(t.kcalMin) + '–' + raggruppa(t.kcalMax) + '.',
        perche: 'Registrate ' + kcalOggi + ' kcal, oltre il massimo ' + raggruppa(t.kcalMax)
          + ' più il margine di 250. In surplus un giorno alto non rovina nulla — conta la media della settimana — '
          + "ma meglio non farlo diventare l'abitudine.",
      });
    }
  }

  /** Di mattina: se ieri si è mangiato poco, si riparte con la colazione più completa del piano. */
  function consigliMattina(c, out) {
    const { t, ieri } = c;
    if (!(c.minuti < 11 * 60 && ieri && ieri.kcal > 0 && ieri.kcal < t.kcalMin - 200)) return;
    const s = CATALOGO.COLAZIONE[0];
    out.push({
      priorita: 1,
      testo: 'Ieri ~' + raggruppa(arrotondaIntero(ieri.kcal)) + ' kcal, sotto il minimo: oggi parti pieno. Colazione: '
        + s.testo + ' (≈ ' + s.kcal + ' kcal).',
      perche: 'Ieri hai chiuso a ' + raggruppa(arrotondaIntero(ieri.kcal)) + ' kcal contro un minimo di '
        + raggruppa(t.kcalMin) + " (l'avviso scatta quando manca più di 200). "
        + 'Il modo più semplice di recuperare è partire con la colazione più completa del piano, '
        + 'senza stravolgere il resto della giornata.',
    });
  }

  /** Il ritmo della giornata: se si è indietro, una proposta concreta ORA; se regge, una conferma (il silenzio non dice nulla). */
  function consigliRitmo(c, out) {
    const { t, g } = c;
    const kcalOggi = raggruppa(arrotondaIntero(g.kcal));
    const attese = raggruppa(arrotondaIntero(c.kcalAttese));
    const puntoMedio = raggruppa(arrotondaIntero(c.centro));
    if (!c.sera && c.buco > 350) {
      const meta = prossimoMomento(c.minuti, g.numeroPerMomento);
      if (meta !== null) {
        const protIndietro = g.prot < t.protMin * c.quotaAttesa - 20;
        const s = scegli(meta, c.buco, protIndietro);
        out.push({
          priorita: 1,
          testo: 'Sei ~' + arrotonda50(c.buco) + ' kcal sotto il ritmo della giornata' + c.etichettaAll + '. '
            + ETICHETTE_FINESTRE[meta] + ': ' + s.testo + ' (≈ ' + s.kcal + ' kcal, ' + s.prot + ' g proteine).',
          perche: 'Alle ' + oreMinuti(c.minuti) + ' la quota attesa è ~' + attese + ' kcal: fette dei momenti già trascorsi ('
            + QUOTE_DESC + ') applicate al punto medio del range (' + puntoMedio
            + (g.allenato ? ", alzato di 150 per l'allenamento registrato" : '') + '). Registrate ' + kcalOggi
            + ' kcal → ritardo ~' + arrotonda50(c.buco) + ', sopra la soglia di avviso (350). '
            + "Propongo il prossimo momento libero con l'opzione del piano più vicina al buco.",
        });
      }
    }
    if (!c.sera && g.kcal > 0 && c.buco <= 350) {
      out.push({
        priorita: 5,
        testo: 'In linea col ritmo: ' + kcalOggi + " kcal su ~" + attese + " attese a quest'ora.",
        perche: 'Alle ' + oreMinuti(c.minuti) + ' la quota attesa è ~' + attese + ' kcal: fette dei momenti già trascorsi ('
          + QUOTE_DESC + ') sul punto medio del range (' + puntoMedio
          + (g.allenato ? ", alzato di 150 per l'allenamento registrato" : '') + '). Hai registrato ' + kcalOggi
          + ' kcal: lo scarto è sotto la soglia di avviso (350), nessuna correzione necessaria.',
      });
    }
  }

  /** Un momento chiuso senza voci: il consiglio è per domani. Se ce n'è più d'uno, il più recente. */
  function consigliSaltato(c, out) {
    const { g } = c;
    let saltata = null;
    for (const w of FINESTRE) {
      if (w.fine <= c.minuti && conta(g.numeroPerMomento, w.momento) === 0 && (saltata === null || w.fine > saltata.fine)) saltata = w;
    }
    if (saltata === null || !(g.kcal > 0)) return;
    const s = scegli(saltata.momento, saltata.quota * c.centro, false);
    const nome = NOMI_SALTATI[saltata.momento];
    out.push({
      priorita: 2,
      testo: 'Oggi niente ' + nome + ': domani prova ' + s.testo + ' (≈ ' + s.kcal + ' kcal in più).',
      perche: 'La finestra di ' + nome + ' (' + orariFinestra(saltata) + ') si è chiusa senza voci registrate. '
        + 'Nel piano quel momento vale ~' + raggruppa(arrotondaIntero(saltata.quota * c.centro)) + ' kcal ('
        + arrotondaIntero(saltata.quota * 100) + '% della giornata): il consiglio per domani serve a non perdere quella quota.',
    });
  }

  /** Proteine indietro anche se le kcal reggono. */
  function consigliProteine(c, out) {
    const { t, g } = c;
    if (c.sera || c.buco > 350) return;
    const attese = t.protMin * c.quotaAttesa;
    if (!(c.quotaAttesa > 0.3 && g.prot < attese - 25)) return;
    out.push({
      priorita: 3,
      testo: 'Proteine indietro: ' + arrotondaIntero(g.prot) + ' g contro ~' + arrotondaIntero(attese)
        + " attesi a quest'ora. Al prossimo pasto scegli uova, yogurt greco, carne o asiago.",
      perche: "Proteine attese a quest'ora: ~" + arrotondaIntero(attese) + ' g (minimo giornaliero ' + t.protMin + ' g × '
        + arrotondaIntero(c.quotaAttesa * 100) + '% di giornata trascorsa). Registrati ' + arrotondaIntero(g.prot)
        + ' g: sotto di oltre 25. Le calorie invece sono in linea col ritmo, quindi basta orientare il prossimo pasto su fonti proteiche.',
    });
  }

  /** Acqua: metà del target entro metà pomeriggio, l'80% entro le 21. */
  function consigliAcqua(c, out) {
    const { t, g } = c;
    const bevuti = fmtLitri(g.acquaMl);
    const obiettivo = fmtLitri(t.acquaMl);
    if (c.minuti >= 15 * 60 && g.acquaMl < Math.trunc(t.acquaMl / 2)) {
      out.push({
        priorita: 4,
        testo: 'Acqua: sei a ' + bevuti + ' su ' + obiettivo + ' L. Tieni la borraccia a portata.',
        perche: 'Regola: metà del target entro metà pomeriggio. Target ' + obiettivo + ' L → soglia '
          + fmt1(t.acquaMl / 2000) + ' L alle 15:00; registrati ' + bevuti + ' L.',
      });
    } else if (c.sera && g.acquaMl < Math.trunc(t.acquaMl * 0.8)) {
      out.push({
        priorita: 4,
        testo: 'Acqua a ' + bevuti + " L: bevine ancora un po' prima di dormire.",
        perche: "Regola serale: almeno l'80% del target entro le 21:00 (" + fmt1(t.acquaMl * 0.8 / 1000) + ' L su '
          + obiettivo + '); registrati ' + bevuti + ' L.',
      });
    }
  }

  /** Voci senza valori: i totali sono sottostimati, e lo si dice chiaro. */
  function consigliSenzaValori(c, out) {
    const n = c.g.nonNoti;
    if (!(n > 0)) return;
    const voci = n === 1 ? 'voce' : 'voci';
    out.push({
      priorita: 9,
      testo: n + ' ' + voci + ' senza valori nutrizionali: i totali reali sono più alti di quelli mostrati.',
      perche: n + ' ' + voci + ' della giornata sono state inserite senza kcal/proteine: i totali mostrati sono quindi '
        + 'un minimo garantito, non il valore reale. Per contarle, aggiungi i valori all\'alimento in dispensa '
        + '(bastano kcal e proteine per 100 g).',
    });
  }

  /** La quota di ultra-processati: quella già calcolata nell'oggetto, altrimenti ricavata dalle kcal. null = campione troppo piccolo. */
  function quotaUltra(q) {
    if (q.quotaUltraProcessati !== undefined) return q.quotaUltraProcessati === null ? null : numero(q.quotaUltraProcessati);
    return numero(q.kcalConGrado) >= MIN_KCAL_GRADO ? numero(q.kcalUltraProcessati) / numero(q.kcalConGrado) : null;
  }

  function coperturaDato(q) {
    if (q.copertura !== undefined && q.copertura !== null) return numero(q.copertura);
    return numero(q.kcalTotali) > 0 ? numero(q.kcalConGrado) / numero(q.kcalTotali) : 0;
  }

  /**
   * Segnali sulla qualità del cibo. Regola non negoziabile: si suggerisce cosa AGGIUNGERE, mai cosa evitare,
   * e nessun alimento viene chiamato "cattivo". Se il dato non c'è, si tace invece di indovinare.
   */
  function consigliQualita(c, out) {
    const { g } = c;
    const q = g.qualita;
    if (!q || g.kcal <= 0) return;
    const porzioni = numero(q.porzioniFruttaVerdura);
    const fibraG = numero(q.fibraG);
    const quota = quotaUltra(q);

    // Frutta e verdura: dal pomeriggio, come traguardo da raggiungere.
    if (c.minuti >= 15 * 60 && porzioni < 3) {
      const mancanti = Math.max(1, Math.ceil(3 - porzioni));
      const quante = porzioni <= 0 ? 'nessuna porzione' : fmt1(porzioni) + ' porzioni';
      out.push({
        priorita: 6,
        testo: 'Frutta e verdura: ' + quante + ' oggi. Aggiungine ' + mancanti + ' — un frutto a metà mattina e verdura a cena bastano.',
        perche: 'Conto una porzione ogni ' + PORZIONE_G + ' g di frutta o verdura registrata: oggi ' + fmt1(porzioni) + '. '
          + "L'obiettivo è 3-5 al giorno. In un surplus contano più del solito: mangi tanto, e fibra e micronutrienti "
          + 'sono ciò che tiene in ordine digestione e recupero mentre il volume di cibo è alto. '
          + "È un'aggiunta al piano, non toglie nulla alle calorie o alle proteine.",
      });
    }

    // Grado di lavorazione: solo la sera, solo se il campione è affidabile.
    if (c.sera && quota !== null && quota > 0.5) {
      out.push({
        priorita: 6,
        testo: 'Circa il ' + arrotondaIntero(quota * 100) + '% delle calorie di oggi viene da prodotti industriali. '
          + 'Il piano regge lo stesso: domani prova a sostituirne uno con cibo fresco — uova, riso, carne, frutta secca.',
        perche: 'Calcolato solo sulle voci di cui conosco il grado di lavorazione (' + raggruppa(arrotondaIntero(q.kcalUltraProcessati))
          + ' kcal su ' + raggruppa(arrotondaIntero(q.kcalConGrado)) + ', cioè il ' + arrotondaIntero(coperturaDato(q) * 100)
          + '% della giornata). '
          + 'Uso la scala NOVA, che misura quanto un cibo è trasformato industrialmente — non quanto è calorico: '
          + 'olio, frutta secca, latte e carne restano fuori, perché in massa servono. '
          + 'Non è un voto sulla giornata: le calorie le hai comunque prese, questo riguarda solo con che cosa.',
      });
    }

    // Fibra: la sera, se la giornata è già consistente.
    if (c.sera && g.kcal >= 1500 && fibraG >= 0.1 && fibraG <= 19.9) {
      out.push({
        priorita: 7,
        testo: 'Fibra: ' + fmt1(fibraG) + ' g oggi. Con una dieta da ' + raggruppa(arrotondaIntero(g.kcal)) + ' kcal punta a '
          + FIBRA_TARGET + ' g: pane e pasta integrali, verdura e frutta secca la coprono senza sforzo.',
        perche: 'Registrati ' + fmt1(fibraG) + ' g di fibra contro un obiettivo di ~' + FIBRA_TARGET + ' g. '
          + 'Con introiti alti la fibra è il marcatore più semplice di una dieta fatta di cibo vero, e aiuta la digestione '
          + 'di pasti abbondanti. Conto solo gli alimenti che riportano il valore: quelli senza dato non li indovino.',
      });
    }

    // Conferma quando la qualità regge: il rinforzo positivo è parte del metodo.
    if (c.sera && porzioni >= 3 && (quota === null || quota <= 0.35)) {
      out.push({
        priorita: 6,
        testo: 'Qualità della giornata buona: ' + fmt1(porzioni) + ' porzioni di frutta e verdura e poca roba industriale.',
        perche: 'Frutta e verdura a ' + fmt1(porzioni) + ' porzioni (obiettivo 3-5)'
          + (quota !== null ? ', e solo il ' + arrotondaIntero(quota * 100) + '% delle calorie da prodotti industriali' : '')
          + '. È esattamente la differenza tra mettere su peso e mettere su peso buono.',
      });
    }
  }

  /**
   * I consigli di adesso: [{ priorita, testo, perche }], dal più importante (0) al meno (9), stabile a parità.
   *   adesso   Date: l'ora locale decide quali momenti sono già passati;
   *   giorno   { kcal, prot, nonNoti, numeroPerMomento, acquaMl, allenato, qualita } del giorno di adesso;
   *   ieri     lo stesso per ieri, oppure null se ieri non c'è niente registrato;
   *   obiettivi { kcalMin, kcalMax, protMin, protMax, acquaMl }.
   * Nei giorni di allenamento i consigli puntano alla metà alta del range (+150 kcal), senza toccare gli obiettivi mostrati.
   */
  function consigli(adesso, giorno, ieri, obiettivi) {
    if (!giorno || !obiettivi) return [];
    const ora = comeData(adesso);
    const t = {
      kcalMin: numero(obiettivi.kcalMin),
      kcalMax: numero(obiettivi.kcalMax),
      protMin: numero(obiettivi.protMin),
      protMax: numero(obiettivi.protMax),
      acquaMl: numero(obiettivi.acquaMl),
    };
    const g = statoGiorno(giorno);
    const minuti = ora.getHours() * 60 + ora.getMinutes();
    const deltaAll = g.allenato ? 150 : 0;
    const centro = (t.kcalMin + t.kcalMax) / 2 + deltaAll;
    const quotaAttesa = FINESTRE.reduce((somma, w) => somma + avanzamento(w, minuti) * w.quota, 0);
    const kcalAttese = centro * quotaAttesa;
    const c = {
      t,
      g,
      ieri: ieri ? statoGiorno(ieri) : null,
      minuti,
      sera: minuti >= 21 * 60,
      deltaAll,
      etichettaAll: g.allenato ? ' (giorno di allenamento)' : '',
      centro,
      quotaAttesa,
      kcalAttese,
      buco: kcalAttese - g.kcal,
    };
    const out = [];
    if (c.sera) consigliSerali(c, out);
    consigliMattina(c, out);
    consigliRitmo(c, out);
    consigliSaltato(c, out);
    consigliProteine(c, out);
    consigliAcqua(c, out);
    consigliQualita(c, out);
    consigliSenzaValori(c, out);
    return out.sort((a, b) => a.priorita - b.priorita); // stabile: a parità resta l'ordine di inserimento
  }

  // --- Allenamento (WorkoutQueries) -------------------------------------------------------------------
  //
  // Query pure sui dati. Una sessione ha { id, date, startedAt, routineId, routineName, type, exercises },
  // un esercizio della sessione { exerciseId, sets }. Le date sono ISO aaaa-mm-gg: l'ordine alfabetico è
  // quello cronologico.

  function sessioniDi(sessioni) {
    return Array.isArray(sessioni) ? sessioni : [];
  }

  function eserciziDi(sessione) {
    return (sessione && sessione.exercises) || [];
  }

  function serieDi(esercizio) {
    return (esercizio && esercizio.sets) || [];
  }

  /** Dalla più recente; a parità di giorno, dall'orario di inizio più recente. */
  function piuRecentePrima(a, b) {
    if (a.date !== b.date) return a.date < b.date ? 1 : -1;
    return numero(b.startedAt) - numero(a.startedAt);
  }

  /** Il nome della scheda di allora; per le sessioni delle prime versioni, "Spinta"/"Trazione"; altrimenti "Allenamento". */
  function etichettaSessione(s) {
    const nome = s.routineName != null ? String(s.routineName) : '';
    if (nome.trim() !== '') return nome;
    if (s.type === 'SPINTA') return 'Spinta';
    if (s.type === 'TRAZIONE') return 'Trazione';
    return 'Allenamento';
  }

  /** Il primo esercizio della sessione con quell'id, se ha almeno una serie. */
  function serieConDati(sessione, esercizioId) {
    const esercizio = eserciziDi(sessione).find((e) => e.exerciseId === esercizioId);
    return esercizio && serieDi(esercizio).length > 0 ? serieDi(esercizio) : null;
  }

  /**
   * Le serie dell'ultima sessione precedente (con serie registrate) per un esercizio: { data, serie } oppure null.
   * Se si indica la sessione in corso, contano solo le sessioni iniziate prima di lei.
   */
  function ultimeSerie(sessioni, esercizioId, sessioneCorrenteId) {
    const lista = sessioniDi(sessioni);
    const senzaCorrente = sessioneCorrenteId == null;
    const corrente = senzaCorrente ? null : lista.find((s) => s.id === sessioneCorrenteId) || null;
    const prime = lista
      .filter((s) => senzaCorrente || s.id !== sessioneCorrenteId)
      .filter((s) => corrente === null || s.date < corrente.date
        || (s.date === corrente.date && numero(s.startedAt) < numero(corrente.startedAt)))
      .sort(piuRecentePrima);
    for (const s of prime) {
      const serie = serieConDati(s, esercizioId);
      if (serie) return { data: s.date, serie: serie.map(normalizzaSerie) };
    }
    return null;
  }

  /** Lo storico completo di un esercizio, dal più recente: [{ data, etichetta, serie }], senza sessioni vuote. */
  function storicoEsercizio(sessioni, esercizioId) {
    const righe = [];
    for (const s of sessioniDi(sessioni).slice().sort(piuRecentePrima)) {
      const serie = serieConDati(s, esercizioId);
      if (serie) righe.push({ data: s.date, etichetta: etichettaSessione(s), serie: serie.map(normalizzaSerie) });
    }
    return righe;
  }

  /** Giorno "di allenamento" = almeno una serie registrata quel giorno. */
  function allenatoIl(sessioni, dataIso) {
    return sessioniDi(sessioni).some((s) => s.date === dataIso && eserciziDi(s).some((e) => serieDi(e).length > 0));
  }

  /** Regola di progressione della scheda: tutte le serie al massimo del range → si aggiunge un disco. */
  function tutteAlMassimo(serie, repMax) {
    return repMax != null && Array.isArray(serie) && serie.length > 0 && serie.every((s) => ripetizioni(s) >= repMax);
  }

  /** Evita pesi come 12.500000001 dopo qualche passo da 0,5 kg. */
  function arrotondaKg(kg) {
    return arrotondaPari(numero(kg) * 100) / 100 + 0;
  }

  /**
   * Cosa proporre per la prima serie di oggi, così nella maggior parte dei casi basta un tocco su "Segna":
   * { set: { kg, rip }, progressione } oppure null se non c'è una volta scorsa.
   *  - di norma, l'ultima serie della volta scorsa;
   *  - se l'ultima volta TUTTE le serie erano al massimo del range, un disco in più ripartendo dal minimo
   *    delle ripetizioni (è la regola della scheda, fatta dall'app invece che a mente);
   *  - a corpo libero non c'è disco da aggiungere, quindi resta com'era.
   * esercizio = { repMin, repMax, incrementoKg } oppure null.
   */
  function serieProposta(serieVoltaScorsa, esercizio) {
    const prec = Array.isArray(serieVoltaScorsa) ? serieVoltaScorsa : [];
    if (prec.length === 0) return null;
    const ultima = normalizzaSerie(prec[prec.length - 1]);
    const pesoMax = prec.reduce((m, s) => Math.max(m, chilogrammi(s)), -Infinity);
    if (esercizio && pesoMax > 0 && tutteAlMassimo(prec, esercizio.repMax)) {
      const passo = esercizio.incrementoKg != null && esercizio.incrementoKg > 0 ? esercizio.incrementoKg : 1;
      const rip = esercizio.repMin != null ? esercizio.repMin : ultima.rip;
      return { set: { kg: arrotondaKg(pesoMax + passo), rip }, progressione: true };
    }
    return { set: ultima, progressione: false };
  }

  /** L'ultima data in cui è stata fatta una sessione di questa scheda (con serie), oppure null. */
  function ultimaVoltaScheda(sessioni, schedaId) {
    let ultima = null;
    for (const s of sessioniDi(sessioni)) {
      if (s.routineId === schedaId && eserciziDi(s).some((e) => serieDi(e).length > 0) && (ultima === null || s.date > ultima)) ultima = s.date;
    }
    return ultima;
  }

  // --- Compila esercizio (CompilaEsercizio) ------------------------------------------------------------
  //
  // Compila da solo il modulo di un esercizio a partire dal nome: regole sulle parole della palestra
  // (italiano e inglese), tarate sulla scheda di partenza. Serie e ripetizioni sono scelte di
  // programmazione e non dati dell'esercizio: qui si usano i range classici per quel tipo di movimento.
  // I modelli sono scritti come nel Kotlin (stesse stringhe, con le barre rovesciate doppie): così si confrontano a occhio.

  function regola(schema, gruppo, serie, repMin, repMax, opzioni) {
    const o = opzioni || {};
    return {
      parole: new RegExp(schema),
      gruppo,
      serie,
      repMin,
      repMax,
      corpoLibero: !!o.corpoLibero,
      unilaterale: !!o.unilaterale,
      nota: o.nota != null ? o.nota : null,
    };
  }

  // L'ordine conta: le regole specifiche stanno prima di quelle generiche
  // ("leg curl" prima di "curl", "alzate laterali" prima di "spalle").
  const REGOLE = [
    // Addominali
    regola('\\bplanc?k\\w*', 'Addominali', '3', 30, 60, { corpoLibero: true, nota: 'a tempo: segna i secondi come ripetizioni' }),
    regola(
      '\\bcrunch\\w*|\\baddominal\\w*|\\bsit ?ups?\\b|\\bleg raises?\\b|' +
        '\\b(alzate|sollevament\\w*|sollevare) (le |delle )?gambe\\b|\\brussian twist|\\bab wheel|' +
        '\\brollout|\\bhollow|\\bmountain climbers?|\\bbicicletta\\b|\\bv ?ups?\\b|\\btoes? to bar',
      'Addominali', '3', 12, 20, { corpoLibero: true },
    ),
    // Polpacci
    regola('\\bpolpacc\\w*|\\bcalf\\b|\\bcalves\\b', 'Polpacci', '3', 15, 25),
    // Salute delle spalle
    regola('\\bface ?pulls?\\b', 'Salute spalle', '2-3', 12, 15),
    regola(
      '\\breverse fly\\w*|\\balzate posteriori|\\bcroci inverse|\\brear delt|\\baperture posteriori|' +
        '\\breverse butterfly|\\bbutterfly invers\\w*|\\bpull ?aparts?\\b',
      'Salute spalle', '2-3', 12, 15,
    ),
    regola('\\brotazion\\w* estern\\w*|\\bexternal rotations?', 'Salute spalle', '2-3', 12, 20),
    // Spalle
    regola('\\balzate laterali|\\blateral raises?', 'Spalle', '3', 12, 20),
    regola('\\balzate frontali|\\bfront raises?', 'Spalle', '3', 10, 15),
    regola('\\btirate al mento|\\bupright rows?', 'Spalle', '3', 10, 12),
    regola(
      '\\bmilitary|\\blento avanti|\\bshoulder press|\\boverhead press|\\barnold|' +
        '\\bspinte? (sopra la testa|in alto)|\\bpush press',
      'Spalle', '3-4', 8, 12,
    ),
    // Tricipiti (prima dei piegamenti e delle distensioni generiche)
    regola('\\bdiamond|\\bpiegament\\w* strett\\w*|\\bclose ?grip push', 'Tricipiti', '3', 8, 15, { corpoLibero: true }),
    regola('\\bdips?\\b', 'Tricipiti', '3', 8, 12, { corpoLibero: true }),
    regola(
      '\\bfrench press|\\bskull ?crushers?|\\bestension\\w* (dei |per i )?tricipit\\w*|' +
        '\\bestension\\w* sopra la testa|\\boverhead (triceps )?extensions?|\\bkick ?backs?|' +
        '\\bpush ?downs?|\\bpressdowns?|\\btricipit\\w*|\\btriceps',
      'Tricipiti', '3', 10, 15,
    ),
    // Avambracci (prima del curl)
    regola('\\bcurl (dei |per i |ai )?polsi|\\bwrist curls?|\\bavambracc\\w*|\\bforearms?', 'Avambracci', '3', 12, 20),
    // Gambe e glutei (prima del curl: leg curl)
    regola('\\bleg curls?|\\bcurl (dei |per i )?femorali|\\bfemoral\\w*|\\bnordic', 'Gambe', '3', 10, 15),
    regola('\\bleg extensions?|\\bestension\\w* (delle |per le )?gambe|\\bquadricip\\w*', 'Gambe', '3', 12, 15),
    regola('\\bleg press|\\bpressa\\b', 'Gambe', '3-4', 10, 15),
    regola('\\bstacc\\w* rumen\\w*|\\brdl\\b|\\bromanian|\\bstiff ?legs?|\\bgambe tese', 'Gambe', '3', 8, 12),
    regola('\\bstacc\\w*|\\bdeadlifts?', 'Gambe', '3-4', 6, 10),
    regola(
      '\\bhip thrusts?|\\bponte (dei |per i )?glutei|\\bglute bridge|\\bglutei|\\bglutes?\\b|' +
        '\\bkettlebell swings?|\\bswings?\\b',
      'Glutei', '3', 8, 15,
    ),
    regola('\\babduzion\\w*|\\badduzion\\w*|\\babduct\\w*|\\badduct\\w*', 'Gambe', '3', 12, 20),
    regola(
      '\\baffond\\w*|\\blunges?\\b|\\bsplit squats?|\\bbulgar\\w*|\\bstep ?ups?\\b|' +
        '\\bsalit\\w* (sul|sulla|sullo) (gradino|panca|step)',
      'Gambe', '3', 8, 12, { unilaterale: true },
    ),
    regola('\\bsquat\\w*|\\baccosciat\\w*|\\bgoblet', 'Gambe', '3-4', 8, 12),
    // Petto
    regola('\\bcroci\\b|\\bfly(e|es|s)?\\b|\\bpec ?deck|\\bbutterfly|\\baperture\\b', 'Petto', '3', 10, 15),
    regola('\\bpiegament\\w*|\\bflession\\w*|\\bpush ?ups?\\b', 'Petto', '3', 8, 15, { corpoLibero: true }),
    regola('\\bpanca\\b|\\bbench|\\bdistension\\w*|\\bchest press|\\bfloor press', 'Petto', '3-4', 8, 12),
    // Schiena
    regola('\\btrazion\\w*|\\bpull ?ups?\\b|\\bchin ?ups?\\b', 'Schiena', '3', 5, 10, { corpoLibero: true }),
    regola('\\blat\\b|\\blat machine|\\bpull ?downs?\\b', 'Schiena', '3-4', 8, 12),
    regola(
      '\\bsuperman|\\bnuotat\\w*|\\biperestension\\w*|\\bhyperextensions?|\\bback extensions?|' +
        '\\blombari|\\bgood mornings?',
      'Schiena', '3', 12, 15,
    ),
    regola('\\bpull ?overs?\\b', 'Schiena', '3', 10, 15),
    regola('\\bscrollat\\w*|\\bshrugs?\\b|\\btrapezi\\w*', 'Trapezio', '3', 10, 15),
    regola('\\bremator\\w*|\\brows?\\b|\\browing|\\bpulley\\b|\\bt ?bar\\b', 'Schiena', '3', 8, 12),
    // Bicipiti
    regola('\\bhammer|\\bmartello\\b', 'Bicipiti', '3', 10, 15),
    regola('\\bcurl\\w*|\\bbicipit\\w*|\\bbiceps', 'Bicipiti', '3', 8, 12),
    // Solo il muscolo, senza il movimento: range medi.
    regola('\\bspall\\w*|\\bdeltoid\\w*|\\bshoulders?\\b', 'Spalle', '3', 10, 15),
    regola('\\bpetto\\b|\\bpettoral\\w*|\\bchest\\b', 'Petto', '3', 8, 12),
    regola('\\bschiena\\b|\\bdorsal\\w*', 'Schiena', '3', 8, 12),
    regola('\\bgamb[ae]\\b|\\blegs?\\b|\\bcosc[ei]\\b', 'Gambe', '3', 8, 12),
  ];

  const UNILATERALE = new RegExp('\\bun braccio\\b|\\buna gamba\\b|\\bsingol\\w*|\\bunilateral\\w*|\\bsingle\\b|\\bone arm');
  const ZAVORRA = new RegExp('\\bzavorr\\w*|\\bcon (peso|disco|manubri\\w*|kettlebell)|\\bweighted');
  const BILANCIERE = new RegExp('\\bbilancier\\w*|\\bbarbell|\\bbil\\b|\\bsmith');
  const MACCHINA = new RegExp(
    '\\bcav[oi]\\b|\\bpulley\\b|\\bcables?\\b|\\bmacchin\\w*|\\bmachine|\\bleg press|\\bpressa\\b|' +
      '\\bpec ?deck|\\bleg (extension|curl)s?|\\bchest press|\\blat\\b',
  );

  /** Minuscolo, senza accenti e trattini: "Push-Up" e "push up" diventano uguali. */
  function normalizzaNome(nome) {
    return String(nome == null ? '' : nome)
      .toLowerCase()
      .normalize('NFD')
      .replace(/\p{Mn}+/gu, '')
      .replace(/[-_/.,()]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
  }

  /**
   * La proposta dalle regole: { gruppo, serie, repMin, repMax, incrementoKg, nota }, oppure null se il nome non si
   * riconosce (e allora si può chiedere all'AI). salto è il salto che l'utente usa già sugli altri esercizi: per i
   * manubri dipende dai suoi dischi, non dall'esercizio. incrementoKg null = a corpo libero oppure salto non noto
   * (il campo resta vuoto, e vale 1 kg).
   */
  function compilaEsercizio(nome, salto) {
    const n = normalizzaNome(nome);
    if (n === '') return null;
    const r = REGOLE.find((x) => x.parole.test(n));
    if (!r) return null;
    const conPeso = !r.corpoLibero || ZAVORRA.test(n);
    let incrementoKg;
    if (!conPeso) incrementoKg = null;
    else if (BILANCIERE.test(n)) incrementoKg = 2.5;
    else if (MACCHINA.test(n)) incrementoKg = 5;
    else incrementoKg = typeof salto === 'number' && Number.isFinite(salto) ? salto : null;
    let nota = null;
    if (r.nota !== null) nota = r.nota;
    else if (r.unilaterale || UNILATERALE.test(n)) nota = 'per lato';
    return { gruppo: r.gruppo, serie: r.serie, repMin: r.repMin, repMax: r.repMax, incrementoKg, nota };
  }

  /** Il salto più usato tra gli esercizi dell'utente (solo quelli impostati e > 0); a parità, il primo incontrato. */
  function saltoAbituale(salti) {
    const volte = new Map();
    for (const s of salti || []) {
      if (typeof s === 'number' && s > 0) volte.set(s, (volte.get(s) || 0) + 1);
    }
    let migliore = null;
    let max = 0;
    for (const [valore, n] of volte) {
      if (n > max) { migliore = valore; max = n; }
    }
    return migliore;
  }

  // --- Scheda del prodotto (SchedaNutrizionale) --------------------------------------------------------
  //
  // Righe della scheda nello stile di Yuka ma TARATE SULL'OBIETTIVO MASSA: qui un cibo calorico è un pregio,
  // non un difetto. Per lo stesso motivo non esiste un punteggio unico: premierebbe l'insalata e punirebbe
  // olio e frutta secca, che in un surplus servono. I livelli sono BUONO (verde) e NEUTRO (grigio): non
  // esiste un livello "cattivo", nessun alimento viene bollato.

  /**
   * La scheda di un alimento (valori per 100 g, o 100 ml): { righe: [{ titolo, nota, valore, ok, livello }], livello }.
   * Mostra solo ciò che si sa davvero: niente dato, niente riga. ok = la riga è verde.
   * Il livello della scheda è il migliore tra le righe (BUONO se almeno una è verde, altrimenti NEUTRO):
   * nel Kotlin non esiste, serve solo a chi vuole colorare l'intestazione e non è mai negativo.
   */
  function schedaNutrizionale(alimento) {
    const a = alimento || {};
    const righe = [];
    const riga = (titolo, nota, valore, ok) => righe.push({ titolo, nota, valore, ok, livello: ok ? 'BUONO' : 'NEUTRO' });

    if (a.kcal100 != null) {
      const k = a.kcal100;
      riga(
        'Energia',
        k >= 300 ? 'Molto calorico: comodo per il surplus' : k >= 150 ? 'Buon apporto di energia' : 'Leggero: ne serve una porzione generosa',
        fmt1(k) + ' kcal',
        k >= 150,
      );
    }
    if (a.prot100 != null) {
      const p = a.prot100;
      riga(
        'Proteine',
        p >= 15 ? 'Ottima fonte di proteine' : p >= 8 ? 'Buon contenuto di proteine' : 'Poche proteine: abbinalo a una fonte proteica',
        fmt1(p) + ' g',
        p >= 8,
      );
    }
    if (a.carb100 != null) {
      riga('Carboidrati', "Energia per l'allenamento", fmt1(a.carb100) + ' g', false);
    }
    if (a.fibra100 != null) {
      const f = a.fibra100;
      riga('Fibre', f >= 6 ? 'Ricco di fibre' : f >= 3 ? "Un po' di fibre" : 'Poche fibre', fmt1(f) + ' g', f >= 3);
    }
    if (a.grassi100 != null) {
      const g = a.grassi100;
      riga('Grassi', g >= 20 ? 'Grassi abbondanti: molte calorie in poco volume' : 'Grassi moderati', fmt1(g) + ' g', false);
    }
    if (a.grassiSaturi100 != null) {
      const s = a.grassiSaturi100;
      riga('Grassi saturi', s <= 1.5 ? 'Povero di grassi saturi' : 'Grassi saturi presenti', fmt1(s) + ' g', s <= 1.5);
    }
    if (a.zuccheri100 != null) {
      const z = a.zuccheri100;
      riga('Zuccheri', z <= 5 ? 'Povero di zuccheri' : 'Zuccheri presenti', fmt1(z) + ' g', z <= 5);
    }
    if (a.sale100 != null) {
      const s = a.sale100;
      riga('Sale', s <= 0.3 ? 'Povero di sale' : 'Sale presente', fmt1(s) + ' g', s <= 0.3);
    }
    if (a.nova != null) {
      const n = a.nova;
      riga(
        'Lavorazione',
        n === 1 ? 'Cibo non lavorato' : n === 2 ? 'Ingrediente di cucina' : n === 3 ? 'Cibo lavorato' : 'Prodotto industriale',
        'NOVA ' + n,
        n <= 2,
      );
    }
    return { righe, livello: righe.some((r) => r.ok) ? 'BUONO' : 'NEUTRO' };
  }

  /** Quanto questa voce porta davvero nel diario: "130 g = 462,8 kcal, 18,8 g di proteine". */
  function contributoScheda(alimento, grammi) {
    const a = alimento || {};
    const g = numero(grammi);
    let testo = fmt1(g) + ' ' + (a.isDrink ? 'ml' : 'g');
    if (a.kcal100 != null) testo += ' = ' + fmt1(g * a.kcal100 / 100) + ' kcal';
    if (a.prot100 != null) testo += ', ' + fmt1(g * a.prot100 / 100) + ' g di proteine';
    return testo;
  }

  // --- Regola del peso (WeightRule) --------------------------------------------------------------------

  /**
   * String.format("%+.1f", x) di Java, con la virgola italiana. Java arrotonda "mezzo in su" sulle cifre
   * decimali più corte del numero (0,35 → +0,4), toFixed guarda il valore binario esatto (0,3): qui si
   * fa come Java perché il testo del consiglio sia identico a quello del telefono.
   */
  function conSegno1(x) {
    const negativo = x < 0 || Object.is(x, -0);
    const m = Math.abs(x);
    let decimi = 0n;
    if (m !== 0) {
      const [mantissa, esponente] = m.toExponential().split('e');
      const cifre = mantissa.replace('.', '');
      const tieni = Number(esponente) + 2; // cifre da tenere: la parte intera più un decimale
      if (tieni >= cifre.length) {
        decimi = BigInt(cifre + '0'.repeat(tieni - cifre.length));
      } else if (tieni >= 0) {
        decimi = (tieni === 0 ? 0n : BigInt(cifre.slice(0, tieni))) + (cifre.charCodeAt(tieni) >= 53 ? 1n : 0n);
      }
    }
    return (negativo ? '-' : '+') + (decimi / 10n) + ',' + (decimi % 10n);
  }

  /**
   * Regola del piano: se dopo 3-4 settimane il peso non si muove, aumenta le porzioni. Guarda le pesate
   * degli ultimi 35 giorni ([{ date, kg }]); serve una distanza di almeno 21 giorni tra la prima e l'ultima
   * e una crescita inferiore a 0,4 kg. Restituisce { testo, giorni, deltaKg } oppure null (nessun avviso).
   * Il Kotlin restituisce solo il testo: l'oggetto, se lo si scrive come testo, dà lo stesso testo.
   * oggi (Date o "aaaa-mm-gg") serve solo ai test: senza, è il giorno di adesso.
   */
  function regolaPeso(pesi, oggi) {
    const o = giornoDa(oggi);
    const recenti = [];
    for (const w of Array.isArray(pesi) ? pesi : []) {
      const g = w ? leggiDataIso(w.date) : null; // una data illeggibile salta la pesata, come runCatching
      if (g && typeof w.kg === 'number' && Number.isFinite(w.kg) && g.n <= o.n && g.n >= o.n - 35) recenti.push({ n: g.n, kg: w.kg });
    }
    recenti.sort((a, b) => a.n - b.n); // stabile: a parità di giorno resta l'ordine di inserimento
    if (recenti.length < 2) return null;
    const primo = recenti[0];
    const ultimo = recenti[recenti.length - 1];
    const giorni = ultimo.n - primo.n;
    if (giorni < 21) return null;
    const delta = ultimo.kg - primo.kg;
    if (delta >= 0.4) return null;
    const testo = 'Peso fermo da ' + giorni + ' giorni (' + conSegno1(delta) + ' kg): il piano dice di aumentare le porzioni. '
      + 'Parti da +50 g di pasta o +1 fetta di pane al giorno.';
    const esito = { testo, giorni, deltaKg: delta };
    Object.defineProperty(esito, 'toString', { value: () => testo }); // non enumerabile: non entra nei confronti
    return esito;
  }

  // --- Obiettivi dal profilo (TargetCalculator) ----------------------------------------------------------

  const FATTORI_ATTIVITA = { SEDENTARIA: 1.2, LEGGERA: 1.375, MODERATA: 1.55, ALTA: 1.725, MOLTO_ALTA: 1.9 };
  const SCOPI = {
    MASSA: { deltaKcal: 400, protPerKg: 1.8 },
    RICOMPOSIZIONE: { deltaKcal: 0, protPerKg: 2.0 },
    DEFINIZIONE: { deltaKcal: -400, protPerKg: 2.2 },
  };

  /** Un valore di un enum scritto come stringa maiuscola; se non c'è o non si riconosce, quello di partenza del profilo. */
  function dalla(tabella, valore, predefinito) {
    const chiave = String(valore).toUpperCase();
    return Object.prototype.hasOwnProperty.call(tabella, chiave) ? tabella[chiave] : tabella[predefinito];
  }

  /** Metabolismo basale, formula di Mifflin-St Jeor. profilo = { annoNascita, sesso, altezzaCm, pesoKg, ... } del file. */
  function metabolismoBasale(profilo, annoCorrente) {
    const p = profilo || {};
    const eta = numero(p.annoNascita) > 0 ? numero(annoCorrente) - p.annoNascita : 0;
    const base = 10 * numero(p.pesoKg) + 6.25 * numero(p.altezzaCm) - 5 * eta;
    return String(p.sesso).toUpperCase() === 'FEMMINA' ? base - 161 : base + 5;
  }

  /** kotlin.math.round(v / 50) * 50: gli estremi del range calorico sono arrotondati a 50 (a metà strada, al pari). */
  function arrotonda50Pari(v) {
    return arrotondaPari(v / 50) * 50 + 0;
  }

  /**
   * Gli obiettivi calcolati dal profilo (Mifflin-St Jeor, standard di settore):
   * { bmr, tdee, kcalMin, kcalMax, protMin, protMax, acquaMl }. Range di ±200 kcal attorno al centro
   * (consumo stimato + surplus o deficit dell'obiettivo), proteine in proporzione al peso, acqua ~40 ml per kg
   * arrotondata a 250 ml e tenuta tra 1500 e 5000. Senza annoCorrente si usa l'anno di adesso.
   */
  function calcolaObiettivi(profilo, annoCorrente) {
    const p = profilo || {};
    const anno = typeof annoCorrente === 'number' && Number.isFinite(annoCorrente) ? annoCorrente : new Date().getFullYear();
    const peso = numero(p.pesoKg);
    const bmr = metabolismoBasale(p, anno);
    const tdee = bmr * dalla(FATTORI_ATTIVITA, p.attivita, 'MODERATA');
    const scopo = dalla(SCOPI, p.obiettivo, 'MASSA');
    const centro = tdee + scopo.deltaKcal;
    const protMin = arrotondaPari(peso * scopo.protPerKg) + 0;
    return {
      bmr: arrotondaPari(bmr) + 0,
      tdee: arrotondaPari(tdee) + 0,
      kcalMin: arrotonda50Pari(centro - 200),
      kcalMax: arrotonda50Pari(centro + 200),
      protMin,
      protMax: protMin + 20,
      acquaMl: Math.min(5000, Math.max(1500, arrotondaPari(peso * 40 / 250) * 250)) + 0,
    };
  }

  // --- Promemoria (NextOccurrence) ---------------------------------------------------------------------

  /**
   * La prossima volta in cui scatta un promemoria, dato l'orario e i giorni attivi, come Date locale.
   * maschera: bit 0 = lunedì ... bit 6 = domenica. Vale solo un istante DOPO adesso (alla stessa ora
   * esatta si passa al giorno dopo). null se non c'è nessun giorno attivo o l'orario non esiste.
   * Si confrontano ora e giorno "da orologio" (come LocalDateTime): il cambio dell'ora legale non sposta nulla.
   */
  function prossimaOccorrenza(adesso, ora, minuto, maschera) {
    if ((maschera & 0b1111111) === 0) return null;
    if (!Number.isInteger(ora) || ora < 0 || ora > 23 || !Number.isInteger(minuto) || minuto < 0 || minuto > 59) return null;
    const ora0 = comeData(adesso);
    const y = ora0.getFullYear();
    const m = ora0.getMonth();
    const d = ora0.getDate();
    const adessoDaOrologio = Date.UTC(y, m, d, ora0.getHours(), ora0.getMinutes(), ora0.getSeconds(), ora0.getMilliseconds());
    for (let i = 0; i <= 7; i++) {
      const giorno = new Date(y, m, d + i); // mezzanotte locale del giorno i
      const candidato = Date.UTC(giorno.getFullYear(), giorno.getMonth(), giorno.getDate(), ora, minuto);
      const bit = 1 << ((giorno.getDay() + 6) % 7); // lunedì = bit 0
      if (candidato > adessoDaOrologio && (maschera & bit) !== 0) {
        return new Date(giorno.getFullYear(), giorno.getMonth(), giorno.getDate(), ora, minuto, 0, 0);
      }
    }
    return null;
  }

  // --- Risposta dell'AI per un esercizio (AiEsercizi.parse) -------------------------------------------

  const GRUPPI_ESERCIZI = ['Petto', 'Schiena', 'Spalle', 'Salute spalle', 'Bicipiti', 'Tricipiti', 'Avambracci',
    'Trapezio', 'Gambe', 'Glutei', 'Polpacci', 'Addominali', 'Altro'];
  const NON_RICONOSCIUTO = 'nome non riconosciuto';

  /** Toglie le recinzioni ```json e il testo attorno alle graffe: l'AI a volte risponde con del contorno. */
  function estraiJson(grezzo) {
    let t = String(grezzo).trim();
    if (t.startsWith('```json')) t = t.slice(7);
    if (t.startsWith('```')) t = t.slice(3);
    if (t.endsWith('```')) t = t.slice(0, -3);
    t = t.trim();
    const i = t.indexOf('{');
    const j = t.lastIndexOf('}');
    return i >= 0 && j > i ? t.slice(i, j + 1) : t;
  }

  /**
   * Legge la risposta dell'AI a "compila questo esercizio" difendendosi da valori strani: meglio un campo
   * vuoto che uno assurdo. Stessa forma di compilaEsercizio, oppure null se nemmeno l'AI riconosce il nome.
   * Se la risposta non è un oggetto JSON lancia un errore (come il Kotlin): chi chiama decide che messaggio dare.
   */
  function leggiRispostaAiEsercizio(grezzo) {
    const o = JSON.parse(estraiJson(grezzo));
    if (o === null || typeof o !== 'object' || Array.isArray(o)) throw new Error('La risposta non è un oggetto');
    const testo = (k) => {
      const v = o[k];
      if (v === undefined || (typeof v === 'object' && v !== null)) return '';
      return String(v).trim(); // null diventa "null", come il content di JsonNull
    };
    const numeroDa = (k) => {
      const t = testo(k).replace(/,/g, '.');
      // Come toDoubleOrNull di Kotlin, che accetta anche il suffisso f/d dei numeri Java ("12f" vale 12).
      return /^[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?[fFdD]?$/.test(t) ? Number(t.replace(/[fFdD]$/, '')) : null;
    };
    const nota = testo('nota');
    if (nota.toLowerCase().includes(NON_RICONOSCIUTO)) return null;

    const gruppo = GRUPPI_ESERCIZI.find((g) => g.toLowerCase() === testo('gruppo').toLowerCase()) || 'Altro';
    const serie = /^\d{1,2}(-\d{1,2})?$/.test(testo('serie')) ? testo('serie') : '3';
    const intero = (k) => {
      const n = numeroDa(k);
      const v = n === null ? null : Math.trunc(n);
      return v !== null && v >= 1 && v <= 120 ? v : null;
    };
    const a = intero('repMin');
    const b = intero('repMax');
    let repMin;
    let repMax;
    if (a !== null && b !== null) { repMin = Math.min(a, b); repMax = Math.max(a, b); }
    else if (a !== null) { repMin = a; repMax = a; }
    else if (b !== null) { repMin = b; repMax = b; }
    else { repMin = 8; repMax = 12; }
    const salto = numeroDa('incrementoKg');
    return {
      gruppo,
      serie,
      repMin,
      repMax,
      incrementoKg: salto !== null && salto > 0 && salto <= 20 ? salto : null,
      nota: nota.trim() !== '' ? nota.slice(0, 80) : null,
    };
  }

  radice.SurplusLogica = {
    // Formattazione
    fmtInt,
    fmtKcal,
    fmt1,
    fmtLitri,
    fmtSerie,
    fmtOra,
    etichettaData,
    etichettaDataBreve,
    // Diario e qualità del cibo
    totaliGiorno,
    perGrammi,
    qualitaGiorno,
    mediaQualita,
    // Consigli
    consigli,
    // Allenamento
    serieProposta,
    ultimeSerie,
    storicoEsercizio,
    allenatoIl,
    tutteAlMassimo,
    arrotondaKg,
    ultimaVoltaScheda,
    compilaEsercizio,
    saltoAbituale,
    leggiRispostaAiEsercizio,
    // Scheda del prodotto, peso, obiettivi, promemoria
    schedaNutrizionale,
    contributoScheda,
    regolaPeso,
    calcolaObiettivi,
    metabolismoBasale,
    prossimaOccorrenza,
  };

  if (typeof module !== 'undefined' && module.exports) module.exports = radice.SurplusLogica;
})();
