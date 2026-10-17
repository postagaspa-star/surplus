package it.gaspa.surplus.logic

import java.text.Normalizer

/**
 * Compila da solo il modulo di un esercizio a partire dal nome.
 *
 * Prima strada, sempre disponibile e gratuita: regole sulle parole della
 * palestra (italiano e inglese), tarate sulla scheda di partenza. Nessun archivio
 * gratuito ha serie e ripetizioni, perché sono scelte di programmazione e non
 * dati dell'esercizio: qui si usano i range classici per quel tipo di movimento.
 * Se il nome non si riconosce, l'app può chiedere all'AI con la chiave dell'utente.
 */
object CompilaEsercizio {

    data class Proposta(
        val gruppo: String,
        val serie: String,
        val repMin: Int,
        val repMax: Int,
        /** null = a corpo libero, oppure salto non noto: il campo resta vuoto (vale 1 kg). */
        val incrementoKg: Double?,
        val nota: String?,
    )

    private class Regola(
        schema: String,
        val gruppo: String,
        val serie: String,
        val repMin: Int,
        val repMax: Int,
        val corpoLibero: Boolean = false,
        val unilaterale: Boolean = false,
        val nota: String? = null,
    ) {
        val parole = Regex(schema)
    }

    // L'ordine conta: le regole specifiche stanno prima di quelle generiche
    // ("leg curl" prima di "curl", "alzate laterali" prima di "spalle").
    private val REGOLE = listOf(
        // Addominali
        Regola("\\bplanc?k\\w*", "Addominali", "3", 30, 60, corpoLibero = true,
            nota = "a tempo: segna i secondi come ripetizioni"),
        Regola(
            "\\bcrunch\\w*|\\baddominal\\w*|\\bsit ?ups?\\b|\\bleg raises?\\b|" +
                "\\b(alzate|sollevament\\w*|sollevare) (le |delle )?gambe\\b|\\brussian twist|\\bab wheel|" +
                "\\brollout|\\bhollow|\\bmountain climbers?|\\bbicicletta\\b|\\bv ?ups?\\b|\\btoes? to bar",
            "Addominali", "3", 12, 20, corpoLibero = true,
        ),
        // Polpacci
        Regola("\\bpolpacc\\w*|\\bcalf\\b|\\bcalves\\b", "Polpacci", "3", 15, 25),
        // Salute delle spalle
        Regola("\\bface ?pulls?\\b", "Salute spalle", "2-3", 12, 15),
        Regola(
            "\\breverse fly\\w*|\\balzate posteriori|\\bcroci inverse|\\brear delt|\\baperture posteriori|" +
                "\\breverse butterfly|\\bbutterfly invers\\w*|\\bpull ?aparts?\\b",
            "Salute spalle", "2-3", 12, 15,
        ),
        Regola("\\brotazion\\w* estern\\w*|\\bexternal rotations?", "Salute spalle", "2-3", 12, 20),
        // Spalle
        Regola("\\balzate laterali|\\blateral raises?", "Spalle", "3", 12, 20),
        Regola("\\balzate frontali|\\bfront raises?", "Spalle", "3", 10, 15),
        Regola("\\btirate al mento|\\bupright rows?", "Spalle", "3", 10, 12),
        Regola(
            "\\bmilitary|\\blento avanti|\\bshoulder press|\\boverhead press|\\barnold|" +
                "\\bspinte? (sopra la testa|in alto)|\\bpush press",
            "Spalle", "3-4", 8, 12,
        ),
        // Tricipiti (prima dei piegamenti e delle distensioni generiche)
        Regola("\\bdiamond|\\bpiegament\\w* strett\\w*|\\bclose ?grip push", "Tricipiti", "3", 8, 15,
            corpoLibero = true),
        Regola("\\bdips?\\b", "Tricipiti", "3", 8, 12, corpoLibero = true),
        Regola(
            "\\bfrench press|\\bskull ?crushers?|\\bestension\\w* (dei |per i )?tricipit\\w*|" +
                "\\bestension\\w* sopra la testa|\\boverhead (triceps )?extensions?|\\bkick ?backs?|" +
                "\\bpush ?downs?|\\bpressdowns?|\\btricipit\\w*|\\btriceps",
            "Tricipiti", "3", 10, 15,
        ),
        // Avambracci (prima del curl)
        Regola("\\bcurl (dei |per i |ai )?polsi|\\bwrist curls?|\\bavambracc\\w*|\\bforearms?", "Avambracci",
            "3", 12, 20),
        // Gambe e glutei (prima del curl: leg curl)
        Regola("\\bleg curls?|\\bcurl (dei |per i )?femorali|\\bfemoral\\w*|\\bnordic", "Gambe", "3", 10, 15),
        Regola("\\bleg extensions?|\\bestension\\w* (delle |per le )?gambe|\\bquadricip\\w*", "Gambe",
            "3", 12, 15),
        Regola("\\bleg press|\\bpressa\\b", "Gambe", "3-4", 10, 15),
        Regola("\\bstacc\\w* rumen\\w*|\\brdl\\b|\\bromanian|\\bstiff ?legs?|\\bgambe tese", "Gambe",
            "3", 8, 12),
        Regola("\\bstacc\\w*|\\bdeadlifts?", "Gambe", "3-4", 6, 10),
        Regola(
            "\\bhip thrusts?|\\bponte (dei |per i )?glutei|\\bglute bridge|\\bglutei|\\bglutes?\\b|" +
                "\\bkettlebell swings?|\\bswings?\\b",
            "Glutei", "3", 8, 15,
        ),
        Regola("\\babduzion\\w*|\\badduzion\\w*|\\babduct\\w*|\\badduct\\w*", "Gambe", "3", 12, 20),
        Regola(
            "\\baffond\\w*|\\blunges?\\b|\\bsplit squats?|\\bbulgar\\w*|\\bstep ?ups?\\b|" +
                "\\bsalit\\w* (sul|sulla|sullo) (gradino|panca|step)",
            "Gambe", "3", 8, 12, unilaterale = true,
        ),
        Regola("\\bsquat\\w*|\\baccosciat\\w*|\\bgoblet", "Gambe", "3-4", 8, 12),
        // Petto
        Regola("\\bcroci\\b|\\bfly(e|es|s)?\\b|\\bpec ?deck|\\bbutterfly|\\baperture\\b", "Petto", "3", 10, 15),
        Regola("\\bpiegament\\w*|\\bflession\\w*|\\bpush ?ups?\\b", "Petto", "3", 8, 15, corpoLibero = true),
        Regola("\\bpanca\\b|\\bbench|\\bdistension\\w*|\\bchest press|\\bfloor press", "Petto", "3-4", 8, 12),
        // Schiena
        Regola("\\btrazion\\w*|\\bpull ?ups?\\b|\\bchin ?ups?\\b", "Schiena", "3", 5, 10, corpoLibero = true),
        Regola("\\blat\\b|\\blat machine|\\bpull ?downs?\\b", "Schiena", "3-4", 8, 12),
        Regola(
            "\\bsuperman|\\bnuotat\\w*|\\biperestension\\w*|\\bhyperextensions?|\\bback extensions?|" +
                "\\blombari|\\bgood mornings?",
            "Schiena", "3", 12, 15,
        ),
        Regola("\\bpull ?overs?\\b", "Schiena", "3", 10, 15),
        Regola("\\bscrollat\\w*|\\bshrugs?\\b|\\btrapezi\\w*", "Trapezio", "3", 10, 15),
        Regola("\\bremator\\w*|\\brows?\\b|\\browing|\\bpulley\\b|\\bt ?bar\\b", "Schiena", "3", 8, 12),
        // Bicipiti
        Regola("\\bhammer|\\bmartello\\b", "Bicipiti", "3", 10, 15),
        Regola("\\bcurl\\w*|\\bbicipit\\w*|\\bbiceps", "Bicipiti", "3", 8, 12),
        // Solo il muscolo, senza il movimento: range medi.
        Regola("\\bspall\\w*|\\bdeltoid\\w*|\\bshoulders?\\b", "Spalle", "3", 10, 15),
        Regola("\\bpetto\\b|\\bpettoral\\w*|\\bchest\\b", "Petto", "3", 8, 12),
        Regola("\\bschiena\\b|\\bdorsal\\w*", "Schiena", "3", 8, 12),
        Regola("\\bgamb[ae]\\b|\\blegs?\\b|\\bcosc[ei]\\b", "Gambe", "3", 8, 12),
    )

