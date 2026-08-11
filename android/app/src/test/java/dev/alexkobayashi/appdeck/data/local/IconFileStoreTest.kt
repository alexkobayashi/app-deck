package dev.alexkobayashi.appdeck.data.local

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IconFileStoreTest {

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
        assertTrue(result.height == 512)
    }
}
