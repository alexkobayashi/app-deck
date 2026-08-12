package dev.alexkobayashi.appdeck.ui.deck.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alexkobayashi.appdeck.R
import dev.alexkobayashi.appdeck.domain.model.ConnectionStatus

/**
 * Só a bolinha do estado da conexão, sem texto.
 *
 * Vive separada do [ConnectionBadge] porque agora tem dois usos: o badge
 * sobre o botão de menu (onde não cabe texto) e o próprio badge com texto,
 * dentro do menu. O mapeamento de cor tem que ficar em um lugar só.
 */
@Composable
fun ConnectionDot(
    status: ConnectionStatus,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
    describeStatus: Boolean = true,
) {
    // describeStatus = false quando um texto ao lado já diz o estado: senão o
    // leitor de tela anuncia a mesma informação duas vezes.
    val label = connectionLabel(status)

    Box(
        modifier = modifier
            .size(size)
            .then(if (describeStatus) Modifier.semantics { contentDescription = label } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (status is ConnectionStatus.Checking) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(size))
        } else {
            // Cor além do texto: o estado precisa ser legível de relance,
            // sem ler.
            val dotColor = when (status) {
                is ConnectionStatus.Online -> Color(0xFF2E9E4F)
                is ConnectionStatus.Offline -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.outline
            }
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }
    }
}

/** Indicador de conexão com o servidor: bolinha mais o texto do estado. */
@Composable
fun ConnectionBadge(status: ConnectionStatus, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        ConnectionDot(status, describeStatus = false)

        Text(
            text = connectionLabel(status),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun connectionLabel(status: ConnectionStatus): String = when (status) {
    is ConnectionStatus.Online ->
        status.serverVersion
            ?.let { stringResource(R.string.connection_online_version, it) }
            ?: stringResource(R.string.connection_online)

    is ConnectionStatus.Offline -> stringResource(R.string.connection_offline)
    ConnectionStatus.Checking -> stringResource(R.string.connection_checking)
    ConnectionStatus.Unknown -> stringResource(R.string.connection_unknown)
}
