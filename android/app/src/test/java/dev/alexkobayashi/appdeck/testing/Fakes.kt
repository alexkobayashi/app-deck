package dev.alexkobayashi.appdeck.testing

import dev.alexkobayashi.appdeck.data.remote.ApiResult
import dev.alexkobayashi.appdeck.data.remote.dto.HealthDto
import dev.alexkobayashi.appdeck.data.repository.ConnectionRepository
import dev.alexkobayashi.appdeck.data.repository.DeckRepository
import dev.alexkobayashi.appdeck.data.repository.ServerConfigRepository
import dev.alexkobayashi.appdeck.domain.model.ConnectionStatus
import dev.alexkobayashi.appdeck.domain.model.DeckItem
import dev.alexkobayashi.appdeck.domain.model.ServerConfig
import dev.alexkobayashi.appdeck.domain.model.ServerConfigState
import dev.alexkobayashi.appdeck.domain.model.ShortcutIcon
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

class FakeDeckRepository : DeckRepository {

    private val items = MutableStateFlow<List<DeckItem>>(emptyList())

    var refreshResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var launchResult: ApiResult<Unit> = ApiResult.Success(Unit)

    /** Quando true, [launch] fica pendurado até [releaseLaunch]. */
    var suspendLaunch: Boolean = false

    var refreshCount: Int = 0
        private set

    val launched = mutableListOf<String>()

    private var gate = CompletableDeferred<Unit>()

    fun emit(list: List<DeckItem>) {
        items.value = list
    }

    fun releaseLaunch() {
        gate.complete(Unit)
    }

    override fun observeDeck(): Flow<List<DeckItem>> = items

    override suspend fun refresh(): ApiResult<Unit> {
        refreshCount++
        return refreshResult
    }

    override suspend fun launch(id: String): ApiResult<Unit> {
        launched += id
        if (suspendLaunch) {
            gate = CompletableDeferred()
            gate.await()
        }
        return launchResult
    }

    override suspend fun findItem(id: String): DeckItem? = items.value.firstOrNull { it.id == id }

    override suspend fun setIcon(appId: String, icon: ShortcutIcon) {
        iconsSet += appId to icon
        items.value = items.value.map { if (it.id == appId) it.copy(icon = icon) else it }
    }

    var imageIconSucceeds = true

    override suspend fun setImageIcon(appId: String, source: android.net.Uri): Boolean {
        if (!imageIconSucceeds) return false
        setIcon(appId, ShortcutIcon.Local("ic_$appId.webp", 1L))
        return true
    }

    override suspend fun clearIcon(appId: String) {
        iconsCleared += appId
        items.value = items.value.map {
            if (it.id == appId) it.copy(icon = ShortcutIcon.Initials(it.initials)) else it
        }
    }

    val iconsSet = mutableListOf<Pair<String, ShortcutIcon>>()
    val iconsCleared = mutableListOf<String>()
}

class FakeConnectionRepository : ConnectionRepository {

    private val status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Unknown)

    fun emit(value: ConnectionStatus) {
        status.value = value
    }

    override suspend fun check(): ConnectionStatus = status.value

    override fun statusFlow(): Flow<ConnectionStatus> = status
}

class FakeServerConfigRepository : ServerConfigRepository {

    private val _state = MutableStateFlow<ServerConfigState>(ServerConfigState.Loading)

    var testResult: ApiResult<HealthDto> = ApiResult.Success(HealthDto(status = "ok", version = "v0.3.0"))
    var saved: ServerConfig? = null
        private set
    var cleared: Boolean = false
        private set

    fun setConfigured(config: ServerConfig = DEFAULT_CONFIG) {
        _state.value = ServerConfigState.Ready(config)
    }

    fun setNotConfigured() {
        _state.value = ServerConfigState.NotConfigured
    }

    override val state: StateFlow<ServerConfigState> = _state

    override val currentConfig: ServerConfig?
        get() = _state.value.configOrNull

    override suspend fun awaitLoaded(): ServerConfigState =
        _state.first { it !is ServerConfigState.Loading }

    override suspend fun save(config: ServerConfig) {
        saved = config
        _state.value = ServerConfigState.Ready(config)
    }

    override suspend fun clear() {
        cleared = true
        _state.value = ServerConfigState.NotConfigured
    }

    override suspend fun testConnection(config: ServerConfig): ApiResult<HealthDto> = testResult

    companion object {
        val DEFAULT_CONFIG = ServerConfig("192.168.0.10", 5050, "token-de-teste-comprido-123456")
    }
}
