package it.gaspa.surplus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import it.gaspa.surplus.data.SettingsRepo
import it.gaspa.surplus.notify.ReminderScheduler
import it.gaspa.surplus.ui.AllenamentoScreen
import it.gaspa.surplus.ui.AppViewModel
import it.gaspa.surplus.ui.ImpostazioniScreen
import it.gaspa.surplus.ui.OggiScreen
import it.gaspa.surplus.ui.OnboardingScreen
import it.gaspa.surplus.ui.StoricoScreen
import it.gaspa.surplus.ui.theme.SurplusTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Idempotente: a ogni apertura riallinea le sveglie alle impostazioni.
        lifecycleScope.launch {
            ReminderScheduler.rescheduleAll(this@MainActivity, SettingsRepo(this@MainActivity).current())
        }
        setContent {
            SurplusTheme {
                SurplusRoot()
            }
        }
    }
}

@Composable
fun SurplusRoot() {
    val vm: AppViewModel = viewModel()
    val profile by vm.profile.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // Primo avvio: profilo + disclaimer prima di tutto il resto.
    if (!profile.isConfigured) {
        OnboardingScreen(vm)
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Oggi") },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(painterResource(R.drawable.ic_dumbbell), contentDescription = null) },
                    label = { Text("Allenamento") },
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    label = { Text("Storico") },
                )
                NavigationBarItem(
                    selected = tab == 3, onClick = { tab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Impostazioni") },
                )
            }
        }
    ) { padding ->
        when (tab) {
            0 -> OggiScreen(vm, Modifier.padding(padding))
            1 -> AllenamentoScreen(vm, Modifier.padding(padding))
            2 -> StoricoScreen(vm, Modifier.padding(padding))
            else -> ImpostazioniScreen(vm, Modifier.padding(padding))
        }
    }
}
