package dev.alexkobayashi.appdeck.domain.model

import dev.alexkobayashi.appdeck.data.remote.ApiError

/** Estado da conexão com o servidor, mostrado no indicador da tela do deck. */
sealed interface ConnectionStatus {
    data object Unknown : ConnectionStatus
    data object Checking : ConnectionStatus
    data class Online(val serverVersion: String?) : ConnectionStatus
    data class Offline(val error: ApiError) : ConnectionStatus

    val isOnline: Boolean get() = this is Online
}
