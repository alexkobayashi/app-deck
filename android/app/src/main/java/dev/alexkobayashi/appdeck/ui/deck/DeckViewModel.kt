package dev.alexkobayashi.appdeck.ui.deck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.alexkobayashi.appdeck.AppContainer
import dev.alexkobayashi.appdeck.data.remote.ApiError
import dev.alexkobayashi.appdeck.data.remote.ApiResult
import dev.alexkobayashi.appdeck.data.repository.ConnectionRepository
import dev.alexkobayashi.appdeck.data.repository.DeckRepository
import dev.alexkobayashi.appdeck.data.repository.ServerConfigRepository
import dev.alexkobayashi.appdeck.domain.model.ConnectionStatus
import dev.alexkobayashi.appdeck.domain.model.DeckItem
import dev.alexkobayashi.appdeck.domain.model.ServerConfigState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Mensagem efêmera para a snackbar. */
sealed interface DeckMessage {
    val id: Long

    data class Launched(override val id: Long, val name: String) : DeckMessage
    data class Failed(override val id: Long, val error: ApiError) : DeckMessage
}

data class DeckUiState(
    val items: List<DeckItem> = emptyList(),
    val connection: ConnectionStatus = ConnectionStatus.Unknown,
    val configState: ServerConfigState = ServerConfigState.Loading,
    val isRefreshing: Boolean = false,
    /** Ids dos atalhos com abertura em andamento — o tile mostra um spinner. */
    val launching: Set<String> = emptySet(),
    val message: DeckMessage? = null,
) {
    val isConfigured: Boolean get() = configState is ServerConfigState.Ready
    val isLoadingConfig: Boolean get() = configState is ServerConfigState.Loading
}

class DeckViewModel(
    private val deckRepository: DeckRepository,
    connectionRepository: ConnectionRepository,
    private val serverConfigRepository: ServerConfigRepository,
) : ViewModel() {

    /** Parte do estado que só existe na UI (não vem de repositório). */
    private data class LocalState(
        val isRefreshing: Boolean = false,
        val launching: Set<String> = emptySet(),
        val message: DeckMessage? = null,
    )

    private val local = MutableStateFlow(LocalState())
    private var messageCounter = 0L
    private var wasOnline = false

    /**
     * O `onEach` fica dentro do fluxo (e não num `collect` no init) de
     * propósito: assim o recarregamento automático e o próprio laço de polling
     * só rodam enquanto a tela está coletando. Um collect no init manteria uma
     * assinatura permanente e o `WhileSubscribed` nunca desligaria o polling.
     */
    private val connection: StateFlow<ConnectionStatus> = connectionRepository.statusFlow()
        .onEach(::refreshOnReconnect)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionStatus.Unknown)

    val uiState: StateFlow<DeckUiState> = combine(
        deckRepository.observeDeck(),
        connection,
        serverConfigRepository.state,
        local,
    ) { items, connection, configState, local ->
        DeckUiState(
            items = items,
            connection = connection,
            configState = configState,
            isRefreshing = local.isRefreshing,
            launching = local.launching,
            message = local.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeckUiState())

    /** Recarrega a lista quando o servidor volta a responder. */
    private fun refreshOnReconnect(status: ConnectionStatus) {
        val online = status.isOnline
        if (online && !wasOnline) {
            viewModelScope.launch { runRefresh(reportErrors = false) }
        }
        wasOnline = online
    }

    fun refresh() {
        viewModelScope.launch { runRefresh(reportErrors = true) }
    }

    private suspend fun runRefresh(reportErrors: Boolean) {
        local.update { it.copy(isRefreshing = true) }
        val result = deckRepository.refresh()
        local.update { state ->
            state.copy(
                isRefreshing = false,
                message = if (reportErrors && result is ApiResult.Failure) {
                    DeckMessage.Failed(nextMessageId(), result.error)
                } else {
                    state.message
                },
            )
        }
    }

    fun launch(item: DeckItem) {
        // Ignora toques repetidos no mesmo atalho enquanto o anterior não
        // respondeu, para não abrir o programa duas vezes.
        if (item.id in local.value.launching) return

        viewModelScope.launch {
            local.update { it.copy(launching = it.launching + item.id) }
            val result = deckRepository.launch(item.id)
            local.update { state ->
                state.copy(
                    launching = state.launching - item.id,
                    message = when (result) {
                        is ApiResult.Success -> DeckMessage.Launched(nextMessageId(), item.name)
                        is ApiResult.Failure -> DeckMessage.Failed(nextMessageId(), result.error)
                    },
                )
            }
            // Atalho inexistente no servidor significa cache desatualizado.
            if (result is ApiResult.Failure && result.error is ApiError.NotFound) {
                runRefresh(reportErrors = false)
            }
        }
    }

    /** Persiste a ordem depois de um arraste. */
    fun saveOrder(orderedIds: List<String>) {
        viewModelScope.launch { deckRepository.saveOrder(orderedIds) }
    }

    fun consumeMessage(id: Long) {
        local.update { if (it.message?.id == id) it.copy(message = null) else it }
    }

    private fun nextMessageId(): Long = ++messageCounter

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DeckViewModel(
                    deckRepository = container.deckRepository,
                    connectionRepository = container.connectionRepository,
                    serverConfigRepository = container.serverConfigRepository,
                )
            }
        }
    }
}
