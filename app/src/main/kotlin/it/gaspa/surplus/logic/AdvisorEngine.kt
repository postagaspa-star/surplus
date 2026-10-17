package it.gaspa.surplus.logic

import it.gaspa.surplus.data.Moment
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

data class Targets(
    val kcalMin: Int,
    val kcalMax: Int,
    val protMin: Int,
    val protMax: Int,
    val waterTargetMl: Int,
)

/** Fotografia della giornata, indipendente da Android: facile da testare. */
data class DayStatus(
    val kcal: Double,
    val prot: Double,
    val unknownCount: Int,
    val countByMoment: Map<Moment, Int>,
    val waterMl: Int,
    val trained: Boolean = false, // almeno una serie registrata quel giorno
    val quality: QualityStatus? = null,
)

/** text = il consiglio; why = spiegazione del calcolo che l'ha generato, coi numeri veri. */
data class Advice(val priority: Int, val text: String, val why: String = "")

/**
 * Consigli deterministici basati sul piano: confronta il mangiato col ritmo
 * atteso della giornata e propone cibi CONCRETI con grammature, prese dalle
 * alternative del piano stesso. Nessuna AI, nessuna rete. Ogni consiglio
 * porta con sé il proprio "perché", coi numeri usati nel calcolo.
 */
object AdvisorEngine {

    private data class Window(val startMin: Int, val endMin: Int, val share: Double)

    // Fasce orarie dei momenti e quota attesa di kcal (somma = 1.0).
    private val windows: Map<Moment, Window> = mapOf(
        Moment.COLAZIONE to Window(390, 570, 0.20),          // 6:30–9:30
        Moment.META_MATTINA to Window(570, 705, 0.08),       // 9:30–11:45
        Moment.PRANZO to Window(705, 870, 0.28),             // 11:45–14:30
        Moment.POST_ALLENAMENTO to Window(870, 1110, 0.12),  // 14:30–18:30
        Moment.CENA to Window(1110, 1275, 0.25),             // 18:30–21:15
        Moment.PRE_NANNA to Window(1275, 1439, 0.07),        // 21:15–24:00
    )

    private const val SHARES_DESC =
        "colazione 20%, metà mattina 8%, pranzo 28%, dopo allenamento 12%, cena 25%, prima di dormire 7%"

    private data class Sugg(val text: String, val kcal: Int, val prot: Int, val protDense: Boolean = false)

    // Alternative concrete del piano, con kcal/proteine stimate.
    private val catalog: Map<Moment, List<Sugg>> = mapOf(
        Moment.COLAZIONE to listOf(
            Sugg("latte 300 ml + 5 fette biscottate con miele + 3 uova", 720, 34, protDense = true),
            Sugg("yogurt greco 170 g + cereali 60 g + frutta secca 30 g", 570, 22, protDense = true),
        ),
        Moment.META_MATTINA to listOf(
            Sugg("un frutto (150 g) + frutta secca 30 g", 270, 7),
            Sugg("yogurt bianco 125 g + 4-5 taralli", 280, 8),
            Sugg("panino piccolo (pane 80 g) con asiago 40 g", 360, 14, protDense = true),
        ),
        Moment.PRANZO to listOf(
            Sugg("pasta 140 g con cubetti di cotto 50 g + 8 taralli", 950, 35, protDense = true),
            Sugg("pasta 130 g con asiago 100 g + pane 50 g", 960, 42, protDense = true),
            Sugg("pasta 150 g + carne 180 g + pane 80 g", 1000, 50, protDense = true),
        ),
        Moment.POST_ALLENAMENTO to listOf(
            Sugg("panino (pane 100 g) con cotto 50 g + frutta secca 30 g", 530, 21, protDense = true),
            Sugg("yogurt greco 170 g + un frutto + 4 taralli", 430, 19, protDense = true),
        ),
        Moment.CENA to listOf(
            Sugg("carne 200 g + patate 300 g + pane 100 g", 780, 48, protDense = true),
            Sugg("frittata di 4 uova + asiago 50 g + pane 100 g", 800, 45, protDense = true),
            Sugg("pesce 200 g + patate 300 g + pane 100 g", 730, 44, protDense = true),
        ),
        Moment.PRE_NANNA to listOf(
            Sugg("yogurt greco 200 g", 190, 18, protDense = true),
            Sugg("un bicchiere grande di latte (300 ml)", 190, 10, protDense = true),
            Sugg("frutta secca 30 g", 180, 6),
            Sugg("yogurt greco 200 g + frutta secca 30 g", 370, 24, protDense = true),
        ),
    )

