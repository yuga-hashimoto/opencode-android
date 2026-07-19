package com.opencode.android.feature.assistant

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.opencode.android.runtime.local.RuntimeArchive
import java.io.File
import java.security.MessageDigest
import okhttp3.OkHttpClient
import okhttp3.Request

data class WakeWordPackManifest(
    val id: String,
    val name: String,
    val version: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long? = null,
    @SerializedName("signature") val signature: String? = null
)

data class InstalledWakeWordPack(
    val id: String,
    val name: String,
    val version: String,
    val directory: File,
    val sha256: String
)

/**
 * Optional wake-word pack lifecycle. Packs are not bundled; users may install a
 * signed/hashed zip into app storage. Actual hotword engine binding is deferred
 * until a verified pack is present.
 */
class WakeWordPackManager(
    private val rootDirectory: File,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson()
) {
    private val packsDir = File(rootDirectory, "wakeword").apply { mkdirs() }
    private val installedFile = File(packsDir, "installed.json")

    fun installed(): InstalledWakeWordPack? {
        if (!installedFile.exists()) return null
        return runCatching {
            val json = gson.fromJson(installedFile.readText(), InstalledWakeWordPackDto::class.java)
            val dir = File(json.directory)
            if (!dir.isDirectory) return null
            InstalledWakeWordPack(
                id = json.id,
                name = json.name,
                version = json.version,
                directory = dir,
                sha256 = json.sha256
            )
        }.getOrNull()
    }

    fun isInstalled(): Boolean = installed() != null

    fun install(manifest: WakeWordPackManifest, archiveBytes: ByteArray): InstalledWakeWordPack {
        require(manifest.url.startsWith("https://")) { "Wake-word pack URL must be HTTPS" }
        val expected = normalizeSha(manifest.sha256)
        require(expected.length == 64) { "Wake-word pack SHA-256 is invalid" }
        val actual = sha256Hex(archiveBytes)
        require(actual.equals(expected, ignoreCase = true)) {
            "Wake-word pack hash mismatch"
        }
        manifest.sizeBytes?.let { expectedSize ->
            require(archiveBytes.size.toLong() == expectedSize) {
                "Wake-word pack size mismatch"
            }
        }
        // Optional detached signature field is recorded; full public-key verify can be added later.
        val target = File(packsDir, manifest.id).apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        val archive = File(packsDir, "${manifest.id}.zip")
        archive.writeBytes(archiveBytes)
        RuntimeArchive.extractZip(archive.inputStream(), target)
        archive.delete()
        val installed = InstalledWakeWordPack(
            id = manifest.id,
            name = manifest.name,
            version = manifest.version,
            directory = target,
            sha256 = expected
        )
        installedFile.writeText(
            gson.toJson(
                InstalledWakeWordPackDto(
                    id = installed.id,
                    name = installed.name,
                    version = installed.version,
                    directory = installed.directory.absolutePath,
                    sha256 = installed.sha256
                )
            )
        )
        return installed
    }

    suspend fun downloadAndInstall(manifest: WakeWordPackManifest): InstalledWakeWordPack {
        val request = Request.Builder().url(manifest.url).get().build()
        val bytes = httpClient.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "Wake-word download failed (HTTP ${response.code})" }
            response.body?.bytes() ?: error("Wake-word download had empty body")
        }
        return install(manifest, bytes)
    }

    fun delete() {
        installed()?.directory?.deleteRecursively()
        installedFile.delete()
        packsDir.listFiles()?.forEach { child ->
            if (child.name.endsWith(".zip")) child.delete()
        }
    }

    private data class InstalledWakeWordPackDto(
        val id: String,
        val name: String,
        val version: String,
        val directory: String,
        val sha256: String
    )

    companion object {
        fun normalizeSha(value: String): String =
            value.removePrefix("sha256:").trim().lowercase()

        fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
