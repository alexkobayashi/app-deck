package dev.alexkobayashi.appdeck.data.repository

import dev.alexkobayashi.appdeck.data.prefs.ServerConfigDataStore
import dev.alexkobayashi.appdeck.data.remote.ApiResult
import dev.alexkobayashi.appdeck.data.remote.HealthProbe
import dev.alexkobayashi.appdeck.data.remote.dto.HealthDto
import dev.alexkobayashi.appdeck.domain.model.ServerConfig
import dev.alexkobayashi.appdeck.domain.model.ServerConfigState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn

/**
 * As interfaces existem para que os ViewModels sejam testáveis com dublês
 * simples, sem DataStore, sem Room e sem rede.
 */
interface ServerConfigRepository {

    val state: StateFlow<ServerConfigState>

    /** Configuração para uso síncrono nos interceptors do OkHttp. */
    val currentConfig: ServerConfig?

    /**
     * Espera a primeira leitura do DataStore.
     *
     * Os repositórios chamam isso antes de qualquer requisição: assim, quando
     * o interceptor rodar, a configuração já está em memória e não há corrida
     * no arranque frio do app.
     */
    suspend fun awaitLoaded(): ServerConfigState

    suspend fun save(config: ServerConfig)

    suspend fun clear()

    /** Testa um endereço sem salvá-lo. */
    suspend fun testConnection(config: ServerConfig): ApiResult<HealthDto>
}

class DefaultServerConfigRepository(
    private val dataStore: ServerConfigDataStore,
    private val healthProbe: HealthProbe,
    scope: CoroutineScope,
) : ServerConfigRepository {

    /**
     * Eagerly porque os interceptors leem [currentConfig] de forma síncrona:
     * a leitura do DataStore precisa começar assim que o app sobe, não quando
     * alguém coletar.
     */
    override val state: StateFlow<ServerConfigState> =
        dataStore.state.stateIn(scope, SharingStarted.Eagerly, ServerConfigState.Loading)

    override val currentConfig: ServerConfig?
        get() = state.value.configOrNull

    override suspend fun awaitLoaded(): ServerConfigState =
        state.first { it !is ServerConfigState.Loading }

    override suspend fun save(config: ServerConfig) = dataStore.save(config)

    override suspend fun clear() = dataStore.clear()

    override suspend fun testConnection(config: ServerConfig): ApiResult<HealthDto> =
        healthProbe.check(config)
}
