package dev.alexkobayashi.appdeck.data.remote

import dev.alexkobayashi.appdeck.data.remote.dto.AppUpsertDto
import dev.alexkobayashi.appdeck.domain.model.ServerConfig
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Exercita a camada de rede contra um servidor real (MockWebServer), cobrindo
 * a matriz de erros que a UI precisa distinguir.
 */
class DeckApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: DeckApi
    private var config: ServerConfig? = null

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        config = ServerConfig(server.hostName, server.port, TOKEN)

        val client = OkHttpClient.Builder()
            .addInterceptor(BaseUrlInterceptor { config })
            .addInterceptor(AuthInterceptor { config?.token })
            .build()

        api = Retrofit.Builder()
            // Mesmo placeholder do app: quem decide o destino é o interceptor.
            .baseUrl("http://localhost/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(DeckApi::class.java)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun enqueue(code: Int, body: String = "") {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }

    @Test
    fun `health devolve status e versao`() = runTest {
        enqueue(200, """{"status":"ok","name":"app-deck","version":"v0.3.0"}""")

        val result = apiCall(json) { api.health() }

        assertTrue(result is ApiResult.Success)
        val health = (result as ApiResult.Success).value
        assertEquals("ok", health.status)
        assertEquals("v0.3.0", health.version)
    }

    @Test
    fun `apps mapeia a lista e os argumentos`() = runTest {
        enqueue(
            200,
            """
            {"apps":[
              {"id":"a1","name":"Calculadora","path":"C:\\calc.exe"},
              {"id":"b2","name":"Chrome","path":"C:\\chrome.exe","args":["--incognito"]}
            ]}
            """.trimIndent(),
        )

        val result = apiCall(json) { api.apps() }

        val apps = (result as ApiResult.Success).value.apps
        assertEquals(2, apps.size)
        assertEquals("a1", apps[0].id)
        // args ausente no JSON precisa virar lista vazia, não nulo.
        assertEquals(emptyList<String>(), apps[0].args)
        assertEquals(listOf("--incognito"), apps[1].args)
    }

    @Test
    fun `campos desconhecidos no JSON nao quebram a leitura`() = runTest {
        // Um servidor mais novo pode acrescentar campos; a versão antiga do
        // app precisa continuar funcionando.
        enqueue(200, """{"apps":[{"id":"a1","name":"X","path":"p","icon":"🌐","futuro":{"a":1}}]}""")

        val result = apiCall(json) { api.apps() }

        assertEquals("a1", (result as ApiResult.Success).value.apps.single().id)
    }

    @Test
    fun `401 vira Unauthorized`() = runTest {
        enqueue(401, """{"error":"não autorizado","code":"unauthorized"}""")

        val result = apiCall(json) { api.apps() }

        assertEquals(ApiError.Unauthorized, result.errorOrNull)
    }

    @Test
    fun `404 vira NotFound`() = runTest {
        enqueue(404, """{"error":"atalho não encontrado: x","code":"not_found"}""")

        val result = apiCall(json) { api.launch("x") }

        assertEquals(ApiError.NotFound, result.errorOrNull)
    }

    @Test
    fun `500 preserva a mensagem e o codigo do servidor`() = runTest {
        enqueue(
            500,
            """{"error":"não foi possível abrir Chrome: executável não encontrado","code":"launch_failed"}""",
        )

        val result = apiCall(json) { api.launch("b2") }

        val error = result.errorOrNull as ApiError.Server
        assertEquals(500, error.status)
        assertEquals("launch_failed", error.code)
        // A mensagem do servidor é o que diz ao usuário qual programa falhou.
        assertTrue(error.message!!.contains("Chrome"))
    }

    @Test
    fun `corpo de erro fora do contrato nao e descartado`() = runTest {
        // Um proxy no caminho pode responder HTML; melhor mostrar o texto
        // bruto que perder a informação.
        enqueue(502, "<html>Bad Gateway</html>")

        val result = apiCall(json) { api.apps() }

        val error = result.errorOrNull as ApiError.Server
        assertEquals(502, error.status)
        assertTrue(error.message!!.contains("Bad Gateway"))
    }

    @Test
    fun `servidor desligado vira NoConnection`() = runTest {
        server.close()

        val result = apiCall(json) { api.apps() }

        assertTrue("erro = ${result.errorOrNull}", result.errorOrNull is ApiError.NoConnection)
    }

    @Test
    fun `sem configuracao vira NotConfigured`() = runTest {
        config = null

        val result = apiCall(json) { api.apps() }

        assertEquals(ApiError.NotConfigured, result.errorOrNull)
    }

    @Test
    fun `delete devolve sucesso com 204 sem corpo`() = runTest {
        enqueue(204)

        val result = apiCall(json) { api.delete("a1") }

        assertTrue("erro = ${result.errorOrNull}", result is ApiResult.Success)
    }

    @Test
    fun `token vai no header Authorization e nunca na URL`() = runTest {
        enqueue(200, """{"apps":[]}""")

        apiCall(json) { api.apps() }

        val request = server.takeRequest()
        assertEquals("Bearer $TOKEN", request.headers["Authorization"])
        assertNotNull(request.url)
        assertTrue(
            "o token apareceu na URL: ${request.url}",
            !request.url.toString().contains(TOKEN),
        )
    }

    @Test
    fun `o interceptor reescreve host e porta para o servidor configurado`() = runTest {
        enqueue(200, """{"apps":[]}""")

        apiCall(json) { api.apps() }

        val request = server.takeRequest()
        // Chegou no MockWebServer, e não no localhost do placeholder.
        assertEquals(server.port, request.url.port)
        assertEquals("/api/apps", request.url.encodedPath)
    }

    @Test
    fun `atualizacao parcial nao envia os campos ausentes`() = runTest {
        enqueue(200, """{"id":"a1","name":"Novo","path":"C:\\a.exe"}""")

        apiCall(json) { api.update("a1", AppUpsertDto(name = "Novo")) }

        val body = server.takeRequest().body?.utf8() ?: ""
        // path e args ausentes: é assim que o PUT preserva o que não mudou.
        assertTrue("corpo = $body", body.contains("\"name\":\"Novo\""))
        assertTrue("corpo = $body", !body.contains("path"))
        assertTrue("corpo = $body", !body.contains("args"))
    }

    private companion object {
        const val TOKEN = "token-de-teste-bem-comprido-123456789"
    }
}