    private val UNILATERALE = Regex("\\bun braccio\\b|\\buna gamba\\b|\\bsingol\\w*|\\bunilateral\\w*|\\bsingle\\b|\\bone arm")
    private val ZAVORRA = Regex("\\bzavorr\\w*|\\bcon (peso|disco|manubri\\w*|kettlebell)|\\bweighted")
    private val BILANCIERE = Regex("\\bbilancier\\w*|\\bbarbell|\\bbil\\b|\\bsmith")
    private val MACCHINA = Regex(
        "\\bcav[oi]\\b|\\bpulley\\b|\\bcables?\\b|\\bmacchin\\w*|\\bmachine|\\bleg press|\\bpressa\\b|" +
            "\\bpec ?deck|\\bleg (extension|curl)s?|\\bchest press|\\blat\\b",
    )

    /** Minuscolo, senza accenti e trattini: "Push-Up" e "push up" diventano uguali. */
    internal fun normalizza(nome: String): String =
        Normalizer.normalize(nome.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[-_/.,()]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /**
     * Proposta dalle regole, o null se il nome non si riconosce.
     * [saltoAbituale] è il salto che l'utente usa già sugli altri esercizi: per
     * i manubri dipende dai suoi dischi, non dall'esercizio.
     */
    fun daRegole(nome: String, saltoAbituale: Double? = null): Proposta? {
        val n = normalizza(nome)
        if (n.isBlank()) return null
        val r = REGOLE.firstOrNull { it.parole.containsMatchIn(n) } ?: return null
        val conPeso = !r.corpoLibero || ZAVORRA.containsMatchIn(n)
        val salto = when {
            !conPeso -> null
            BILANCIERE.containsMatchIn(n) -> 2.5
            MACCHINA.containsMatchIn(n) -> 5.0
            else -> saltoAbituale
        }
        val nota = when {
            r.nota != null -> r.nota
            r.unilaterale || UNILATERALE.containsMatchIn(n) -> "per lato"
            else -> null
        }
        return Proposta(r.gruppo, r.serie, r.repMin, r.repMax, salto, nota)
    }

    /** Il salto più usato tra gli esercizi dell'utente, se ne ha impostato qualcuno. */
    fun saltoAbituale(salti: List<Double?>): Double? =
        salti.filterNotNull().filter { it > 0 }
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key
}
