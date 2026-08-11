package dev.alexkobayashi.appdeck

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import dev.alexkobayashi.appdeck.ui.navigation.AppDeckNavHost
import dev.alexkobayashi.appdeck.ui.theme.AppDeckTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as AppDeckApplication).container

        setContent {
            AppDeckTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppDeckNavHost(container = container)
                }
            }
        }
    }
}
