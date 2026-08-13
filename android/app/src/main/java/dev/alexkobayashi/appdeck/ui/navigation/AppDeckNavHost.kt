package dev.alexkobayashi.appdeck.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.alexkobayashi.appdeck.AppContainer
import dev.alexkobayashi.appdeck.ui.deck.DeckScreen
import dev.alexkobayashi.appdeck.ui.editor.ShortcutEditorScreen
import dev.alexkobayashi.appdeck.ui.iconpicker.IconPickerScreen
import dev.alexkobayashi.appdeck.ui.serverconfig.ServerConfigScreen
import dev.alexkobayashi.appdeck.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
data object DeckRoute

@Serializable
data object ServerConfigRoute

@Serializable
data object SettingsRoute

@Serializable
data class IconPickerRoute(val appId: String)

/** appId nulo significa criar um atalho novo. */
@Serializable
data class ShortcutEditorRoute(val appId: String? = null)

@Composable
fun AppDeckNavHost(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = DeckRoute) {
        composable<DeckRoute> {
            DeckScreen(
                container = container,
                onOpenSettings = { navController.navigate(ServerConfigRoute) },
                onOpenAppSettings = { navController.navigate(SettingsRoute) },
                onEditIcon = { appId -> navController.navigate(IconPickerRoute(appId)) },
                onAddShortcut = { navController.navigate(ShortcutEditorRoute()) },
                onEditShortcut = { appId -> navController.navigate(ShortcutEditorRoute(appId)) },
            )
        }

        composable<ServerConfigRoute> {
            ServerConfigScreen(
                container = container,
                onDone = { navController.popBackStack() },
            )
        }

        composable<SettingsRoute> {
            SettingsScreen(
                container = container,
                onDone = { navController.popBackStack() },
            )
        }

        composable<IconPickerRoute> { backStackEntry ->
            IconPickerScreen(
                container = container,
                appId = backStackEntry.toRoute<IconPickerRoute>().appId,
                onDone = { navController.popBackStack() },
            )
        }

        composable<ShortcutEditorRoute> { backStackEntry ->
            ShortcutEditorScreen(
                container = container,
                appId = backStackEntry.toRoute<ShortcutEditorRoute>().appId,
                onDone = { navController.popBackStack() },
                onCreated = { appId ->
                    // Sai do editor e abre o seletor de ícone do atalho novo,
                    // sem deixar o editor no histórico: voltar dali deve levar
                    // ao deck, não a um formulário já salvo.
                    navController.popBackStack()
                    navController.navigate(IconPickerRoute(appId))
                },
            )
        }
    }
}
