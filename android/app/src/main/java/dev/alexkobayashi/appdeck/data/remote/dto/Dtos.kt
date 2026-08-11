package dev.alexkobayashi.appdeck.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * DTOs espelhando o contrato documentado em docs/api.md.
 *
 * Todos os campos além dos obrigatórios têm valor padrão: o Json é
 * configurado com ignoreUnknownKeys, então um servidor mais novo pode mandar
 * campos extras sem quebrar o app, e um servidor mais antigo pode omitir os
 * opcionais.
 */

@Serializable
data class HealthDto(
    val status: String = "",
    val name: String? = null,
    val version: String? = null,
)

@Serializable
data class AppsResponseDto(
    val apps: List<AppDto> = emptyList(),
)

@Serializable
data class AppDto(
    val id: String,
    val name: String,
    val path: String,
    val args: List<String> = emptyList(),
)

@Serializable
data class LaunchResponseDto(
    val status: String? = null,
    val id: String? = null,
    val name: String? = null,
)

/**
 * Corpo de POST e PUT de atalho.
 *
 * Os campos são nulos por padrão e o Json usa explicitNulls = false, então um
 * campo não informado simplesmente não vai no JSON — que é exatamente a
 * semântica de atualização parcial do PUT: campo ausente fica como está.
 */
@Serializable
data class AppUpsertDto(
    val name: String? = null,
    val path: String? = null,
    val args: List<String>? = null,
)

/** Corpo de erro da API: `{"error": "...", "code": "..."}`. */
@Serializable
data class ApiErrorDto(
    val error: String? = null,
    val code: String? = null,
)
