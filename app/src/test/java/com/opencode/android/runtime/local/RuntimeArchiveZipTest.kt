package com.opencode.android.runtime.local

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RuntimeArchiveZipTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `extracts within entry and total limits`() {
        val destination = temp.newFolder("valid")
        val archive = zip(mapOf("a.txt" to "abc", "nested/b.txt" to "12345"))

        val stats = RuntimeArchive.extractZip(
            archive.inputStream(),
            destination,
            ZipExtractionLimits(maxEntries = 3, maxEntryBytes = 10, maxTotalBytes = 10)
        )

        assertEquals(2, stats.entries)
        assertEquals(8, stats.extractedBytes)
        assertEquals("abc", destination.resolve("a.txt").readText())
    }

    @Test
    fun `rejects traversal backslash and absolute paths`() {
        val destination = temp.newFolder("unsafe")
        listOf("../escape.txt", "..\\escape.txt", "/absolute.txt").forEach { path ->
            assertThrows(IllegalArgumentException::class.java) {
                RuntimeArchive.extractZip(zip(mapOf(path to "x")).inputStream(), destination)
            }
        }
        assertFalse(temp.root.resolve("escape.txt").exists())
    }

    @Test
    fun `rejects too many entries and expanded bytes`() {
        val destination = temp.newFolder("limits")
        assertThrows(IllegalArgumentException::class.java) {
            RuntimeArchive.extractZip(
                zip(mapOf("a" to "1", "b" to "2")).inputStream(),
                destination,
                ZipExtractionLimits(maxEntries = 1, maxEntryBytes = 10, maxTotalBytes = 10)
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RuntimeArchive.extractZip(
                zip(mapOf("large" to "123456")).inputStream(),
                destination,
                ZipExtractionLimits(maxEntries = 2, maxEntryBytes = 5, maxTotalBytes = 5)
            )
        }
    }

    private fun zip(entries: Map<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, value) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
