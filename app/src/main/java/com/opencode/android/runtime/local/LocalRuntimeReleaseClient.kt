package com.opencode.android.runtime.local

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

data class LocalRuntimeReleaseAsset(
    val name: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long
) {
    val requiredFreeBytes: Long
        get() = sizeBytes * SPACE_MULTIPLIER + UPDATE_SAFETY_BYTES

    companion object {
        private const val SPACE_MULTIPLIER = 4L
        private const val UPDATE_SAFETY_BYTES = 64L * 1024L * 1024L
    }
}

data class LocalRuntimeRelease(
    val version: String,
    val releaseNotes: String,
    val asset: LocalRuntimeReleaseAsset
)

sealed interface LocalRuntimeUpdateCheck {
    val currentVersion: String

    data class UpToDate(
        override val currentVersion: String,
        val latestVersion: String
    ) : LocalRuntimeUpdateCheck

    data class Available(
        override val currentVersion: String,
        val release: LocalRuntimeRelease
    ) : LocalRuntimeUpdateCheck
}

class LocalRuntimeReleaseClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val endpoint: HttpUrl = OFFICIAL_RELEASE_ENDPOINT.toHttpUrl(),
    private val gson: Gson = Gson(),
    private val maxReleaseNotesCharacters: Int = 12_000
) {
    init {
        require(endpoint.isHttps || endpoint.host in LOOPBACK_HOSTS) {
            "OpenCode release API must use HTTPS"
        }
        require(maxReleaseNotesCharacters >= 0) { "Release note limit must not be negative" }
    }

    suspend fun check(currentVersion: String, abi: String): LocalRuntimeUpdateCheck =
        withContext(Dispatchers.IO) {
            val assetName = requireNotNull(ASSET_NAME_BY_ABI[abi]) {
                "Unsupported Android ABI for OpenCode updates: $abi"
            }
            val normalizedCurrent = normalizeOpenCodeVersion(currentVersion)
            val request = Request.Builder()
                .url(endpoint)
                .header("Accept", GITHUB_ACCEPT)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            val releaseDto = httpClient.newCall(request).execute().use { response ->
                require(response.isSuccessful) {
                    "OpenCode release check failed with HTTP ${response.code}"
                }
                val body = requireNotNull(response.body) {
                    "OpenCode release response had no body"
                }
                gson.fromJson(body.charStream(), GitHubReleaseDto::class.java)
            }
            val latestVersion = normalizeOpenCodeVersion(releaseDto.tagName)
            val assetDto = requireNotNull(releaseDto.assets.firstOrNull { it.name == assetName }) {
                "OpenCode release $latestVersion does not contain $assetName"
            }
            val digest = assetDto.digest
            require(digest != null && SHA256_DIGEST.matches(digest)) {
                "OpenCode release asset is missing a valid SHA-256 digest"
            }
            val assetUrl = assetDto.downloadUrl.toHttpUrl()
            require(assetUrl.isHttps) { "OpenCode release asset URL must use HTTPS" }
            require(assetDto.size > 0L) { "OpenCode release asset size must be positive" }

            val release = LocalRuntimeRelease(
                version = latestVersion,
                releaseNotes = releaseDto.body.orEmpty().take(maxReleaseNotesCharacters),
                asset = LocalRuntimeReleaseAsset(
                    name = assetDto.name,
                    url = assetUrl.toString(),
                    sha256 = digest.removePrefix(SHA256_PREFIX),
                    sizeBytes = assetDto.size
                )
            )
            if (compareOpenCodeVersions(latestVersion, normalizedCurrent) > 0) {
                LocalRuntimeUpdateCheck.Available(normalizedCurrent, release)
            } else {
                LocalRuntimeUpdateCheck.UpToDate(normalizedCurrent, latestVersion)
            }
        }

    private data class GitHubReleaseDto(
        @SerializedName("tag_name") val tagName: String,
        @SerializedName("body") val body: String?,
        @SerializedName("assets") val assets: List<GitHubReleaseAssetDto> = emptyList()
    )

    private data class GitHubReleaseAssetDto(
        @SerializedName("name") val name: String,
        @SerializedName("size") val size: Long,
        @SerializedName("browser_download_url") val downloadUrl: String,
        @SerializedName("digest") val digest: String?
    )

    companion object {
        const val OFFICIAL_RELEASE_ENDPOINT =
            "https://api.github.com/repos/anomalyco/opencode/releases/latest"
        private const val GITHUB_ACCEPT = "application/vnd.github+json"
        private const val USER_AGENT = "OpenCode-Android"
        private const val SHA256_PREFIX = "sha256:"
        private val SHA256_DIGEST = Regex("^sha256:[a-f0-9]{64}$")
        private val LOOPBACK_HOSTS = setOf("127.0.0.1", "localhost", "::1")
        private val ASSET_NAME_BY_ABI = mapOf(
            "arm64-v8a" to "opencode-linux-arm64-musl.tar.gz",
            "x86_64" to "opencode-linux-x64-musl.tar.gz"
        )
    }
}

internal fun compareOpenCodeVersions(left: String, right: String): Int {
    val leftParts = parseOpenCodeVersion(left)
    val rightParts = parseOpenCodeVersion(right)
    repeat(maxOf(leftParts.size, rightParts.size)) { index ->
        val comparison = (leftParts.getOrElse(index) { 0 })
            .compareTo(rightParts.getOrElse(index) { 0 })
        if (comparison != 0) return comparison
    }
    return 0
}

internal fun normalizeOpenCodeVersion(version: String): String =
    parseOpenCodeVersion(version).joinToString(".")

private fun parseOpenCodeVersion(version: String): List<Int> {
    val normalized = version.trim()
        .removePrefix("v")
        .substringBefore('-')
        .substringBefore('+')
    require(VERSION.matches(normalized)) { "Invalid OpenCode version: $version" }
    return normalized.split('.').map { component -> component.toInt() }
}

private val VERSION = Regex("^[0-9]+(?:\\.[0-9]+){1,3}$")
