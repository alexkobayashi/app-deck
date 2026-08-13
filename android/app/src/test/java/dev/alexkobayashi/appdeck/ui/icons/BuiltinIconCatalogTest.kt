package dev.alexkobayashi.appdeck.ui.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guarda os invariantes do catálogo, não o seu conteúdo: acrescentar um ícone
 * não deve exigir editar teste, mas apagar um asset ou renomear um arquivo
 * precisa falhar aqui, e não na tela do usuário.
 */
class BuiltinIconCatalogTest {

    @Test
    fun `todo icone aponta para um recurso existente`() {
        // R.drawable.x nao compila se o arquivo nao existe, e vale 0 se o
        // recurso for resolvido para nada.
        val semRecurso = BuiltinIconCatalog.all.filter { it.res == 0 }

        assertTrue("ícones sem drawable: ${semRecurso.map { it.key }}", semRecurso.isEmpty())
    }

    @Test
    fun `nao ha chave duplicada`() {
        val chaves = BuiltinIconCatalog.all.map { it.key }

        assertEquals(chaves.size, chaves.distinct().size)
    }

    @Test
    fun `a chave e um nome valido de recurso android`() {
        // A chave vira ic_builtin_<chave> em res/, onde maiúscula, hífen e
        // acento são inválidos. Um nome fora disso não compilaria, mas o
        // teste diz *por que*.
        val valida = Regex("[a-z][a-z0-9_]*")
        val invalidas = BuiltinIconCatalog.all.map { it.key }.filterNot { valida.matches(it) }

        assertTrue("chaves inválidas: $invalidas", invalidas.isEmpty())
    }

    @Test
    fun `todo icone tem rotulo para o leitor de tela`() {
        val semRotulo = BuiltinIconCatalog.all.filter { it.label.isBlank() }

        assertTrue("ícones sem label: ${semRotulo.map { it.key }}", semRotulo.isEmpty())
    }

    @Test
    fun `nenhum grupo esta vazio`() {
        val vazios = BuiltinIconCatalog.groups.filter { it.icons.isEmpty() }

        assertTrue("grupos vazios: ${vazios.map { it.label }}", vazios.isEmpty())
    }

    @Test
    fun `find devolve o icone da chave e nulo para chave desconhecida`() {
        val algum = BuiltinIconCatalog.all.first()

        assertNotNull(BuiltinIconCatalog.find(algum.key))
        assertEquals(algum, BuiltinIconCatalog.find(algum.key))
        assertNull(BuiltinIconCatalog.find("nao_existe_no_pacote"))
    }
}
