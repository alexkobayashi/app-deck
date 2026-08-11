package dev.alexkobayashi.appdeck.domain.model

/**
 * Um atalho do deck.
 *
 * O [id] vem do servidor e é estável: é por ele que a customização de ícone
 * (fase seguinte) fica amarrada ao atalho, sobrevivendo a renomeações.
 */
data class DeckItem(
    val id: String,
    val name: String,
    val path: String,
    val args: List<String> = emptyList(),
) {
    /** Iniciais usadas no ícone padrão, enquanto não há ícone customizado. */
    val initials: String
        get() = name.trim()
            .split(' ', '-', '_')
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifEmpty { "?" }
}
