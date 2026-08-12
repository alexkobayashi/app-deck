package dev.alexkobayashi.appdeck.domain.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Conteúdo do QR code gerado pelo servidor: `{"ip", "port", "token"}`.
 *
 * O contrato está em docs/api.md.
 */
data class PairingPayload(
    val ip: String,
    val port: Int,
    val token: String,
) {
    fun toServerConfig(): ServerConfig = ServerConfig(ip, port, token)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Interpreta o texto lido do QR, ou devolve null se não for um
         * pareamento válido.
         *
         * A leitura é feita campo a campo em vez de por desserialização
         * direta porque `port` precisa ser aceito como **número ou string**:
         * o servidor sempre manda número, mas o config.json do protótipo
         * gravava a porta como string, e ser tolerante aqui é barato.
         */
        fun parse(raw: String): PairingPayload? {
            val obj = try {
                json.parseToJsonElement(raw.trim()) as? JsonObject
            } catch (e: Exception) {
                null
            } ?: return null

            val ip = obj["ip"]?.jsonPrimitiveOrNull()?.content?.trim().orEmpty()
            val token = obj["token"]?.jsonPrimitiveOrNull()?.content?.trim().orEmpty()
            val port = obj["port"]?.jsonPrimitiveOrNull()?.content?.trim()?.toIntOrNull() ?: 0

            val payload = PairingPayload(ip, port, token)
            return payload.takeIf { it.isValid() }
        }

        private fun kotlinx.serialization.json.JsonElement.jsonPrimitiveOrNull(): JsonPrimitive? =
            try {
                jsonPrimitive
            } catch (e: Exception) {
                null
            }
    }

    private fun isValid(): Boolean =
        ip.isNotBlank() &&
            !ip.contains("://") &&
            !ip.contains('/') &&
            port in ServerConfig.VALID_PORTS &&
            token.isNotBlank()
}
