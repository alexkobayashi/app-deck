package dev.alexkobayashi.appdeck.ui.icons

/**
 * Catálogo curado de emojis, pensado para atalhos de PC.
 *
 * Lista fixa em vez de um teclado de emoji completo: o objetivo é achar um
 * ícone bom em dois segundos, não navegar por milhares de opções. O usuário
 * que quiser algo fora daqui usa a galeria.
 */
object EmojiCatalog {

    data class Group(val label: String, val emojis: List<String>)

    val groups: List<Group> = listOf(
        Group(
            "Trabalho",
            listOf(
                "📄", "📝", "📊", "📈", "📉", "📋", "🗂️", "📁",
                "📅", "✉️", "📬", "🖇️", "📌", "🔖", "🗒️", "🖨️",
            ),
        ),
        Group(
            "Desenvolvimento",
            listOf(
                "💻", "⌨️", "🖥️", "🐛", "⚙️", "🔧", "🔨", "🧩",
                "🗄️", "🔀", "🧪", "📦", "🚀", "⚡", "🔌", "🧱",
            ),
        ),
        Group(
            "Internet",
            listOf(
                "🌐", "🔍", "☁️", "📡", "🔗", "📶", "🛡️", "🔒",
                "💬", "📨", "🐙", "📰",
            ),
        ),
        Group(
            "Mídia",
            listOf(
                "🎵", "🎶", "🎧", "🎤", "🎬", "📺", "📷", "🖼️",
                "🎨", "🎹", "🎸", "🔊", "🎙️", "📹",
            ),
        ),
        Group(
            "Jogos",
            listOf(
                "🎮", "🕹️", "👾", "🎲", "♟️", "🏆", "🎯", "🧨",
            ),
        ),
        Group(
            "Sistema",
            listOf(
                "🗃️", "💾", "🖱️", "🔋", "🧹", "🗑️", "🔑", "🧰",
                "📐", "🧮", "⏱️", "🔔",
            ),
        ),
        Group(
            "Outros",
            listOf(
                "⭐", "❤️", "🔥", "✅", "❓", "💡", "🏠", "☕",
                "🍕", "🐱", "🐶", "🌙",
            ),
        ),
    )

    /** Todos os emojis, sem agrupamento. */
    val all: List<String> = groups.flatMap { it.emojis }
}
