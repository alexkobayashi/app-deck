package dev.alexkobayashi.appdeck.data.remote

import dev.alexkobayashi.appdeck.data.remote.dto.HealthDto
import dev.alexkobayashi.appdeck.domain.model.ServerConfig
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Testa um endereço de servidor **sem** salvá-lo.
 *
 * Usado pelo botão "Testar conexão" e, na fase do QR, para validar o
 * pareamento antes de persistir — assim um QR de outro app falha na hora, em
 * vez de virar um deck quebrado.
 *
 * Recebe um cliente sem os interceptors de base URL e de autenticação, já que
 * aqui a URL é absoluta e o token vem da configuração em teste, não da salva.
 */
class HealthProbe(
    private val client: OkHttpClient,
    private val json: Json,
) {
    suspend fun check(config: ServerConfig): ApiResult<HealthDto> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${config.baseUrl}api/health")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val raw = response.body.string()
                if (!response.isSuccessful) {
                    return@withContext ApiResult.Failure(
                        when (response.code) {
                            401 -> ApiError.Unauthorized
                            404 -> ApiError.NotFound
                            else -> ApiError.Server(response.code, message = raw?.take(200))
                        }
                    )
                }
                if (raw.isNullOrBlank()) {
                    return@withContext ApiResult.Failure(ApiError.Unknown())
                }
                ApiResult.Success(json.decodeFromString<HealthDto>(raw))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            ApiResult.Failure(ApiError.NoConnection(e))
        } catch (e: Throwable) {
            // Responder 200 com algo que não é o JSON esperado quer dizer que
            // do outro lado não há um App Deck — um roteador ou uma câmera IP,
            // por exemplo.
            ApiResult.Failure(ApiError.Unknown(e))
        }
    }
}
