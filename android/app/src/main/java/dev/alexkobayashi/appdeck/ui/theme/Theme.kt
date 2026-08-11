package dev.alexkobayashi.appdeck.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Azul do deck, o mesmo do ícone e do servidor.
private val DeckBlue = Color(0xFF2F6BE0)
private val DeckBlueLight = Color(0xFF5B9CFF)

private val LightColors = lightColorScheme(
    primary = DeckBlue,
    secondary = DeckBlueLight,
)

private val DarkColors = darkColorScheme(
    primary = DeckBlueLight,
    secondary = DeckBlue,
)

@Composable
fun AppDeckTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Material You a partir do Android 12: o deck adota as cores do papel
    // de parede, o que combina com ele ser uma extensão da tela inicial.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(colorScheme = colors, content = content)
}
