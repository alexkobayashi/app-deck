package dev.alexkobayashi.appdeck.data.scanner

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Procura um QR code em cada quadro da câmera.
 *
 * Usa o ML Kit **embutido** (`com.google.mlkit:barcode-scanning`), cujo
 * modelo vai dentro do APK. A variante `play-services-mlkit-*` baixaria o
 * modelo em runtime pela Play Store, o que este app não pode assumir: o APK é
 * distribuído fora da loja, e num aparelho corporativo a Play Store pode
 * estar restrita ou ausente.
 */
class QrAnalyzer(private val onFound: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build(),
    )

    /**
     * A câmera continua entregando quadros depois do primeiro acerto, e o
     * callback navega de tela. Sem esta guarda, o pareamento dispararia
     * várias vezes.
     */
    private val found = AtomicBoolean(false)

    // androidx.annotation.OptIn, não o do Kotlin: ExperimentalGetImage é um
    // marcador do androidx, e o lint só reconhece essa forma.
    @OptIn(markerClass = [ExperimentalGetImage::class])
    override fun analyze(image: ImageProxy) {
        val media = image.image
        if (found.get() || media == null) {
            image.close()
            return
        }

        val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { codes ->
                val value = codes.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) }
                if (value != null && found.compareAndSet(false, true)) {
                    onFound(value)
                }
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "falha ao analisar quadro", error)
            }
            // O ImageProxy precisa ser fechado sempre, senão a câmera para de
            // entregar quadros depois de encher a fila.
            .addOnCompleteListener { image.close() }
    }

    fun close() {
        scanner.close()
    }

    private companion object {
        const val TAG = "AppDeckQrAnalyzer"
    }
}
