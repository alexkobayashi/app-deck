package dev.alexkobayashi.appdeck.ui.deck.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.alexkobayashi.appdeck.R
import dev.alexkobayashi.appdeck.domain.model.ConnectionStatus

private val ButtonSize = 40.dp

/**
 * Único controle fixo da tela do deck: um botão redondo que abre o menu de
 * ações, com a bolinha de conexão sobreposta.
 *
 * Substitui a TopAppBar antiga (título + badge + quatro ícones), que custava
 * ~72dp de altura — caro em paisagem, onde a altura é o recurso escasso.
 */
@Composable
fun DeckMenuButton(
    status: ConnectionStatus,
    canAdd: Boolean,
    canRefresh: Boolean,
    canEdit: Boolean,
    onAdd: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    // O DropdownMenu se ancora ao pai, então este Box é o que define onde o
    // menu aparece.
    Box(modifier = modifier) {
        FilledTonalIconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(ButtonSize),
        ) {
            Icon(
                Icons.Filled.Menu,
                contentDescription = stringResource(R.string.deck_menu_open),
            )
        }

        // O anel da cor do fundo entra como padding *depois* do background:
        // sem ele a bolinha encosta no botão e as duas formas se confundem.
        ConnectionDot(
            status = status,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 3.dp, y = (-3).dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.background)
                .padding(2.dp),
        )

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // O texto do estado da conexão não cabe mais na tela; fica aqui
            // como cabeçalho, para a bolinha não ser a única pista.
            ConnectionBadge(
                status = status,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            HorizontalDivider()

            DeckMenuItem(
                label = stringResource(R.string.deck_action_add),
                icon = Icons.Filled.Add,
                enabled = canAdd,
                onClick = {
                    expanded = false
                    onAdd()
                },
            )
            DeckMenuItem(
                label = stringResource(R.string.deck_action_refresh),
                icon = Icons.Filled.Refresh,
                enabled = canRefresh,
                onClick = {
                    expanded = false
                    onRefresh()
                },
            )
            DeckMenuItem(
                label = stringResource(R.string.deck_action_edit),
                icon = Icons.Filled.Edit,
                enabled = canEdit,
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DeckMenuItem(
                label = stringResource(R.string.deck_action_settings),
                icon = Icons.Filled.Settings,
                onClick = {
                    expanded = false
                    onSettings()
                },
            )
        }
    }
}

@Composable
private fun DeckMenuItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        enabled = enabled,
        onClick = onClick,
    )
}
