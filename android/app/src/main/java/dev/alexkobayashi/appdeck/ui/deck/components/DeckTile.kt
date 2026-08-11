package dev.alexkobayashi.appdeck.ui.deck.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.alexkobayashi.appdeck.domain.model.DeckItem
import dev.alexkobayashi.appdeck.ui.common.ShortcutIconView

/**
 * Um botão do deck.
 *
 * Usa combinedClickable em vez do onClick do Card porque o toque longo é a
 * porta de entrada para trocar o ícone, e o Card não expõe onLongClick.
 */
@Composable
fun DeckTile(
    item: DeckItem,
    isLaunching: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .aspectRatio(1f)
            .combinedClickable(
                // Durante a abertura o toque é ignorado, para um toque duplo
                // acidental não abrir o programa duas vezes. O toque longo
                // continua valendo: trocar o ícone não depende do servidor.
                enabled = true,
                onClick = { if (!isLaunching) onClick() },
                onLongClick = onLongClick,
            ),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.alpha(if (isLaunching) 0.35f else 1f),
            ) {
                ShortcutIconView(icon = item.icon, size = 48.dp)
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (isLaunching) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
        }
    }
}
