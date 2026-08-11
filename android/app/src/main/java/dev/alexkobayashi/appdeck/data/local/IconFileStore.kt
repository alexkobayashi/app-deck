package dev.alexkobayashi.appdeck.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Guarda as imagens escolhidas na galeria no armazenamento interno do app.
 *
 * A imagem é copiada em vez de referenciada por Uri: a permissão que o Photo
 * Picker concede é temporária, e a foto original pode ser apagada da galeria.
 * Sem a cópia, o ícone sumiria depois.
 */
class IconFileStore(
    private val context: Context,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dir: File
        get() = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    fun fileFor(fileName: String): File = File(dir, fileName)

    /**
     * Lê a imagem apontada por [source], recorta no centro em quadrado,
     * reduz para [TARGET_SIZE] e grava como WEBP.
     *
     * Devolve o nome do arquivo, ou null se a imagem não pôde ser lida.
     */
    suspend fun save(appId: String, source: Uri): String? = withContext(Dispatchers.IO) {
        val bitmap = decodeScaled(source) ?: return@withContext null
        val square = cropCenterSquare(bitmap, TARGET_SIZE)
        // O recorte devolve outro bitmap; o original não serve mais.
        if (square !== bitmap) bitmap.recycle()

        val fileName = "ic_${appId}_${now()}.webp"
        // Escrita atômica pelo mesmo princípio do config.json do servidor:
        // grava num temporário e renomeia, para nunca existir um arquivo de
        // ícone truncado se o processo morrer no meio.
        val tmp = File(dir, "$fileName.tmp")
        val target = File(dir, fileName)
        try {
            tmp.outputStream().use { out ->
                square.compress(compressFormat(), QUALITY, out)
            }
            if (!tmp.renameTo(target)) {
                tmp.delete()
                return@withContext null
            }
        } catch (e: Exception) {
            tmp.delete()
            return@withContext null
        } finally {
            square.recycle()
        }
        fileName
    }

    /** Apaga um ícone. Arquivo já inexistente não é erro. */
    suspend fun delete(fileName: String) = withContext(Dispatchers.IO) {
        File(dir, fileName).delete()
        Unit
    }

    /**
     * Remove arquivos que nenhuma customização referencia — sobras de trocas
     * de ícone interrompidas.
     */
    suspend fun deleteExcept(keep: Set<String>) = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { file ->
            if (file.name !in keep) file.delete()
        }
        Unit
    }

    /**
     * Decodifica já reduzida: uma foto de 12 MP decodificada inteira seria
     * ~48 MB de bitmap, o suficiente para derrubar o app por falta de memória.
     */
    private fun decodeScaled(source: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, TARGET_SIZE)
        }
        val decoded = context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        return applyExifRotation(source, decoded)
    }

    /** Foto tirada com o celular de lado chega deitada sem isto. */
    private fun applyExifRotation(source: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            context.contentResolver.openInputStream(source)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { if (it !== bitmap) bitmap.recycle() }
    }

    private fun compressFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            // WEBP simples está depreciado a partir da API 30, mas é o que
            // existe na 26-29, que o app ainda suporta.
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }

    companion object {
        const val DIR_NAME = "icons"
        const val TARGET_SIZE = 512
        const val QUALITY = 85

        /** Maior potência de 2 que ainda deixa a imagem acima do alvo. */
        fun sampleSizeFor(width: Int, height: Int, target: Int): Int {
            var sample = 1
            val smallerSide = minOf(width, height)
            while (smallerSide / (sample * 2) >= target) {
                sample *= 2
            }
            return sample
        }

        /**
         * Recorte central quadrado seguido de redimensionamento.
         *
         * Recorte automático em vez de uma tela de recorte manual: para um
         * ícone de 48dp, escolher a moldura raramente compensa o passo extra.
         */
        fun cropCenterSquare(bitmap: Bitmap, targetSize: Int): Bitmap {
            val side = minOf(bitmap.width, bitmap.height)
            val x = (bitmap.width - side) / 2
            val y = (bitmap.height - side) / 2
            val cropped = Bitmap.createBitmap(bitmap, x, y, side, side)
            if (side == targetSize) return cropped

            val scaled = Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
            if (scaled !== cropped) cropped.recycle()
            return scaled
        }
    }
}
