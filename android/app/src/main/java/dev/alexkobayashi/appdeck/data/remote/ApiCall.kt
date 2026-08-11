package dev.alexkobayashi.appdeck.data.remote

import dev.alexkobayashi.appdeck.data.remote.dto.ApiErrorDto
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.Response

/**
 * Executa uma chamada da API traduzindo status HTTP e exceções para
 * [ApiResult].
 *
 * Único ponto do app que conhece códigos HTTP: dali para cima só circulam
 * [ApiError].
 */
suspend fun <T> apiCall(
    json: Json,
    block: suspend () -> Response<T>,
): ApiResult<T> = try {
    val response = block()
    val body = response.body()
    when {
        response.isSuccessful && body != null -> ApiResult.Success(body)

        // 204 do DELETE: sucesso sem corpo. Só é válido quando o chamador
        // espera Unit.
        response.isSuccessful -> {
            @Suppress("UNCHECKED_CAST")
            ApiResult.Success(Unit as T)
        }

        else -> ApiResult.Failure(response.toApiError(json))
    }
} catch (e: CancellationException) {
    // Cancelamento não é falha: precisa continuar propagando, senão um
    // ViewModel destruído viraria um erro na tela.
    throw e
} catch (e: NotConfiguredException) {
    ApiResult.Failure(ApiError.NotConfigured)
} catch (e: IOException) {
    // Servidor desligado, IP errado, celular em outra rede, timeout.
    ApiResult.Failure(ApiError.NoConnection(e))
} catch (e: Throwable) {
    ApiResult.Failure(ApiError.Unknown(e))
}

private fun Response<*>.toApiError(json: Json): ApiError {
    val parsed = parseErrorBody(json, errorBody()?.string())
    return when (code()) {
        401 -> ApiError.Unauthorized
        404 -> ApiError.NotFound
        else -> ApiError.Server(
            status = code(),
            code = parsed?.code,
            message = parsed?.error,
        )
    }
}

private fun parseErrorBody(json: Json, raw: String?): ApiErrorDto? {
    if (raw.isNullOrBlank()) return null
    return try {
        json.decodeFromString<ApiErrorDto>(raw)
    } catch (e: Exception) {
        // Corpo de erro fora do contrato (um proxy no caminho, por exemplo):
        // aproveita o texto bruto como mensagem em vez de perder a
        // informação.
        ApiErrorDto(error = raw.take(200))
    }
}
