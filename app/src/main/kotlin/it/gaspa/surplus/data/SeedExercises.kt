package it.gaspa.surplus.data

/**
 * Esercizi di partenza: scheda upper body con manubri regolabili.
 * Dalla 2.5.0 servono solo a riempire l'archivio e le due schede iniziali:
 * poi sono dell'utente, modificabili come quelli che crea lui.
 * Addominali e polpacci sono validi in entrambe le sessioni, come da scheda.
 */
object SeedExercises {

    private fun e(
        id: String, nome: String, gruppo: String,
        spinta: Boolean, trazione: Boolean,
        serie: String? = null, repMin: Int? = null, repMax: Int? = null,
        nota: String? = null,
    ) = Exercise(id, nome, gruppo, spinta, trazione, serie, repMin, repMax, nota, custom = false)

    val all: List<Exercise> = listOf(
        // ---- Sessione Spinta ----
        e("shoulder-press", "Shoulder Press", "Spalle", spinta = true, trazione = false, serie = "3-4", repMin = 8, repMax = 12),
        e("alzate-laterali", "Alzate Laterali", "Spalle", spinta = true, trazione = false, serie = "3", repMin = 12, repMax = 20),
        e("french-press", "French Press", "Tricipiti", spinta = true, trazione = false, serie = "3", repMin = 10, repMax = 15, nota = "estensione sopra la testa"),
        e("diamond-pushup", "Diamond Push-up", "Tricipiti", spinta = true, trazione = false, nota = "vicino al cedimento"),
        e("floor-press", "Floor Press con manubri", "Petto", spinta = true, trazione = false, serie = "3", repMin = 8, repMax = 12),

        // ---- Sessione Trazione ----
        e("rematore-un-braccio", "Rematore a un braccio", "Schiena", spinta = false, trazione = true, serie = "3", repMin = 10, repMax = 12, nota = "per lato"),
        e("superman-row", "Superman Row", "Schiena", spinta = false, trazione = true, serie = "2-3", repMin = 12, repMax = 15, nota = "nuotata a terra"),
        e("curl-manubri", "Curl con manubri", "Bicipiti", spinta = false, trazione = true, serie = "3", repMin = 8, repMax = 12),
        e("hammer-curl", "Hammer Curl", "Bicipiti", spinta = false, trazione = true, serie = "2-3", repMin = 10, repMax = 15),
        e("reverse-fly", "Reverse Fly", "Salute spalle", spinta = false, trazione = true, serie = "2-3", repMin = 12, repMax = 15),
        e("face-pull", "Face Pull", "Salute spalle", spinta = false, trazione = true, serie = "2-3", repMin = 12, repMax = 15, nota = "con elastico"),

        // ---- Valide in entrambe le sessioni (come da scheda) ----
        e("leg-raises", "Leg Raises a terra", "Addominali", spinta = true, trazione = true, serie = "3", repMin = 12, repMax = 20),
        e("crunch", "Crunch", "Addominali", spinta = true, trazione = true, serie = "3", repMin = 15, repMax = 20, nota = "anche con manubrio leggero sul petto"),
        e("polpacci-una-gamba", "Alzate polpacci a una gamba", "Polpacci", spinta = true, trazione = true, serie = "3", repMin = 15, repMax = 25, nota = "vicino al cedimento"),
    )
}
