package dev.alexkobayashi.appdeck.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.alexkobayashi.appdeck.domain.model.ServerConfig
import dev.alexkobayashi.appdeck.domain.model.ServerConfigState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Guarda a configuração do servidor.
 *
 * DataStore em vez de Room porque é um registro único, sem consulta. O
 * arquivo fica no armazenamento privado do app e o backup está desligado no
 * manifest, então o token não sai do aparelho.
 */
private val Context.serverConfigDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "server_config",
)

class ServerConfigDataStore(context: Context) {

    private val dataStore = context.serverConfigDataStore

    val state: Flow<ServerConfigState> = dataStore.data.map { prefs ->
        val host = prefs[KEY_HOST]?.trim().orEmpty()
        val port = prefs[KEY_PORT] ?: 0
        val token = prefs[KEY_TOKEN].orEmpty()
        val config = ServerConfig(host, port, token)
        if (config.isValid) ServerConfigState.Ready(config) else ServerConfigState.NotConfigured
    }

    suspend fun save(config: ServerConfig) {
        dataStore.edit { prefs ->
            prefs[KEY_HOST] = config.host.trim()
            prefs[KEY_PORT] = config.port
            prefs[KEY_TOKEN] = config.token.trim()
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_HOST = stringPreferencesKey("host")
        val KEY_PORT = intPreferencesKey("port")
        val KEY_TOKEN = stringPreferencesKey("token")
    }
}
