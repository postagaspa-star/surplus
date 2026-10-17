package it.gaspa.surplus.data

/**
 * Quale copia dei dati usare all'avvio. Tenuta separata dai file perché è la
 * parte che decide se il diario si salva o si perde: così si prova nei test.
 *
 * Ordine: il file principale; se manca o è rovinato (vuoto, troncato), l'ultima
 * copia buona presa all'avvio precedente; poi il file del vecchio formato a
 * utente unico, anche se indietro nel tempo; solo alla fine si riparte da zero.
 */
object SceltaDati {

    enum class Fonte { PRINCIPALE, COPIA_PRECEDENTE, VECCHIO_FORMATO, NUOVI }

    data class Esito(
        val dati: AppData,
        val fonte: Fonte,
        /** Il principale c'era ma non si leggeva: va messo da parte, non sovrascritto. */
        val principaleRovinato: Boolean,
    )

    /** I testi sono null quando il file non esiste. */
    fun scegli(
        principale: String?,
        copiaPrecedente: String?,
        vecchioFormato: String?,
        decodifica: (String) -> AppData,
        nuovi: () -> AppData,
    ): Esito {
        fun prova(testo: String?): AppData? =
            testo?.takeIf { it.isNotBlank() }?.let { runCatching { decodifica(it) }.getOrNull() }

        prova(principale)?.let { return Esito(it, Fonte.PRINCIPALE, principaleRovinato = false) }
        val rovinato = principale != null
        prova(copiaPrecedente)?.let { return Esito(it, Fonte.COPIA_PRECEDENTE, rovinato) }
        // Anche un file vecchio è meglio di un diario vuoto.
        prova(vecchioFormato)?.let { return Esito(it, Fonte.VECCHIO_FORMATO, rovinato) }
        return Esito(nuovi(), Fonte.NUOVI, rovinato)
    }
}
