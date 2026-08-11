package dev.alexkobayashi.appdeck.data.remote

import java.io.IOException

/**
 * Falhas de API traduzidas para categorias que a UI sabe tratar.
 *
 * O ponto de existir esse tipo em vez de propagar exceções é que cada caso
 * exige uma mensagem e uma ação diferentes: "token inválido" manda o usuário
 * para a configuração, "sem conexão" sugere checar o Wi-Fi, e "atalho não
 * encontrado" pede um recarregamento da lista.
 */
sealed interface ApiError {

    /** Nenhum servidor configurado ainda. */
    data object NotConfigured : ApiError

    /** O servidor não respondeu: desligado, IP errado, outra rede, firewall. */
    data class NoConnection(val cause: Throwable? = null) : ApiError

    /** 401: token ausente ou incorreto. */
    data object Unauthorized : ApiError

    /** 404: o atalho não existe mais no servidor. */
    data object NotFound : ApiError

    /** Qualquer outro erro que o servidor tenha reportado em JSON. */
    data class Server(
        val status: Int,
        val code: String? = null,
        val message: String? = null,
    ) : ApiError

    data class Unknown(val cause: Throwable? = null) : ApiError
}

/**
 * Lançada pelos interceptors quando ainda não há servidor configurado.
 *
 * Precisa ser uma [IOException] porque é o único tipo de exceção que o OkHttp
 * permite um interceptor lançar.
 */
class NotConfiguredException : IOException("nenhum servidor configurado")

/** Resultado de uma chamada de API. */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}

val ApiResult<*>.errorOrNull: ApiError?
    get() = (this as? ApiResult.Failure)?.error
