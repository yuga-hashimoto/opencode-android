package com.opencode.android.runtime.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalRuntimeManifestTest {
    private val architecture = LocalRuntimeArchitecture(
        alpineUrl = "https://example.com/alpine.tar.gz",
        alpineSha256 = "a".repeat(64),
        openCodeUrl = "https://example.com/opencode.tar.gz",
        openCodeSha256 = "b".repeat(64)
    )

    @Test
    fun `valid manifest resolves architecture`() {
        val manifest = LocalRuntimeManifest(
            schemaVersion = 1,
            runtimeVersion = "2026.07.18.1",
            openCodeVersion = "1.18.3",
            alpineVersion = "3.24.1",
            port = 4097,
            architectures = mapOf("arm64-v8a" to architecture)
        )

        manifest.validate()

        assertEquals(architecture, manifest.architecture("arm64-v8a"))
    }

    @Test
    fun `manifest rejects insecure download URL`() {
        val invalid = architecture.copy(alpineUrl = "http://example.com/alpine.tar.gz")

        assertThrows(IllegalArgumentException::class.java) {
            invalid.validate("arm64-v8a")
        }
    }

    @Test
    fun `manifest rejects invalid hash`() {
        val invalid = architecture.copy(openCodeSha256 = "not-a-hash")

        assertThrows(IllegalArgumentException::class.java) {
            invalid.validate("arm64-v8a")
        }
    }
}
