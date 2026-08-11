package dev.alexkobayashi.appdeck.ui.common

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import dev.alexkobayashi.appdeck.data.local.IconFileStore
import dev.alexkobayashi.appdeck.domain.model.ShortcutIcon
import java.io.File

/**
 * Renderiza o ícone de um atalho, qualquer que seja a origem.
 *
 * Único ponto do app que sabe desenhar cada tipo de ícone — o tile do deck e
 * a pré-visualização do seletor usam exatamente o mesmo componente, então o
 * que o usuário vê no seletor é o que ele vai ver na grade.
 */
@Composable
fun ShortcutIconView(
    icon: ShortcutIcon,
    // modifier antes de size: é a convenção do Compose (o lint cobra), e
    // permite que quem chama passe só o modifier sem nomear os argumentos.
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    when (icon) {
        is ShortcutIcon.Emoji -> Box(
            modifier = modifier.size(size),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = icon.char,
                // Emoji não herda cor do tema: é desenhado pela fonte do
                // sistema. Só o tamanho importa aqui.
                style = TextStyle(fontSize = (size.value * 0.62f).sp),
            )
        }

        is ShortcutIcon.Initials -> InitialsIcon(icon.text, size, modifier)

        // Builtin e Local ainda não têm renderização própria: o pacote de
        // vetores e a integração com o Coil entram nos incrementos seguintes.
        // Até lá estes casos são inalcançáveis, porque o seletor não oferece
        // essas opções — o fallback existe para não haver estado sem desenho.
        is ShortcutIcon.Builtin -> InitialsIcon(icon.key.take(2).uppercase(), size, modifier)

        is ShortcutIcon.Local -> {
            val context = LocalContext.current
            val file = remember(icon.fileName) {
                File(File(context.filesDir, IconFileStore.DIR_NAME), icon.fileName)
            }
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(Uri.fromFile(file))
                    // updatedAt entra na chave de cache: sem isso, trocar a
                    // imagem continuaria exibindo a anterior, porque o Coil
                    // guardaria a antiga sob o mesmo nome de arquivo.
                    .memoryCacheKey("${icon.fileName}:${icon.updatedAt}")
                    .diskCacheKey("${icon.fileName}:${icon.updatedAt}")
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = modifier
                    .size(size)
                    .clip(RoundedCornerShape(size * 0.25f)),
            )
        }
    }
}

@Composable
private fun InitialsIcon(text: String, size: Dp, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = (size.value * 0.34f).sp,
                ),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
