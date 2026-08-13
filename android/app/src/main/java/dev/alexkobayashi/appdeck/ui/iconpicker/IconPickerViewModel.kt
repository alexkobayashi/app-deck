package dev.alexkobayashi.appdeck.ui.iconpicker

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.alexkobayashi.appdeck.AppContainer
import dev.alexkobayashi.appdeck.data.repository.DeckRepository
import dev.alexkobayashi.appdeck.domain.model.DeckItem
import dev.alexkobayashi.appdeck.domain.model.ShortcutIcon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class IconPickerViewModel(
    private val appId: String,
    private val deckRepository: DeckRepository,
) : ViewModel() {

    /**
     * Observa o atalho em vez de guardar uma cópia: a pré-visualização passa
     * a refletir a escolha assim que ela é gravada, sem estado duplicado.
     */
    val item: StateFlow<DeckItem?> = deckRepository.observeDeck()
        .map { list -> list.firstOrNull { it.id == appId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _imageError = MutableStateFlow(false)
    val imageError: StateFlow<Boolean> = _imageError.asStateFlow()

    fun chooseEmoji(char: String) {
        viewModelScope.launch { deckRepository.setIcon(appId, ShortcutIcon.Emoji(char)) }
    }

    fun chooseBuiltin(key: String) {
        viewModelScope.launch { deckRepository.setIcon(appId, ShortcutIcon.Builtin(key)) }
    }

    /**
     * Copia a imagem escolhida na galeria e a define como ícone.
     *
     * A cópia é imediata porque a permissão que o Photo Picker concede é
     * temporária — guardar só a Uri deixaria o ícone quebrado depois.
     */
    fun chooseImage(uri: Uri) {
        viewModelScope.launch {
            val ok = deckRepository.setImageIcon(appId, uri)
            _imageError.value = !ok
        }
    }

    fun consumeImageError() {
        _imageError.value = false
    }

    fun clearIcon() {
        viewModelScope.launch { deckRepository.clearIcon(appId) }
    }

    companion object {
        fun factory(container: AppContainer, appId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { IconPickerViewModel(appId, container.deckRepository) }
            }
    }
}
