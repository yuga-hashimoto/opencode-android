package com.opencode.android.runtime.local

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.opencode.android.runtime.LocalRuntimeStatus
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class LocalRuntimeMetadata(
    @SerializedName("version") val version: String,
    @SerializedName("port") val port: Int,
    @SerializedName("installedAt") val installedAt: Long,
    @SerializedName("runtimeVersion") val runtimeVersion: String = "legacy",
    @SerializedName("abi") val abi: String = "unknown"
)

class LocalRuntimeManager(
    private val runtimeDirectory: File,
    private val abi: String,
    private val portProbe: (Int) -> Boolean = ::defaultPortProbe,
    private val installer: LocalRuntimeInstaller? = null,
    private val processLauncher: LocalRuntimeProcessLauncher? = null
) {
    private val operationMutex = Mutex()
    private val mutableState = MutableStateFlow(computeStatus())
    val state: StateFlow<LocalRuntimeStatus> = mutableState.asStateFlow()

    fun status(): LocalRuntimeStatus {
        val operation = mutableState.value
        if (operation is LocalRuntimeStatus.Installing || operation is LocalRuntimeStatus.Starting) return operation
        return computeStatus().also { mutableState.value = it }
    }

    fun installedPort(): Int? = readMetadata()?.port

    fun isHealthy(): Boolean = installedPort()?.let(portProbe) == true

    suspend fun installAndStart(): Result<LocalRuntimeStatus.Ready> = operationMutex.withLock {
        val configuredInstaller = installer
            ?: return@withLock Result.failure(IllegalStateException("Local runtime installer is not configured"))
        runCatching {
            val installed = configuredInstaller.install { progress, step ->
                mutableState.value = LocalRuntimeStatus.Installing(progress, step)
            }
            mutableState.value = LocalRuntimeStatus.Stopped(installed.metadata.version, installed.metadata.port)
            startInstalled(installed)
        }.onFailure { error ->
            mutableState.value = LocalRuntimeStatus.Broken(error.message ?: "ローカルランタイムの導入に失敗しました")
        }
    }

    suspend fun start(): Result<LocalRuntimeStatus.Ready> = operationMutex.withLock {
        startLocked()
    }

    suspend fun ensureRunning(): Result<LocalRuntimeStatus.Ready> = operationMutex.withLock {
        val metadata = readMetadata()
            ?: return@withLock Result.failure(IllegalStateException("Local runtime is not installed"))
        if (portProbe(metadata.port)) {
            return@withLock Result.success(
                LocalRuntimeStatus.Ready(metadata.version, metadata.port).also { mutableState.value = it }
            )
        }
        withContext(Dispatchers.IO) { processLauncher?.stop() }
        startLocked()
    }

    suspend fun stop(): Result<LocalRuntimeStatus.Stopped> = operationMutex.withLock {
        runCatching {
            withContext(Dispatchers.IO) { processLauncher?.stop() }
            val metadata = readMetadata() ?: error("Local runtime metadata is missing")
            LocalRuntimeStatus.Stopped(metadata.version, metadata.port).also { mutableState.value = it }
        }.onFailure { error ->
            mutableState.value = LocalRuntimeStatus.Broken(error.message ?: "ローカルOpenCodeを停止できません")
        }
    }

    suspend fun deleteRuntime(): Result<LocalRuntimeStatus.NotInstalled> = operationMutex.withLock {
        runCatching {
            withContext(Dispatchers.IO) {
                processLauncher?.stop()
                if (runtimeDirectory.exists()) {
                    require(runtimeDirectory.deleteRecursively()) {
                        "ローカルランタイムを完全に削除できませんでした"
                    }
                }
            }
            LocalRuntimeStatus.NotInstalled.also { mutableState.value = it }
        }.onFailure { error ->
            mutableState.value = LocalRuntimeStatus.Broken(
                error.message ?: "ローカルランタイムを削除できません"
            )
        }
    }

    suspend fun reinstall(): Result<LocalRuntimeStatus.Ready> = operationMutex.withLock {
        withContext(Dispatchers.IO) { processLauncher?.stop() }
        File(runtimeDirectory, METADATA_FILE).delete()
        val configuredInstaller = installer
            ?: return@withLock Result.failure(IllegalStateException("Local runtime installer is not configured"))
        runCatching {
            val installed = configuredInstaller.install { progress, step ->
                mutableState.value = LocalRuntimeStatus.Installing(progress, step)
            }
            startInstalled(installed)
        }.onFailure { error ->
            mutableState.value = LocalRuntimeStatus.Broken(error.message ?: "ローカルランタイムの再導入に失敗しました")
        }
    }

    private suspend fun startLocked(): Result<LocalRuntimeStatus.Ready> = runCatching {
        val configuredInstaller = installer
            ?: error("Local runtime installer is not configured")
        withContext(Dispatchers.IO) {
            configuredInstaller.recoverInterruptedActivation()
        }
        val installed = configuredInstaller.installedRuntime()
            ?: error("Local runtime is not installed")
        startInstalled(installed)
    }.onFailure { error ->
        mutableState.value = LocalRuntimeStatus.Broken(
            error.message ?: "ローカルOpenCodeを起動できません"
        )
    }

    private suspend fun startInstalled(
        installed: LocalRuntimeInstaller.InstalledRuntime
    ): LocalRuntimeStatus.Ready = withContext(Dispatchers.IO) {
        val launcher = processLauncher
            ?: error("Local runtime process launcher is not configured")
        mutableState.value = LocalRuntimeStatus.Starting(installed.metadata.version, installed.metadata.port)
        if (!portProbe(installed.metadata.port)) launcher.start(installed)
        val ready = LocalRuntimeStatus.Ready(installed.metadata.version, installed.metadata.port)
        mutableState.value = ready
        ready
    }

    private fun computeStatus(): LocalRuntimeStatus {
        if (abi !in SUPPORTED_ABIS) return LocalRuntimeStatus.UnsupportedAbi(abi)
        val metadataFile = File(runtimeDirectory, METADATA_FILE)
        if (!metadataFile.isFile) return LocalRuntimeStatus.NotInstalled
        val metadata = runCatching {
            Gson().fromJson(metadataFile.readText(), LocalRuntimeMetadata::class.java)
        }.getOrElse { error ->
            return LocalRuntimeStatus.Broken("Runtime metadata is invalid: ${error.message}")
        }
        val rootfs = File(runtimeDirectory, "environment/rootfs")
        val openCode = File(rootfs, "usr/local/bin/opencode")
        if (!rootfs.isDirectory || !openCode.isFile) {
            return LocalRuntimeStatus.Broken("ローカルランタイムのファイルが不足しています")
        }
        if (metadata.version.isBlank() || metadata.port !in 1..65535) {
            return LocalRuntimeStatus.Broken("Runtime metadata contains invalid values")
        }
        return if (portProbe(metadata.port)) {
            LocalRuntimeStatus.Ready(metadata.version, metadata.port)
        } else {
            LocalRuntimeStatus.Stopped(metadata.version, metadata.port)
        }
    }

    private fun readMetadata(): LocalRuntimeMetadata? {
        val metadataFile = File(runtimeDirectory, METADATA_FILE)
        if (!metadataFile.isFile) return null
        return runCatching {
            Gson().fromJson(metadataFile.readText(), LocalRuntimeMetadata::class.java)
        }.getOrNull()
    }

    companion object {
        private const val METADATA_FILE = "metadata.json"
        private val SUPPORTED_ABIS = setOf("arm64-v8a", "x86_64")

        fun defaultPortProbe(port: Int): Boolean = runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), 300)
            }
            true
        }.getOrDefault(false)
    }
}
