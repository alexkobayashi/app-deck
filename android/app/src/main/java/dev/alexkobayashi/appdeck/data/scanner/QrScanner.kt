package dev.alexkobayashi.appdeck.data.scanner

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Resultado de uma tentativa de leitura de QR. */
sealed interface ScanResult {
    data class Success(val raw: String) : ScanResult

    /** O usuário fechou o leitor. Não é erro, não deve virar mensagem. */
    data object Cancelled : ScanResult

    /** Sem Play Services, módulo indisponível, câmera ocupada. */
    data class Failed(val cause: Throwable?) : ScanResult
}

/** Abstração para o ViewModel poder ser testado sem câmera nem Play Services. */
interface QrScanner {
    suspend fun scan(): ScanResult
}

/**
 * Leitor baseado no Google Play Services.
 *
 * Escolhido em vez de CameraX + ML Kit embutido porque **não exige permissão
 * de câmera**: a captura acontece no processo do Play Services, não no do
 * app. Menos código, menos superfície de permissão, e nenhuma tela de câmera
 * para manter.
 *
 * O custo é depender do Play Services — presente em praticamente todo
 * aparelho com Play Store, mas não em todos; nesse caso [scan] devolve
 * [ScanResult.Failed] e a tela sugere a configuração manual, que continua
 * existindo.
 *
 * Precisa de um Context de Activity: o leitor abre uma tela própria.
 */
class GmsQrScanner(private val activityContext: Context) : QrScanner {

    private val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .enableAutoZoom()
        .build()

    override suspend fun scan(): ScanResult = suspendCancellableCoroutine { continuation ->
        try {
            GmsBarcodeScanning.getClient(activityContext, options)
                .startScan()
                .addOnSuccessListener { barcode ->
                    val value = barcode.rawValue
                    continuation.resume(
                        if (value.isNullOrBlank()) {
                            ScanResult.Failed(null)
                        } else {
                            ScanResult.Success(value)
                        },
                    )
                }
                .addOnCanceledListener { continuation.resume(ScanResult.Cancelled) }
                .addOnFailureListener { continuation.resume(ScanResult.Failed(it)) }
        } catch (e: Exception) {
            continuation.resume(ScanResult.Failed(e))
        }
    }
}
