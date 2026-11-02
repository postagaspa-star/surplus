/*
 * Surplus per il computer: le sole chiamate che l'interfaccia fa.
 *
 * La pagina è servita da https://surplus.locale/ e chiama SOLO /locale/*, che il
 * programma intercetta dentro WebView2: niente porte aperte sul computer e
 * niente internet da qui. La chiave del servizio AI non passa mai di qua.
 *
 * Qui non si decide niente: si consegna { ok, stato, dati, errore }.
 */
(function (radice) {
  'use strict';

  const ATTESA_MS = 15000;
  // Le foto passano da un servizio esterno: possono volerci diverse decine di secondi.
  const ATTESA_LUNGA_MS = 180000;
  const FRASE_PROGRAMMA = 'Il programma non ha risposto. Se il problema resta, chiudi e riapri Surplus.';

  function percorsoAmmesso(percorso) {
    return typeof percorso === 'string' && /^\/locale\//.test(percorso);
  }

  async function chiama(metodo, percorso, corpo) {
    if (!percorsoAmmesso(percorso)) throw new Error('Chiamata fuori dall\'accordo: ' + percorso);
    const controllo = new AbortController();
    const lunga = percorso === '/locale/foto' || percorso === '/locale/esercizio'
      || percorso === '/locale/scegli-immagini' || percorso === '/locale/esporta' || percorso === '/locale/importa';
    const timer = setTimeout(() => controllo.abort(), lunga ? ATTESA_LUNGA_MS : ATTESA_MS);
    try {
      const opzioni = {
        method: metodo,
        cache: 'no-store',
        headers: { Accept: 'application/json' },
        signal: controllo.signal,
      };
      if (corpo !== undefined) {
        opzioni.headers['Content-Type'] = 'application/json';
        opzioni.body = JSON.stringify(corpo);
      }
      const risposta = await fetch(percorso, opzioni);
      const testo = await risposta.text();
      let dati = null;
      if (testo) {
        try { dati = JSON.parse(testo); } catch (e) { dati = null; }
      }
      return normalizza(risposta.status, dati);
    } catch (e) {
      return { ok: false, stato: 0, dati: null, errore: FRASE_PROGRAMMA };
    } finally {
      clearTimeout(timer);
    }
  }

  function normalizza(stato, dati) {
    const codice = Number(stato) || 0;
    const ok = codice >= 200 && codice < 300 && !(dati && dati.errore);
    let errore = null;
    if (!ok) {
      errore = (dati && typeof dati.errore === 'string' && dati.errore) || 'L\'operazione non è riuscita. Riprova.';
    }
    return { ok, stato: codice, dati, errore };
  }

  radice.SurplusApi = {
    get: (p) => chiama('GET', p),
    post: (p, c) => chiama('POST', p, c === undefined ? {} : c),
  };
})(window);
