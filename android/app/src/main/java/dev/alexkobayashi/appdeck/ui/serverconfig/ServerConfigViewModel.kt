package dev.alexkobayashi.appdeck.ui.serverconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.alexkobayashi.appdeck.AppContainer
import dev.alexkobayashi.appdeck.data.remote.ApiError
import dev.alexkobayashi.appdeck.data.remote.ApiResult
import dev.alexkobayashi.appdeck.data.repository.ServerConfigRepository
import dev.alexkobayashi.appdeck.domain.model.ServerConfig
import dev.alexkobayashi.appdeck.domain.model.ServerConfigState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface TestResult {
    data class Ok(val serverVersion: String?) : TestResult
    data class Failed(val error: ApiError) : TestResult
}

data class ServerConfigUiState(
    val host: String = "",
    val port: String = ServerConfig.DEFAULT_PORT.toString(),
    val token: String = "",
    val isTesting: Boolean = false,
    val testResult: TestResult? = null,
    val justSaved: Boolean = false,
) {
    val hostError: Boolean get() = host.isNotEmpty() && !isHostValid(host)
    val portError: Boolean get() = port.isNotEmpty() && parsedPort == null

    val parsedPort: Int?
        get() = port.toIntOrNull()?.takeIf { it in ServerConfig.VALID_PORTS }

    /** Configuração montada a partir do formulário, se estiver completa. */
    val config: ServerConfig?
        get() {
            val p = parsedPort ?: return null
            if (!isHostValid(host) || token.isBlank()) return null
            return ServerConfig(host.trim(), p, token.trim())
        }

    val canSubmit: Boolean get() = config != null && !isTesting
}

/**
 * Validação deliberadamente permissiva: aceita IP e também nome de host, para
 * não recusar um `meu-pc.local`. O que é recusado é o que quebraria a URL —
 * espaços, esquema e porta embutida.
 */
internal fun isHostValid(host: String): Boolean {
    val trimmed = host.trim()
    if (trimmed.isBlank()) return false
    if (trimmed.any { it.isWhitespace() }) return false
    return !trimmed.contains("://") && !trimmed.contains('/') && !trimmed.contains(':')
}

class ServerConfigViewModel(
    private val repository: ServerConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerConfigUiState())
    val uiState: StateFlow<ServerConfigUiState> = _uiState.asStateFlow()

    init {
        // Pré-preenche com o que já está salvo, para editar em vez de digitar
        // tudo de novo.
        viewModelScope.launch {
            val current = (repository.awaitLoaded() as? ServerConfigState.Ready)?.config ?: return@launch
            _uiState.update {
                it.copy(
                    host = current.host,
                    port = current.port.toString(),
                    token = current.token,
                )
            }
        }
    }

    fun onHostChange(value: String) = update { it.copy(host = value, testResult = null) }

    // Filtra para dígitos: um teclado numérico ainda deixa colar texto.
    fun onPortChange(value: String) =
        update { it.copy(port = value.filter(Char::isDigit).take(5), testResult = null) }

    fun onTokenChange(value: String) = update { it.copy(token = value, testResult = null) }

    fun test() {
        val config = _uiState.value.config ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, testResult = null) }
            val result = repository.testConnection(config)
            _uiState.update {
                it.copy(
                    isTesting = false,
                    testResult = when (result) {
                        is ApiResult.Success -> TestResult.Ok(result.value.version)
                        is ApiResult.Failure -> TestResult.Failed(result.error)
                    },
                )
            }
        }
    }

    fun save() {
        val config = _uiState.value.config ?: return
        viewModelScope.launch {
            repository.save(config)
            _uiState.update { it.copy(justSaved = true) }
        }
    }

    fun consumeSaved() = update { it.copy(justSaved = false) }

    private inline fun update(block: (ServerConfigUiState) -> ServerConfigUiState) {
        _uiState.update(block)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ServerConfigViewModel(container.serverConfigRepository) }
        }
    }
}
