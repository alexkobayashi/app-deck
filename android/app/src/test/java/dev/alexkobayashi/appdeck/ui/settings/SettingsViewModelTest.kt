package dev.alexkobayashi.appdeck.ui.settings

import dev.alexkobayashi.appdeck.domain.model.ThemeMode
import dev.alexkobayashi.appdeck.testing.FakeSettingsRepository
import dev.alexkobayashi.appdeck.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val repository = FakeSettingsRepository()

    private fun viewModel() = SettingsViewModel(repository)

    @Test
    fun `comeca acompanhando o sistema`() = runTest {
        val vm = viewModel()

        advanceUntilIdle()

        assertEquals(ThemeMode.System, vm.themeMode.value)
        assertNull(repository.savedMode)
    }

    @Test
    fun `escolher um tema persiste a preferencia`() = runTest {
        val vm = viewModel()

        vm.chooseThemeMode(ThemeMode.Dark)
        advanceUntilIdle()

        assertEquals(ThemeMode.Dark, repository.savedMode)
    }

    @Test
    fun `o estado exposto reflete o que foi gravado`() = runTest {
        val vm = viewModel()

        vm.chooseThemeMode(ThemeMode.Light)
        advanceUntilIdle()

        // A tela lê o StateFlow do repositório, não uma cópia: sem isso, a
        // marcação do rádio poderia divergir do tema que está na tela.
        assertEquals(ThemeMode.Light, vm.themeMode.value)
    }
}
