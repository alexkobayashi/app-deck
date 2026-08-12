package dev.alexkobayashi.appdeck.ui.editor

import dev.alexkobayashi.appdeck.data.remote.ApiError
import dev.alexkobayashi.appdeck.data.remote.ApiResult
import dev.alexkobayashi.appdeck.domain.model.DeckItem
import dev.alexkobayashi.appdeck.testing.FakeDeckRepository
import dev.alexkobayashi.appdeck.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShortcutEditorViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val deck = FakeDeckRepository()

    private val chrome = DeckItem(
        id = "b2",
        name = "Chrome",
        path = "C:\\chrome.exe",
        args = listOf("--incognito"),
    )

    private fun viewModel(appId: String? = null) = ShortcutEditorViewModel(appId, deck)

    @Test
    fun `formulario novo comeca vazio e nao pode salvar`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isNew)
        assertEquals("", vm.uiState.value.name)
        assertFalse(vm.uiState.value.canSave)
    }

    @Test
    fun `editar pre-preenche com os dados do atalho`() = runTest {
        deck.emit(listOf(chrome))
        val vm = viewModel("b2")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isNew)
        assertEquals("Chrome", state.name)
        assertEquals("C:\\chrome.exe", state.path)
        assertEquals("--incognito", state.args)
    }

    @Test
    fun `nome ou caminho em branco impedem salvar`() = runTest {
        val vm = viewModel()
        vm.onNameChange("Steam")
        assertFalse(vm.uiState.value.canSave)

        vm.onPathChange("   ")
        assertFalse(vm.uiState.value.canSave)

        vm.onPathChange("C:\\steam.exe")
        assertTrue(vm.uiState.value.canSave)
    }

    @Test
    fun `criar envia os campos com espacos removidos`() = runTest {
        val vm = viewModel()
        vm.onNameChange("  Steam  ")
        vm.onPathChange("  C:\\steam.exe  ")
        vm.onArgsChange(" -silent  -no-browser ")

        vm.save()
        advanceUntilIdle()

        assertEquals(
            Triple("Steam", "C:\\steam.exe", listOf("-silent", "-no-browser")),
            deck.created.single(),
        )
    }

    @Test
    fun `criar com sucesso devolve o id para a tela oferecer o icone`() = runTest {
        deck.createResult = ApiResult.Success("id-novo")
        val vm = viewModel()
        vm.onNameChange("Steam")
        vm.onPathChange("C:\\steam.exe")

        vm.save()
        advanceUntilIdle()

        assertEquals(EditorOutcome.Created("id-novo"), vm.uiState.value.outcome)
    }

    @Test
    fun `editar envia o id e mantem o resultado Updated`() = runTest {
        deck.emit(listOf(chrome))
        val vm = viewModel("b2")
        advanceUntilIdle()
        vm.onNameChange("Chrome Anônimo")

        vm.save()
        advanceUntilIdle()

        assertEquals("b2", deck.updated.single().first)
        assertEquals(EditorOutcome.Updated, vm.uiState.value.outcome)
    }

    @Test
    fun `falha do servidor vira Failed com o erro original`() = runTest {
        deck.createResult = ApiResult.Failure(ApiError.Unauthorized)
        val vm = viewModel()
        vm.onNameChange("Steam")
        vm.onPathChange("C:\\steam.exe")

        vm.save()
        advanceUntilIdle()

        assertEquals(EditorOutcome.Failed(ApiError.Unauthorized), vm.uiState.value.outcome)
        // Falhar não pode deixar o botão travado em "salvando".
        assertFalse(vm.uiState.value.isSaving)
    }

    @Test
    fun `excluir chama o repositorio e sinaliza Deleted`() = runTest {
        deck.emit(listOf(chrome))
        val vm = viewModel("b2")
        advanceUntilIdle()

        vm.delete()
        advanceUntilIdle()

        assertEquals(listOf("b2"), deck.deleted)
        assertEquals(EditorOutcome.Deleted, vm.uiState.value.outcome)
    }

    @Test
    fun `excluir num formulario novo nao faz nada`() = runTest {
        val vm = viewModel()

        vm.delete()
        advanceUntilIdle()

        assertTrue(deck.deleted.isEmpty())
    }

    @Test
    fun `parseArgs separa por espaco e descarta vazios`() {
        assertEquals(
            listOf("--incognito", "--new-window"),
            ShortcutEditorViewModel.parseArgs("  --incognito   --new-window  "),
        )
        assertEquals(emptyList<String>(), ShortcutEditorViewModel.parseArgs("   "))
        assertEquals(emptyList<String>(), ShortcutEditorViewModel.parseArgs(""))
    }
}
