package dev.alexkobayashi.appdeck.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingPayloadTest {

    @Test
    fun `le o payload que o servidor gera`() {
        val raw = """{"ip":"192.168.3.186","port":5050,"token":"juTLaJ-yoSa8fy__-uZZ"}"""

        val payload = PairingPayload.parse(raw)

        assertEquals(PairingPayload("192.168.3.186", 5050, "juTLaJ-yoSa8fy__-uZZ"), payload)
    }

    // O servidor sempre manda número, mas o config.json do protótipo gravava
    // "porta" como string. Ser tolerante aqui é barato.
    @Test
    fun `aceita port como string`() {
        val raw = """{"ip":"192.168.0.10","port":"5050","token":"abc123"}"""

        assertEquals(5050, PairingPayload.parse(raw)?.port)
    }

    @Test
    fun `ignora campos desconhecidos`() {
        // Uma versão futura do servidor pode acrescentar campos ao QR.
        val raw = """{"ip":"192.168.0.10","port":5050,"token":"abc","v":2,"extra":{"a":1}}"""

        assertEquals("192.168.0.10", PairingPayload.parse(raw)?.ip)
    }

    @Test
    fun `remove espacos nas pontas`() {
        val raw = """  {"ip":" 192.168.0.10 ","port":5050,"token":" abc "}  """

        val payload = PairingPayload.parse(raw)

        assertEquals("192.168.0.10", payload?.ip)
        assertEquals("abc", payload?.token)
    }

    @Test
    fun `recusa o que nao e pareamento do App Deck`() {
        val cases = listOf(
            "https://exemplo.com",                                   // QR de site
            "texto solto",                                           // QR de texto
            "",                                                      // vazio
            "{",                                                     // JSON quebrado
            """{"ip":"192.168.0.10"}""",                             // sem port nem token
            """{"ip":"","port":5050,"token":"abc"}""",               // ip vazio
            """{"ip":"192.168.0.10","port":0,"token":"abc"}""",      // porta inválida
            """{"ip":"192.168.0.10","port":70000,"token":"abc"}""",  // porta fora da faixa
            """{"ip":"192.168.0.10","port":5050,"token":""}""",      // sem token
            """{"ip":"http://192.168.0.10","port":5050,"token":"a"}""", // ip com esquema
            """["ip","port"]""",                                     // JSON que não é objeto
        )

        cases.forEach { raw ->
            assertNull("deveria recusar: $raw", PairingPayload.parse(raw))
        }
    }

    @Test
    fun `converte para a configuracao do servidor`() {
        val payload = PairingPayload("192.168.0.10", 5050, "abc")

        assertEquals(ServerConfig("192.168.0.10", 5050, "abc"), payload.toServerConfig())
    }
}
