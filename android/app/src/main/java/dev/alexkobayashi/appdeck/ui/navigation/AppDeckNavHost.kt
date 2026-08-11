package dev.alexkobayashi.appdeck.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.alexkobayashi.appdeck.AppContainer
import dev.alexkobayashi.appdeck.ui.deck.DeckScreen
import dev.alexkobayashi.appdeck.ui.serverconfig.ServerConfigScreen
import kotlinx.serialization.Serializable

@Serializable
data object DeckRoute

@Serializable
data object ServerConfigRoute

@Composable
fun AppDeckNavHost(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = DeckRoute) {
        composable<DeckRoute> {
            DeckScreen(
                container = container,
                onOpenSettings = { navController.navigate(ServerConfigRoute) },
            )
        }
        composable<ServerConfigRoute> {
            ServerConfigScreen(
                container = container,
                onDone = { navController.popBackStack() },
            )
        }
    }
}