    fun advise(now: LocalDateTime, day: DayStatus, yesterday: DayStatus?, t: Targets): List<Advice> {
        val out = mutableListOf<Advice>()
        val nowMin = now.hour * 60 + now.minute
        // Giorno di allenamento: i consigli puntano alla metà alta del range,
        // senza toccare il target visibile (3300–3700 resta fermo).
        val trainDelta = if (day.trained) 150 else 0
        val trainTag = if (day.trained) " (giorno di allenamento)" else ""
        val midKcal = (t.kcalMin + t.kcalMax) / 2.0 + trainDelta
        val expectedShare = windows.values.sumOf { w -> progress(w, nowMin) * w.share }
        val expectedKcal = midKcal * expectedShare
        val kcalGap = expectedKcal - day.kcal
        val isEvening = nowMin >= 21 * 60

        // Bilancio serale: il consiglio più importante della giornata.
        if (isEvening) {
            val eveningFloor = t.kcalMin + trainDelta
            val toMin = eveningFloor - day.kcal
            val protGap = t.protMin - day.prot
            when {
                toMin > 150 -> {
                    val s = pick(Moment.PRE_NANNA, toMin, needProtein = protGap > 10)
                    val protPart = if (protGap > 10) " e ~${protGap.roundToInt()} g di proteine" else ""
                    val why = buildString {
                        append("Bilancio serale (dopo le 21:00). Minimo del giorno: ${grp(t.kcalMin)} kcal")
                        if (day.trained) append(" +150 per l'allenamento registrato oggi → ${grp(eveningFloor)}")
                        append(". Finora: ${grp(day.kcal.roundToInt())} kcal e ${day.prot.roundToInt()} g di proteine (minimo ${t.protMin} g). ")
                        append("Mancano ~${round50(toMin)} kcal")
                        if (protGap > 10) append(" e ~${protGap.roundToInt()} g di proteine")
                        append(": propongo l'opzione «prima di dormire» del piano più vicina al buco")
                        if (protGap > 10) append(", scelta tra quelle ricche di proteine")
                        append(".")
                    }
                    out += Advice(0, "Ti mancano ~${round50(toMin)} kcal$protPart per il minimo di ${grp(eveningFloor)}$trainTag: prima di dormire ${s.text} (≈ ${s.kcal} kcal, ${s.prot} g proteine).", why)
                }
                day.kcal <= t.kcalMax + 250 -> {
                    val protGapNow = t.protMin - day.prot
                    if (protGapNow > 10) {
                        out += Advice(
                            0,
                            "Calorie ok (${grp(day.kcal.roundToInt())}), ma proteine a ${day.prot.roundToInt()} g su ${t.protMin}: prima di dormire yogurt greco 200 g (+18 g di proteine).",
                            "Calorie nel range utile (${grp(day.kcal.roundToInt())} su ${grp(t.kcalMin)}–${grp(t.kcalMax)}), ma proteine a ${day.prot.roundToInt()} g contro il minimo di ${t.protMin}: per la crescita muscolare sono la parte non negoziabile del piano. Lo yogurt greco è l'opzione serale con più proteine per caloria.",
                        )
                    } else {
                        out += Advice(
                            0,
                            "Giornata a target: ${grp(day.kcal.roundToInt())} kcal e ${day.prot.roundToInt()} g di proteine. Continua così.",
                            "Sei a ${grp(day.kcal.roundToInt())} kcal — dentro il minimo utile (${grp(eveningFloor)}${if (day.trained) ", alzato di 150 per l'allenamento" else ""}, con tolleranza di 150) e sotto il massimo ${grp(t.kcalMax)} (+250 di margine) — con ${day.prot.roundToInt()} g di proteine ≥ ${t.protMin}. Nessun intervento: come dice il piano, conta la costanza.",
                        )
                    }
                }
                else -> {
                    out += Advice(
                        0,
                        "Oggi ${grp(day.kcal.roundToInt())} kcal, sopra il range: in fase di massa non è un dramma, domani torna nel ${grp(t.kcalMin)}–${grp(t.kcalMax)}.",
                        "Registrate ${grp(day.kcal.roundToInt())} kcal, oltre il massimo ${grp(t.kcalMax)} più il margine di 250. In surplus un giorno alto non rovina nulla — conta la media della settimana — ma meglio non farlo diventare l'abitudine.",
                    )
                }
            }
        }

        // Mattina: recupero rispetto a ieri.
        if (nowMin < 11 * 60 && yesterday != null && yesterday.kcal > 0 && yesterday.kcal < t.kcalMin - 200) {
            val s = catalog.getValue(Moment.COLAZIONE).first()
            out += Advice(
                1,
                "Ieri ~${grp(yesterday.kcal.roundToInt())} kcal, sotto il minimo: oggi parti pieno. Colazione: ${s.text} (≈ ${s.kcal} kcal).",
                "Ieri hai chiuso a ${grp(yesterday.kcal.roundToInt())} kcal contro un minimo di ${grp(t.kcalMin)} (l'avviso scatta quando manca più di 200). Il modo più semplice di recuperare è partire con la colazione più completa del piano, senza stravolgere il resto della giornata.",
            )
        }

        // Ritmo della giornata: indietro rispetto all'atteso → proposta concreta ORA.
        if (!isEvening && kcalGap > 350) {
            val target = nextActionableMoment(nowMin, day)
            if (target != null) {
                val protBehind = day.prot < t.protMin * expectedShare - 20
                val s = pick(target, kcalGap, needProtein = protBehind)
                out += Advice(
                    1,
                    "Sei ~${round50(kcalGap)} kcal sotto il ritmo della giornata$trainTag. ${windowLabel(target)}: ${s.text} (≈ ${s.kcal} kcal, ${s.prot} g proteine).",
                    "Alle ${hm(nowMin)} la quota attesa è ~${grp(expectedKcal.roundToInt())} kcal: fette dei momenti già trascorsi ($SHARES_DESC) applicate al punto medio del range (${grp(midKcal.roundToInt())}${if (day.trained) ", alzato di 150 per l'allenamento registrato" else ""}). Registrate ${grp(day.kcal.roundToInt())} kcal → ritardo ~${round50(kcalGap)}, sopra la soglia di avviso (350). Propongo il prossimo momento libero con l'opzione del piano più vicina al buco.",
                )
            }
        }

        // Conferma quando il ritmo regge: il silenzio non è un'informazione.
        if (!isEvening && day.kcal > 0 && kcalGap <= 350) {
            out += Advice(
                5,
                "In linea col ritmo: ${grp(day.kcal.roundToInt())} kcal su ~${grp(expectedKcal.roundToInt())} attese a quest'ora.",
                "Alle ${hm(nowMin)} la quota attesa è ~${grp(expectedKcal.roundToInt())} kcal: fette dei momenti già trascorsi ($SHARES_DESC) sul punto medio del range (${grp(midKcal.roundToInt())}${if (day.trained) ", alzato di 150 per l'allenamento registrato" else ""}). Hai registrato ${grp(day.kcal.roundToInt())} kcal: lo scarto è sotto la soglia di avviso (350), nessuna correzione necessaria.",
            )
        }

        // Momento saltato (finestra chiusa, nessuna voce): consiglio per domani.
        val missed = windows.entries
            .filter { (m, w) -> w.endMin <= nowMin && (day.countByMoment[m] ?: 0) == 0 }
            .maxByOrNull { it.value.endMin }
        if (missed != null && day.kcal > 0) {
            val m = missed.key
            val w = windows.getValue(m)
            val s = pick(m, w.share * midKcal, needProtein = false)
            out += Advice(
                2,
                "Oggi niente ${missedName(m)}: domani prova ${s.text} (≈ ${s.kcal} kcal in più).",
                "La finestra di ${missedName(m)} (${windowStr(m)}) si è chiusa senza voci registrate. Nel piano quel momento vale ~${grp((w.share * midKcal).roundToInt())} kcal (${(w.share * 100).roundToInt()}% della giornata): il consiglio per domani serve a non perdere quella quota.",
            )
        }

        // Proteine indietro anche se le kcal reggono.
        if (!isEvening && kcalGap <= 350) {
            val expectedProt = t.protMin * expectedShare
            if (expectedShare > 0.3 && day.prot < expectedProt - 25) {
                out += Advice(
                    3,
                    "Proteine indietro: ${day.prot.roundToInt()} g contro ~${expectedProt.roundToInt()} attesi a quest'ora. Al prossimo pasto scegli uova, yogurt greco, carne o asiago.",
                    "Proteine attese a quest'ora: ~${expectedProt.roundToInt()} g (minimo giornaliero ${t.protMin} g × ${(expectedShare * 100).roundToInt()}% di giornata trascorsa). Registrati ${day.prot.roundToInt()} g: sotto di oltre 25. Le calorie invece sono in linea col ritmo, quindi basta orientare il prossimo pasto su fonti proteiche.",
                )
            }
        }

        // Acqua.
        val waterL = day.waterMl / 1000.0
        val targetL = t.waterTargetMl / 1000.0
        if (nowMin >= 15 * 60 && day.waterMl < t.waterTargetMl / 2) {
            out += Advice(
                4,
                "Acqua: sei a ${lt(waterL)} su ${lt(targetL)} L. Tieni la borraccia a portata.",
                "Regola: metà del target entro metà pomeriggio. Target ${lt(targetL)} L → soglia ${lt(t.waterTargetMl / 2000.0)} L alle 15:00; registrati ${lt(waterL)} L.",
            )
        } else if (isEvening && day.waterMl < (t.waterTargetMl * 0.8).toInt()) {
            out += Advice(
                4,
                "Acqua a ${lt(waterL)} L: bevine ancora un po' prima di dormire.",
                "Regola serale: almeno l'80% del target entro le 21:00 (${lt(t.waterTargetMl * 0.8 / 1000.0)} L su ${lt(targetL)}); registrati ${lt(waterL)} L.",
            )
        }

        // Qualità del cibo: SOLO in positivo, mai un rimprovero (regola di progetto).
        out += qualityAdvice(nowMin, day)

        // Voci senza valori: totali sottostimati, dillo chiaro.
        if (day.unknownCount > 0) {
            val plural = if (day.unknownCount == 1) "voce" else "voci"
            out += Advice(
                9,
                "${day.unknownCount} $plural senza valori nutrizionali: i totali reali sono più alti di quelli mostrati.",
                "${day.unknownCount} $plural della giornata sono state inserite senza kcal/proteine: i totali mostrati sono quindi un minimo garantito, non il valore reale. Per contarle, aggiungi i valori all'alimento in dispensa (bastano kcal e proteine per 100 g).",
            )
        }

        return out.sortedBy { it.priority }
    }

