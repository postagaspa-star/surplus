package it.gaspa.surplus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Verde del sito e dell'icona: identità dell'app, uguale in chiaro e in scuro. */
val BrandGreen = Color(0xFF14532D)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    secondary = Color(0xFF558B2F),
    tertiary = Color(0xFF00695C),
    surfaceVariant = Color(0xFFEEF3EA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF81C784),
    secondary = Color(0xFFAED581),
    tertiary = Color(0xFF4DB6AC),
)

@Composable
fun SurplusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
