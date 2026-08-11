package dev.alexkobayashi.appdeck.ui.deck.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import dev.alexkobayashi.appdeck.R
import dev.alexkobayashi.appdeck.domain.model.ConnectionStatus

/** Indicador de conexão com o servidor, mostrado sob o título. */
@Composable
fun ConnectionBadge(status: ConnectionStatus, modifier: Modifier = Modifier) {
    val label = when (status) {
        is ConnectionStatus.Online ->
            status.serverVersion
                ?.let { stringResource(R.string.connection_online_version, it) }
                ?: stringResource(R.string.connection_online)

        is ConnectionStatus.Offline -> stringResource(R.string.connection_offline)
        ConnectionStatus.Checking -> stringResource(R.string.connection_checking)
        ConnectionStatus.Unknown -> stringResource(R.string.connection_unknown)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.semantics { contentDescription = label },
    ) {
        if (status is ConnectionStatus.Checking) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(10.dp))
        } else {
            // Cor além do texto: o estado precisa ser legível de relance,
            // sem ler.
            val dotColor = when (status) {
                is ConnectionStatus.Online -> Color(0xFF2E9E4F)
                is ConnectionStatus.Offline -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.outline
            }
            Row(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            ) {}
        }

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
