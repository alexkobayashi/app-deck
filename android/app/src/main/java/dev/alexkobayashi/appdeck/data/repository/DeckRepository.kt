package dev.alexkobayashi.appdeck.data.repository

import dev.alexkobayashi.appdeck.data.local.CachedAppDao
import dev.alexkobayashi.appdeck.data.local.CachedAppEntity
import dev.alexkobayashi.appdeck.data.local.StringListConverter
import dev.alexkobayashi.appdeck.data.remote.ApiError
import dev.alexkobayashi.appdeck.data.remote.ApiResult
import dev.alexkobayashi.appdeck.data.remote.DeckApi
import dev.alexkobayashi.appdeck.data.remote.apiCall
import dev.alexkobayashi.appdeck.data.remote.dto.AppDto
import dev.alexkobayashi.appdeck.data.remote.map
import dev.alexkobayashi.appdeck.domain.model.DeckItem
import dev.alexkobayashi.appdeck.domain.model.ServerConfigState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

interface DeckRepository {
    /** Sempre observa o cache local — nunca a rede diretamente. */
    fun observeDeck(): Flow<List<DeckItem>>

    /** Busca a lista no servidor e substitui o cache. */
    suspend fun refresh(): ApiResult<Unit>

    /** Manda o servidor abrir o programa do atalho. */
    suspend fun launch(id: String): ApiResult<Unit>
}

/**
 * Fonte única dos atalhos do deck.
 *
 * A UI observa sempre o cache local; a rede só alimenta esse cache. Assim a
 * grade nunca fica vazia por causa de uma requisição em andamento ou de um
 * servidor desligado.
 */
class DefaultDeckRepository(
    private val api: DeckApi,
    private val dao: CachedAppDao,
    private val configRepository: ServerConfigRepository,
    private val json: Json,
) : DeckRepository {

    override fun observeDeck(): Flow<List<DeckItem>> =
        dao.observeAll().map { entities -> entities.map { it.toDeckItem() } }

    override suspend fun refresh(): ApiResult<Unit> {
        notConfigured()?.let { return it }

        val result = apiCall(json) { api.apps() }
        if (result is ApiResult.Success) {
            dao.replaceAll(
                result.value.apps.mapIndexed { index, dto -> dto.toEntity(index) },
            )
        }
        return result.map { }
    }

    override suspend fun launch(id: String): ApiResult<Unit> {
        notConfigured()?.let { return it }
        return apiCall(json) { api.launch(id) }.map { }
    }

    private suspend fun notConfigured(): ApiResult.Failure? =
        if (configRepository.awaitLoaded() is ServerConfigState.Ready) {
            null
        } else {
            ApiResult.Failure(ApiError.NotConfigured)
        }
}

private val argsConverter = StringListConverter()

internal fun CachedAppEntity.toDeckItem(): DeckItem = DeckItem(
    id = id,
    name = name,
    path = path,
    args = argsConverter.toList(args),
)

internal fun AppDto.toEntity(order: Int): CachedAppEntity = CachedAppEntity(
    id = id,
    name = name,
    path = path,
    args = argsConverter.fromList(args),
    serverOrder = order,
)
