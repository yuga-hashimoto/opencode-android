package com.opencode.android.runtime.local

import android.system.Os
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

data class ZipExtractionLimits(
    val maxEntries: Int = 128,
    val maxEntryBytes: Long = 64L * 1024L * 1024L,
    val maxTotalBytes: Long = 256L * 1024L * 1024L
) {
    init {
        require(maxEntries > 0)
        require(maxEntryBytes > 0L)
        require(maxTotalBytes > 0L)
        require(maxEntryBytes <= maxTotalBytes)
    }
}

data class ZipExtractionStats(
    val entries: Int,
    val extractedBytes: Long
)

object RuntimeArchive {
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verifySha256(file: File, expected: String) {
        val actual = sha256(file)
        require(actual.equals(expected, ignoreCase = true)) {
            "SHA-256 mismatch for ${file.name}: expected $expected, got $actual"
        }
    }

    fun extractZip(
        input: InputStream,
        destination: File,
        limits: ZipExtractionLimits = ZipExtractionLimits()
    ): ZipExtractionStats {
        destination.mkdirs()
        val canonicalRoot = destination.canonicalFile
        var entryCount = 0
        var totalBytes = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                require(entryCount <= limits.maxEntries) {
                    "ZIP contains more than ${limits.maxEntries} entries"
                }
                require(entry.name.isNotBlank()) { "ZIP entry name is empty" }
                require(!entry.name.startsWith('/') && '\\' !in entry.name) {
                    "Unsafe ZIP entry path: ${entry.name}"
                }
                if (entry.size >= 0L) {
                    require(entry.size <= limits.maxEntryBytes) {
                        "ZIP entry is too large: ${entry.name}"
                    }
                    require(totalBytes + entry.size <= limits.maxTotalBytes) {
                        "ZIP expands beyond ${limits.maxTotalBytes} bytes"
                    }
                }

                val target = File(destination, entry.name).canonicalFile
                require(target.path == canonicalRoot.path || target.path.startsWith(canonicalRoot.path + File.separator)) {
                    "Archive entry escapes destination: ${entry.name}"
                }
                if (entry.isDirectory) {
                    require(target.mkdirs() || target.isDirectory) {
                        "Unable to create ZIP directory: ${entry.name}"
                    }
                } else {
                    target.parentFile?.let { parent ->
                        require(parent.mkdirs() || parent.isDirectory) {
                            "Unable to create ZIP directory: ${parent.name}"
                        }
                    }
                    var entryBytes = 0L
                    target.outputStream().buffered().use { output ->
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            entryBytes += count
                            totalBytes += count
                            require(entryBytes <= limits.maxEntryBytes) {
                                "ZIP entry is too large: ${entry.name}"
                            }
                            require(totalBytes <= limits.maxTotalBytes) {
                                "ZIP expands beyond ${limits.maxTotalBytes} bytes"
                            }
                            output.write(buffer, 0, count)
                        }
                    }
                    target.setReadable(true, true)
                    target.setWritable(true, true)
                }
                zip.closeEntry()
            }
        }
        return ZipExtractionStats(entryCount, totalBytes)
    }

    fun extractTarGz(input: InputStream, destination: File) {
        destination.mkdirs()
        val canonicalRoot = destination.canonicalFile
        GzipCompressorInputStream(input.buffered()).use { gzip ->
            TarArchiveInputStream(gzip).use { tar ->
                while (true) {
                    val entry = tar.nextTarEntry ?: break
                    val target = File(destination, entry.name).canonicalFile
                    require(target.path == canonicalRoot.path || target.path.startsWith(canonicalRoot.path + File.separator)) {
                        "Archive entry escapes destination: ${entry.name}"
                    }
                    when {
                        entry.isDirectory -> target.mkdirs()
                        entry.isSymbolicLink -> {
                            target.parentFile?.mkdirs()
                            target.delete()
                            Os.symlink(entry.linkName, target.absolutePath)
                        }
                        entry.isLink -> {
                            val source = File(destination, entry.linkName).canonicalFile
                            require(source.path == canonicalRoot.path || source.path.startsWith(canonicalRoot.path + File.separator)) {
                                "Archive hard link escapes destination: ${entry.linkName}"
                            }
                            target.parentFile?.mkdirs()
                            target.delete()
                            Os.link(source.absolutePath, target.absolutePath)
                        }
                        entry.isFile -> {
                            target.parentFile?.mkdirs()
                            target.outputStream().buffered().use { output -> tar.copyTo(output) }
                            val executable = entry.mode and 0b001_001_001 != 0
                            target.setReadable(true, false)
                            target.setWritable(true, true)
                            if (executable) target.setExecutable(true, false)
                        }
                    }
                }
            }
        }
    }
}
