package com.opencode.android.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenCodeUrlTest {
    @Test
    fun `adds http scheme and trailing slash for private LAN host`() {
        val result = OpenCodeUrl.normalize("192.168.1.20:4096")
        assertEquals("http://192.168.1.20:4096/", result.getOrThrow().toString())
    }

    @Test
    fun `keeps https path and appends trailing slash`() {
        val result = OpenCodeUrl.normalize("https://example.com/opencode")
        assertEquals("https://example.com/opencode/", result.getOrThrow().toString())
    }

    @Test
    fun `rejects unsupported scheme`() {
        assertTrue(OpenCodeUrl.normalize("ftp://example.com").isFailure)
    }

    @Test
    fun `rejects public cleartext endpoint`() {
        assertTrue(OpenCodeUrl.normalize("http://example.com").isFailure)
    }

    @Test
    fun `allows tailscale cgnat endpoint`() {
        assertTrue(OpenCodeUrl.normalize("http://100.64.0.10:4096").isSuccess)
    }

    @Test
    fun `allows localhost local domains and ipv6 ula literals`() {
        assertTrue(OpenCodeUrl.normalize("http://127.0.0.1:4096").isSuccess)
        assertTrue(OpenCodeUrl.normalize("http://opencode.local:4096").isSuccess)
        assertTrue(OpenCodeUrl.normalize("http://[fd00::1]:4096").isSuccess)
    }

    @Test
    fun `does not mistake public hostnames beginning with fc or fd for ipv6 ula`() {
        assertTrue(OpenCodeUrl.normalize("http://fcbarcelona.com:4096").isFailure)
        assertTrue(OpenCodeUrl.normalize("http://fdupdates.example.com:4096").isFailure)
    }
}
