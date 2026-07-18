package com.opencode.android.runtime

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.opencode.android.backend.LocalRuntimeStatus
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

data class LocalRuntimeMetadata(
    @SerializedName("version") val version: String,
    @SerializedName("port") val port: Int,
    @SerializedName("installedAt") val installedAt: Long
)

class LocalRuntimeManager(
    private val runtimeDirectory: File,
    private val abi: String,
    private val portProbe: (Int) -> Boolean = ::defaultPortProbe
) {
    fun status(): LocalRuntimeStatus {
        if (abi !in SUPPORTED_ABIS) return LocalRuntimeStatus.UnsupportedAbi(abi)
        val metadataFile = File(runtimeDirectory, METADATA_FILE)
        if (!metadataFile.exists()) return LocalRuntimeStatus.NotInstalled

        val metadata = runCatching {
            Gson().fromJson(metadataFile.readText(), LocalRuntimeMetadata::class.java)
        }.getOrElse { error ->
            return LocalRuntimeStatus.Broken("Runtime metadata is invalid: ${error.message}")
        }

        if (metadata.version.isBlank() || metadata.port !in 1..65535) {
            return LocalRuntimeStatus.Broken("Runtime metadata contains invalid values")
        }
        if (!portProbe(metadata.port)) {
            return LocalRuntimeStatus.Broken("OpenCode local server is not listening on port ${metadata.port}")
        }
        return LocalRuntimeStatus.Ready(metadata.version, metadata.port)
    }

    companion object {
        private const val METADATA_FILE = "metadata.json"
        private val SUPPORTED_ABIS = setOf("arm64-v8a", "x86_64")

        private fun defaultPortProbe(port: Int): Boolean = runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), 300)
            }
            true
        }.getOrDefault(false)
    }
}
