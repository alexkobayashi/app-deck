package dev.alexkobayashi.appdeck.ui.serverconfig

import dev.alexkobayashi.appdeck.data.remote.ApiError
import dev.alexkobayashi.appdeck.data.remote.ApiResult
import dev.alexkobayashi.appdeck.data.scanner.QrScanner
import dev.alexkobayashi.appdeck.data.scanner.ScanResult
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

    // --- Pareamento por QR ---

    private class FakeScanner(private val result: ScanResult) : QrScanner {
        override suspend fun scan(): ScanResult = result
    }

    @Test
    fun `escanear um QR valido preenche, valida e salva`() = runTest {
        val vm = viewModel()
        val qr = """{"ip":"192.168.3.186","port":5050,"token":"token-do-servidor"}"""

        vm.scanAndPair(FakeScanner(ScanResult.Success(qr)))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("192.168.3.186", state.host)
        assertEquals("5050", state.port)
        assertEquals("token-do-servidor", state.token)
        assertEquals(ServerConfig("192.168.3.186", 5050, "token-do-servidor"), repository.saved)
        assertTrue(state.justSaved)
    }

    // O ponto central do fluxo: um QR de um servidor que ja mudou de IP nao
    // pode ser persistido, senao vira um deck quebrado descoberto depois.
    @Test
    fun `QR que nao responde e preenchido mas nao salvo`() = runTest {
        repository.testResult = ApiResult.Failure(ApiError.NoConnection())
        val vm = viewModel()
        val qr = """{"ip":"192.168.9.99","port":5050,"token":"tok"}"""

        vm.scanAndPair(FakeScanner(ScanResult.Success(qr)))
        advanceUntilIdle()

        val state = vm.uiState.value
        // Preenchido, para o usuário ver o que foi lido e poder corrigir.
        assertEquals("192.168.9.99", state.host)
        assertNull(repository.saved)
        assertFalse(state.justSaved)
        assertTrue(state.testResult is TestResult.Failed)
    }

    @Test
    fun `QR de outro app vira erro especifico`() = runTest {
        val vm = viewModel()

        vm.scanAndPair(FakeScanner(ScanResult.Success("https://exemplo.com")))
        advanceUntilIdle()

        assertEquals(ScanError.NotAPairingCode, vm.uiState.value.scanError)
        assertNull(repository.saved)
    }

    @Test
    fun `fechar o leitor nao e erro`() = runTest {
        val vm = viewModel()

        vm.scanAndPair(FakeScanner(ScanResult.Cancelled))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNull(state.scanError)
        assertFalse(state.isScanning)
        assertNull(repository.saved)
    }

    @Test
    fun `leitor indisponivel sugere preenchimento manual`() = runTest {
        val vm = viewModel()

        vm.scanAndPair(FakeScanner(ScanResult.Failed(IllegalStateException("sem Play Services"))))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.scanError is ScanError.ScannerUnavailable)
        assertFalse(vm.uiState.value.isScanning)
    }

    // Distinto de ScannerUnavailable porque a acao do usuario e outra: aqui e
    // aguardar o download do modulo, nao desistir e digitar a mao.
    @Test
    fun `modulo ainda nao baixado tem erro proprio, com o detalhe preservado`() = runTest {
        val vm = viewModel()

        vm.scanAndPair(FakeScanner(ScanResult.ModuleUnavailable("installModules: ApiException código 8")))
        advanceUntilIdle()

        val error = vm.uiState.value.scanError
        assertTrue(error is ScanError.ModuleUnavailable)
        // O detalhe tem que chegar à tela: sem cabo USB, ele é o único
        // diagnóstico disponível.
        assertEquals("installModules: ApiException código 8", error?.detail)
        assertFalse(vm.uiState.value.isScanning)
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
