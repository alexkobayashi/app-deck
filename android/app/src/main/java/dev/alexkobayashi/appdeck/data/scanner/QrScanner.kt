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
     *
     * [detail] carrega o motivo do Play Services. Ele é exibido na tela de
     * propósito: um APK distribuído fora da Play Store não tem canal de
     * relatório de erro, e sem isso a única alternativa é adivinhar.
     */
    data class ModuleUnavailable(val detail: String?) : ScanResult

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

    override suspend fun scan(): ScanResult = try {
        val module = ensureModuleAvailable()
        if (module == null) startScan() else ScanResult.ModuleUnavailable(module)
    } catch (e: Throwable) {
        // Throwable, não Exception: se o R8 remover uma classe do Play
        // Services, o que chega aqui é NoClassDefFoundError, que é Error e
        // passaria batido por catch(Exception) — derrubando o app.
        Log.w(TAG, "leitura de QR falhou", e)
        ScanResult.Failed(e)
    }

    /**
     * Garante que o módulo de leitura existe, pedindo a instalação se faltar.
     *
     * Devolve `null` quando o módulo está pronto, ou uma descrição curta do
     * motivo quando não está — descrição que sobe até a tela, porque um APK
     * distribuído fora da Play Store não tem outro canal de diagnóstico.
     */
    private suspend fun ensureModuleAvailable(): String? = suspendCancellableCoroutine { cont ->
        // Tudo aqui é chamada síncrona ao Play Services e pode lançar antes
        // de qualquer listener ser registrado. Sem esta proteção a exceção
        // escapa da corrotina e fecha o app — foi o que aconteceu.
        val client = try {
            GmsBarcodeScanning.getClient(activityContext, options)
        } catch (e: Throwable) {
            cont.resume(describe("criar cliente", e))
            return@suspendCancellableCoroutine
        }

        val moduleInstall = try {
            ModuleInstall.getClient(activityContext)
        } catch (e: Throwable) {
            cont.resume(describe("ModuleInstall", e))
            return@suspendCancellableCoroutine
        }

        moduleInstall.areModulesAvailable(client)
            .addOnSuccessListener { response ->
                if (response.areModulesAvailable()) {
                    cont.resume(null)
                    return@addOnSuccessListener
                }
                try {
                    moduleInstall
                        .installModules(ModuleInstallRequest.newBuilder().addApi(client).build())
                        .addOnSuccessListener { installResponse ->
                            // installModules devolve assim que o pedido é
                            // aceito, não quando o download termina. Se o
                            // módulo já estava lá, dá para seguir direto.
                            if (installResponse.areModulesAlreadyInstalled()) {
                                cont.resume(null)
                            } else {
                                cont.resume("download iniciado")
                            }
                        }
                        .addOnFailureListener { error ->
                            cont.resume(describe("installModules", error))
                        }
                } catch (e: Throwable) {
                    cont.resume(describe("installModules (sync)", e))
                }
            }
            .addOnFailureListener { error ->
                // Sem Play Services, areModulesAvailable já falha aqui.
                cont.resume(describe("areModulesAvailable", error))
            }
    }

    /** Descrição curta e sem dado sensível, para caber num rodapé de tela. */
    private fun describe(etapa: String, e: Throwable): String {
        Log.w(TAG, "falha em $etapa", e)
        val code = (e as? com.google.android.gms.common.api.ApiException)?.statusCode
        val suffix = if (code != null) " código $code" else ""
        return "$etapa: ${e.javaClass.simpleName}$suffix ${e.message.orEmpty()}".trim().take(180)
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
        } catch (e: Throwable) {
            Log.w(TAG, "startScan lançou exceção", e)
            cont.resume(ScanResult.Failed(e))
        }
    }

    private companion object {
        const val TAG = "AppDeckQrScanner"
    }
}
