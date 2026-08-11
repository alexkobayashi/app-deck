package dev.alexkobayashi.appdeck.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.alexkobayashi.appdeck.R
import dev.alexkobayashi.appdeck.data.remote.ApiError
import dev.alexkobayashi.appdeck.domain.model.ServerConfig

/**
 * Traduz [ApiError] para uma mensagem acionável.
 *
 * A tradução acontece na camada de UI, não no ViewModel, para que o estado
 * continue livre de strings — o que também deixa os testes de ViewModel
 * asseverando sobre tipos em vez de texto.
 */
@Composable
fun apiErrorMessage(error: ApiError, config: ServerConfig? = null): String = when (error) {
    ApiError.NotConfigured -> stringResource(R.string.error_not_configured)

    is ApiError.NoConnection -> stringResource(
        R.string.error_no_connection,
        config?.let { "${it.host}:${it.port}" } ?: "—",
    )

    ApiError.Unauthorized -> stringResource(R.string.error_unauthorized)

    ApiError.NotFound -> stringResource(R.string.error_not_found)

    // A mensagem do servidor é mais específica que qualquer texto genérico
    // que o app pudesse inventar — ela diz, por exemplo, qual programa não
    // pôde ser aberto.
    is ApiError.Server -> error.message?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.error_server, error.status)

    is ApiError.Unknown -> stringResource(R.string.error_unknown)
}
