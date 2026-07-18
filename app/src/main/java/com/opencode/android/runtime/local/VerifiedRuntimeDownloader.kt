package com.opencode.android.runtime.local

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class VerifiedRuntimeDownloader(
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    private val operationMutex = Mutex()

    suspend fun download(
        url: String,
        destination: File,
        expectedSha256: String,
        expectedSizeBytes: Long? = null,
        onProgress: (Float?) -> Unit = {}
    ) = operationMutex.withLock {
        withContext(Dispatchers.IO) {
            downloadLocked(
                url = url,
                destination = destination,
                expectedSha256 = expectedSha256,
                expectedSizeBytes = expectedSizeBytes,
                onProgress = onProgress
            )
        }
    }

    private fun downloadLocked(
        url: String,
        destination: File,
        expectedSha256: String,
        expectedSizeBytes: Long?,
        onProgress: (Float?) -> Unit
    ) {
        val parsedUrl = url.toHttpUrl()
        require(parsedUrl.isHttps || parsedUrl.host in LOOPBACK_HOSTS) {
            "Runtime download URL must use HTTPS"
        }
        require(SHA256.matches(expectedSha256)) { "Invalid expected SHA-256" }
        require(expectedSizeBytes == null || expectedSizeBytes > 0L) {
            "Expected download size must be positive"
        }
        destination.parentFile?.mkdirs()
        val partial = File(destination.parentFile, destination.name + ".partial")
        val backup = File(destination.parentFile, destination.name + ".backup")
        partial.delete()
        recoverBackupIfDestinationMissing(destination, backup)

        try {
            val request = Request.Builder().url(parsedUrl).get().build()
            var downloaded = 0L
            httpClient.newCall(request).execute().use { response ->
                require(response.isSuccessful) {
                    "Runtime download failed with HTTP ${response.code}"
                }
                val body = requireNotNull(response.body) { "Runtime download response had no body" }
                partial.outputStream().buffered().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            onProgress(expectedSizeBytes?.let { downloaded.toFloat() / it })
                        }
                    }
                }
            }
            expectedSizeBytes?.let { expected ->
                require(downloaded == expected) {
                    "Runtime download size mismatch: expected $expected, got $downloaded"
                }
            }
            RuntimeArchive.verifySha256(partial, expectedSha256)

            if (destination.isFile && runCatching {
                    RuntimeArchive.verifySha256(destination, expectedSha256)
                }.isSuccess
            ) {
                partial.delete()
                backup.delete()
                onProgress(1f)
                return
            }

            if (!backup.exists() && destination.exists()) {
                move(destination, backup)
            } else if (backup.exists() && destination.exists()) {
                require(destination.delete()) {
                    "Unable to remove the unverified runtime download"
                }
            }
            try {
                move(partial, destination)
                backup.delete()
            } catch (error: Throwable) {
                destination.delete()
                if (backup.exists()) {
                    runCatching { move(backup, destination) }
                        .exceptionOrNull()
                        ?.let(error::addSuppressed)
                }
                throw error
            }
            onProgress(1f)
        } finally {
            partial.delete()
            recoverBackupIfDestinationMissing(destination, backup)
        }
    }

    private fun recoverBackupIfDestinationMissing(destination: File, backup: File) {
        if (!destination.exists() && backup.exists()) {
            move(backup, destination)
        }
    }

    private fun move(source: File, destination: File) {
        require(source.parentFile?.canonicalFile == destination.parentFile?.canonicalFile) {
            "Verified runtime download moves must stay on one filesystem"
        }
        destination.parentFile?.mkdirs()
        require(source.renameTo(destination)) {
            "Unable to move ${source.name} to ${destination.name}"
        }
    }

    companion object {
        private val SHA256 = Regex("^[a-f0-9]{64}$")
        private val LOOPBACK_HOSTS = setOf("127.0.0.1", "localhost", "::1")
    }
}
