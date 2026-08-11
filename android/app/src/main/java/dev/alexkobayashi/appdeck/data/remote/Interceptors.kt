package dev.alexkobayashi.appdeck.data.remote

import dev.alexkobayashi.appdeck.domain.model.ServerConfig
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Reescreve host e porta de cada requisição com o servidor configurado.
 *
 * O Retrofit exige uma baseUrl fixa na construção, mas aqui o endereço muda
 * em runtime (o usuário troca de PC, o IP muda, o pareamento por QR
 * reconfigura tudo). A baseUrl passada ao Retrofit é um placeholder e este
 * interceptor substitui o destino real.
 *
 * O provider é síncrono de propósito: quem chama a API já garantiu que a
 * configuração foi carregada, então não há leitura suspensa aqui — um
 * runBlocking dentro de interceptor seria uma fonte fácil de deadlock.
 */
class BaseUrlInterceptor(
    private val configProvider: () -> ServerConfig?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val config = configProvider() ?: throw NotConfiguredException()
        val url = chain.request().url.newBuilder()
            .scheme("http")
            .host(config.host)
            .port(config.port)
            .build()
        return chain.proceed(chain.request().newBuilder().url(url).build())
    }
}

/**
 * Injeta `Authorization: Bearer <token>`.
 *
 * O token nunca vai na query string — o servidor recusa com 401, e a URL
 * ficaria gravada em logs de qualquer intermediário.
 */
class AuthInterceptor(
    private val tokenProvider: () -> String?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider()?.takeIf { it.isNotBlank() } ?: throw NotConfiguredException()
        val request = chain.request().newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(request)
    }
}
