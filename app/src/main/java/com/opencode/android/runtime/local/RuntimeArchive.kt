package com.opencode.android.runtime.local

import android.system.Os
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

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

    fun extractTarGz(input: InputStream, destination: File) {
        destination.mkdirs()
        val canonicalRoot = destination.canonicalFile
        GzipCompressorInputStream(input.buffered()).use { gzip ->
            TarArchiveInputStream(gzip).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    val target = File(destination, entry.name).canonicalFile
                    require(target.path == canonicalRoot.path || target.path.startsWith(canonicalRoot.path + File.separator)) {
                        "Archive entry escapes destination: ${entry.name}"
                    }
                    when {
                        entry.isDirectory -> target.mkdirs()
                        entry.isSymbolicLink -> {
                            target.parentFile?.mkdirs()
                            target.delete()
                            val linkTarget = File(target.parentFile, entry.linkName).canonicalFile
                            if (linkTarget.path.startsWith(canonicalRoot.path + File.separator) ||
                                linkTarget.path == canonicalRoot.path ||
                                !entry.linkName.startsWith("/")
                            ) {
                                Os.symlink(entry.linkName, target.absolutePath)
                            }
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
                    entry = tar.nextEntry
                }
            }
        }
    }
}
