package dev.alexkobayashi.appdeck.data.repository

import dev.alexkobayashi.appdeck.data.local.ShortcutCustomizationEntity
import dev.alexkobayashi.appdeck.domain.model.DeckItem
import dev.alexkobayashi.appdeck.domain.model.IconType
import dev.alexkobayashi.appdeck.domain.model.ShortcutIcon
import org.junit.Assert.assertEquals
import org.junit.Test

class IconMappingTest {

    @Test
    fun `sem customizacao cai nas iniciais do nome`() {
        val icon = (null as ShortcutCustomizationEntity?).toShortcutIcon("Google Chrome")
        assertEquals(ShortcutIcon.Initials("GC"), icon)
    }

    @Test
    fun `emoji`() {
        val entity = ShortcutCustomizationEntity(
            appId = "a1",
            iconType = IconType.EMOJI,
            iconRef = "🌐",
        )
        assertEquals(ShortcutIcon.Emoji("🌐"), entity.toShortcutIcon("Chrome"))
    }

    @Test
    fun `builtin guarda a chave, nunca o id do recurso`() {
        val entity = ShortcutCustomizationEntity(
            appId = "a1",
            iconType = IconType.BUILTIN,
            iconRef = "browser",
        )
        assertEquals(ShortcutIcon.Builtin("browser"), entity.toShortcutIcon("Chrome"))
    }

    @Test
    fun `imagem carrega o updatedAt para invalidar o cache do Coil`() {
        val entity = ShortcutCustomizationEntity(
            appId = "a1",
            iconType = IconType.IMAGE,
            iconRef = "ic_a1_123.webp",
            updatedAt = 987L,
        )
        assertEquals(ShortcutIcon.Local("ic_a1_123.webp", 987L), entity.toShortcutIcon("Chrome"))
    }

    @Test
    fun `tipo definido com referencia nula cai nas iniciais em vez de ficar vazio`() {
        // Só acontece se alguém editar o banco à mão, mas um tile em branco
        // seria pior que as iniciais.
        listOf(IconType.EMOJI, IconType.BUILTIN, IconType.IMAGE).forEach { type ->
            val entity = ShortcutCustomizationEntity(appId = "a1", iconType = type, iconRef = null)
            assertEquals(
                "tipo $type",
                // "Bloco de Notas" -> B + D: as duas primeiras palavras.
                ShortcutIcon.Initials("BD"),
                entity.toShortcutIcon("Bloco de Notas"),
            )
        }
    }

    @Test
    fun `iniciais`() {
        val cases = mapOf(
            "Google Chrome" to "GC",
            "Calculadora" to "C",
            "VS Code" to "VC",
            "vs-code" to "VC",
            "bloco_de_notas" to "BD",
            "   " to "?",
            "" to "?",
        )
        cases.forEach { (name, expected) ->
            assertEquals(name, expected, DeckItem.initialsOf(name))
        }
    }
}
