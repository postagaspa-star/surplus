package it.gaspa.surplus.logic

import it.gaspa.surplus.data.Food

/**
 * Righe della scheda prodotto, nello stile di Yuka ma **tarate sull'obiettivo
 * massa**: qui un cibo calorico è un pregio, non un difetto. Per lo stesso
 * motivo non esiste un punteggio unico 0-100: premierebbe l'insalata e
 * punirebbe olio e frutta secca, che in un surplus servono.
 *
 * Livelli: BUONO = verde, NEUTRO = grigio. Non esiste un livello "cattivo":
 * è la stessa regola dei consigli, nessun alimento viene bollato.
 */
enum class Livello { BUONO, NEUTRO }

data class RigaScheda(
    val etichetta: String,
    val giudizio: String,
    val valore: String,
    val livello: Livello,
)

object SchedaNutrizionale {

    /** Mostra solo ciò che sappiamo davvero: niente dato, niente riga. */
    fun righe(food: Food): List<RigaScheda> {
        val out = mutableListOf<RigaScheda>()
        val unit = if (food.isDrink) "ml" else "g"

        food.kcal100?.let { k ->
            out += RigaScheda(
                "Energia",
                when {
                    k >= 300 -> "Molto calorico: comodo per il surplus"
                    k >= 150 -> "Buon apporto di energia"
                    else -> "Leggero: ne serve una porzione generosa"
                },
                "${fmt(k)} kcal",
                if (k >= 150) Livello.BUONO else Livello.NEUTRO,
            )
        }
        food.prot100?.let { p ->
            out += RigaScheda(
                "Proteine",
                when {
                    p >= 15 -> "Ottima fonte di proteine"
                    p >= 8 -> "Buon contenuto di proteine"
                    else -> "Poche proteine: abbinalo a una fonte proteica"
                },
                "${fmt(p)} g",
                if (p >= 8) Livello.BUONO else Livello.NEUTRO,
            )
        }
        food.carb100?.let { c ->
            out += RigaScheda("Carboidrati", "Energia per l'allenamento", "${fmt(c)} g", Livello.NEUTRO)
        }
        food.fibra100?.let { f ->
            out += RigaScheda(
                "Fibre",
                when {
                    f >= 6 -> "Ricco di fibre"
                    f >= 3 -> "Un po' di fibre"
                    else -> "Poche fibre"
                },
                "${fmt(f)} g",
                if (f >= 3) Livello.BUONO else Livello.NEUTRO,
            )
        }
        food.grassi100?.let { g ->
            out += RigaScheda(
                "Grassi",
                if (g >= 20) "Grassi abbondanti: molte calorie in poco volume" else "Grassi moderati",
                "${fmt(g)} g",
                Livello.NEUTRO,
            )
        }
        food.grassiSaturi100?.let { s ->
            out += RigaScheda(
                "Grassi saturi",
                if (s <= 1.5) "Povero di grassi saturi" else "Grassi saturi presenti",
                "${fmt(s)} g",
                if (s <= 1.5) Livello.BUONO else Livello.NEUTRO,
            )
        }
        food.zuccheri100?.let { z ->
            out += RigaScheda(
                "Zuccheri",
                if (z <= 5) "Povero di zuccheri" else "Zuccheri presenti",
                "${fmt(z)} g",
                if (z <= 5) Livello.BUONO else Livello.NEUTRO,
            )
        }
        food.sale100?.let { s ->
            out += RigaScheda(
                "Sale",
                if (s <= 0.3) "Povero di sale" else "Sale presente",
                "${fmt(s)} g",
                if (s <= 0.3) Livello.BUONO else Livello.NEUTRO,
            )
        }
        food.nova?.let { n ->
            out += RigaScheda(
                "Lavorazione",
                when (n) {
                    1 -> "Cibo non lavorato"
                    2 -> "Ingrediente di cucina"
                    3 -> "Cibo lavorato"
                    else -> "Prodotto industriale"
                },
                "NOVA $n",
                if (n <= 2) Livello.BUONO else Livello.NEUTRO,
            )
        }

        return out.also {
            // L'unità serve solo a chi legge la riga dell'energia: qui non aggiunge nulla.
            if (unit.isEmpty()) Unit
        }
    }

    /** Riepilogo di quanto questa voce porta davvero nel diario. */
    fun contributo(food: Food, grams: Double): String {
        val unit = if (food.isDrink) "ml" else "g"
        val kcal = food.kcal100?.let { grams * it / 100 }
        val prot = food.prot100?.let { grams * it / 100 }
        return buildString {
            append("${fmt(grams)} $unit")
            if (kcal != null) append(" = ${fmt(kcal)} kcal")
            if (prot != null) append(", ${fmt(prot)} g di proteine")
        }
    }

    private fun fmt(v: Double): String {
        val r = kotlin.math.round(v * 10) / 10
        return if (r == kotlin.math.floor(r)) r.toInt().toString()
        else r.toString().replace('.', ',')
    }
}