    /**
     * Segnali sulla qualità del cibo. Regola non negoziabile: si suggerisce
     * cosa AGGIUNGERE, mai cosa evitare, e nessun alimento viene chiamato
     * "cattivo". Se il dato non c'è, l'app tace invece di indovinare.
     */
    private fun qualityAdvice(nowMin: Int, day: DayStatus): List<Advice> {
        val q = day.quality ?: return emptyList()
        if (day.kcal <= 0) return emptyList()
        val out = mutableListOf<Advice>()
        val isEvening = nowMin >= 21 * 60
        val porzioni = q.porzioniFruttaVerdura
        val quota = q.quotaUltraProcessati

        // Frutta e verdura: dal pomeriggio, come traguardo da raggiungere.
        if (nowMin >= 15 * 60 && porzioni < 3) {
            val mancanti = kotlin.math.ceil(3 - porzioni).toInt().coerceAtLeast(1)
            val quante = if (porzioni <= 0.0) "nessuna porzione" else "${fmtP(porzioni)} porzioni"
            out += Advice(
                6,
                "Frutta e verdura: $quante oggi. Aggiungine $mancanti — un frutto a metà mattina e verdura a cena bastano.",
                "Conto una porzione ogni ${DietQuality.PORZIONE_G.toInt()} g di frutta o verdura registrata: oggi ${fmtP(porzioni)}. " +
                    "L'obiettivo è 3-5 al giorno. In un surplus contano più del solito: mangi tanto, e fibra e micronutrienti " +
                    "sono ciò che tiene in ordine digestione e recupero mentre il volume di cibo è alto. " +
                    "È un'aggiunta al piano, non toglie nulla alle calorie o alle proteine.",
            )
        }

        // Grado di lavorazione: solo la sera, solo se il campione è affidabile.
        if (isEvening && quota != null && quota > 0.5) {
            out += Advice(
                6,
                "Circa il ${(quota * 100).roundToInt()}% delle calorie di oggi viene da prodotti industriali. Il piano regge lo stesso: domani prova a sostituirne uno con cibo fresco — uova, riso, carne, frutta secca.",
                "Calcolato solo sulle voci di cui conosco il grado di lavorazione (${grp(q.kcalUltraProcessati.roundToInt())} kcal su " +
                    "${grp(q.kcalConGrado.roundToInt())}, cioè il ${(q.copertura * 100).roundToInt()}% della giornata). " +
                    "Uso la scala NOVA, che misura quanto un cibo è trasformato industrialmente — non quanto è calorico: " +
                    "olio, frutta secca, latte e carne restano fuori, perché in massa servono. " +
                    "Non è un voto sulla giornata: le calorie le hai comunque prese, questo riguarda solo con che cosa.",
            )
        }

        // Fibra: la sera, se la giornata è già consistente.
        if (isEvening && day.kcal >= 1500 && q.fibraG in 0.1..19.9) {
            out += Advice(
                7,
                "Fibra: ${fmt1q(q.fibraG)} g oggi. Con una dieta da ${grp(day.kcal.roundToInt())} kcal punta a ${DietQuality.FIBRA_TARGET.toInt()} g: pane e pasta integrali, verdura e frutta secca la coprono senza sforzo.",
                "Registrati ${fmt1q(q.fibraG)} g di fibra contro un obiettivo di ~${DietQuality.FIBRA_TARGET.toInt()} g. " +
                    "Con introiti alti la fibra è il marcatore più semplice di una dieta fatta di cibo vero, e aiuta la digestione " +
                    "di pasti abbondanti. Conto solo gli alimenti che riportano il valore: quelli senza dato non li indovino.",
            )
        }

        // Conferma quando la qualità regge: il rinforzo positivo è parte del metodo.
        if (isEvening && porzioni >= 3 && (quota == null || quota <= 0.35)) {
            out += Advice(
                6,
                "Qualità della giornata buona: ${fmtP(porzioni)} porzioni di frutta e verdura e poca roba industriale.",
                "Frutta e verdura a ${fmtP(porzioni)} porzioni (obiettivo 3-5)" +
                    (quota?.let { ", e solo il ${(it * 100).roundToInt()}% delle calorie da prodotti industriali" } ?: "") +
                    ". È esattamente la differenza tra mettere su peso e mettere su peso buono.",
            )
        }

        return out
    }

