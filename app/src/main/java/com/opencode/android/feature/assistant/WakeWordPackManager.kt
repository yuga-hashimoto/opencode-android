package com.opencode.android.feature.assistant

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.opencode.android.runtime.local.RuntimeArchive
import com.opencode.android.runtime.local.ZipExtractionLimits
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request

data class WakeWordPackManifest(
    @SerializedName("schemaVersion") val schemaVersion: Int = 1,
    val id: String,
    val name: String,
    val version: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val requiredFiles: List<String>? = listOf("pack.json"),
    val signature: String
)

data class WakeWordPackDescriptor(
    @SerializedName("schemaVersion") val schemaVersion: Int = 1,
    val id: String,
    val version: String,
    val languageTag: String,
    val phrases: List<String>
)

data class InstalledWakeWordPack(
    val id: String,
    val name: String,
    val version: String,
    val directory: File,
    val sha256: String,
    val languageTag: String,
    val phrases: List<String>
)

/**
 * Installs optional signed wake-word phrase packs. The app trusts only manifests
 * signed by the embedded maintainer key, verifies the archive hash and size,
 * limits ZIP expansion, validates pack.json, and activates through staging.
 */
class WakeWordPackManager(
    private val rootDirectory: File,
    private val trustedPublicKey: PublicKey,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson(),
    private val maxArchiveBytes: Long = DEFAULT_MAX_ARCHIVE_BYTES
) {
    init {
        require(maxArchiveBytes in 1..ABSOLUTE_MAX_ARCHIVE_BYTES)
    }

    private val packsDir = File(rootDirectory, "wakeword").apply { mkdirs() }
    private val activeDirectory = File(packsDir, "active")
    private val stagingDirectory = File(packsDir, "staging")
    private val rollbackDirectory = File(packsDir, "rollback")
    private val installedFile = File(packsDir, "installed.json")

    fun installed(): InstalledWakeWordPack? {
        if (!installedFile.isFile || !activeDirectory.isDirectory) return null
        return runCatching {
            val dto = gson.fromJson(installedFile.readText(), InstalledWakeWordPackDto::class.java)
            require(dto.id.matches(PACK_ID))
            require(dto.name.isNotBlank() && dto.version.isNotBlank())
            require(dto.languageTag.isNotBlank())
            require(dto.phrases.isNotEmpty())
            InstalledWakeWordPack(
                id = dto.id,
                name = dto.name,
                version = dto.version,
                directory = activeDirectory,
                sha256 = normalizeSha(dto.sha256),
                languageTag = dto.languageTag,
                phrases = normalizePhrases(dto.phrases)
            )
        }.getOrNull()
    }

    fun isInstalled(): Boolean = installed() != null

    @Synchronized
    fun install(
        manifest: WakeWordPackManifest,
        archiveBytes: ByteArray
    ): InstalledWakeWordPack {
        validateManifest(manifest)
        require(archiveBytes.size.toLong() == manifest.sizeBytes) {
            "Wake-word pack size mismatch"
        }
        require(archiveBytes.size.toLong() <= maxArchiveBytes) {
            "Wake-word pack exceeds the configured size limit"
        }
        val expectedSha = normalizeSha(manifest.sha256)
        require(sha256Hex(archiveBytes) == expectedSha) {
            "Wake-word pack hash mismatch"
        }

        stagingDirectory.deleteRecursively()
        require(stagingDirectory.mkdirs()) { "Unable to create wake-word staging directory" }
        try {
            RuntimeArchive.extractZip(
                input = archiveBytes.inputStream(),
                destination = stagingDirectory,
                limits = ZipExtractionLimits(
                    maxEntries = MAX_ZIP_ENTRIES,
                    maxEntryBytes = minOf(MAX_ZIP_ENTRY_BYTES, maxArchiveBytes * 4),
                    maxTotalBytes = minOf(MAX_ZIP_EXPANDED_BYTES, maxArchiveBytes * 8)
                )
            )
            val requiredFiles = validatedRequiredFiles(manifest)
            requiredFiles.forEach { relativePath ->
                val file = safePackFile(stagingDirectory, relativePath)
                require(file.isFile && file.length() > 0L) {
                    "Wake-word pack is missing required file: $relativePath"
                }
            }
            val descriptor = readAndValidateDescriptor(
                File(stagingDirectory, DESCRIPTOR_FILE),
                manifest
            )
            val installed = activate(manifest, descriptor, expectedSha)
            rollbackDirectory.deleteRecursively()
            return installed
        } catch (error: Throwable) {
            stagingDirectory.deleteRecursively()
            throw error
        }
    }

    suspend fun downloadManifestAndInstall(manifestUrl: String): InstalledWakeWordPack {
        val normalizedManifestUrl = validatedHttpsUrl(manifestUrl, "Wake-word manifest URL")
        val manifestBytes = downloadLimited(normalizedManifestUrl, MAX_MANIFEST_BYTES)
        val manifest = runCatching {
            gson.fromJson(manifestBytes.toString(Charsets.UTF_8), WakeWordPackManifest::class.java)
        }.getOrElse { error ->
            throw IllegalArgumentException("Wake-word manifest JSON is invalid", error)
        }
        validateManifest(manifest)
        val archiveBytes = downloadLimited(manifest.url, manifest.sizeBytes)
        return install(manifest, archiveBytes)
    }

    suspend fun downloadAndInstall(manifest: WakeWordPackManifest): InstalledWakeWordPack {
        validateManifest(manifest)
        return install(manifest, downloadLimited(manifest.url, manifest.sizeBytes))
    }

    @Synchronized
    fun delete() {
        activeDirectory.deleteRecursively()
        stagingDirectory.deleteRecursively()
        rollbackDirectory.deleteRecursively()
        installedFile.delete()
        packsDir.listFiles().orEmpty()
            .filter { it.isFile && (it.name.endsWith(".tmp") || it.name.endsWith(".partial")) }
            .forEach(File::delete)
    }

    fun validateManifest(manifest: WakeWordPackManifest) {
        require(manifest.schemaVersion == 1) {
            "Unsupported wake-word manifest schema: ${manifest.schemaVersion}"
        }
        require(manifest.id.matches(PACK_ID)) { "Wake-word pack id is invalid" }
        require(manifest.name.isNotBlank() && manifest.name.length <= 100) {
            "Wake-word pack name is invalid"
        }
        require(manifest.version.isNotBlank() && manifest.version.length <= 64) {
            "Wake-word pack version is invalid"
        }
        validatedHttpsUrl(manifest.url, "Wake-word pack URL")
        require(normalizeSha(manifest.sha256).matches(SHA256)) {
            "Wake-word pack SHA-256 is invalid"
        }
        require(manifest.sizeBytes in 1..maxArchiveBytes) {
            "Wake-word pack size is invalid"
        }
        validatedRequiredFiles(manifest)
        val signatureBytes = runCatching { Base64.getDecoder().decode(manifest.signature.trim()) }
            .getOrElse { throw IllegalArgumentException("Wake-word pack signature is invalid", it) }
        require(signatureBytes.isNotEmpty() && signatureBytes.size <= MAX_SIGNATURE_BYTES) {
            "Wake-word pack signature is invalid"
        }
        val verifier = Signature.getInstance(SIGNATURE_ALGORITHM)
        verifier.initVerify(trustedPublicKey)
        verifier.update(signingPayload(manifest))
        require(verifier.verify(signatureBytes)) {
            "Wake-word pack signature verification failed"
        }
    }

    private fun activate(
        manifest: WakeWordPackManifest,
        descriptor: WakeWordPackDescriptor,
        sha256: String
    ): InstalledWakeWordPack {
        rollbackDirectory.deleteRecursively()
        if (activeDirectory.exists()) {
            require(activeDirectory.renameTo(rollbackDirectory)) {
                "Unable to preserve the previous wake-word pack"
            }
        }
        try {
            require(stagingDirectory.renameTo(activeDirectory)) {
                "Unable to activate wake-word pack"
            }
            val installed = InstalledWakeWordPack(
                id = manifest.id,
                name = manifest.name.trim(),
                version = manifest.version.trim(),
                directory = activeDirectory,
                sha256 = sha256,
                languageTag = descriptor.languageTag.trim(),
                phrases = normalizePhrases(descriptor.phrases)
            )
            writeInstalledMetadata(installed)
            return installed
        } catch (error: Throwable) {
            activeDirectory.deleteRecursively()
            if (rollbackDirectory.exists()) rollbackDirectory.renameTo(activeDirectory)
            throw error
        }
    }

    private fun readAndValidateDescriptor(
        descriptorFile: File,
        manifest: WakeWordPackManifest
    ): WakeWordPackDescriptor {
        val descriptor = runCatching {
            gson.fromJson(descriptorFile.readText(), WakeWordPackDescriptor::class.java)
        }.getOrElse { error ->
            throw IllegalArgumentException("Wake-word pack descriptor is invalid", error)
        }
        require(descriptor.schemaVersion == 1) {
            "Unsupported wake-word descriptor schema: ${descriptor.schemaVersion}"
        }
        require(descriptor.id == manifest.id) { "Wake-word descriptor id does not match manifest" }
        require(descriptor.version == manifest.version) {
            "Wake-word descriptor version does not match manifest"
        }
        require(descriptor.languageTag.matches(LANGUAGE_TAG)) {
            "Wake-word language tag is invalid"
        }
        normalizePhrases(descriptor.phrases)
        return descriptor
    }

    private fun validatedRequiredFiles(manifest: WakeWordPackManifest): List<String> {
        val files = manifest.requiredFiles.orEmpty().map(String::trim).distinct()
        require(files.isNotEmpty() && files.size <= MAX_REQUIRED_FILES) {
            "Wake-word manifest requiredFiles is invalid"
        }
        require(DESCRIPTOR_FILE in files) { "Wake-word manifest must require pack.json" }
        files.forEach { relativePath ->
            require(relativePath.isNotEmpty() && relativePath.length <= MAX_RELATIVE_PATH_LENGTH) {
                "Wake-word required file path is invalid"
            }
            require(!relativePath.startsWith('/') && '\\' !in relativePath) {
                "Wake-word required file path is unsafe"
            }
            val canonical = File("/safe-root", relativePath).canonicalPath
            require(canonical.startsWith("/safe-root/")) {
                "Wake-word required file path escapes the pack"
            }
        }
        return files
    }

    private fun safePackFile(root: File, relativePath: String): File {
        val canonicalRoot = root.canonicalFile
        val file = File(root, relativePath).canonicalFile
        require(file.path.startsWith(canonicalRoot.path + File.separator)) {
            "Wake-word file escapes pack directory"
        }
        return file
    }

    private fun writeInstalledMetadata(installed: InstalledWakeWordPack) {
        val payload = gson.toJson(
            InstalledWakeWordPackDto(
                id = installed.id,
                name = installed.name,
                version = installed.version,
                sha256 = installed.sha256,
                languageTag = installed.languageTag,
                phrases = installed.phrases
            )
        )
        installedFile.parentFile?.mkdirs()
        val temporary = File(installedFile.parentFile, "${installedFile.name}.tmp")
        temporary.delete()
        try {
            FileOutputStream(temporary).use { output ->
                output.write(payload.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            Files.move(
                temporary.toPath(),
                installedFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            temporary.delete()
        }
    }

    private fun downloadLimited(url: String, expectedMaxBytes: Long): ByteArray {
        val normalized = validatedHttpsUrl(url, "Wake-word download URL")
        require(expectedMaxBytes in 1..maxOf(maxArchiveBytes, MAX_MANIFEST_BYTES)) {
            "Wake-word download size limit is invalid"
        }
        val request = Request.Builder().url(normalized).get().build()
        return httpClient.newCall(request).execute().use { response ->
            require(response.isSuccessful) {
                "Wake-word download failed (HTTP ${response.code})"
            }
            val body = requireNotNull(response.body) { "Wake-word download had empty body" }
            val declaredLength = body.contentLength()
            require(declaredLength < 0L || declaredLength <= expectedMaxBytes) {
                "Wake-word download exceeds the signed size limit"
            }
            val output = ByteArrayOutputStream(minOf(expectedMaxBytes, 64L * 1024L).toInt())
            body.byteStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= expectedMaxBytes) {
                        "Wake-word download exceeds the signed size limit"
                    }
                    output.write(buffer, 0, count)
                }
            }
            output.toByteArray()
        }
    }

    private data class InstalledWakeWordPackDto(
        val id: String,
        val name: String,
        val version: String,
        val sha256: String,
        val languageTag: String,
        val phrases: List<String>
    )

    companion object {
        private const val DESCRIPTOR_FILE = "pack.json"
        private const val SIGNATURE_ALGORITHM = "SHA256withRSA"
        private const val MAX_MANIFEST_BYTES = 64L * 1024L
        private const val DEFAULT_MAX_ARCHIVE_BYTES = 32L * 1024L * 1024L
        private const val ABSOLUTE_MAX_ARCHIVE_BYTES = 128L * 1024L * 1024L
        private const val MAX_ZIP_ENTRIES = 64
        private const val MAX_ZIP_ENTRY_BYTES = 64L * 1024L * 1024L
        private const val MAX_ZIP_EXPANDED_BYTES = 256L * 1024L * 1024L
        private const val MAX_REQUIRED_FILES = 32
        private const val MAX_RELATIVE_PATH_LENGTH = 180
        private const val MAX_SIGNATURE_BYTES = 1_024
        private val PACK_ID = Regex("^[a-z0-9][a-z0-9._-]{0,63}$")
        private val SHA256 = Regex("^[a-f0-9]{64}$")
        private val LANGUAGE_TAG = Regex("^[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*$")

        fun normalizeSha(value: String): String =
            value.removePrefix("sha256:").trim().lowercase()

        fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.joinToString("") { "%02x".format(it) }
        }

        internal fun signingPayload(manifest: WakeWordPackManifest): ByteArray {
            val requiredFiles = manifest.requiredFiles.orEmpty().map(String::trim).sorted()
            return listOf(
                manifest.schemaVersion.toString(),
                manifest.id.trim(),
                manifest.name.trim(),
                manifest.version.trim(),
                validatedHttpsUrl(manifest.url, "Wake-word pack URL"),
                normalizeSha(manifest.sha256),
                manifest.sizeBytes.toString(),
                requiredFiles.joinToString("\u0000")
            ).joinToString("\n").toByteArray(Charsets.UTF_8)
        }

        fun parseRsaPublicKeyPem(pem: String): PublicKey {
            val encoded = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace(Regex("\\s"), "")
            val bytes = Base64.getDecoder().decode(encoded)
            return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(bytes))
        }

        private fun validatedHttpsUrl(raw: String, label: String): String {
            val uri = runCatching { URI(raw.trim()) }
                .getOrElse { throw IllegalArgumentException("$label is invalid", it) }
            require(uri.scheme.equals("https", ignoreCase = true)) { "$label must use HTTPS" }
            require(!uri.host.isNullOrBlank()) { "$label must include a host" }
            require(uri.userInfo.isNullOrBlank()) { "$label must not contain credentials" }
            return uri.toASCIIString()
        }

        private fun normalizePhrases(raw: List<String>): List<String> {
            val phrases = raw
                .map { phrase -> phrase.trim().lowercase() }
                .filter(String::isNotEmpty)
                .distinct()
            require(phrases.isNotEmpty() && phrases.size <= 20) {
                "Wake-word phrases are invalid"
            }
            require(phrases.all { it.length in 2..64 }) {
                "Wake-word phrase length is invalid"
            }
            return phrases
        }
    }
}
