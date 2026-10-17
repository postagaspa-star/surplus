package it.gaspa.surplus.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.gaspa.surplus.data.AppData
import it.gaspa.surplus.data.ExSet
import it.gaspa.surplus.data.Exercise
import it.gaspa.surplus.data.ExerciseLog
import it.gaspa.surplus.data.Routine
import it.gaspa.surplus.data.WorkoutSession
import it.gaspa.surplus.logic.CompilaEsercizio
import it.gaspa.surplus.logic.WorkoutQueries
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/**
 * Allenamento in tre viste: le schede (si parte con un tocco), la sessione in
 * corso (un esercizio aperto alla volta) e la modifica di una scheda.
 */
@Composable
fun AllenamentoScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val data by vm.data.collectAsState()
    val openId by vm.openWorkoutId.collectAsState()
    // Scheda in modifica: una esistente, o una bozza nuova non ancora salvata.
    var bozza by remember { mutableStateOf<Routine?>(null) }
    val session = data.workouts.find { it.id == openId }
    val b = bozza
    when {
        session != null -> SessioneView(vm, data, session, modifier)
        b != null -> SchedaEditorView(vm, data, b, onChiudi = { bozza = null }, modifier = modifier)
        else -> ListaView(
            vm = vm,
            data = data,
            onModifica = { bozza = it },
            onNuova = { bozza = Routine(id = "r-${UUID.randomUUID()}", nome = "") },
            modifier = modifier,
        )
    }
}

// ---------- Schede e sessioni passate ----------

