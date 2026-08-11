package dev.alexkobayashi.appdeck.ui.deck

import app.cash.turbine.test
import dev.alexkobayashi.appdeck.data.remote.ApiError
import dev.alexkobayashi.appdeck.data.remote.ApiResult
import dev.alexkobayashi.appdeck.domain.model.ConnectionStatus
import dev.alexkobayashi.appdeck.domain.model.DeckItem
import dev.alexkobayashi.appdeck.testing.FakeConnectionRepository
import dev.alexkobayashi.appdeck.testing.FakeDeckRepository
import dev.alexkobayashi.appdeck.testing.FakeServerConfigRepository
import dev.alexkobayashi.appdeck.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DeckViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val deck = FakeDeckRepository()
    private val connection = FakeConnectionRepository()
    private val config = FakeServerConfigRepository()

    private fun viewModel() = DeckViewModel(deck, connection, config)

    private val calc = DeckItem(id = "a1", name = "Calculadora", path = "C:\\calc.exe")
    private val chrome = DeckItem(id = "b2", name = "Google Chrome", path = "C:\\chrome.exe")

    @Test
    fun `o deck vem do cache local`() = runTest {
        deck.emit(listOf(calc, chrome))
        config.setConfigured()

        viewModel().uiState.test {
            // O stateIn emite o valor inicial antes do combine convergir, por
            // isso o teste espera o estado montado em vez do primeiro item.
            val state = awaitItemWhere { it.items.isNotEmpty() }
            assertEquals(listOf(calc, chrome), state.items)
            assertTrue(state.isConfigured)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sem servidor configurado o estado sinaliza para a tela de configuracao`() = runTest {
        config.setNotConfigured()

        viewModel().uiState.test {
            val state = awaitItemWhere { !it.isLoadingConfig }
            assertTrue(!state.isConfigured)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tocar no atalho chama launch com o id certo`() = runTest {
        deck.emit(listOf(calc))
        config.setConfigured()
        val vm = viewModel()

        vm.uiState.test {
            awaitItem()
            vm.launch(calc)

            // Sucesso reportado com o nome do atalho na mensagem.
            val message = awaitItemWhere { it.message != null }.message
            assertTrue(message is DeckMessage.Launched)
            assertEquals("Calculadora", (message as DeckMessage.Launched).name)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf("a1"), deck.launched)
    }

    @Test
    fun `falha ao abrir reporta o erro da API`() = runTest {
        deck.emit(listOf(calc))
        config.setConfigured()
        deck.launchResult = ApiResult.Failure(
            ApiError.Server(500, "launch_failed", "não foi possível abrir Calculadora"),
        )
        val vm = viewModel()

        vm.uiState.test {
            awaitItem()
            vm.launch(calc)

            val message = awaitItemWhere { it.message != null }.message
            assertTrue(message is DeckMessage.Failed)
            val error = (message as DeckMessage.Failed).error
            assertTrue(error is ApiError.Server)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `atalho inexistente no servidor dispara recarga do cache`() = runTest {
        deck.emit(listOf(calc))
        config.setConfigured()
        deck.launchResult = ApiResult.Failure(ApiError.NotFound)
        val vm = viewModel()

        vm.uiState.test {
            awaitItem()
            vm.launch(calc)
            awaitItemWhere { it.message != null }
            cancelAndIgnoreRemainingEvents()
        }

        // O cache estava desatualizado: recarregar é a reação correta.
        assertTrue("refreshes = ${deck.refreshCount}", deck.refreshCount >= 1)
    }

    @Test
    fun `toque repetido no mesmo atalho e ignorado enquanto o anterior nao respondeu`() = runTest {
        deck.emit(listOf(calc))
        config.setConfigured()
        deck.suspendLaunch = true
        val vm = viewModel()

        vm.uiState.test {
            awaitItem()
            vm.launch(calc)
            // Espera o atalho entrar em "abrindo".
            awaitItemWhere { calc.id in it.launching }

            vm.launch(calc)
            vm.launch(calc)
            cancelAndIgnoreRemainingEvents()
        }

        deck.releaseLaunch()
        assertEquals(1, deck.launched.size)
    }

    @Test
    fun `reconectar recarrega a lista sozinho`() = runTest {
        config.setConfigured()
        val vm = viewModel()

        vm.uiState.test {
            awaitItem()
            connection.emit(ConnectionStatus.Offline(ApiError.NoConnection()))
            awaitItemWhere { it.connection is ConnectionStatus.Offline }

            connection.emit(ConnectionStatus.Online("v0.3.0"))
            awaitItemWhere { it.connection is ConnectionStatus.Online }
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue("refreshes = ${deck.refreshCount}", deck.refreshCount >= 1)
    }

    @Test
    fun `recarregar manualmente reporta o erro`() = runTest {
        config.setConfigured()
        deck.refreshResult = ApiResult.Failure(ApiError.NoConnection())
        val vm = viewModel()

        vm.uiState.test {
            awaitItem()
            vm.refresh()

            val message = awaitItemWhere { it.message != null }.message
            assertTrue(message is DeckMessage.Failed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `consumir a mensagem limpa o estado`() = runTest {
        deck.emit(listOf(calc))
        config.setConfigured()
        val vm = viewModel()

        vm.uiState.test {
            awaitItem()
            vm.launch(calc)
            val message = awaitItemWhere { it.message != null }.message!!

            vm.consumeMessage(message.id)
            assertEquals(null, awaitItemWhere { it.message == null }.message)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

/**
 * Avança pelas emissões até encontrar uma que satisfaça [predicate].
 *
 * Necessário porque o estado é montado por `combine` de vários fluxos: uma
 * única ação produz emissões intermediárias, e o teste se importa com o
 * estado final, não com a ordem exata em que os fluxos convergem.
 */
private suspend fun app.cash.turbine.ReceiveTurbine<DeckUiState>.awaitItemWhere(
    predicate: (DeckUiState) -> Boolean,
): DeckUiState {
    repeat(30) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
    error("nenhuma emissão satisfez a condição em 30 tentativas")
}
