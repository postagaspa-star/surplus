package it.gaspa.surplus.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.gaspa.surplus.R
import it.gaspa.surplus.data.Attivita
import it.gaspa.surplus.data.Obiettivo
import it.gaspa.surplus.data.Sesso
import it.gaspa.surplus.data.TargetCalculator
import it.gaspa.surplus.data.UserProfile
import it.gaspa.surplus.ui.theme.BrandGreen
import java.time.LocalDate

/**
 * Primo avvio: profilo → target calcolati → disclaimer.
 * Tutto saltabile: chi non vuole compilare niente parte con i valori di default
 * e li sistema dalle impostazioni.
 */
@Composable
fun OnboardingScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val annoCorrente = LocalDate.now().year
    var nome by remember { mutableStateOf("") }
    var anno by remember { mutableStateOf("") }
    var sesso by remember { mutableStateOf(Sesso.MASCHIO) }
    var altezza by remember { mutableStateOf("") }
    var peso by remember { mutableStateOf("") }
    var attivita by remember { mutableStateOf(Attivita.MODERATA) }
    var obiettivo by remember { mutableStateOf(Obiettivo.MASSA) }
    var accepted by remember { mutableStateOf(false) }

    val annoVal = anno.toIntOrNull()
    val altVal = altezza.toIntOrNull()
    val pesoVal = peso.replace(',', '.').toDoubleOrNull()
    val datiOk = annoVal != null && annoVal in 1920..annoCorrente &&
        altVal != null && altVal in 100..250 &&
        pesoVal != null && pesoVal in 30.0..250.0

    val profile = UserProfile(
        nome = nome.trim(),
        annoNascita = annoVal ?: 0,
        sesso = sesso,
        altezzaCm = altVal ?: 0,
        pesoKg = pesoVal ?: 0.0,
        attivita = attivita,
        obiettivo = obiettivo,
    )
    val eta = if (annoVal != null) annoCorrente - annoVal else null
    val minorenne = eta != null && eta < 18
    val targets = if (datiOk) TargetCalculator.compute(profile, annoCorrente) else null

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Barra fissa: i pulsanti restano sopra la barra di sistema del telefono,
            // sempre raggiungibili anche a modulo mezzo compilato.
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Button(
                        enabled = datiOk && accepted,
                        onClick = { vm.completeOnboarding(profile, applyTargets = true) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Inizia") }
                    TextButton(
                        enabled = accepted,
                        onClick = { vm.completeOnboarding(UserProfile(), applyTargets = false) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Salta: userò i valori di default") }
                    if (!accepted) {
                        Text(
                            "Per continuare accetta l'avvertenza qui sopra.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    ) { padding ->
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState()),
    ) {
        // Intestazione con l'identità dell'app: stesso verde del sito e dell'icona.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .background(BrandGreen)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(88.dp),
            )
            Text(
                "Benvenuto in Surplus",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                "Due minuti per calcolare i tuoi target. Puoi cambiarli quando vuoi dalle impostazioni.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }

    Column(
        Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = nome, onValueChange = { nome = it },
            label = { Text("Nome (facoltativo)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = anno, onValueChange = { anno = it },
                label = { Text("Anno di nascita") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = altezza, onValueChange = { altezza = it },
                label = { Text("Altezza (cm)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = peso, onValueChange = { peso = it },
            label = { Text("Peso attuale (kg)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Sesso", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sesso.entries.forEach { s ->
                FilterChip(selected = sesso == s, onClick = { sesso = s }, label = { Text(s.label) })
            }
        }

        Text("Quanto ti muovi", style = MaterialTheme.typography.labelLarge)
        Attivita.entries.forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = attivita == a,
                    onClick = { attivita = a },
                    label = { Text(a.label) },
                )
                Text(
                    "  ${a.descr}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text("Obiettivo", style = MaterialTheme.typography.labelLarge)
        Obiettivo.entries.forEach { o ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = obiettivo == o,
                    onClick = { obiettivo = o },
                    label = { Text(o.label) },
                )
                Text(
                    "  ${o.descr}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        targets?.let { t ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("I tuoi target", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Calorie: ${fmtInt(t.kcalMin)}–${fmtInt(t.kcalMax)} kcal al giorno", style = MaterialTheme.typography.bodyMedium)
                    Text("Proteine: ${t.protMin}–${t.protMax} g al giorno", style = MaterialTheme.typography.bodyMedium)
                    Text("Acqua: ${fmtLiters(t.waterMl)} L al giorno", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Calcolati con la formula di Mifflin-St Jeor: metabolismo basale ${fmtInt(t.bmr)} kcal, " +
                            "consumo giornaliero stimato ${fmtInt(t.tdee)} kcal (attività ${attivita.label.lowercase()}), " +
                            "poi ${if (obiettivo.deltaKcal >= 0) "+" else ""}${obiettivo.deltaKcal} kcal per l'obiettivo ${obiettivo.label.lowercase()}. " +
                            "Sono una stima di partenza: se dopo 3-4 settimane il peso non si muove, l'app te lo dice.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        HorizontalDivider()

        DisclaimerBox(minorenne)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = accepted, onCheckedChange = { accepted = it })
            Text(
                "Ho letto e capito: Surplus non è un servizio medico.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(4.dp))
    }
    }
    }
}

/** Punto 6 degli esperti: disclaimer sempre, avviso rafforzato per i minorenni. */
@Composable
fun DisclaimerBox(minorenne: Boolean, modifier: Modifier = Modifier) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (minorenne) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Da leggere", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Surplus è un diario personale, non un servizio medico e non un sostituto del parere " +
                    "di un medico o di un dietista. I target sono stime statistiche basate su formule " +
                    "standard: non tengono conto della tua storia clinica, di allergie, intolleranze o " +
                    "terapie in corso. I consigli sono calcoli automatici su quello che scrivi tu, " +
                    "nessuno li ha validati per il tuo caso.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (minorenne) {
                Text(
                    "Hai meno di 18 anni: in età di crescita diete, surplus e deficit calorici andrebbero " +
                        "impostati insieme a un adulto responsabile e a un professionista. " +
                        "Parlane con i tuoi genitori e con il tuo medico prima di seguire questi numeri.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                "Se noti un rapporto problematico con cibo, peso o allenamento, fermati e parlane con qualcuno.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
