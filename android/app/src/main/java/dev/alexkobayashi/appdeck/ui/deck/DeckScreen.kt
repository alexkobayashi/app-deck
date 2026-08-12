package dev.alexkobayashi.appdeck.ui.deck

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.alexkobayashi.appdeck.AppContainer
import dev.alexkobayashi.appdeck.R
import dev.alexkobayashi.appdeck.domain.model.DeckItem
import dev.alexkobayashi.appdeck.ui.common.apiErrorMessage
import dev.alexkobayashi.appdeck.ui.deck.components.DeckMenuButton
import dev.alexkobayashi.appdeck.ui.deck.components.DeckTile
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

/** Distância do botão de menu às bordas da tela. */
private val OverlayPadding = 8.dp

/**
 * Topo maior que os outros lados para reservar a faixa do botão de menu
 * (8dp de padding + 40dp do botão + 8dp de folga). Voltar o `top` para 16dp
 * faz a grade usar a altura inteira, com o botão flutuando por cima do
 * primeiro tile.
 */
private val GridContentPadding =
    PaddingValues(start = 16.dp, top = 56.dp, end = 16.dp, bottom = 16.dp)

@Composable
fun DeckScreen(
    container: AppContainer,
    onOpenSettings: () -> Unit,
    onEditIcon: (String) -> Unit,
    onAddShortcut: () -> Unit,
    onEditShortcut: (String) -> Unit,
    viewModel: DeckViewModel = viewModel(factory = DeckViewModel.factory(container)),
) {
    // collectAsStateWithLifecycle: para de coletar quando a tela sai de
    // primeiro plano, o que também interrompe o polling de /api/health.
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current

    // Modo de edição explícito: sem ele, o toque longo teria dois
    // significados (arrastar e trocar ícone) e um atrapalharia o outro.
    var editMode by rememberSaveable { mutableStateOf(false) }

    // Atalho cujo menu está aberto. O toque longo agora abre um menu em vez
    // de ir direto ao seletor de ícone: descoberta por gesto invisível só
    // funciona quando há uma ação óbvia, e agora são duas.
    var menuFor by remember { mutableStateOf<DeckItem?>(null) }

    val message = state.message
    val launchedText = (message as? DeckMessage.Launched)
        ?.let { stringResource(R.string.deck_launched, it.name) }
    val failedText = (message as? DeckMessage.Failed)
        ?.let { apiErrorMessage(it.error, state.configState.configOrNull) }

    LaunchedEffect(message?.id) {
        val text = launchedText ?: failedText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        message?.id?.let(viewModel::consumeMessage)
    }

    // Sem topBar: o deck ocupa a tela toda e o único controle fixo é o botão
    // de menu sobreposto no canto superior esquerdo. O Scaffold fica só pelo
    // snackbar e pelos insets do sistema.
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isRefreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when {
                state.isLoadingConfig -> LoadingState()

                !state.isConfigured -> EmptyState(
                    title = stringResource(R.string.deck_not_configured_title),
                    body = stringResource(R.string.deck_not_configured_body),
                    actionLabel = stringResource(R.string.deck_not_configured_action),
                    onAction = onOpenSettings,
                )

                state.items.isEmpty() -> EmptyState(
                    title = stringResource(R.string.deck_empty_title),
                    body = stringResource(R.string.deck_empty_body),
                    actionLabel = stringResource(R.string.deck_action_refresh),
                    onAction = viewModel::refresh,
                )

                else -> DeckGrid(
                    items = state.items,
                    launching = state.launching,
                    editMode = editMode,
                    onLaunch = { item ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.launch(item)
                    },
                    onEditIcon = { item -> menuFor = item },
                    onOrderChanged = viewModel::saveOrder,
                )
            }

            // Sobreposto por último para ficar acima da grade. No modo de
            // edição o botão dá lugar ao "Concluir": sem ele não haveria como
            // sair do modo, já que o header foi embora.
            val overlayModifier = Modifier
                .align(Alignment.TopStart)
                .padding(OverlayPadding)

            if (editMode) {
                EditModeBar(onDone = { editMode = false }, modifier = overlayModifier)
            } else {
                DeckMenuButton(
                    status = state.connection,
                    canAdd = state.isConfigured,
                    canRefresh = !state.isRefreshing,
                    canEdit = state.items.isNotEmpty(),
                    onAdd = onAddShortcut,
                    onRefresh = viewModel::refresh,
                    onEdit = { editMode = true },
                    onSettings = onOpenSettings,
                    modifier = overlayModifier,
                )
            }
        }
    }

    menuFor?.let { item ->
        ShortcutMenuSheet(
            item = item,
            onDismiss = { menuFor = null },
            onEditIcon = {
                menuFor = null
                onEditIcon(item.id)
            },
            onEditShortcut = {
                menuFor = null
                onEditShortcut(item.id)
            },
        )
    }
}

/**
 * Faixa do modo de edição, no lugar do botão de menu: saída do modo mais a
 * dica de como reordenar.
 */
@Composable
private fun EditModeBar(onDone: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(end = 12.dp),
        ) {
            TextButton(onClick = onDone) {
                Text(stringResource(R.string.deck_action_edit_done))
            }
            Text(
                text = stringResource(R.string.deck_edit_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Menu do toque longo: trocar ícone ou editar o atalho. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShortcutMenuSheet(
    item: DeckItem,
    onDismiss: () -> Unit,
    onEditIcon: () -> Unit,
    onEditShortcut: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.menu_change_icon)) },
                leadingContent = { Icon(Icons.Filled.Face, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onEditIcon),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.menu_edit)) },
                leadingContent = { Icon(Icons.Filled.Edit, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onEditShortcut),
            )
        }
    }
}

@Composable
private fun DeckGrid(
    items: List<DeckItem>,
    launching: Set<String>,
    editMode: Boolean,
    onLaunch: (DeckItem) -> Unit,
    onEditIcon: (DeckItem) -> Unit,
    onOrderChanged: (List<String>) -> Unit,
) {
    val gridState = rememberLazyGridState()

    // Cópia local reordenada durante o arraste: o item precisa acompanhar o
    // dedo imediatamente, sem esperar a ida ao banco e a volta pelo Flow.
    var ordered by remember(items) { mutableStateOf(items) }

    val reorderableState = rememberReorderableLazyGridState(gridState) { from, to ->
        ordered = ordered.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    LazyVerticalGrid(
        state = gridState,
        // Adaptive em vez de um número fixo de colunas: o mesmo layout serve
        // para celular em retrato, paisagem e tablet.
        columns = GridCells.Adaptive(minSize = 104.dp),
        contentPadding = GridContentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // key estável: sem ela, a grade reembaralha itens ao recarregar e a
        // animação de arraste troca o item errado de lugar.
        items(items = ordered, key = { it.id }) { item ->
            ReorderableItem(reorderableState, key = item.id) { isDragging ->
                val elevation by animateDpAsState(
                    targetValue = if (isDragging) 8.dp else 0.dp,
                    label = "elevation",
                )
                DeckTile(
                    item = item,
                    isLaunching = item.id in launching,
                    // No modo de edição os handlers precisam ser nulos, e não
                    // apenas inertes: um combinedClickable registrado consome
                    // o toque longo e o arraste nunca começa.
                    onClick = if (editMode) null else ({ onLaunch(item) }),
                    onLongClick = if (editMode) null else ({ onEditIcon(item) }),
                    modifier = if (editMode) {
                        Modifier
                            .shadow(elevation, RoundedCornerShape(16.dp))
                            .longPressDraggableHandle(
                                onDragStopped = { onOrderChanged(ordered.map { it.id }) },
                            )
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}
