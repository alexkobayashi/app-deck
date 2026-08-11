package dev.alexkobayashi.appdeck.domain.model

/**
 * Ícone de um atalho, escolhido e guardado **localmente** no aparelho.
 *
 * O servidor manda apenas nome e caminho — ele nunca sabe nada sobre ícones.
 * A escolha fica amarrada ao [DeckItem.id], que é estável, então renomear o
 * atalho ou trocar o executável no servidor não perde o ícone.
 */
sealed interface ShortcutIcon {

    /** Emoji digitado ou escolhido no catálogo. Mantém a ideia do protótipo. */
    data class Emoji(val char: String) : ShortcutIcon

    /**
     * Ícone do pacote embutido, identificado por uma chave estável.
     *
     * A chave é o que vai para o banco — nunca o id do recurso, que muda a
     * cada build e deixaria o ícone apontando para outro desenho.
     */
    data class Builtin(val key: String) : ShortcutIcon

    /**
     * Imagem escolhida na galeria, salva no armazenamento interno do app.
     *
     * [updatedAt] entra na chave de cache do Coil: sem isso, trocar a imagem
     * mantendo o mesmo nome de arquivo continuaria exibindo a antiga.
     */
    data class Local(val fileName: String, val updatedAt: Long) : ShortcutIcon

    /** Ausência de escolha: as iniciais do nome num círculo. */
    data class Initials(val text: String) : ShortcutIcon
}

/** Como o ícone é persistido. Room converte o enum sozinho. */
enum class IconType {
    NONE,
    EMOJI,
    BUILTIN,
    IMAGE,
}
