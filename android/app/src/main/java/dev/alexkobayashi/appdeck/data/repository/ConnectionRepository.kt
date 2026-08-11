package dev.alexkobayashi.appdeck.data.repository

import dev.alexkobayashi.appdeck.data.remote.ApiError
import dev.alexkobayashi.appdeck.data.remote.ApiResult
import dev.alexkobayashi.appdeck.data.remote.DeckApi
import dev.alexkobayashi.appdeck.data.remote.apiCall
import dev.alexkobayashi.appdeck.domain.model.ConnectionStatus
import dev.alexkobayashi.appdeck.domain.model.ServerConfigState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

interface ConnectionRepository {
    suspend fun check(): ConnectionStatus

    /**
     * Fluxo frio que reconsulta o servidor periodicamente.
     *
     * Sendo frio e coletado com `stateIn(WhileSubscribed)`, o laço para
     * sozinho quando a tela sai de primeiro plano — nada de gastar bateria
     * consultando um PC a cada 15 segundos com o app fechado.
     */
    fun statusFlow(): Flow<ConnectionStatus>
}

/** Monitora a disponibilidade do servidor via `GET /api/health`. */
class DefaultConnectionRepository(
    private val api: DeckApi,
    private val configRepository: ServerConfigRepository,
    private val json: Json,
) : ConnectionRepository {

    override suspend fun check(): ConnectionStatus {
        if (configRepository.awaitLoaded() !is ServerConfigState.Ready) {
            return ConnectionStatus.Offline(ApiError.NotConfigured)
        }
        return when (val result = apiCall(json) { api.health() }) {
            is ApiResult.Success -> ConnectionStatus.Online(result.value.version)
            is ApiResult.Failure -> ConnectionStatus.Offline(result.error)
        }
    }

    override fun statusFlow(): Flow<ConnectionStatus> = flow {
        emit(ConnectionStatus.Checking)
        var consecutiveFailures = 0
        while (true) {
            val status = check()
            emit(status)
            consecutiveFailures = if (status.isOnline) 0 else consecutiveFailures + 1
            delay(intervalFor(consecutiveFailures))
        }
    }

    /**
     * O intervalo aumenta depois de falhas consecutivas: se o PC está
     * desligado, insistir de 15 em 15 segundos não ajuda em nada.
     */
    private fun intervalFor(consecutiveFailures: Int): Duration = when {
        consecutiveFailures == 0 -> 15.seconds
        consecutiveFailures <= 3 -> 30.seconds
        else -> 60.seconds
    }
}
