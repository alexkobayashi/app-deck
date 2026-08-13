package dev.alexkobayashi.appdeck.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.alexkobayashi.appdeck.AppContainer
import dev.alexkobayashi.appdeck.data.repository.SettingsRepository
import dev.alexkobayashi.appdeck.domain.model.ThemeMode
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /**
     * Expõe o StateFlow do repositório em vez de manter uma cópia: a mesma
     * fonte alimenta a MainActivity, e um estado duplicado aqui poderia
     * divergir do tema que está de fato na tela.
     */
    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode

    fun chooseThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(container.settingsRepository) }
        }
    }
}
