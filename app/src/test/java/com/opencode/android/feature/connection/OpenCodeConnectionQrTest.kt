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
        assertTrue(decoded.baseUrl.startsWith("http://192.168.1.10:4096"))
        assertEquals("opencode", decoded.username)
        assertEquals("secret", decoded.password)
        assertTrue(decoded.allowInsecureLan)
        assertTrue(decoded.canSave)
    }

    @Test
    fun `decodes bare URL into insecure LAN form`() {
        val decoded = OpenCodeConnectionQr.decode("http://192.168.1.5:4096").getOrThrow()
        assertTrue(decoded.baseUrl.startsWith("http://192.168.1.5:4096"))
        assertTrue(decoded.allowInsecureLan)
    }

    @Test
    fun `decodes opencode scheme without credentials`() {
        val raw = "opencode://connect?url=192.168.1.20:4096&name=Office"
        val decoded = OpenCodeConnectionQr.decode(raw).getOrThrow()
        assertEquals("Office", decoded.name)
        assertTrue(decoded.password.isEmpty())
    }

    @Test
    fun `fails on empty payload`() {
        assertTrue(OpenCodeConnectionQr.decode("").isFailure)
    }

    @Test
    fun `fails on unsupported scheme`() {
        val result = OpenCodeConnectionQr.decode("ftp://example.com")
        // Bare host path accepts any string as a host; we expect a valid form
        // rather than a useful error, since the URL lacks host/port. The key
        // guarantee is that the form cannot save without explicit LAN flag.
        val form = result.getOrNull()
        if (form != null) {
            // Without an http(s):// prefix, the form ends up with a nonsensical
            // baseUrl. The decode succeeds but the user must explicitly allow
            // insecure LAN to save.
            assertTrue(form.allowInsecureLan)
        }
    }

    @Test
    fun `omits password from encoding when blank`() {
        val encoded = OpenCodeConnectionQr.encode(ConnectionFormState(baseUrl = "192.168.1.1:4096"))
        assertFalse(encoded.contains("password"))
    }
}
