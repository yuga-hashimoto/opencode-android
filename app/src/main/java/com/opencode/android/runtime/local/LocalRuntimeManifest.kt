package com.opencode.android.runtime.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class LocalRuntimeManifest(
    @SerializedName("schemaVersion") val schemaVersion: Int,
    @SerializedName("runtimeVersion") val runtimeVersion: String,
    @SerializedName("openCodeVersion") val openCodeVersion: String,
    @SerializedName("alpineVersion") val alpineVersion: String,
    @SerializedName("port") val port: Int,
    @SerializedName("architectures") val architectures: Map<String, LocalRuntimeArchitecture>
) {
    fun architecture(abi: String): LocalRuntimeArchitecture =
        requireNotNull(architectures[abi]) { "Local runtime does not support ABI $abi" }

    fun validate() {
        require(schemaVersion == 1) { "Unsupported local runtime manifest schema: $schemaVersion" }
        require(runtimeVersion.isNotBlank()) { "Runtime version is missing" }
        require(openCodeVersion.isNotBlank()) { "OpenCode version is missing" }
        require(port in 1024..65535) { "Invalid local OpenCode port: $port" }
        require(architectures.isNotEmpty()) { "Runtime manifest has no architectures" }
        architectures.forEach { (abi, item) -> item.validate(abi) }
    }
}

data class LocalRuntimeArchitecture(
    @SerializedName("alpineUrl") val alpineUrl: String,
    @SerializedName("alpineSha256") val alpineSha256: String,
    @SerializedName("openCodeUrl") val openCodeUrl: String,
    @SerializedName("openCodeSha256") val openCodeSha256: String
) {
    fun validate(abi: String) {
        require(alpineUrl.startsWith("https://")) { "Alpine URL for $abi must use HTTPS" }
        require(openCodeUrl.startsWith("https://")) { "OpenCode URL for $abi must use HTTPS" }
        require(SHA256.matches(alpineSha256)) { "Invalid Alpine SHA-256 for $abi" }
        require(SHA256.matches(openCodeSha256)) { "Invalid OpenCode SHA-256 for $abi" }
    }

    companion object {
        private val SHA256 = Regex("^[a-f0-9]{64}$")
    }
}

class LocalRuntimeManifestReader(
    private val context: Context,
    private val gson: Gson = Gson()
) {
    fun read(): LocalRuntimeManifest {
        val payload = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        return gson.fromJson(payload, LocalRuntimeManifest::class.java).also { it.validate() }
    }

    companion object {
        private const val ASSET_NAME = "local-runtime-manifest.json"
    }
}