@Composable
private fun ListaView(
    vm: AppViewModel,
    data: AppData,
    onModifica: (Routine) -> Unit,
    onNuova: () -> Unit,
    modifier: Modifier,
) {
    val oggi = LocalDate.now().toString()
    val sessioni = data.workouts.sortedWith(
        compareByDescending<WorkoutSession> { it.date }.thenByDescending { it.startedAt }
    )
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Le tue schede", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        if (data.routines.isEmpty()) {
            item {
                Text(
                    "Nessuna scheda: creane una con gli esercizi che fai davvero.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(data.routines, key = { "scheda-${it.id}" }) { r ->
            val serieOggi = data.workouts
                .filter { it.date == oggi && it.routineId == r.id }
                .sumOf { s -> s.exercises.sumOf { it.sets.size } }
            val ultima = WorkoutQueries.ultimaVolta(data.workouts, r.id)
            SchedaCard(
                scheda = r,
                sottotitolo = if (serieOggi > 0) {
                    "In corso oggi · $serieOggi serie — tocca per continuare"
                } else buildString {
                    append(if (r.exerciseIds.size == 1) "1 esercizio" else "${r.exerciseIds.size} esercizi")
                    ultima?.let { append(" · ultima volta ${dateLabelShortIso(it).lowercase()}") }
                },
                inCorso = serieOggi > 0,
                onAvvia = { vm.avviaScheda(r) },
                onModifica = { onModifica(r) },
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onNuova, modifier = Modifier.weight(1f)) { Text("Nuova scheda") }
                OutlinedButton(onClick = { vm.avviaScheda(null) }, modifier = Modifier.weight(1f)) {
                    Text("Allenamento libero")
                }
            }
        }
        if (sessioni.isEmpty()) {
            item {
                Text(
                    "Tocca una scheda per iniziare. Gli esercizi che salti non vengono salvati: " +
                        "nessuno è obbligatorio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item {
                Text(
                    "Sessioni",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(sessioni, key = { it.id }) { s -> SessioneRiga(s) { vm.openWorkout(s.id) } }
        }
    }
}

@Composable
private fun SchedaCard(
    scheda: Routine,
    sottotitolo: String,
    inCorso: Boolean,
    onAvvia: () -> Unit,
    onModifica: () -> Unit,
) {
    Card(
        colors = if (inCorso) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else CardDefaults.cardColors(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAvvia),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    scheda.nome.ifBlank { "Senza nome" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    sottotitolo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onModifica) {
                Icon(Icons.Default.Edit, "Modifica scheda", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SessioneRiga(s: WorkoutSession, onClick: () -> Unit) {
    val nEx = s.exercises.count { it.sets.isNotEmpty() }
    val nSets = s.exercises.sumOf { it.sets.size }
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${s.etichetta} — ${dateLabelShortIso(s.date)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                val orari = when {
                    s.firstSetAt > 0 && s.lastSetAt > s.firstSetAt ->
                        " · ${fmtClock(s.firstSetAt)}→${fmtClock(s.lastSetAt)}"
                    s.firstSetAt > 0 -> " · ${fmtClock(s.firstSetAt)}"
                    else -> ""
                }
                Text(
                    if (nSets == 0) "vuota"
                    else "${if (nEx == 1) "1 esercizio" else "$nEx esercizi"} · " +
                        "${if (nSets == 1) "1 serie" else "$nSets serie"}$orari",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------- Sessione in corso ----------

@Composable
private fun SessioneView(
    vm: AppViewModel,
    data: AppData,
    session: WorkoutSession,
    modifier: Modifier,
) {
    BackHandler { vm.closeWorkout() }

    val archivio = data.exercises
    val scheda = data.routines.find { it.id == session.routineId }
    var picker by remember { mutableStateOf(false) }
    var nuovoEx by remember { mutableStateOf(false) }
    var ancheInScheda by remember { mutableStateOf(false) }
    var storicoDi by remember { mutableStateOf<Pair<String, String>?>(null) }
    var confermaElimina by remember { mutableStateOf(false) }
    // Un esercizio aperto alla volta: si parte dal primo ancora da fare.
    var attivo by remember(session.id) {
        mutableStateOf(
            session.exercises.firstOrNull { it.sets.isEmpty() }?.exerciseId
                ?: session.exercises.lastOrNull()?.exerciseId
        )
    }
    val lista = rememberLazyListState()
    // Porta in vista l'esercizio aperto, lasciando sopra la riga precedente per orientarsi
    // (l'intestazione è la riga 0, quindi l'esercizio i sta alla riga i + 1).
    LaunchedEffect(attivo) {
        val i = session.exercises.indexOfFirst { it.exerciseId == attivo }
        if (i > 0) lista.animateScrollToItem(i)
    }

    LazyColumn(
        state = lista,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.closeWorkout() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Chiudi sessione")
                }
                Column(Modifier.weight(1f)) {
                    Text(session.etichetta, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        dateLabelShortIso(session.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    sessionTimes(session)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                IconButton(onClick = { confermaElimina = true }) {
                    Icon(Icons.Default.Delete, "Elimina sessione", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        itemsIndexed(session.exercises, key = { _, log -> log.exerciseId }) { i, log ->
            val ex = archivio.find { it.id == log.exerciseId }
            val prev = WorkoutQueries.previousSets(data.workouts, log.exerciseId, session.id)
            if (log.exerciseId == attivo) {
                val prossimo = session.exercises.drop(i + 1).firstOrNull { it.sets.isEmpty() }
                    ?: session.exercises.firstOrNull { it.sets.isEmpty() && it.exerciseId != log.exerciseId }
                EsercizioAttivo(
                    log = log,
                    ex = ex,
                    prev = prev,
                    onSegna = { w, r -> vm.addSet(session.id, log.exerciseId, w, r) },
                    onTogliSerie = { idx -> vm.removeSet(session.id, log.exerciseId, idx) },
                    onStorico = { storicoDi = log.exerciseId to (ex?.nome ?: log.nome) },
                    onTogli = { vm.removeExerciseFromWorkout(session.id, log.exerciseId) },
                    onProssimo = prossimo?.let { p -> { attivo = p.exerciseId } },
                )
            } else {
                EsercizioCompatto(log, ex, prev) { attivo = log.exerciseId }
            }
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { picker = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Esercizio")
                }
                Button(onClick = { vm.closeWorkout() }, modifier = Modifier.weight(1f)) { Text("Termina") }
            }
            Text(
                "Gli esercizi senza serie non vengono salvati.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }

    // Le scelte fatte qui valgono per la sessione; la spunta porta l'esercizio anche nella scheda.
    val opzioneScheda: (@Composable () -> Unit)? = if (scheda == null) null else (@Composable {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { ancheInScheda = !ancheInScheda },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = ancheInScheda, onCheckedChange = { ancheInScheda = it })
            Text("Aggiungilo anche alla scheda ${scheda.nome}", style = MaterialTheme.typography.bodySmall)
        }
    })

    fun aggiungi(e: Exercise) {
        vm.addExerciseToWorkout(session.id, e.id, e.nome)
        if (ancheInScheda && scheda != null) vm.aggiungiAScheda(scheda.id, e.id)
        attivo = e.id
    }

    if (picker) {
        ArchivioPicker(
            titolo = "Aggiungi esercizio",
            disponibili = archivio.filter { e -> session.exercises.none { it.exerciseId == e.id } },
            onScegli = { e -> aggiungi(e); picker = false },
            onNuovo = { picker = false; nuovoEx = true },
            onDismiss = { picker = false },
            extra = opzioneScheda,
        )
    }

    if (nuovoEx) {
        EsercizioEditorDialog(
            iniziale = null,
            onSalva = { e -> aggiungi(vm.creaEsercizio(e)); nuovoEx = false },
            onElimina = null,
            onDismiss = { nuovoEx = false },
            onCompila = { vm.compilaEsercizio(it) },
        )
    }

    storicoDi?.let { (exId, nome) ->
        StoricoEsercizioDialog(
            nome = nome,
            rows = WorkoutQueries.history(data.workouts, exId),
            onDismiss = { storicoDi = null },
        )
    }

    if (confermaElimina) {
        ConfermaDialog(
            titolo = "Eliminare la sessione?",
            testo = "Verranno cancellate tutte le serie registrate in questa sessione.",
            azione = "Elimina",
            onConferma = { confermaElimina = false; vm.deleteWorkout(session.id) },
            onDismiss = { confermaElimina = false },
        )
    }
}

/** Riga chiusa: nome e, a colpo d'occhio, cosa hai fatto oggi o l'ultima volta. */
@Composable
private fun EsercizioCompatto(
    log: ExerciseLog,
    ex: Exercise?,
    prev: Pair<String, List<ExSet>>?,
    onApri: () -> Unit,
) {
    val fatto = log.sets.isNotEmpty()
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onApri),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(ex?.nome ?: log.nome, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    when {
                        fatto -> log.sets.joinToString(" · ") { fmtSet(it) }
                        prev != null -> "Ultima volta: " + prev.second.joinToString(" · ") { fmtSet(it) }
                        else -> obiettivo(ex) ?: "Mai fatto"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (fatto) ColorOk else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (fatto) {
                Text(
                    "✓ ${log.sets.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = ColorOk,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/**
 * Esercizio aperto. Peso e ripetizioni sono già proposti (l'ultima serie, o un
 * disco in più quando la scheda lo prevede): nella maggior parte dei casi basta
 * un tocco su "Segna", senza tastiera.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EsercizioAttivo(
    log: ExerciseLog,
    ex: Exercise?,
    prev: Pair<String, List<ExSet>>?,
    onSegna: (Double, Int) -> Unit,
    onTogliSerie: (Int) -> Unit,
    onStorico: () -> Unit,
    onTogli: () -> Unit,
    onProssimo: (() -> Unit)?,
) {
    val proposta = log.sets.lastOrNull()?.let { WorkoutQueries.Proposta(it, progressione = false) }
        ?: WorkoutQueries.proposta(prev?.second, ex)
    var peso by remember(log.exerciseId) { mutableStateOf(proposta?.set?.weightKg ?: 0.0) }
    var rip by remember(log.exerciseId) { mutableStateOf(proposta?.set?.reps ?: ex?.repMin ?: 10) }
    var scriviPeso by remember { mutableStateOf(false) }
    var scriviRip by remember { mutableStateOf(false) }
    var serieDaTogliere by remember { mutableStateOf<Int?>(null) }
    var confermaTogli by remember { mutableStateOf(false) }
    val passo = ex?.passoKg ?: 1.0

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ex?.nome ?: log.nome, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    obiettivo(ex)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = onStorico) { Text("Storico") }
                IconButton(onClick = { if (log.sets.isEmpty()) onTogli() else confermaTogli = true }) {
                    Icon(Icons.Default.Close, "Togli da oggi", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                prev?.let { (data, serie) ->
                    Text(
                        "Ultima volta (${dateLabelShortIso(data)}): " + serie.joinToString(" · ") { fmtSet(it) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (log.sets.isEmpty() && proposta?.progressione == true) {
                    Text(
                        "L'ultima volta tutte le serie erano al massimo: ti propongo ${fmt1(proposta.set.weightKg)} kg.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ColorOk,
                        fontWeight = FontWeight.Medium,
                    )
                }

                if (log.sets.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        log.sets.forEachIndexed { i, s ->
                            AssistChip(
                                onClick = { serieDaTogliere = i },
                                label = { Text("${i + 1}. ${fmtSet(s)}") },
                                trailingIcon = { Icon(Icons.Default.Close, "Togli serie", Modifier.size(14.dp)) },
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stepper(
                        testo = if (peso > 0) "${fmt1(peso)} kg" else "corpo",
                        onMeno = { peso = WorkoutQueries.arrotonda((peso - passo).coerceAtLeast(0.0)) },
                        onPiu = { peso = WorkoutQueries.arrotonda(peso + passo) },
                        onTocca = { scriviPeso = true },
                        modifier = Modifier.weight(1f),
                    )
                    Stepper(
                        testo = "$rip rip",
                        onMeno = { rip = (rip - 1).coerceAtLeast(1) },
                        onPiu = { rip += 1 },
                        onTocca = { scriviRip = true },
                        modifier = Modifier.weight(1f),
                    )
                }
                Button(onClick = { onSegna(peso, rip) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Segna serie ${log.sets.size + 1}")
                }
                if (onProssimo != null && log.sets.isNotEmpty()) {
                    TextButton(onClick = onProssimo, modifier = Modifier.align(Alignment.End)) {
                        Text("Prossimo esercizio")
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    }
                }
            }
        }
    }

    if (scriviPeso) {
        NumeroDialog(
            titolo = "Peso (kg, 0 = corpo libero)",
            iniziale = if (peso > 0) fmt1(peso) else "0",
            decimali = true,
            onOk = { peso = WorkoutQueries.arrotonda(it); scriviPeso = false },
            onDismiss = { scriviPeso = false },
        )
    }
    if (scriviRip) {
        NumeroDialog(
            titolo = "Ripetizioni",
            iniziale = rip.toString(),
            decimali = false,
            onOk = { rip = it.toInt().coerceAtLeast(1); scriviRip = false },
            onDismiss = { scriviRip = false },
        )
    }
    val daTogliere = serieDaTogliere?.let { i -> log.sets.getOrNull(i)?.let { i to it } }
    if (daTogliere != null) {
        val (i, s) = daTogliere
        ConfermaDialog(
            titolo = "Togliere la serie ${i + 1}?",
            testo = fmtSet(s),
            azione = "Togli",
            onConferma = { onTogliSerie(i); serieDaTogliere = null },
            onDismiss = { serieDaTogliere = null },
        )
    }
    if (confermaTogli) {
        ConfermaDialog(
            titolo = "Togliere l'esercizio da oggi?",
            testo = "Le serie già segnate oggi per questo esercizio verranno cancellate.",
            azione = "Togli",
            onConferma = { confermaTogli = false; onTogli() },
            onDismiss = { confermaTogli = false },
        )
    }
}

/** Valore con − e +: niente tastiera per i piccoli aggiustamenti, tocca il numero per scriverlo. */
@Composable
private fun Stepper(
    testo: String,
    onMeno: () -> Unit,
    onPiu: () -> Unit,
    onTocca: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Tasti chiari sulla card colorata: con il colore di serie sparirebbero nello sfondo.
    val colori = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = onMeno, colors = colori) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            testo,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onTocca)
                .padding(vertical = 10.dp),
        )
        FilledTonalIconButton(onClick = onPiu, colors = colori) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** "Prima serie 17:42 → ultima 18:25 (43 min)", con fallback se manca una delle due. */
private fun sessionTimes(s: WorkoutSession): String? {
    if (s.firstSetAt <= 0 && s.lastSetAt <= 0) return null
    if (s.firstSetAt > 0 && s.lastSetAt > s.firstSetAt) {
        val mins = ((s.lastSetAt - s.firstSetAt) / 60_000L).toInt()
        return "Prima serie ${fmtClock(s.firstSetAt)} → ultima ${fmtClock(s.lastSetAt)} ($mins min)"
    }
    val single = if (s.firstSetAt > 0) s.firstSetAt else s.lastSetAt
    return "Prima serie ${fmtClock(single)}"
}

/** "3-4 serie × 8-12 rip — nota", solo con i pezzi presenti. */
private fun obiettivo(ex: Exercise?): String? = ex?.let { e ->
    buildString {
        e.serie?.takeIf { it.isNotBlank() }?.let { append("$it serie") }
        val rip = when {
            e.repMin != null && e.repMax != null && e.repMin != e.repMax -> "${e.repMin}-${e.repMax} rip"
            e.repMin != null -> "${e.repMin} rip"
            e.repMax != null -> "${e.repMax} rip"
            else -> null
        }
        rip?.let {
            if (isNotEmpty()) append(" × ")
            append(it)
        }
        e.nota?.takeIf { it.isNotBlank() }?.let {
            if (isNotEmpty()) append(" — ")
            append(it)
        }
    }.ifBlank { null }
}

// ---------- Modifica scheda ----------

@Composable
private fun SchedaEditorView(
    vm: AppViewModel,
    data: AppData,
    iniziale: Routine,
    onChiudi: () -> Unit,
    modifier: Modifier,
) {
    val esistente = data.routines.any { it.id == iniziale.id }
    var nome by remember(iniziale.id) { mutableStateOf(iniziale.nome) }
    var ids by remember(iniziale.id) { mutableStateOf(iniziale.exerciseIds) }
    var picker by remember { mutableStateOf(false) }
    var nuovoEx by remember { mutableStateOf(false) }
    var modificaEx by remember { mutableStateOf<Exercise?>(null) }
    var confermaElimina by remember { mutableStateOf(false) }
    val archivio = data.exercises
    val esercizi = ids.mapNotNull { id -> archivio.find { it.id == id } }

    // Si salva uscendo: niente tasto dimenticato. Una scheda nuova lasciata vuota non viene creata.
    fun esci() {
        val vuota = nome.isBlank() && esercizi.isEmpty()
        if (!(vuota && !esistente)) {
            val nomeFinale = nome.trim().ifBlank { iniziale.nome.ifBlank { "Scheda ${data.routines.size + 1}" } }
            vm.salvaScheda(iniziale.copy(nome = nomeFinale, exerciseIds = esercizi.map { it.id }))
        }
        onChiudi()
    }

    BackHandler { esci() }

    fun sposta(da: Int, a: Int) {
        if (a !in esercizi.indices) return
        val l = esercizi.map { it.id }.toMutableList()
        val x = l.removeAt(da)
        l.add(a, x)
        ids = l
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { esci() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Salva e torna indietro")
                }
                Text(
                    if (esistente) "Modifica scheda" else "Nuova scheda",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { esci() }) { Text("Fatto") }
            }
        }
        item {
            OutlinedTextField(
                value = nome,
                onValueChange = { nome = it },
                label = { Text("Nome (es. Spinta, Gambe, Full body)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text(
                "Esercizi, nell'ordine in cui li fai",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (esercizi.isEmpty()) {
                Text(
                    "Ancora nessuno: aggiungi solo quelli che fai davvero.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        itemsIndexed(esercizi, key = { _, e -> e.id }) { i, e ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable { modificaEx = e }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(e.nome, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            obiettivo(e) ?: "tocca per impostare serie e ripetizioni",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { sposta(i, i - 1) }, enabled = i > 0, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, "Sposta su")
                    }
                    IconButton(onClick = { sposta(i, i + 1) }, enabled = i < esercizi.lastIndex, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, "Sposta giù")
                    }
                    IconButton(onClick = { ids = ids - e.id }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Close, "Togli dalla scheda", Modifier.size(18.dp))
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Aggiungi esercizio")
            }
            Text(
                "Tocca un esercizio per cambiare nome, serie, ripetizioni e salto di peso.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (esistente) {
            item {
                TextButton(onClick = { confermaElimina = true }) {
                    Text("Elimina scheda", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (picker) {
        ArchivioPicker(
            titolo = "Aggiungi alla scheda",
            disponibili = archivio.filter { it.id !in ids },
            // Resta aperto: di solito si aggiungono più esercizi di fila.
            onScegli = { e -> ids = ids + e.id },
            onNuovo = { picker = false; nuovoEx = true },
            onDismiss = { picker = false },
        )
    }

    if (nuovoEx) {
        EsercizioEditorDialog(
            iniziale = null,
            onSalva = { e ->
                // Con un nome già in archivio torna l'esercizio esistente: niente doppioni in lista.
                val id = vm.creaEsercizio(e).id
                if (id !in ids) ids = ids + id
                nuovoEx = false
            },
            onElimina = null,
            onDismiss = { nuovoEx = false },
            onCompila = { vm.compilaEsercizio(it) },
        )
    }

    modificaEx?.let { e ->
        EsercizioEditorDialog(
            iniziale = e,
            onSalva = { vm.salvaEsercizio(it); modificaEx = null },
            onElimina = { vm.eliminaEsercizio(e.id); ids = ids - e.id; modificaEx = null },
            onDismiss = { modificaEx = null },
            onCompila = { vm.compilaEsercizio(it) },
        )
    }

    if (confermaElimina) {
        ConfermaDialog(
            titolo = "Eliminare la scheda?",
            testo = "Le sessioni già fatte restano nello storico, con il loro nome.",
            azione = "Elimina",
            onConferma = { confermaElimina = false; vm.eliminaScheda(iniziale.id); onChiudi() },
            onDismiss = { confermaElimina = false },
        )
    }
}

// ---------- Dialoghi ----------

/** Archivio esercizi: scegli con un tocco, o creane uno nuovo. */
@Composable
private fun ArchivioPicker(
    titolo: String,
    disponibili: List<Exercise>,
    onScegli: (Exercise) -> Unit,
    onNuovo: () -> Unit,
    onDismiss: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    var cerca by remember { mutableStateOf("") }
    val filtrati = disponibili.filter {
        cerca.isBlank() || it.nome.contains(cerca.trim(), ignoreCase = true) ||
            it.gruppo.contains(cerca.trim(), ignoreCase = true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titolo) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (disponibili.size > 8) {
                    OutlinedTextField(
                        value = cerca,
                        onValueChange = { cerca = it },
                        label = { Text("Cerca") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // In cima, non in fondo alla lista: per una scheda nuova è spesso la prima cosa che serve.
                OutlinedButton(onClick = onNuovo, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Nuovo esercizio")
                }
                extra?.invoke()
                if (filtrati.isEmpty()) {
                    Text(
                        if (disponibili.isEmpty()) "Hai già aggiunto tutti gli esercizi dell'archivio."
                        else "Nessun esercizio con questo nome.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                filtrati.groupBy { it.gruppo }.toSortedMap(String.CASE_INSENSITIVE_ORDER).forEach { (gruppo, lista) ->
                    Text(
                        gruppo,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    lista.forEach { e ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onScegli(e) }
                                .padding(vertical = 6.dp),
                        ) {
                            Text(e.nome, style = MaterialTheme.typography.bodyMedium)
                            obiettivo(e)?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fatto") } },
    )
}

@Composable
private fun EsercizioEditorDialog(
    iniziale: Exercise?,
    onSalva: (Exercise) -> Unit,
    onElimina: (() -> Unit)?,
    onDismiss: () -> Unit,
    onCompila: (suspend (String) -> AppViewModel.EsitoCompila)? = null,
) {
    var nome by remember { mutableStateOf(iniziale?.nome.orEmpty()) }
    var gruppo by remember { mutableStateOf(iniziale?.gruppo.orEmpty()) }
    var serie by remember { mutableStateOf(iniziale?.serie.orEmpty()) }
    var repMin by remember { mutableStateOf(iniziale?.repMin?.toString().orEmpty()) }
    var repMax by remember { mutableStateOf(iniziale?.repMax?.toString().orEmpty()) }
    var salto by remember { mutableStateOf(iniziale?.incrementoKg?.let { fmt1(it) }.orEmpty()) }
    var nota by remember { mutableStateOf(iniziale?.nota.orEmpty()) }
    var confermaElimina by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var compilando by remember { mutableStateOf(false) }
    var esitoCompila by remember { mutableStateOf<String?>(null) }

    /** Riempie solo i campi vuoti: quello che hai già scritto non si tocca. Restituisce quanti ne ha riempiti. */
    fun applica(p: CompilaEsercizio.Proposta): Int {
        var n = 0
        if (gruppo.isBlank()) { gruppo = p.gruppo; n++ }
        if (serie.isBlank()) { serie = p.serie; n++ }
        if (repMin.isBlank()) { repMin = p.repMin.toString(); n++ }
        if (repMax.isBlank()) { repMax = p.repMax.toString(); n++ }
        if (salto.isBlank() && p.incrementoKg != null) { salto = fmt1(p.incrementoKg); n++ }
        if (nota.isBlank() && p.nota != null) { nota = p.nota; n++ }
        return n
    }

    fun compila(azione: suspend (String) -> AppViewModel.EsitoCompila) {
        compilando = true
        esitoCompila = null
        scope.launch {
            esitoCompila = when (val e = azione(nome)) {
                is AppViewModel.EsitoCompila.DalleRegole ->
                    if (applica(e.p) == 0) "Nessun campo vuoto da compilare: svuota quelli che vuoi rifare."
                    else "Compilato dalle regole dell'app: controlla e cambia quello che vuoi."
                is AppViewModel.EsitoCompila.DallAi ->
                    if (applica(e.p) == 0) "Nessun campo vuoto da compilare: svuota quelli che vuoi rifare."
                    else "Compilato dall'AI: controlla e cambia quello che vuoi."
                is AppViewModel.EsitoCompila.NonRiconosciuto ->
                    if (e.conChiave) "Neanche l'AI riconosce questo nome: scrivi i dettagli a mano."
                    else "Non riconosco questo nome. Scrivi i dettagli a mano, oppure metti la tua chiave AI " +
                        "in Impostazioni → Riconoscimento foto: così compila anche i nomi insoliti."
                is AppViewModel.EsitoCompila.Errore -> e.messaggio
            }
            compilando = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (iniziale == null) "Nuovo esercizio" else "Modifica esercizio") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = nome, onValueChange = { nome = it },
                    label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                if (onCompila != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            enabled = nome.isNotBlank() && !compilando,
                            onClick = { compila(onCompila) },
                        ) { Text(if (compilando) "Cerco…" else "Compila da solo") }
                        if (compilando) {
                            Spacer(Modifier.width(10.dp))
                            CircularProgressIndicator(Modifier.size(18.dp))
                        }
                    }
                    Text(
                        esitoCompila ?: "Scrivi il nome e tocca qui: muscolo, serie, ripetizioni e salto di peso " +
                            "si compilano da soli.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = gruppo, onValueChange = { gruppo = it },
                    label = { Text("Muscolo (es. Petto)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = serie, onValueChange = { serie = it },
                    label = { Text("Serie (es. 3 oppure 3-4)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = repMin, onValueChange = { repMin = it.filter(Char::isDigit) },
                        label = { Text("Rip. min") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = repMax, onValueChange = { repMax = it.filter(Char::isDigit) },
                        label = { Text("Rip. max") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    "Con minimo e massimo, quando l'ultima volta hai chiuso tutte le serie al massimo " +
                        "l'app ti propone da sola di salire di peso.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = salto, onValueChange = { salto = it },
                    label = { Text("Salto di peso in kg (vuoto = 1)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Quanto aumenta il peso con un disco in più: è anche il passo dei tasti − e +.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = nota, onValueChange = { nota = it },
                    label = { Text("Nota (facoltativa)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                if (onElimina != null) {
                    TextButton(onClick = { confermaElimina = true }) {
                        Text("Elimina dall'archivio", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = nome.isNotBlank(),
                onClick = {
                    val a = repMin.toIntOrNull()?.takeIf { it > 0 }
                    val b = repMax.toIntOrNull()?.takeIf { it > 0 }
                    // Minimo e massimo scritti al contrario: li rimetto in ordine invece di rifiutare.
                    val (min, max) = if (a != null && b != null && a > b) b to a else a to b
                    onSalva(
                        Exercise(
                            id = iniziale?.id ?: "custom-ex-${UUID.randomUUID()}",
                            nome = nome.trim(),
                            gruppo = gruppo.trim().ifBlank { "Altro" },
                            spinta = iniziale?.spinta ?: false,
                            trazione = iniziale?.trazione ?: false,
                            serie = serie.trim().ifBlank { null },
                            repMin = min,
                            repMax = max,
                            nota = nota.trim().ifBlank { null },
                            custom = iniziale?.custom ?: true,
                            incrementoKg = salto.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 },
                        )
                    )
                },
            ) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )

    if (confermaElimina && onElimina != null) {
        ConfermaDialog(
            titolo = "Eliminare «${iniziale?.nome}»?",
            testo = "Sparisce dall'archivio e dalle schede. Le serie già fatte restano nello storico.",
            azione = "Elimina",
            onConferma = { confermaElimina = false; onElimina() },
            onDismiss = { confermaElimina = false },
        )
    }
}

/** Numero scritto a mano, per i salti grandi che con − e + richiederebbero troppi tocchi. */
@Composable
private fun NumeroDialog(
    titolo: String,
    iniziale: String,
    decimali: Boolean,
    onOk: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var testo by remember { mutableStateOf(iniziale) }
    val valore = testo.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }
    val fuoco = remember { FocusRequester() }
    LaunchedEffect(Unit) { fuoco.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titolo) },
        text = {
            OutlinedTextField(
                value = testo,
                onValueChange = { testo = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (decimali) KeyboardType.Decimal else KeyboardType.Number,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fuoco),
            )
        },
        confirmButton = {
            TextButton(enabled = valore != null, onClick = { valore?.let(onOk) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

@Composable
private fun ConfermaDialog(
    titolo: String,
    testo: String,
    azione: String,
    onConferma: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titolo) },
        text = { Text(testo) },
        confirmButton = { TextButton(onClick = onConferma) { Text(azione) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

@Composable
private fun StoricoEsercizioDialog(
    nome: String,
    rows: List<WorkoutQueries.HistoryRow>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(nome) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (rows.isEmpty()) {
                    Text("Nessuna sessione precedente con questo esercizio.", style = MaterialTheme.typography.bodyMedium)
                }
                rows.forEach { r ->
                    Column {
                        Text(
                            "${dateLabelShortIso(r.date)} — ${r.etichetta}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            r.sets.joinToString(" · ") { fmtSet(it) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
    )
}
