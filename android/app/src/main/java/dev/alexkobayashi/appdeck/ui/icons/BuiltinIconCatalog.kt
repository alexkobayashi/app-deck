package dev.alexkobayashi.appdeck.ui.icons

import androidx.annotation.DrawableRes
import dev.alexkobayashi.appdeck.R

/**
 * Ícones de aplicativos embutidos no APK.
 *
 * A [BuiltinIcon.key] é o que vai para o banco, e vem do nome do arquivo em
 * `res/drawable-nodpi/ic_builtin_<key>.png`. Renomear o arquivo quebra os
 * ícones já escolhidos pelo usuário — a chave é contrato, não detalhe.
 *
 * O mapa é estático de propósito: o release roda com `isShrinkResources`, que
 * remove drawable alcançado apenas por reflexão ou `getIdentifier()`.
 */
object BuiltinIconCatalog {

    data class BuiltinIcon(
        val key: String,
        @DrawableRes val res: Int,
        /**
         * Nome falado pelo leitor de tela. A célula do seletor não mostra
         * texto — igual às de emoji — então sem isto a grade seria 23 imagens
         * indistinguíveis no TalkBack.
         */
        val label: String,
    )

    data class Group(val label: String, val icons: List<BuiltinIcon>)

    val groups: List<Group> = listOf(
        Group(
            "Sistema",
            listOf(
                BuiltinIcon("explorer", R.drawable.ic_builtin_explorer, "Explorador de arquivos"),
                BuiltinIcon("calculator", R.drawable.ic_builtin_calculator, "Calculadora"),
                BuiltinIcon("notes", R.drawable.ic_builtin_notes, "Notas"),
                BuiltinIcon("terminal", R.drawable.ic_builtin_terminal, "Terminal"),
            ),
        ),
        Group(
            "Navegadores",
            listOf(
                BuiltinIcon("chrome", R.drawable.ic_builtin_chrome, "Google Chrome"),
            ),
        ),
        Group(
            "Desenvolvimento",
            listOf(
                BuiltinIcon("vscode", R.drawable.ic_builtin_vscode, "VS Code"),
                BuiltinIcon("github", R.drawable.ic_builtin_github, "GitHub"),
                BuiltinIcon("postman", R.drawable.ic_builtin_postman, "Postman"),
            ),
        ),
        Group(
            "IA",
            listOf(
                BuiltinIcon("chatgpt", R.drawable.ic_builtin_chatgpt, "ChatGPT"),
                BuiltinIcon("claude", R.drawable.ic_builtin_claude, "Claude"),
                BuiltinIcon("gemini", R.drawable.ic_builtin_gemini, "Gemini"),
            ),
        ),
        Group(
            "Comunicação",
            listOf(
                BuiltinIcon("whatsapp", R.drawable.ic_builtin_whatsapp, "WhatsApp"),
                BuiltinIcon("slack", R.drawable.ic_builtin_slack, "Slack"),
                BuiltinIcon("discord", R.drawable.ic_builtin_discord, "Discord"),
                BuiltinIcon(
                    "googlehangouts",
                    R.drawable.ic_builtin_googlehangouts,
                    "Google Hangouts",
                ),
                BuiltinIcon("linkedin", R.drawable.ic_builtin_linkedin, "LinkedIn"),
            ),
        ),
        Group(
            "Produtividade",
            listOf(
                BuiltinIcon("googledrive", R.drawable.ic_builtin_googledrive, "Google Drive"),
                BuiltinIcon("evernote", R.drawable.ic_builtin_evernote, "Evernote"),
            ),
        ),
        Group(
            "Mídia e jogos",
            listOf(
                BuiltinIcon("spotify", R.drawable.ic_builtin_spotify, "Spotify"),
                BuiltinIcon("youtube", R.drawable.ic_builtin_youtube, "YouTube"),
                BuiltinIcon("netflix", R.drawable.ic_builtin_netflix, "Netflix"),
                BuiltinIcon("twitch", R.drawable.ic_builtin_twitch, "Twitch"),
                BuiltinIcon("steam", R.drawable.ic_builtin_steam, "Steam"),
            ),
        ),
    )

    /** Todos os ícones, sem agrupamento. */
    val all: List<BuiltinIcon> = groups.flatMap { it.icons }

    private val byKey: Map<String, BuiltinIcon> = all.associateBy { it.key }

    /**
     * Nulo quando a chave gravada não existe mais no pacote — asset removido
     * ou banco de uma versão anterior. Quem chama cai no ícone de iniciais.
     */
    fun find(key: String): BuiltinIcon? = byKey[key]
}
