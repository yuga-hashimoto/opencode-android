package com.opencode.android.runtime.local

import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

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
        val memory = memoryStore(mapOf("openai" to "sk-123"), setOf("openai"))

        val authFile = memory.store.syncToRuntime(rootfs)

        assertTrue(authFile.exists())
        val payload = JsonParser.parseString(authFile.readText()).asJsonObject
        assertEquals("api", payload.getAsJsonObject("openai")["type"].asString)
        assertEquals("sk-123", payload.getAsJsonObject("openai")["key"].asString)
        assertEquals(File(rootfs, "root/.local/share/opencode/auth.json").absolutePath, authFile.absolutePath)
    }

    @Test
    fun `sync preserves unmanaged OAuth and API entries`() {
        val rootfs = temp.newFolder("preserve-rootfs")
        val authFile = File(rootfs, "root/.local/share/opencode/auth.json").apply {
            parentFile.mkdirs()
            writeText(
                """{
                  "anthropic":{"type":"oauth","access":"oauth-token"},
                  "custom":{"type":"api","key":"custom-key"},
                  "openai":{"type":"api","key":"old-managed-key"}
                }""".trimIndent()
            )
        }
        val memory = memoryStore(mapOf("openai" to "new-managed-key"), setOf("openai"))

        memory.store.syncToRuntime(rootfs)

        val payload = JsonParser.parseString(authFile.readText()).asJsonObject
        assertEquals("oauth-token", payload.getAsJsonObject("anthropic")["access"].asString)
        assertEquals("custom-key", payload.getAsJsonObject("custom")["key"].asString)
        assertEquals("new-managed-key", payload.getAsJsonObject("openai")["key"].asString)
    }

    @Test
    fun `cleared managed credential is removed without deleting unmanaged entries`() {
        val rootfs = temp.newFolder("clear-rootfs")
        val authFile = File(rootfs, "root/.local/share/opencode/auth.json").apply {
            parentFile.mkdirs()
            writeText(
                """{
                  "openai":{"type":"api","key":"managed-key"},
                  "anthropic":{"type":"oauth","access":"oauth-token"}
                }""".trimIndent()
            )
        }
        val memory = memoryStore(mapOf("openai" to "managed-key"), setOf("openai"))

        memory.store.clearCredential("openai")
        memory.store.syncToRuntime(rootfs)

        val payload = JsonParser.parseString(authFile.readText()).asJsonObject
        assertFalse(payload.has("openai"))
        assertEquals("oauth-token", payload.getAsJsonObject("anthropic")["access"].asString)
        assertTrue("openai" in memory.managedIds)
    }

    @Test
    fun `blank key removes encrypted credential but keeps provider managed`() {
        val memory = memoryStore(mapOf("openai" to "sk-123"), setOf("openai"))

        memory.store.setCredential("openai", "  ")

        assertFalse(memory.store.hasCredential("openai"))
        assertTrue(memory.store.credentials().isEmpty())
        assertTrue("openai" in memory.managedIds)
    }

    @Test
    fun `unmanage provider preserves encrypted key but stops runtime overwrite`() {
        val rootfs = temp.newFolder("oauth-rootfs")
        val authFile = File(rootfs, "root/.local/share/opencode/auth.json").apply {
            parentFile.mkdirs()
            writeText("""{"openai":{"type":"oauth","access":"oauth-token"}}""")
        }
        val memory = memoryStore(mapOf("openai" to "api-key"), setOf("openai"))

        memory.store.unmanageProvider("openai")
        memory.store.syncToRuntime(rootfs)

        val payload = JsonParser.parseString(authFile.readText()).asJsonObject
        assertEquals("oauth", payload.getAsJsonObject("openai")["type"].asString)
        assertEquals("api-key", memory.store.credentials()["openai"])
        assertFalse("openai" in memory.managedIds)
    }

    @Test
    fun `malformed existing auth file is preserved and sync fails`() {
        val rootfs = temp.newFolder("malformed-rootfs")
        val authFile = File(rootfs, "root/.local/share/opencode/auth.json").apply {
            parentFile.mkdirs()
            writeText("not-json")
        }
        val memory = memoryStore(mapOf("openai" to "sk-123"), setOf("openai"))

        assertThrows(IllegalStateException::class.java) {
            memory.store.syncToRuntime(rootfs)
        }

        assertEquals("not-json", authFile.readText())
    }

    private fun memoryStore(
        initialCredentials: Map<String, String> = emptyMap(),
        initialManagedIds: Set<String> = emptySet()
    ): MemoryCredentialStore {
        val credentials = initialCredentials.toMutableMap()
        val managedIds = initialManagedIds.toMutableSet()
        val store = LocalProviderCredentialStore(
            load = { credentials.toMap() },
            save = {
                credentials.clear()
                credentials.putAll(it)
            },
            loadManagedProviderIds = { managedIds.toSet() },
            saveManagedProviderIds = {
                managedIds.clear()
                managedIds.addAll(it)
            }
        )
        return MemoryCredentialStore(store, managedIds)
    }

    private data class MemoryCredentialStore(
        val store: LocalProviderCredentialStore,
        val managedIds: MutableSet<String>
    )
}
