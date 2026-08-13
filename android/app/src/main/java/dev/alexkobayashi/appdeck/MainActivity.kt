package dev.alexkobayashi.appdeck

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alexkobayashi.appdeck.domain.model.ThemeMode
import dev.alexkobayashi.appdeck.ui.navigation.AppDeckNavHost
import dev.alexkobayashi.appdeck.ui.theme.AppDeckTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as AppDeckApplication).container

        setContent {
            val mode by container.settingsRepository.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (mode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }

            // O enableEdgeToEdge() sem argumento decide a cor dos ícones das
            // barras pelo tema do *sistema*. Com a preferência do app podendo
            // divergir dele, sem isto o resultado seria ícone claro sobre
            // fundo claro.
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }

            AppDeckTheme(darkTheme = darkTheme) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppDeckNavHost(container = container)
                }
            }
        }
    }
}
