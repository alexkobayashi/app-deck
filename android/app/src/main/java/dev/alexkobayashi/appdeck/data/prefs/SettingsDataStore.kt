package dev.alexkobayashi.appdeck.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.alexkobayashi.appdeck.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Preferências de aparência do app.
 *
 * Arquivo separado do `server_config` de propósito: o token não divide arquivo
 * com ajuste cosmético, e um dia limpar a configuração do servidor não deve
 * levar o tema embora.
 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
)

/**
 * O DataStore entra pelo construtor, e não pela extension de [Context], para o
 * teste poder apontar para um arquivo temporário. A extension é um singleton
 * por processo: usada aqui dentro, ela amarraria todos os testes ao mesmo
 * arquivo.
 */
class SettingsDataStore(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.settingsDataStore)

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        ThemeMode.fromKey(prefs[KEY_THEME_MODE])
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.key
        }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
