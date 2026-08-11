package dev.alexkobayashi.appdeck.data.remote

import dev.alexkobayashi.appdeck.data.remote.dto.AppDto
import dev.alexkobayashi.appdeck.data.remote.dto.AppUpsertDto
import dev.alexkobayashi.appdeck.data.remote.dto.AppsResponseDto
import dev.alexkobayashi.appdeck.data.remote.dto.HealthDto
import dev.alexkobayashi.appdeck.data.remote.dto.LaunchResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * A API do servidor, conforme docs/api.md.
 *
 * Todos os métodos devolvem [Response] para que o mapeamento de status HTTP
 * para [ApiError] seja explícito e o corpo de erro possa ser lido — um 401 e
 * um 404 exigem reações diferentes na UI.
 *
 * O host, a porta e o header Authorization são injetados por interceptors,
 * então não aparecem aqui.
 */
interface DeckApi {

    @GET("api/health")
    suspend fun health(): Response<HealthDto>

    @GET("api/apps")
    suspend fun apps(): Response<AppsResponseDto>

    @POST("api/apps/{id}/launch")
    suspend fun launch(@Path("id") id: String): Response<LaunchResponseDto>

    @POST("api/apps")
    suspend fun create(@Body body: AppUpsertDto): Response<AppDto>

    @PUT("api/apps/{id}")
    suspend fun update(@Path("id") id: String, @Body body: AppUpsertDto): Response<AppDto>

    @DELETE("api/apps/{id}")
    suspend fun delete(@Path("id") id: String): Response<Unit>
}
