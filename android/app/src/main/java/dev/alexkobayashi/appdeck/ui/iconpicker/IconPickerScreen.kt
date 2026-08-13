package dev.alexkobayashi.appdeck.ui.iconpicker

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.alexkobayashi.appdeck.AppContainer
import dev.alexkobayashi.appdeck.R
import dev.alexkobayashi.appdeck.domain.model.ShortcutIcon
import dev.alexkobayashi.appdeck.ui.common.ShortcutIconView
import dev.alexkobayashi.appdeck.ui.icons.BuiltinIconCatalog
import dev.alexkobayashi.appdeck.ui.icons.EmojiCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPickerScreen(
    container: AppContainer,
    appId: String,
    onDone: () -> Unit,
    viewModel: IconPickerViewModel = viewModel(
        // key por appId: sem ela, abrir o seletor de outro atalho reusaria o
        // ViewModel do anterior.
        key = "icon-picker-$appId",
        factory = IconPickerViewModel.factory(container, appId),
    ),
) {
    val item by viewModel.item.collectAsStateWithLifecycle()
    val imageError by viewModel.imageError.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val imageErrorText = stringResource(R.string.icon_image_error)

    // Photo Picker: não exige permissão de armazenamento, e em aparelhos sem
    // o seletor nativo a própria biblioteca cai para o seletor de documentos.
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(viewModel::chooseImage) }

    val pickImage: () -> Unit = {
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    LaunchedEffect(imageError) {
        if (imageError) {
            snackbarHostState.showSnackbar(imageErrorText)
            viewModel.consumeImageError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.icon_title))
                        item?.let {
                            Text(
                                text = it.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.config_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::clearIcon) {
                        Text(stringResource(R.string.icon_remove))
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 56.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            // Pré-visualização com o mesmo componente do tile do deck: o que
            // aparece aqui é exatamente o que vai aparecer na grade.
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ShortcutIconView(
                        icon = item?.icon ?: ShortcutIcon.Initials("?"),
                        size = 72.dp,
                    )
                    Button(onClick = { pickImage() }) {
                        Text(stringResource(R.string.icon_from_gallery))
                    }
                }
            }

            // Os ícones de aplicativo vêm antes do emoji: para um atalho que
            // abre um programa conhecido, o logo dele é a primeira escolha
            // óbvia, e o emoji virou a alternativa.
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(stringResource(R.string.icon_builtin_section))
            }
            BuiltinIconCatalog.groups.forEach { group ->
                item(span = { GridItemSpan(maxLineSpan) }) { GroupLabel(group.label) }
                items(items = group.icons, key = { "builtin-${it.key}" }) { builtin ->
                    PickerCell(
                        selected = (item?.icon as? ShortcutIcon.Builtin)?.key == builtin.key,
                        onClick = { viewModel.chooseBuiltin(builtin.key) },
                        // A célula não mostra texto, igual às de emoji, então
                        // sem isto a grade seria um punhado de imagens
                        // indistinguíveis no leitor de tela.
                        label = builtin.label,
                    ) {
                        ShortcutIconView(
                            icon = ShortcutIcon.Builtin(builtin.key),
                            size = 32.dp,
                        )
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(stringResource(R.string.icon_emoji_section))
            }
            EmojiCatalog.groups.forEach { group ->
                item(span = { GridItemSpan(maxLineSpan) }) { GroupLabel(group.label) }
                items(items = group.emojis, key = { "${group.label}-$it" }) { emoji ->
                    PickerCell(
                        selected = (item?.icon as? ShortcutIcon.Emoji)?.char == emoji,
                        onClick = { viewModel.chooseEmoji(emoji) },
                    ) {
                        // O emoji é lido como texto pelo TalkBack, então não
                        // precisa de label próprio.
                        Text(text = emoji, style = TextStyle(fontSize = 26.sp))
                    }
                }
            }
        }
    }
}

/** Cabeçalho de uma origem de ícone (aplicativos, emoji). */
@Composable
private fun SectionHeader(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()
    }
}

/** Subgrupo dentro de uma origem (Sistema, Desenvolvimento, ...). */
@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/**
 * Célula clicável da grade, comum às duas origens.
 *
 * Uma só implementação para emoji e ícone de aplicativo: as cores de seleção
 * precisam ser idênticas nos dois casos, e duplicá-las é como elas divergem.
 */
@Composable
private fun PickerCell(
    selected: Boolean,
    onClick: () -> Unit,
    label: String? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .then(
                if (label == null) {
                    Modifier
                } else {
                    Modifier.semantics { contentDescription = label }
                },
            ),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            content()
        }
    }
}