    private fun fmtP(v: Double): String {
        val r = kotlin.math.round(v * 10) / 10
        return if (r == kotlin.math.floor(r)) r.toInt().toString() else r.toString().replace('.', ',')
    }

    private fun fmt1q(v: Double): String = fmtP(v)

    // ---- helper ----

    private fun progress(w: Window, nowMin: Int): Double = when {
        nowMin >= w.endMin -> 1.0
        nowMin <= w.startMin -> 0.0
        else -> (nowMin - w.startMin).toDouble() / (w.endMin - w.startMin)
    }

    private fun nextActionableMoment(nowMin: Int, day: DayStatus): Moment? {
        val open = windows.entries.filter { it.value.endMin > nowMin }.sortedBy { it.value.startMin }
        return open.firstOrNull { (m, _) -> (day.countByMoment[m] ?: 0) == 0 }?.key
            ?: open.firstOrNull()?.key
    }

    private fun pick(moment: Moment, kcalGap: Double, needProtein: Boolean): Sugg {
        val options = catalog.getValue(moment)
        val pool = if (needProtein) options.filter { it.protDense }.ifEmpty { options } else options
        val best = pool.minByOrNull { abs(it.kcal - kcalGap) } ?: pool.first()
        return if (kcalGap in 1.0..best.kcal * 0.6) {
            best.copy(text = best.text + " (va bene anche mezza porzione)")
        } else best
    }

