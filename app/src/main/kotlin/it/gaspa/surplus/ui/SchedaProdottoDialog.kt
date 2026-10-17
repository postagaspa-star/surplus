package it.gaspa.surplus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import it.gaspa.surplus.data.Food
import it.gaspa.surplus.logic.Livello
import it.gaspa.surplus.logic.SchedaNutrizionale
import kotlinx.coroutines.delay

private const val DURATA_MS = 5_000L
private const val PASSO_MS = 50L

/**
 * Scheda del prodotto appena registrato, in stile Yuka: resta 5 secondi e poi
 * sparisce da sola. Un tocco sulla scheda mette in pausa il conto alla rovescia,
 * un altro lo fa ripartire; il pulsante sotto la chiude subito.
 *
 * La prima volta in assoluto un velo spiega il meccanismo, e mentre è visibile
 * il tempo NON scorre: nessuno deve leggere di corsa le istruzioni.
 */
@Composable
fun SchedaProdottoDialog(
    food: Food,
    grams: Double,
    mostraTutorial: Boolean,
    onTutorialVisto: () -> Unit,
    onDismiss: () -> Unit,
) {
    val righe = remember(food) { SchedaNutrizionale.righe(food) }
    var inPausa by remember { mutableStateOf(false) }
    var velo by remember { mutableStateOf(mostraTutorial) }
    var rimasto by remember { mutableLongStateOf(DURATA_MS) }
    val scuro = isSystemInDarkTheme()

    // Bianco caldo, non acceso: la scheda deve riposare gli occhi, non abbagliare.
    val sfondoScheda = if (scuro) Color(0xFF1C1F1C) else Color(0xFFF7F6F2)
    val testo = if (scuro) Color(0xFFE8F0E9) else Color(0xFF1A2E1D)
    val testoTenue = testo.copy(alpha = 0.6f)

    LaunchedEffect(inPausa, velo) {
        if (inPausa || velo) return@LaunchedEffect
        while (rimasto > 0) {
            delay(PASSO_MS)
            rimasto -= PASSO_MS
        }
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.72f),
            ) {
                Card(
                    Modifier
                        .fillMaxSize()
                        // Un tocco sulla scheda: pausa. Un altro: riparte.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { if (!velo) inPausa = !inPausa },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = sfondoScheda),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        // Tempo che scorre: si vede subito se è in pausa.
                        LinearProgressIndicator(
                            progress = { (rimasto.toFloat() / DURATA_MS).coerceIn(0f, 1f) },
                            color = if (inPausa) testoTenue else MaterialTheme.colorScheme.primary,
                            trackColor = Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                        )

                        Column(
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                        ) {
                            Text(
                                food.nome,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = testo,
                            )
                            food.hint?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = testoTenue)
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                SchedaNutrizionale.contributo(food, grams),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(16.dp))

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Valori", style = MaterialTheme.typography.titleMedium, color = testo)
                                Text(
                                    if (food.isDrink) "Per 100 ml" else "Per 100 g",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = testoTenue,
                                )
                            }
                            Spacer(Modifier.height(4.dp))

                            if (righe.isEmpty()) {
                                Text(
                                    "Per questo alimento non hai ancora inserito valori nutrizionali.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = testoTenue,
                                    modifier = Modifier.padding(vertical = 12.dp),
                                )
                            }
                            righe.forEach { r ->
                                HorizontalDivider(color = testo.copy(alpha = 0.08f))
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            r.etichetta,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = testo,
                                        )
                                        Text(
                                            r.giudizio,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = testoTenue,
                                        )
                                    }
                                    Text(
                                        r.valore,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = testo,
                                    )
                                    Spacer(Modifier.size(10.dp))
                                    Box(
                                        Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (r.livello == Livello.BUONO) ColorOk
                                                else testo.copy(alpha = 0.25f)
                                            )
                                    )
                                }
                            }
                        }

                        if (inPausa) {
                            Text(
                                "In pausa — tocca di nuovo per far ripartire",
                                style = MaterialTheme.typography.labelMedium,
                                color = testoTenue,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                            )
                        }
                    }
                }

                // Velo del primo utilizzo: spiega, e intanto ferma il tempo.
                if (velo) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF20241F).copy(alpha = 0.88f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { velo = false; onTutorialVisto() }
                            .padding(28.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Come funziona questa scheda",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(18.dp))
                        listOf(
                            "Resta 5 secondi e poi si chiude da sola.",
                            "Tocca la scheda per mettere in pausa il tempo, toccala di nuovo per farlo ripartire.",
                            "Con «Chiudi», qui sotto, la fai sparire subito.",
                        ).forEach {
                            Text(
                                "•  $it",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.92f),
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Tocca lo schermo per iniziare",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.65f),
                        )
                    }
                }
            }

            // Pulsante fuori dalla scheda, nel vuoto sotto: nessun bordo.
            TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 10.dp)) {
                Text(
                    "Chiudi",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
