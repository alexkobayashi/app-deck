package dev.alexkobayashi.appdeck.domain.model

/**
 * Um atalho do deck, já combinando o que veio do servidor com a
 * customização local.
 *
 * O [id] vem do servidor e é estável: é por ele que o ícone escolhido fica
 * amarrado ao atalho, sobrevivendo a renomeações e a trocas de caminho.
 */
data class DeckItem(
    val id: String,
    val name: String,
    val path: String,
    val args: List<String> = emptyList(),
    val icon: ShortcutIcon = ShortcutIcon.Initials(""),
    /** Posição escolhida pelo usuário; ausente, vale a ordem do servidor. */
    val sortOrder: Int? = null,
) {
    /** Iniciais usadas quando não há ícone escolhido. */
    val initials: String
        get() = initialsOf(name)

    companion object {
        fun initialsOf(name: String): String = name.trim()
            .split(' ', '-', '_')
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifEmpty { "?" }
    }
}