    private fun missedName(m: Moment): String = when (m) {
        Moment.COLAZIONE -> "colazione"
        Moment.META_MATTINA -> "spuntino di metà mattina"
        Moment.PRANZO -> "pranzo"
        Moment.POST_ALLENAMENTO -> "spuntino post allenamento"
        Moment.CENA -> "cena"
        Moment.PRE_NANNA -> "spuntino serale"
        Moment.BEVANDE -> "bevande"
    }

    private fun windowLabel(m: Moment): String = when (m) {
        Moment.META_MATTINA -> "a metà mattina"
        Moment.POST_ALLENAMENTO -> "dopo l'allenamento"
        Moment.PRE_NANNA -> "prima di dormire"
        Moment.COLAZIONE -> "a colazione"
        Moment.PRANZO -> "a pranzo"
        Moment.CENA -> "a cena"
        Moment.BEVANDE -> ""
    }

    private fun hm(min: Int): String = "${min / 60}:${(min % 60).toString().padStart(2, '0')}"

    private fun windowStr(m: Moment): String {
        val w = windows.getValue(m)
        return "${hm(w.startMin)}–${hm(w.endMin)}"
    }

    private fun round50(v: Double): Int = (v / 50.0).roundToInt() * 50

    /** Raggruppamento migliaia all'italiana (3.450), senza dipendere dal Locale. */
    fun grp(n: Int): String {
        val s = n.toString()
        if (s.length <= 3) return s
        return s.reversed().chunked(3).joinToString(".").reversed()
    }

    /** Litri con una cifra decimale e virgola italiana. */
    fun lt(v: Double): String {
        val r = kotlin.math.round(v * 10.0) / 10.0
        return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString().replace('.', ',')
    }
}
