package dev.alexkobayashi.appdeck.data.repository

import android.net.Uri
import dev.alexkobayashi.appdeck.data.local.CachedAppDao
import dev.alexkobayashi.appdeck.data.local.IconFileStore
import dev.alexkobayashi.appdeck.data.local.CachedAppEntity
import dev.alexkobayashi.appdeck.data.local.ShortcutCustomizationDao
import dev.alexkobayashi.appdeck.data.local.ShortcutCustomizationEntity
import dev.alexkobayashi.appdeck.data.local.StringListConverter
import dev.alexkobayashi.appdeck.data.remote.ApiError
import dev.alexkobayashi.appdeck.data.remote.ApiResult
import dev.alexkobayashi.appdeck.data.remote.DeckApi
import dev.alexkobayashi.appdeck.data.remote.apiCall
import dev.alexkobayashi.appdeck.data.remote.dto.AppDto
import dev.alexkobayashi.appdeck.data.remote.map
import dev.alexkobayashi.appdeck.domain.model.DeckItem
import dev.alexkobayashi.appdeck.domain.model.IconType
import dev.alexkobayashi.appdeck.domain.model.ServerConfigState
import dev.alexkobayashi.appdeck.domain.model.ShortcutIcon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

interface DeckRepository {
    /** Sempre observa o cache local — nunca a rede diretamente. */
    fun observeDeck(): Flow<List<DeckItem>>

    /** Busca a lista no servidor e substitui o cache. */
    suspend fun refresh(): ApiResult<Unit>

    /** Manda o servidor abrir o programa do atalho. */
    suspend fun launch(id: String): ApiResult<Unit>

    suspend fun findItem(id: String): DeckItem?

    /** Grava o ícone escolhido para o atalho. */
    suspend fun setIcon(appId: String, icon: ShortcutIcon)

    /**
     * Copia a imagem escolhida na galeria para o armazenamento do app e a
     * define como ícone. Devolve false se a imagem não pôde ser lida.
     */
    suspend fun setImageIcon(appId: String, source: Uri): Boolean

    /** Volta o atalho para as iniciais do nome. */
    suspend fun clearIcon(appId: String)
}

/**
 * Fonte única dos atalhos do deck.
 *
 * A UI observa sempre o estado local; a rede só alimenta o cache. Assim a
 * grade nunca fica vazia por causa de uma requisição em andamento ou de um
 * servidor desligado.
 */
class DefaultDeckRepository(
    private val api: DeckApi,
    private val dao: CachedAppDao,
    private val customizationDao: ShortcutCustomizationDao,
    private val configRepository: ServerConfigRepository,
    private val json: Json,
    private val iconFileStore: IconFileStore? = null,
    private val now: () -> Long = System::currentTimeMillis,
) : DeckRepository {

    override fun observeDeck(): Flow<List<DeckItem>> =
        combine(dao.observeAll(), customizationDao.observeAll()) { apps, customizations ->
            val byAppId = customizations.associateBy { it.appId }
            apps
                .map { entity -> entity.toDeckItem(byAppId[entity.id]) }
                // sortedBy é estável: quem não tem ordem escolhida vai para o
                // fim preservando a ordem que o servidor mandou.
                .sortedBy { it.sortOrder ?: Int.MAX_VALUE }
        }

    override suspend fun refresh(): ApiResult<Unit> {
        notConfigured()?.let { return it }

        val result = apiCall(json) { api.apps() }
        if (result is ApiResult.Success) {
            // Só o cache é substituído. As customizações continuam intactas:
            // um atalho que desapareceu da lista pode ser só um servidor que
            // reiniciou, e apagar o ícone escolhido seria irreversível.
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

    override suspend fun findItem(id: String): DeckItem? =
        observeDeck().first().firstOrNull { it.id == id }

    override suspend fun setIcon(appId: String, icon: ShortcutIcon) {
        val (type, ref) = when (icon) {
            is ShortcutIcon.Emoji -> IconType.EMOJI to icon.char
            is ShortcutIcon.Builtin -> IconType.BUILTIN to icon.key
            is ShortcutIcon.Local -> IconType.IMAGE to icon.fileName
            is ShortcutIcon.Initials -> IconType.NONE to null
        }
        val existing = customizationDao.findByAppId(appId)
        customizationDao.upsert(
            (existing ?: ShortcutCustomizationEntity(appId = appId)).copy(
                iconType = type,
                iconRef = ref,
                updatedAt = now(),
            ),
        )
        // Trocar de ícone deixaria a imagem anterior órfã ocupando espaço.
        discardPreviousImage(existing, keep = ref)
    }

    override suspend fun setImageIcon(appId: String, source: Uri): Boolean {
        val store = iconFileStore ?: return false
        val fileName = store.save(appId, source) ?: return false
        setIcon(appId, ShortcutIcon.Local(fileName, now()))
        return true
    }

    override suspend fun clearIcon(appId: String) {
        val existing = customizationDao.findByAppId(appId) ?: return
        // A linha é preservada quando há ordem escolhida: remover o ícone não
        // deve bagunçar a posição do atalho no deck.
        if (existing.sortOrder != null) {
            customizationDao.upsert(
                existing.copy(iconType = IconType.NONE, iconRef = null, updatedAt = now()),
            )
        } else {
            customizationDao.deleteByAppId(appId)
        }
        discardPreviousImage(existing, keep = null)
    }

    /** Apaga o arquivo do ícone anterior, se havia um e ele não é mais usado. */
    private suspend fun discardPreviousImage(
        previous: ShortcutCustomizationEntity?,
        keep: String?,
    ) {
        val stale = previous?.takeIf { it.iconType == IconType.IMAGE }?.iconRef ?: return
        if (stale != keep) iconFileStore?.delete(stale)
    }

    private suspend fun notConfigured(): ApiResult.Failure? =
        if (configRepository.awaitLoaded() is ServerConfigState.Ready) {
            null
        } else {
            ApiResult.Failure(ApiError.NotConfigured)
        }
}

private val argsConverter = StringListConverter()

internal fun CachedAppEntity.toDeckItem(
    customization: ShortcutCustomizationEntity?,
): DeckItem = DeckItem(
    id = id,
    name = name,
    path = path,
    args = argsConverter.toList(args),
    icon = customization.toShortcutIcon(name),
    sortOrder = customization?.sortOrder,
)

/** Traduz o que está no banco para o tipo que a UI sabe desenhar. */
internal fun ShortcutCustomizationEntity?.toShortcutIcon(appName: String): ShortcutIcon {
    val fallback = ShortcutIcon.Initials(DeckItem.initialsOf(appName))
    if (this == null) return fallback
    val ref = iconRef
    return when (iconType) {
        // iconRef nulo com tipo definido só acontece se alguém editar o banco
        // à mão; cair nas iniciais é melhor que exibir um espaço vazio.
        IconType.EMOJI -> ref?.let { ShortcutIcon.Emoji(it) } ?: fallback
        IconType.BUILTIN -> ref?.let { ShortcutIcon.Builtin(it) } ?: fallback
        IconType.IMAGE -> ref?.let { ShortcutIcon.Local(it, updatedAt) } ?: fallback
        IconType.NONE -> fallback
    }
}

internal fun AppDto.toEntity(order: Int): CachedAppEntity = CachedAppEntity(
    id = id,
    name = name,
    path = path,
    args = argsConverter.fromList(args),
    serverOrder = order,
)
