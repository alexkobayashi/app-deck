package dev.alexkobayashi.appdeck.data.local

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class IconFileStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var store: IconFileStore

    @Before
    fun setUp() {
        store = IconFileStore(RuntimeEnvironment.getApplication(), now = { 42L })
    }

    private var imageCounter = 0

    /** Grava um PNG de verdade e devolve a Uri file:// dele. */
    private fun writeImage(width: Int, height: Int): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val file = temp.newFile("origem-${imageCounter++}-${width}x$height.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(file)
    }

    private fun iconsDir(): File =
        File(RuntimeEnvironment.getApplication().filesDir, IconFileStore.DIR_NAME)

    // Este é o teste que faltava no incremento anterior. Ele teria pego na
    // hora o bug em que o resultado de decodeStream com inJustDecodeBounds
    // (sempre null, por definição) era tratado como falha, fazendo TODA
    // imagem ser recusada.
    @Test
    fun `salva uma imagem e devolve o nome do arquivo`() = runTest {
        val uri = writeImage(1200, 800)

        val fileName = store.save("a1", uri)

        assertNotNull("nenhuma imagem deveria ser recusada aqui", fileName)
        assertEquals("ic_a1_42.webp", fileName)
        assertTrue(store.fileFor(fileName!!).exists())
        assertTrue(store.fileFor(fileName).length() > 0)
    }

    @Test
    fun `a imagem salva fica quadrada no tamanho alvo`() = runTest {
        val fileName = store.save("a1", writeImage(1200, 400))!!

        val saved = BitmapFactory.decodeFile(store.fileFor(fileName).absolutePath)

        assertEquals(IconFileStore.TARGET_SIZE, saved.width)
        assertEquals(IconFileStore.TARGET_SIZE, saved.height)
    }

    @Test
    fun `imagem pequena tambem e aceita`() = runTest {
        // Um ícone de 64px não deve ser recusado só por ser menor que o alvo.
        assertNotNull(store.save("a1", writeImage(64, 64)))
    }

    @Test
    fun `nao deixa arquivo temporario para tras`() = runTest {
        store.save("a1", writeImage(800, 800))

        val leftovers = iconsDir().listFiles()?.filter { it.name.endsWith(".tmp") }.orEmpty()

        assertTrue("temporários deixados: $leftovers", leftovers.isEmpty())
    }

    @Test
    fun `uri ilegivel devolve null em vez de estourar`() = runTest {
        val missing = Uri.fromFile(File(temp.root, "nao-existe.png"))

        assertNull(store.save("a1", missing))
    }

    // Um arquivo que não é imagem não pode ser testado aqui: o BitmapFactory
    // do Robolectric é simulado e devolve um bitmap de dimensões fixas sem
    // olhar o conteúdo. Em produção o decodeStream falha e outWidth fica -1,
    // caindo no mesmo caminho já coberto por "uri ilegivel".

    @Test
    fun `delete remove o arquivo e e idempotente`() = runTest {
        val fileName = store.save("a1", writeImage(600, 600))!!

        store.delete(fileName)
        assertTrue(!store.fileFor(fileName).exists())

        // Apagar duas vezes não pode estourar.
        store.delete(fileName)
    }

    @Test
    fun `deleteExcept limpa apenas o que nao esta em uso`() = runTest {
        val keep = store.save("a1", writeImage(600, 600))!!
        val drop = store.save("b2", writeImage(600, 600))!!

        store.deleteExcept(setOf(keep))

        assertTrue(store.fileFor(keep).exists())
        assertTrue(!store.fileFor(drop).exists())
    }

    /**
     * O sample size evita decodificar a foto inteira: uma imagem de 12 MP
     * viraria ~48 MB de bitmap na memória, o suficiente para derrubar o app.
     */
    @Test
    fun `sampleSize reduz sem passar abaixo do alvo`() {
        // 4000x3000, alvo 512: o menor lado dividido por 4 ainda é 750 (>512),
        // por 8 seria 375 (<512) — logo 4.
        assertEquals(4, IconFileStore.sampleSizeFor(4000, 3000, 512))
        assertEquals(1, IconFileStore.sampleSizeFor(600, 800, 512))
        // Imagem menor que o alvo não é reduzida.
        assertEquals(1, IconFileStore.sampleSizeFor(100, 100, 512))
        assertEquals(2, IconFileStore.sampleSizeFor(2000, 1024, 512))
    }

    @Test
    fun `recorte central devolve um quadrado no tamanho alvo`() {
        val wide = Bitmap.createBitmap(1000, 400, Bitmap.Config.ARGB_8888)

        val result = IconFileStore.cropCenterSquare(wide, 512)

        assertEquals(512, result.width)
        assertEquals(512, result.height)
    }

    @Test
    fun `recorte de imagem alta tambem fica quadrado`() {
        val tall = Bitmap.createBitmap(400, 1000, Bitmap.Config.ARGB_8888)

        val result = IconFileStore.cropCenterSquare(tall, 512)

        assertEquals(512, result.width)
        assertEquals(512, result.height)
    }

    @Test
    fun `imagem ja quadrada no tamanho alvo passa direto`() {
        val square = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)

        val result = IconFileStore.cropCenterSquare(square, 512)

        assertEquals(512, result.width)
        assertEquals(512, result.height)
    }
}
