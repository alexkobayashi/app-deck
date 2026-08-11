package dev.alexkobayashi.appdeck.domain.model

/** Endereço e token do servidor App Deck rodando no PC. */
data class ServerConfig(
    val host: String,
    val port: Int,
    val token: String,
) {
    /** Base URL no formato que o Retrofit espera (com barra no fim). */
    val baseUrl: String get() = "http://$host:$port/"

    val isValid: Boolean
        get() = host.isNotBlank() && port in VALID_PORTS && token.isNotBlank()

    companion object {
        val VALID_PORTS = 1..65535
        const val DEFAULT_PORT = 5050
    }
}

/**
 * Estado da configuração.
 *
 * [Loading] existe para distinguir "o DataStore ainda não respondeu" de
 * "o usuário não configurou nada" — sem isso, o app mostraria a tela de
 * configuração por um instante a cada abertura.
 */
sealed interface ServerConfigState {
    data object Loading : ServerConfigState
    data object NotConfigured : ServerConfigState
    data class Ready(val config: ServerConfig) : ServerConfigState

    val configOrNull: ServerConfig?
        get() = (this as? Ready)?.config
}
