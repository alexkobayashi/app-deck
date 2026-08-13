package dev.alexkobayashi.appdeck.domain.model

/**
 * Tema escolhido pelo usuário.
 *
 * [System] é o padrão e é o comportamento que o app sempre teve: seguir o
 * aparelho. As outras duas existem porque o deck é usado apoiado na mesa, num
 * ambiente cuja luz não tem relação com o horário que decide o tema do
 * sistema.
 */
enum class ThemeMode(val key: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
    ;

    companion object {
        /**
         * A chave é o que vai para o disco — nunca o nome da constante, que
         * um rename silencioso transformaria em valor desconhecido, jogando a
         * escolha do usuário de volta para o padrão.
         */
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: System
    }
}
