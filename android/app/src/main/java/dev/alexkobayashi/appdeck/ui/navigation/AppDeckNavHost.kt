package dev.alexkobayashi.appdeck.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.alexkobayashi.appdeck.AppContainer
import dev.alexkobayashi.appdeck.ui.deck.DeckScreen
import dev.alexkobayashi.appdeck.ui.iconpicker.IconPickerScreen
import dev.alexkobayashi.appdeck.ui.serverconfig.ServerConfigScreen
import kotlinx.serialization.Serializable

@Serializable
data object DeckRoute

@Serializable
data object ServerConfigRoute

@Serializable
data class IconPickerRoute(val appId: String)

@Composable
fun AppDeckNavHost(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = DeckRoute) {
        composable<DeckRoute> {
            DeckScreen(
                container = container,
                onOpenSettings = { navController.navigate(ServerConfigRoute) },
                onEditIcon = { appId -> navController.navigate(IconPickerRoute(appId)) },
            )
        }
        composable<ServerConfigRoute> {
            ServerConfigScreen(
                container = container,
                onDone = { navController.popBackStack() },
            )
        }
        composable<IconPickerRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<IconPickerRoute>()
            IconPickerScreen(
                container = container,
                appId = route.appId,
                onDone = { navController.popBackStack() },
            )
        }
    }
}
