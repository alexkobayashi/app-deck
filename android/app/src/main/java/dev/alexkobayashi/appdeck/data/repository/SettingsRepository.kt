package dev.alexkobayashi.appdeck.data.repository

import dev.alexkobayashi.appdeck.data.prefs.SettingsDataStore
import dev.alexkobayashi.appdeck.domain.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * A interface existe para o ViewModel ser testável com dublê simples, sem
 * DataStore — o mesmo motivo do [ServerConfigRepository].
 */
interface SettingsRepository {

    val themeMode: StateFlow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)
}

class DefaultSettingsRepository(
    private val dataStore: SettingsDataStore,
    scope: CoroutineScope,
) : SettingsRepository {

    /**
     * Eagerly, e com [ThemeMode.System] como valor inicial: a MainActivity
     * precisa de um tema no primeiro frame, antes de a leitura do disco
     * terminar. System é o comportamento de sempre, então quem não mexeu na
     * preferência não vê troca nenhuma.
     */
    override val themeMode: StateFlow<ThemeMode> =
        dataStore.themeMode.stateIn(scope, SharingStarted.Eagerly, ThemeMode.System)

    override suspend fun setThemeMode(mode: ThemeMode) = dataStore.setThemeMode(mode)
}
