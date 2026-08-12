package dev.alexkobayashi.appdeck.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.alexkobayashi.appdeck.AppContainer
import dev.alexkobayashi.appdeck.data.remote.ApiError
import dev.alexkobayashi.appdeck.data.remote.ApiResult
import dev.alexkobayashi.appdeck.data.repository.DeckRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** O que aconteceu depois de salvar ou excluir, para a tela reagir. */
sealed interface EditorOutcome {
    /** Criado: o id vem junto para a tela oferecer a escolha do ícone. */
    data class Created(val appId: String) : EditorOutcome
    data object Updated : EditorOutcome
    data object Deleted : EditorOutcome
    data class Failed(val error: ApiError) : EditorOutcome
}

data class ShortcutEditorUiState(
    val name: String = "",
    val path: String = "",
    val args: String = "",
    val isNew: Boolean = true,
    val isSaving: Boolean = false,
    val outcome: EditorOutcome? = null,
) {
    val canSave: Boolean
        get() = name.isNotBlank() && path.isNotBlank() && !isSaving
}

class ShortcutEditorViewModel(
    private val appId: String?,
    private val deckRepository: DeckRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShortcutEditorUiState(isNew = appId == null))
    val uiState: StateFlow<ShortcutEditorUiState> = _uiState.asStateFlow()

    init {
        if (appId != null) {
            viewModelScope.launch {
                val item = deckRepository.findItem(appId) ?: return@launch
                _uiState.update {
                    it.copy(
                        name = item.name,
                        path = item.path,
                        // Argumentos numa linha só, separados por espaço.
                        args = item.args.joinToString(" "),
                    )
                }
            }
        }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value) }

    fun onPathChange(value: String) = _uiState.update { it.copy(path = value) }

    fun onArgsChange(value: String) = _uiState.update { it.copy(args = value) }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val name = state.name.trim()
            val path = state.path.trim()
            val args = parseArgs(state.args)

            val outcome = if (appId == null) {
                when (val result = deckRepository.createShortcut(name, path, args)) {
                    is ApiResult.Success -> EditorOutcome.Created(result.value)
                    is ApiResult.Failure -> EditorOutcome.Failed(result.error)
                }
            } else {
                when (val result = deckRepository.updateShortcut(appId, name, path, args)) {
                    is ApiResult.Success -> EditorOutcome.Updated
                    is ApiResult.Failure -> EditorOutcome.Failed(result.error)
                }
            }
            _uiState.update { it.copy(isSaving = false, outcome = outcome) }
        }
    }

    fun delete() {
        val id = appId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val outcome = when (val result = deckRepository.deleteShortcut(id)) {
                is ApiResult.Success -> EditorOutcome.Deleted
                is ApiResult.Failure -> EditorOutcome.Failed(result.error)
            }
            _uiState.update { it.copy(isSaving = false, outcome = outcome) }
        }
    }

    fun consumeOutcome() = _uiState.update { it.copy(outcome = null) }

    companion object {
        /**
         * Separa por espaços simples.
         *
         * Limitação conhecida e documentada na própria tela: um argumento com
         * espaço (um caminho, por exemplo) precisaria de aspas, que a v1 não
         * interpreta. O caso comum é `--incognito`, `-silent` e afins.
         */
        fun parseArgs(raw: String): List<String> =
            raw.split(' ', '\t', '\n').map(String::trim).filter(String::isNotEmpty)

        fun factory(container: AppContainer, appId: String?): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ShortcutEditorViewModel(appId, container.deckRepository) }
            }
    }
}
