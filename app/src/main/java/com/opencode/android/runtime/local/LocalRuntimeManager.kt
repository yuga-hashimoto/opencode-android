package com.opencode.android.runtime.local

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.opencode.android.runtime.LocalRuntimeStatus
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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
    private val processLauncher: LocalRuntimeProcessLauncher? = null,
    private val updateEngine: LocalRuntimeUpdateEngine? = null,
    private val runtimeOperations: LocalRuntimeOperations? = null
) {
    private val operationMutex = Mutex()
    private val mutableState = MutableStateFlow(computeStatus())
    val state: StateFlow<LocalRuntimeStatus> = mutableState.asStateFlow()

    private val mutableLastOperation = MutableStateFlow<LocalRuntimeOperationResult?>(null)
    val lastOperation: StateFlow<LocalRuntimeOperationResult?> = mutableLastOperation.asStateFlow()

    fun status(): LocalRuntimeStatus {
        val operation = mutableState.value
        if (
            operation is LocalRuntimeStatus.Installing ||
            operation is LocalRuntimeStatus.Starting ||
            operation is LocalRuntimeStatus.Updating
        ) {
            return operation
        }
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
            mutableState.value = LocalRuntimeStatus.Broken(
                error.message ?: "ローカルランタイムの導入に失敗しました"
            )
        }
    }

    suspend fun start(): Result<LocalRuntimeStatus.Ready> = operationMutex.withLock {
        startLocked()
    }

    suspend fun ensureRunning(): Result<LocalRuntimeStatus.Ready> = operationMutex.withLock {
        updateEngine?.recover()
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
            mutableState.value = LocalRuntimeStatus.Broken(
                error.message ?: "ローカルOpenCodeを停止できません"
            )
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
            mutableLastOperation.value = null
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
            mutableState.value = LocalRuntimeStatus.Broken(
                error.message ?: "ローカルランタイムの再導入に失敗しました"
            )
        }
    }

    suspend fun checkForUpdate(): Result<LocalRuntimeUpdateCheck> = operationMutex.withLock {
        val engine = updateEngine
            ?: return@withLock Result.failure(IllegalStateException("Local runtime updater is not configured"))
        val metadata = currentMetadataForOperation()
            ?: return@withLock Result.failure(IllegalStateException("Local runtime is not installed"))
        runCatching { engine.check(metadata.version, abi) }
            .onFailure { error ->
                if (error is CancellationException) throw error
                mutableLastOperation.value = LocalRuntimeOperationResult.Failed(
                    operation = "update-check",
                    message = error.message ?: "OpenCodeの更新確認に失敗しました"
                )
            }
    }

    suspend fun rollbackVersion(): String? = operationMutex.withLock {
        updateEngine?.rollbackVersion()
    }

    suspend fun updateToLatest(): Result<LocalRuntimeStatus.Ready> = operationMutex.withLock {
        updateToLatestLocked()
    }

    suspend fun rollback(): Result<LocalRuntimeStatus.Ready> = operationMutex.withLock {
        rollbackLocked()
    }

    private suspend fun updateToLatestLocked(): Result<LocalRuntimeStatus.Ready> {
        val engine = updateEngine
            ?: return Result.failure(IllegalStateException("Local runtime updater is not configured"))
        val current = currentMetadataForOperation()
            ?: return Result.failure(IllegalStateException("Local runtime is not installed"))
        val check = runCatching { engine.check(current.version, abi) }
            .getOrElse { error ->
                if (error is CancellationException) throw error
                mutableLastOperation.value = LocalRuntimeOperationResult.Failed(
                    "update-check",
                    error.message ?: "OpenCodeの更新確認に失敗しました"
                )
                return Result.failure(error)
            }

        if (check is LocalRuntimeUpdateCheck.UpToDate) {
            val ready = if (portProbe(current.port)) {
                LocalRuntimeStatus.Ready(current.version, current.port)
                    .also { mutableState.value = it }
            } else {
                startForOperation()
            }
            mutableLastOperation.value = LocalRuntimeOperationResult.UpdateSkipped(current.version)
            return Result.success(ready)
        }

        val available = check as LocalRuntimeUpdateCheck.Available
        val targetVersion = available.release.version
        mutableState.value = LocalRuntimeStatus.Updating(
            currentVersion = current.version,
            targetVersion = targetVersion,
            progress = null,
            step = "更新を準備しています"
        )
        val prepared = runCatching {
            engine.prepare(available.release) { progress, step ->
                mutableState.value = LocalRuntimeStatus.Updating(
                    currentVersion = current.version,
                    targetVersion = targetVersion,
                    progress = progress,
                    step = step
                )
            }
        }.getOrElse { error ->
            restoreStateBeforeMutation(current)
            if (error is CancellationException) throw error
            mutableLastOperation.value = LocalRuntimeOperationResult.Failed(
                "update-prepare",
                error.message ?: "OpenCodeの更新準備に失敗しました"
            )
            return Result.failure(error)
        }

        return try {
            stopForOperation()
            engine.activate(prepared)
            val ready = startForOperation()
            engine.commit()
            mutableState.value = ready
            mutableLastOperation.value = LocalRuntimeOperationResult.Updated(
                fromVersion = current.version,
                toVersion = ready.version
            )
            Result.success(ready)
        } catch (error: Throwable) {
            val recovery = withContext(NonCancellable) {
                restoreAfterFailedMutation(
                    engine = engine,
                    fallbackMetadata = current,
                    attemptedVersion = targetVersion,
                    originalError = error,
                    rollbackOperation = false
                )
            }
            if (error is CancellationException) throw error
            recovery
        }
    }

    private suspend fun rollbackLocked(): Result<LocalRuntimeStatus.Ready> {
        val engine = updateEngine
            ?: return Result.failure(IllegalStateException("Local runtime updater is not configured"))
        val current = currentMetadataForOperation()
            ?: return Result.failure(IllegalStateException("Local runtime is not installed"))
        val targetVersion = runCatching { engine.rollbackVersion() }
            .getOrElse { error ->
                if (error is CancellationException) throw error
                mutableLastOperation.value = LocalRuntimeOperationResult.Failed(
                    "rollback-check",
                    error.message ?: "ロールバック可能なバージョンを確認できません"
                )
                return Result.failure(error)
            }
            ?: return Result.failure(IllegalStateException("Rollback version is unavailable"))

        mutableState.value = LocalRuntimeStatus.Updating(
            currentVersion = current.version,
            targetVersion = targetVersion,
            progress = null,
            step = "OpenCode ${targetVersion}へロールバックしています"
        )
        return try {
            stopForOperation()
            engine.rollback()
            val ready = startForOperation()
            engine.commit()
            mutableState.value = ready
            mutableLastOperation.value = LocalRuntimeOperationResult.RolledBack(
                fromVersion = current.version,
                toVersion = ready.version
            )
            Result.success(ready)
        } catch (error: Throwable) {
            val recovery = withContext(NonCancellable) {
                restoreAfterFailedMutation(
                    engine = engine,
                    fallbackMetadata = current,
                    attemptedVersion = targetVersion,
                    originalError = error,
                    rollbackOperation = true
                )
            }
            if (error is CancellationException) throw error
            recovery
        }
    }

    private suspend fun restoreAfterFailedMutation(
        engine: LocalRuntimeUpdateEngine,
        fallbackMetadata: LocalRuntimeMetadata,
        attemptedVersion: String,
        originalError: Throwable,
        rollbackOperation: Boolean
    ): Result<LocalRuntimeStatus.Ready> {
        runCatching { stopForOperation() }
            .exceptionOrNull()
            ?.let(originalError::addSuppressed)
        val recoveryError = runCatching { engine.recover() }.exceptionOrNull()
        recoveryError?.let(originalError::addSuppressed)
        val restoredMetadata = currentMetadataForOperation() ?: fallbackMetadata
        val restart = runCatching { startForOperation() }
        return restart.fold(
            onSuccess = { ready ->
                mutableState.value = ready
                mutableLastOperation.value = if (rollbackOperation) {
                    LocalRuntimeOperationResult.RollbackFailedRestored(
                        attemptedVersion = attemptedVersion,
                        restoredVersion = ready.version,
                        reason = originalError.message ?: "ロールバック後の起動に失敗しました"
                    )
                } else {
                    LocalRuntimeOperationResult.AutomaticRollback(
                        failedVersion = attemptedVersion,
                        restoredVersion = ready.version,
                        reason = originalError.message ?: "更新後の起動に失敗しました"
                    )
                }
                Result.failure(originalError)
            },
            onFailure = { restartError ->
                originalError.addSuppressed(restartError)
                mutableState.value = LocalRuntimeStatus.Broken(
                    "OpenCode ${restoredMetadata.version}を復元しましたが起動できません: ${restartError.message.orEmpty()}"
                )
                mutableLastOperation.value = LocalRuntimeOperationResult.Failed(
                    operation = if (rollbackOperation) "rollback-recovery" else "update-recovery",
                    message = originalError.message ?: "OpenCodeランタイムを復元できません"
                )
                Result.failure(originalError)
            }
        )
    }

    private fun restoreStateBeforeMutation(metadata: LocalRuntimeMetadata) {
        mutableState.value = if (portProbe(metadata.port)) {
            LocalRuntimeStatus.Ready(metadata.version, metadata.port)
        } else {
            LocalRuntimeStatus.Stopped(metadata.version, metadata.port)
        }
    }

    private fun currentMetadataForOperation(): LocalRuntimeMetadata? =
        runtimeOperations?.currentMetadata() ?: readMetadata()

    private suspend fun stopForOperation() {
        val operations = runtimeOperations
        if (operations != null) {
            operations.stop()
        } else {
            withContext(Dispatchers.IO) { processLauncher?.stop() }
        }
    }

    private suspend fun startForOperation(): LocalRuntimeStatus.Ready {
        runtimeOperations?.let { return it.start() }
        val configuredInstaller = installer
            ?: error("Local runtime installer is not configured")
        val installed = configuredInstaller.installedRuntime()
            ?: error("Local runtime is not installed")
        return startInstalled(installed)
    }

    private suspend fun startLocked(): Result<LocalRuntimeStatus.Ready> = runCatching {
        updateEngine?.recover()
        runtimeOperations?.let { operations ->
            val ready = operations.start()
            mutableState.value = ready
            return@runCatching ready
        }
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
        mutableState.value = LocalRuntimeStatus.Starting(
            installed.metadata.version,
            installed.metadata.port
        )
        if (!portProbe(installed.metadata.port)) launcher.start(installed)
        val ready = LocalRuntimeStatus.Ready(
            installed.metadata.version,
            installed.metadata.port
        )
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
