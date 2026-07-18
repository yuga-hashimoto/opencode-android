package com.opencode.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ConnectionProfileTest {
    @Test
    fun `connection profiles round trip through json`() {
        val original = listOf(
            ConnectionProfile(
                id = "mac-mini",
                name = "Mac mini",
                baseUrl = "http://192.168.1.10:4096/",
                username = "opencode",
                password = "secret",
                allowInsecureLan = true
            )
        )

        val encoded = ConnectionProfileCodec.encode(original)
        val decoded = ConnectionProfileCodec.decode(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `toString never exposes password`() {
        val profile = ConnectionProfile(
            id = "pc",
            name = "PC",
            baseUrl = "https://example.com/",
            username = "opencode",
            password = "super-secret",
            allowInsecureLan = false
        )

        assertFalse(profile.toString().contains("super-secret"))
    }
}
