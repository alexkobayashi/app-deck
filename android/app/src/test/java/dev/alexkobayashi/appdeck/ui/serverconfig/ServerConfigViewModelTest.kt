package dev.alexkobayashi.appdeck.ui.serverconfig

import dev.alexkobayashi.appdeck.data.remote.ApiError
import dev.alexkobayashi.appdeck.data.remote.ApiResult
import dev.alexkobayashi.appdeck.domain.model.ServerConfig
import dev.alexkobayashi.appdeck.testing.FakeServerConfigRepository
import dev.alexkobayashi.appdeck.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerConfigViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val repository = FakeServerConfigRepository()

    private fun viewModel() = ServerConfigViewModel(repository)

    @Test
    fun `pre-preenche com a configuracao salva`() = runTest {
        repository.setConfigured(ServerConfig("192.168.1.7", 5051, "tok"))
        val vm = viewModel()

        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("192.168.1.7", state.host)
        assertEquals("5051", state.port)
        assertEquals("tok", state.token)
    }

    @Test
    fun `porta padrao aparece no formulario vazio`() = runTest {
        repository.setNotConfigured()
        val vm = viewModel()

        advanceUntilIdle()

        assertEquals(ServerConfig.DEFAULT_PORT.toString(), vm.uiState.value.port)
    }

    @Test
    fun `campo de porta aceita apenas digitos`() = runTest {
        val vm = viewModel()

        vm.onPortChange("50a5b0")

        assertEquals("5050", vm.uiState.value.port)
    }

    @Test
    fun `porta fora do intervalo invalida o formulario`() = runTest {
        val vm = viewModel()
        vm.onHostChange("192.168.0.10")
        vm.onTokenChange("tok")

        vm.onPortChange("70000")

        assertTrue(vm.uiState.value.portError)
        assertNull(vm.uiState.value.config)
        assertFalse(vm.uiState.value.canSubmit)
    }

    @Test
    fun `host com esquema ou porta embutida e recusado`() = runTest {
        val vm = viewModel()
        vm.onTokenChange("tok")

        // Erros que um usuário comete de verdade ao copiar do navegador.
        listOf("http://192.168.0.10", "192.168.0.10:5050", "192.168.0.10/deck", "192.168 .0.10")
            .forEach { invalid ->
                vm.onHostChange(invalid)
                assertTrue("deveria recusar $invalid", vm.uiState.value.hostError)
                assertNull(vm.uiState.value.config)
            }
    }

    @Test
    fun `nome de host e aceito, nao so IP`() = runTest {
        val vm = viewModel()
        vm.onTokenChange("tok")

        vm.onHostChange("meu-pc.local")

        assertFalse(vm.uiState.value.hostError)
        assertEquals("meu-pc.local", vm.uiState.value.config?.host)
    }

    @Test
    fun `testar conexao com sucesso mostra a versao do servidor`() = runTest {
        val vm = viewModel()
        vm.onHostChange("192.168.0.10")
        vm.onTokenChange("tok")

        vm.test()
        advanceUntilIdle()

        val result = vm.uiState.value.testResult
        assertTrue(result is TestResult.Ok)
        assertEquals("v0.3.0", (result as TestResult.Ok).serverVersion)
    }

    @Test
    fun `testar conexao com falha mostra o erro sem salvar`() = runTest {
        repository.testResult = ApiResult.Failure(ApiError.Unauthorized)
        val vm = viewModel()
        vm.onHostChange("192.168.0.10")
        vm.onTokenChange("errado")

        vm.test()
        advanceUntilIdle()

        assertEquals(ApiError.Unauthorized, (vm.uiState.value.testResult as TestResult.Failed).error)
        // Testar nunca persiste: a configuração ruim não pode virar a salva.
        assertNull(repository.saved)
    }

    @Test
    fun `editar um campo limpa o resultado do teste anterior`() = runTest {
        val vm = viewModel()
        vm.onHostChange("192.168.0.10")
        vm.onTokenChange("tok")
        vm.test()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.testResult != null)

        vm.onHostChange("192.168.0.11")

        assertNull(vm.uiState.value.testResult)
    }

    @Test
    fun `salvar persiste a configuracao e sinaliza para a UI`() = runTest {
        val vm = viewModel()
        vm.onHostChange(" 192.168.0.10 ")
        vm.onPortChange("5050")
        vm.onTokenChange(" tok ")

        vm.save()
        advanceUntilIdle()

        // Espaços nas pontas são removidos: colar de um QR ou de um chat
        // costuma trazer sujeira.
        assertEquals(ServerConfig("192.168.0.10", 5050, "tok"), repository.saved)
        assertTrue(vm.uiState.value.justSaved)
    }

    @Test
    fun `salvar com formulario incompleto nao faz nada`() = runTest {
        val vm = viewModel()
        vm.onHostChange("192.168.0.10")
        // Sem token.

        vm.save()
        advanceUntilIdle()

        assertNull(repository.saved)
        assertFalse(vm.uiState.value.justSaved)
    }
}
