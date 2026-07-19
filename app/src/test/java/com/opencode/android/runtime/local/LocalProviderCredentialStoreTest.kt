package com.opencode.android.runtime.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalProviderCredentialStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `encode and decode round trip`() {
        val encoded = LocalProviderCredentialStore.encodeMap(mapOf("openai" to "sk-test"))
        val decoded = LocalProviderCredentialStore.decodeMap(encoded)
        assertEquals(mapOf("openai" to "sk-test"), decoded)
    }

    @Test
    fun `sync writes auth json for runtime rootfs`() {
        val rootfs = temp.newFolder("rootfs")
        val memory = mutableMapOf("openai" to "sk-123")
        val store = LocalProviderCredentialStore(
            load = { memory.toMap() },
            save = {
                memory.clear()
                memory.putAll(it)
            }
        )

        val authFile = store.syncToRuntime(rootfs)

        assertTrue(authFile.exists())
        val text = authFile.readText()
        assertTrue(text.contains("openai"))
        assertTrue(text.contains("sk-123"))
        assertTrue(text.contains("api"))
        assertEquals(File(rootfs, "root/.local/share/opencode/auth.json").absolutePath, authFile.absolutePath)
    }

    @Test
    fun `blank key removes credential`() {
        val memory = mutableMapOf("openai" to "sk-123")
        val store = LocalProviderCredentialStore(
            load = { memory.toMap() },
            save = {
                memory.clear()
                memory.putAll(it)
            }
        )
        store.setCredential("openai", "  ")
        assertFalse(store.hasCredential("openai"))
        assertTrue(store.credentials().isEmpty())
    }
}
