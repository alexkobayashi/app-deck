package dev.alexkobayashi.appdeck.data.scanner

import android.content.Context
import android.util.Log
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
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

    /**
     * O módulo de leitura ainda não está no aparelho.
     *
     * Distinto de [Failed] porque a ação do usuário é diferente: aqui é
     * esperar/conectar à internet, não desistir e digitar à mão.
     */
    data object ModuleUnavailable : ScanResult

    /** Sem Play Services, câmera ocupada, ou falha inesperada. */
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
 * O custo é depender do Play Services e de um módulo que é baixado sob
 * demanda. O manifest declara `com.google.mlkit.vision.DEPENDENCIES` para o
 * módulo vir junto na instalação; ainda assim, num app recém-instalado ele
 * pode não estar pronto, e por isso [scan] pede a instalação antes de abrir
 * a câmera.
 *
 * Precisa de um Context de Activity: o leitor abre uma tela própria.
 */
class GmsQrScanner(private val activityContext: Context) : QrScanner {

    private val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .enableAutoZoom()
        .build()

    override suspend fun scan(): ScanResult {
        if (!ensureModuleAvailable()) return ScanResult.ModuleUnavailable
        return startScan()
    }

    /**
     * Garante que o módulo de leitura existe, pedindo a instalação se faltar.
     *
     * Sem esta checagem, o `startScan` de um app recém-instalado falha com uma
     * exceção genérica e o usuário só vê "não foi possível abrir o leitor",
     * sem pista de que era só aguardar um download.
     */
    private suspend fun ensureModuleAvailable(): Boolean = suspendCancellableCoroutine { cont ->
        val client = GmsBarcodeScanning.getClient(activityContext, options)
        val moduleInstall = ModuleInstall.getClient(activityContext)

        moduleInstall.areModulesAvailable(client)
            .addOnSuccessListener { response ->
                if (response.areModulesAvailable()) {
                    cont.resume(true)
                    return@addOnSuccessListener
                }
                moduleInstall
                    .installModules(ModuleInstallRequest.newBuilder().addApi(client).build())
                    .addOnSuccessListener { cont.resume(true) }
                    .addOnFailureListener { error ->
                        Log.w(TAG, "instalação do módulo de leitura falhou", error)
                        cont.resume(false)
                    }
            }
            .addOnFailureListener { error ->
                // Sem Play Services, areModulesAvailable já falha aqui.
                Log.w(TAG, "consulta de disponibilidade do módulo falhou", error)
                cont.resume(false)
            }
    }

    private suspend fun startScan(): ScanResult = suspendCancellableCoroutine { cont ->
        try {
            GmsBarcodeScanning.getClient(activityContext, options)
                .startScan()
                .addOnSuccessListener { barcode ->
                    val value = barcode.rawValue
                    cont.resume(
                        if (value.isNullOrBlank()) {
                            ScanResult.Failed(null)
                        } else {
                            ScanResult.Success(value)
                        },
                    )
                }
                .addOnCanceledListener { cont.resume(ScanResult.Cancelled) }
                .addOnFailureListener { error ->
                    // O motivo real precisa aparecer em algum lugar: sem isso,
                    // diagnosticar uma falha em release é adivinhação.
                    Log.w(TAG, "startScan falhou", error)
                    cont.resume(ScanResult.Failed(error))
                }
        } catch (e: Exception) {
            Log.w(TAG, "startScan lançou exceção", e)
            cont.resume(ScanResult.Failed(e))
        }
    }

    private companion object {
        const val TAG = "AppDeckQrScanner"
    }
}
