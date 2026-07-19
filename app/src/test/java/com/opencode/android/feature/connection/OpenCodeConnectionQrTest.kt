package com.opencode.android.feature.connection

import com.opencode.android.feature.workspace.ConnectionFormState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenCodeConnectionQrTest {
    @Test
    fun `encodes and decodes a complete connection payload`() {
        val form = ConnectionFormState(
            id = "test",
            name = "Mac mini",
            baseUrl = "http://192.168.1.10:4096",
            username = "opencode",
            password = "secret",
            allowInsecureLan = true
        )

        val encoded = OpenCodeConnectionQr.encode(form)
        val decoded = OpenCodeConnectionQr.decode(encoded).getOrThrow()

        assertEquals("Mac mini", decoded.name)
        assertEquals("http://192.168.1.10:4096/", decoded.baseUrl)
        assertEquals("opencode", decoded.username)
        assertEquals("secret", decoded.password)
        assertTrue(decoded.allowInsecureLan)
        assertTrue(decoded.canSave)
    }

    @Test
    fun `decodes bare trusted LAN URL into insecure LAN form`() {
        val decoded = OpenCodeConnectionQr.decode("192.168.1.5:4096").getOrThrow()

        assertEquals("http://192.168.1.5:4096/", decoded.baseUrl)
        assertTrue(decoded.allowInsecureLan)
    }

    @Test
    fun `decodes opencode scheme with explicit LAN permission`() {
        val raw = "opencode://connect?url=192.168.1.20%3A4096&name=Office&lan=1"

        val decoded = OpenCodeConnectionQr.decode(raw).getOrThrow()

        assertEquals("Office", decoded.name)
        assertEquals("http://192.168.1.20:4096/", decoded.baseUrl)
        assertTrue(decoded.password.isEmpty())
        assertTrue(decoded.allowInsecureLan)
    }

    @Test
    fun `fails on empty unsupported or public cleartext payload`() {
        assertTrue(OpenCodeConnectionQr.decode("").isFailure)
        assertTrue(OpenCodeConnectionQr.decode("ftp://example.com").isFailure)
        assertTrue(OpenCodeConnectionQr.decode("http://example.com:4096").isFailure)
    }

    @Test
    fun `rejects URL embedded credentials`() {
        assertTrue(OpenCodeConnectionQr.decode("https://user:pass@example.com").isFailure)
    }

    @Test
    fun `opencode LAN HTTP requires explicit permission`() {
        val raw = "opencode://connect?url=192.168.1.20%3A4096&name=Office"

        assertTrue(OpenCodeConnectionQr.decode(raw).isFailure)
    }

    @Test
    fun `omits password from encoding when blank`() {
        val encoded = OpenCodeConnectionQr.encode(
            ConnectionFormState(
                name = "LAN",
                baseUrl = "192.168.1.1:4096",
                allowInsecureLan = true
            )
        )

        assertFalse(encoded.contains("password"))
    }
}
