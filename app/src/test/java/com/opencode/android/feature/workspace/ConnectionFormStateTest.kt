package com.opencode.android.feature.workspace

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionFormStateTest {
    @Test
    fun `save is disabled when name or endpoint is missing`() {
        assertFalse(ConnectionFormState().canSave)
        assertFalse(ConnectionFormState(name = "Mac mini").canSave)
        assertFalse(ConnectionFormState(baseUrl = "192.168.1.10:4096").canSave)
    }

    @Test
    fun `save is enabled for valid LAN and HTTPS endpoints`() {
        assertTrue(
            ConnectionFormState(
                name = "Mac mini",
                baseUrl = "192.168.1.10:4096",
                allowInsecureLan = true
            ).canSave
        )
        assertTrue(
            ConnectionFormState(
                name = "Server",
                baseUrl = "https://opencode.example.com"
            ).canSave
        )
    }

    @Test
    fun `public cleartext endpoint cannot be saved`() {
        assertFalse(
            ConnectionFormState(
                name = "Unsafe",
                baseUrl = "http://example.com:4096",
                allowInsecureLan = true
            ).canSave
        )
    }
}
