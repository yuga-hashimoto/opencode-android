package com.opencode.android.feature.assistant

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WakeWordPackManagerTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `rejects non https manifest url`() {
        val manager = WakeWordPackManager(rootDirectory = temp.newFolder())
        val manifest = WakeWordPackManifest(
            id = "demo",
            name = "Demo",
            version = "1.0.0",
            url = "http://example.com/pack.zip",
            sha256 = "a".repeat(64)
        )
        val bytes = "hello".toByteArray()
        val error = runCatching { manager.install(manifest, bytes) }.exceptionOrNull()
        assertTrue(error?.message?.contains("HTTPS") == true)
        assertFalse(manager.isInstalled())
    }

    @Test
    fun `rejects invalid sha length`() {
        val manager = WakeWordPackManager(rootDirectory = temp.newFolder())
        val manifest = WakeWordPackManifest(
            id = "demo",
            name = "Demo",
            version = "1.0.0",
            url = "https://example.com/pack.zip",
            sha256 = "short"
        )
        val error = runCatching { manager.install(manifest, "data".toByteArray()) }.exceptionOrNull()
        assertTrue(error?.message?.contains("SHA-256") == true)
    }

    @Test
    fun `rejects hash mismatch`() {
        val manager = WakeWordPackManager(rootDirectory = temp.newFolder())
        val bytes = "data".toByteArray()
        val manifest = WakeWordPackManifest(
            id = "demo",
            name = "Demo",
            version = "1.0.0",
            url = "https://example.com/pack.zip",
            sha256 = "0".repeat(64)
        )
        val error = runCatching { manager.install(manifest, bytes) }.exceptionOrNull()
        assertTrue(error?.message?.contains("hash mismatch") == true)
    }

    @Test
    fun `deletes installed pack`() {
        val root = temp.newFolder()
        val manager = WakeWordPackManager(rootDirectory = root)
        val zipBytes = zipWithEntry("hello.txt", "hi")
        val sha = WakeWordPackManager.sha256Hex(zipBytes)
        val manifest = WakeWordPackManifest(
            id = "demo",
            name = "Demo",
            version = "1.0.0",
            url = "https://example.com/pack.zip",
            sha256 = sha
        )
        val installed = manager.install(manifest, zipBytes)
        assertEquals("Demo", installed.name)
        assertTrue(manager.isInstalled())
        assertTrue(File(installed.directory, "hello.txt").isFile)

        manager.delete()
        assertFalse(manager.isInstalled())
        assertNull(manager.installed())
    }

    private fun zipWithEntry(name: String, content: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            val entry = java.util.zip.ZipEntry(name)
            zip.putNextEntry(entry)
            zip.write(content.toByteArray())
            zip.closeEntry()
        }
        return out.toByteArray()
    }
}
