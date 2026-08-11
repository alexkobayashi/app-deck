package dev.alexkobayashi.appdeck.ui.deck

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.alexkobayashi.appdeck.AppContainer
import dev.alexkobayashi.appdeck.R
import dev.alexkobayashi.appdeck.domain.model.DeckItem
import dev.alexkobayashi.appdeck.ui.common.apiErrorMessage
import dev.alexkobayashi.appdeck.ui.deck.components.ConnectionBadge
import dev.alexkobayashi.appdeck.ui.deck.components.DeckTile
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckScreen(
    container: AppContainer,
    onOpenSettings: () -> Unit,
    onEditIcon: (String) -> Unit,
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.deck_title))
                        if (editMode) {
                            Text(
                                text = stringResource(R.string.deck_edit_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            ConnectionBadge(state.connection)
                        }
                    }
                },
                actions = {
                    if (editMode) {
                        TextButton(onClick = { editMode = false }) {
                            Text(stringResource(R.string.deck_action_edit_done))
                        }
                    } else {
                        IconButton(onClick = viewModel::refresh, enabled = !state.isRefreshing) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.deck_action_refresh),
                            )
                        }
                        IconButton(
                            onClick = { editMode = true },
                            enabled = state.items.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.deck_action_edit),
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.deck_action_settings),
                            )
                        }
                    }
                },
            )
        },
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
                    onEditIcon = { item -> onEditIcon(item.id) },
                    onOrderChanged = viewModel::saveOrder,
                )
            }
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
        contentPadding = PaddingValues(16.dp),
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
