package dev.alexkobayashi.appdeck.data.repository

import androidx.room.Room
import dev.alexkobayashi.appdeck.data.local.AppDeckDatabase
import dev.alexkobayashi.appdeck.data.local.CachedAppEntity
import dev.alexkobayashi.appdeck.data.remote.DeckApi
import dev.alexkobayashi.appdeck.data.remote.dto.AppDto
import dev.alexkobayashi.appdeck.data.remote.dto.AppUpsertDto
import dev.alexkobayashi.appdeck.data.remote.dto.AppsResponseDto
import dev.alexkobayashi.appdeck.data.remote.dto.HealthDto
import dev.alexkobayashi.appdeck.data.remote.dto.LaunchResponseDto
import dev.alexkobayashi.appdeck.domain.model.IconType
import dev.alexkobayashi.appdeck.domain.model.ShortcutIcon
import dev.alexkobayashi.appdeck.testing.FakeServerConfigRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import retrofit2.Response

/**
 * Testa o repositório contra um Room de verdade em memória — assim a entidade,
 * o DAO e a combinação de cache com customização são exercitados juntos, e não
 * simulados.
 */
@RunWith(RobolectricTestRunner::class)
class DeckRepositoryRoomTest {

    private lateinit var db: AppDeckDatabase
    private lateinit var repository: DefaultDeckRepository
    private val api = StubDeckApi()
    private val config = FakeServerConfigRepository().apply { setConfigured() }
    private var clock = 1_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDeckDatabase::class.java,
        ).allowMainThreadQueries().build()

        repository = DefaultDeckRepository(
            api = api,
            dao = db.cachedAppDao(),
            customizationDao = db.customizationDao(),
            configRepository = config,
            json = Json { ignoreUnknownKeys = true },
            now = { clock },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedCache(vararg apps: Pair<String, String>) {
        db.cachedAppDao().replaceAll(
            apps.mapIndexed { index, (id, name) ->
                CachedAppEntity(id = id, name = name, path = "C:\\$name.exe", args = "[]", serverOrder = index)
            },
        )
    }

    @Test
    fun `deck comeca com as iniciais do nome`() = runTest {
        seedCache("a1" to "Google Chrome")

        val item = repository.observeDeck().first().single()

        assertEquals(ShortcutIcon.Initials("GC"), item.icon)
    }

    @Test
    fun `setIcon grava o emoji e aparece no deck`() = runTest {
        seedCache("a1" to "Chrome")

        repository.setIcon("a1", ShortcutIcon.Emoji("🌐"))

        assertEquals(ShortcutIcon.Emoji("🌐"), repository.observeDeck().first().single().icon)
        val row = db.customizationDao().findByAppId("a1")
        assertEquals(IconType.EMOJI, row?.iconType)
        assertEquals("🌐", row?.iconRef)
        assertEquals(1_000L, row?.updatedAt)
    }

    @Test
    fun `trocar o icone atualiza o updatedAt, que invalida o cache de imagem`() = runTest {
        seedCache("a1" to "Chrome")

        repository.setIcon("a1", ShortcutIcon.Emoji("🌐"))
        clock = 2_000L
        repository.setIcon("a1", ShortcutIcon.Emoji("🔥"))

        assertEquals(2_000L, db.customizationDao().findByAppId("a1")?.updatedAt)
    }

    // A invariante central do plano: um refresh do servidor nunca pode apagar
    // o icone que o usuario escolheu.
    @Test
    fun `o icone escolhido sobrevive a um refresh`() = runTest {
        seedCache("a1" to "Chrome")
        repository.setIcon("a1", ShortcutIcon.Emoji("🌐"))

        api.apps = listOf(AppDto(id = "a1", name = "Chrome", path = "C:\\outro-lugar\\chrome.exe"))
        repository.refresh()

        val item = repository.observeDeck().first().single()
        assertEquals(ShortcutIcon.Emoji("🌐"), item.icon)
        // O caminho mudou no servidor, mas o id e o icone continuam.
        assertEquals("C:\\outro-lugar\\chrome.exe", item.path)
    }

    // Um servidor reiniciado sem atalhos nao deve destruir customizacoes: o
    // usuario pode so ter parado o servidor no meio de uma edicao.
    @Test
    fun `atalho que desaparece do servidor mantem a customizacao guardada`() = runTest {
        seedCache("a1" to "Chrome")
        repository.setIcon("a1", ShortcutIcon.Emoji("🌐"))

        api.apps = emptyList()
        repository.refresh()
        assertTrue(repository.observeDeck().first().isEmpty())
        assertNotNull(db.customizationDao().findByAppId("a1"))

        // Quando o atalho volta, o icone volta com ele.
        api.apps = listOf(AppDto(id = "a1", name = "Chrome", path = "C:\\chrome.exe"))
        repository.refresh()
        assertEquals(ShortcutIcon.Emoji("🌐"), repository.observeDeck().first().single().icon)
    }

    @Test
    fun `clearIcon remove a linha quando nao ha ordem escolhida`() = runTest {
        seedCache("a1" to "Chrome")
        repository.setIcon("a1", ShortcutIcon.Emoji("🌐"))

        repository.clearIcon("a1")

        assertNull(db.customizationDao().findByAppId("a1"))
        assertEquals(ShortcutIcon.Initials("C"), repository.observeDeck().first().single().icon)
    }

    @Test
    fun `clearIcon preserva a linha quando ha ordem escolhida`() = runTest {
        seedCache("a1" to "Chrome", "b2" to "Edge")
        repository.setIcon("a1", ShortcutIcon.Emoji("🌐"))
        db.customizationDao().saveOrder(listOf("b2", "a1"))

        repository.clearIcon("a1")

        // Remover o ícone não pode bagunçar a posição no deck.
        val row = db.customizationDao().findByAppId("a1")
        assertEquals(IconType.NONE, row?.iconType)
        assertEquals(1, row?.sortOrder)
        assertEquals(listOf("b2", "a1"), repository.observeDeck().first().map { it.id })
    }

    @Test
    fun `ordem escolhida vem antes, e atalho novo entra no fim`() = runTest {
        seedCache("a1" to "A", "b2" to "B", "c3" to "C")
        // Só dois recebem ordem explícita.
        db.customizationDao().saveOrder(listOf("c3", "a1"))

        val ids = repository.observeDeck().first().map { it.id }

        assertEquals(listOf("c3", "a1", "b2"), ids)
    }

    @Test
    fun `sem ordem escolhida vale a ordem do servidor`() = runTest {
        seedCache("a1" to "A", "b2" to "B", "c3" to "C")

        assertEquals(listOf("a1", "b2", "c3"), repository.observeDeck().first().map { it.id })
    }

    @Test
    fun `findOrphans encontra customizacao de atalho que nao existe mais`() = runTest {
        seedCache("a1" to "Chrome")
        repository.setIcon("a1", ShortcutIcon.Emoji("🌐"))
        repository.setIcon("sumiu", ShortcutIcon.Emoji("👻"))

        val orphans = db.customizationDao().findOrphans()

        assertEquals(listOf("sumiu"), orphans.map { it.appId })
    }
}

/** API mínima para exercitar o refresh sem rede. */
private class StubDeckApi : DeckApi {
    var apps: List<AppDto> = emptyList()

    override suspend fun health(): Response<HealthDto> = Response.success(HealthDto("ok"))
    override suspend fun apps(): Response<AppsResponseDto> = Response.success(AppsResponseDto(apps))
    override suspend fun launch(id: String): Response<LaunchResponseDto> =
        Response.success(LaunchResponseDto("launched", id))

    override suspend fun create(body: AppUpsertDto): Response<AppDto> =
        Response.success(AppDto("novo", body.name.orEmpty(), body.path.orEmpty()))

    override suspend fun update(id: String, body: AppUpsertDto): Response<AppDto> =
        Response.success(AppDto(id, body.name.orEmpty(), body.path.orEmpty()))

    override suspend fun delete(id: String): Response<Unit> = Response.success(Unit)
}
