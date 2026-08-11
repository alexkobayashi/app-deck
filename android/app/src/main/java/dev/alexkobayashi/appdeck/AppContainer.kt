package dev.alexkobayashi.appdeck

import android.content.Context
import dev.alexkobayashi.appdeck.data.local.AppDeckDatabase
import dev.alexkobayashi.appdeck.data.prefs.ServerConfigDataStore
import dev.alexkobayashi.appdeck.data.remote.AuthInterceptor
import dev.alexkobayashi.appdeck.data.remote.BaseUrlInterceptor
import dev.alexkobayashi.appdeck.data.remote.DeckApi
import dev.alexkobayashi.appdeck.data.remote.HealthProbe
import dev.alexkobayashi.appdeck.data.repository.ConnectionRepository
import dev.alexkobayashi.appdeck.data.repository.DeckRepository
import dev.alexkobayashi.appdeck.data.repository.DefaultConnectionRepository
import dev.alexkobayashi.appdeck.data.repository.DefaultDeckRepository
import dev.alexkobayashi.appdeck.data.repository.DefaultServerConfigRepository
import dev.alexkobayashi.appdeck.data.repository.ServerConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Grafo de dependências do app, montado à mão.
 *
 * Injeção manual em vez de Hilt: com um Activity e meia dúzia de singletons,
 * este arquivo é menor que a configuração equivalente do Hilt e evita uma
 * segunda dependência de geração de código no build.
 */
class AppContainer(context: Context) {

    /** Escopo para trabalho que vive enquanto o processo viver. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val json: Json = Json {
        // Tolera campos que este app ainda não conhece: o servidor pode
        // evoluir sem quebrar uma versão antiga do app.
        ignoreUnknownKeys = true
        // Campo nulo não é serializado, que é a semântica da atualização
        // parcial do PUT: campo ausente fica como está no servidor.
        explicitNulls = false
        encodeDefaults = false
    }

    /**
     * Cliente base, sem os interceptors de endereço e token.
     *
     * Timeouts curtos de propósito: numa rede local, uma resposta que demora
     * mais de alguns segundos significa que o servidor não está lá. Esperar
     * dez segundos só faz o app parecer travado.
     */
    private val baseClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                        // O header Authorization carrega o token: não pode
                        // aparecer no Logcat nem em build de debug.
                        redactHeader("Authorization")
                    },
                )
            }
        }
        .build()

    private val serverConfigDataStore = ServerConfigDataStore(context)

    private val healthProbe = HealthProbe(baseClient, json)

    val serverConfigRepository: ServerConfigRepository = DefaultServerConfigRepository(
        dataStore = serverConfigDataStore,
        healthProbe = healthProbe,
        scope = appScope,
    )

    private val apiClient: OkHttpClient = baseClient.newBuilder()
        .addInterceptor(BaseUrlInterceptor { serverConfigRepository.currentConfig })
        .addInterceptor(AuthInterceptor { serverConfigRepository.currentConfig?.token })
        .build()

    private val api: DeckApi = Retrofit.Builder()
        // Placeholder: o BaseUrlInterceptor troca host e porta em cada
        // requisição. O Retrofit exige uma baseUrl na construção, mas o
        // endereço real só é conhecido em runtime.
        .baseUrl("http://localhost/")
        .client(apiClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(DeckApi::class.java)

    private val database = AppDeckDatabase.create(context)

    val deckRepository: DeckRepository = DefaultDeckRepository(
        api = api,
        dao = database.cachedAppDao(),
        customizationDao = database.customizationDao(),
        configRepository = serverConfigRepository,
        json = json,
    )

    val connectionRepository: ConnectionRepository = DefaultConnectionRepository(
        api = api,
        configRepository = serverConfigRepository,
        json = json,
    )
}
