package com.opencode.android.runtime.local

import com.google.gson.Gson
import com.opencode.android.runtime.LocalRuntimeStatus
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class LocalRuntimeManagerUpdateTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var runtime: File
    private lateinit var operations: FakeRuntimeOperations
    private lateinit var engine: FakeUpdateEngine

    @Before
    fun setUp() {
        runtime = temporaryFolder.newFolder("runtime")
        writeVersion("1.18.3")
        operations = FakeRuntimeOperations {
            runtime.resolve("metadata.json").takeIf(File::isFile)?.let { readMetadata() }
        }
        engine = FakeUpdateEngine(
            runtime = runtime,
            metadataReader = ::readMetadata,
            metadataWriter = ::writeVersion
        )
    }

    @Test
    fun `normal start recovers an interrupted update before launching`() = runTest {
        writeVersion("1.19.0")
        engine.forcedRecoveryVersion = "1.18.3"
        val manager = manager()

        val result = manager.start()

        assertTrue(result.isSuccess)
        assertEquals(1, engine.recoverCalls)
        assertEquals("1.18.3", readMetadata().version)
        assertEquals(LocalRuntimeStatus.Ready("1.18.3", 4097), result.getOrNull())
    }

    @Test
    fun `ensure running recovers interrupted update before health short circuit`() = runTest {
        writeVersion("1.19.0")
        engine.forcedRecoveryVersion = "1.18.3"
        val manager = LocalRuntimeManager(
            runtimeDirectory = runtime,
            abi = "arm64-v8a",
            portProbe = { true },
            updateEngine = engine,
            runtimeOperations = operations
        )

        val result = manager.ensureRunning()

        assertTrue(result.isSuccess)
        assertEquals(1, engine.recoverCalls)
        assertEquals("1.18.3", readMetadata().version)
        assertEquals(LocalRuntimeStatus.Ready("1.18.3", 4097), result.getOrNull())
    }

    @Test
    fun `up to date update is a no op and keeps runtime ready`() = runTest {
        engine.updateCheck = LocalRuntimeUpdateCheck.UpToDate("1.18.3", "1.18.3")
        val manager = manager()

        val result = manager.updateToLatest()

        assertTrue(result.isSuccess)
        assertEquals(LocalRuntimeStatus.Ready("1.18.3", 4097), result.getOrNull())
        assertEquals(0, operations.stopCalls)
        assertEquals(1, operations.startCalls)
        assertEquals(0, engine.prepareCalls)
        assertEquals(LocalRuntimeOperationResult.UpdateSkipped("1.18.3"), manager.lastOperation.value)
    }

    @Test
    fun `successful update stops activates starts and commits`() = runTest {
        engine.updateCheck = available("1.19.0")
        val manager = manager()

        val result = manager.updateToLatest()

        assertTrue(result.isSuccess)
        assertEquals(LocalRuntimeStatus.Ready("1.19.0", 4097), result.getOrNull())
        assertEquals(1, operations.stopCalls)
        assertEquals(1, engine.prepareCalls)
        assertEquals(1, engine.activateCalls)
        assertEquals(1, operations.startCalls)
        assertEquals(1, engine.commitCalls)
        assertEquals("1.19.0", readMetadata().version)
        assertEquals(
            LocalRuntimeOperationResult.Updated("1.18.3", "1.19.0"),
            manager.lastOperation.value
        )
    }

    @Test
    fun `failed new version startup automatically restores and restarts previous version`() = runTest {
        engine.updateCheck = available("1.19.0")
        operations.startFailures += IllegalStateException("new server failed")
        val manager = manager()

        val result = manager.updateToLatest()

        assertTrue(result.isFailure)
        assertEquals(1, engine.recoverCalls)
        assertEquals(2, operations.startCalls)
        assertEquals("1.18.3", readMetadata().version)
        assertEquals(LocalRuntimeStatus.Ready("1.18.3", 4097), manager.state.value)
        val operation = manager.lastOperation.value as LocalRuntimeOperationResult.AutomaticRollback
        assertEquals("1.19.0", operation.failedVersion)
        assertEquals("1.18.3", operation.restoredVersion)
        assertTrue(operation.reason.contains("new server failed"))
    }

    @Test
    fun `cancelling update during new startup still restores previous version`() = runTest {
        engine.updateCheck = available("1.19.0")
        operations.firstStartGate = CompletableDeferred()
        val manager = manager()

        val job = launch { manager.updateToLatest() }
        runCurrent()
        assertEquals(1, operations.startCalls)
        assertEquals("1.19.0", readMetadata().version)

        job.cancelAndJoin()

        assertEquals(1, engine.recoverCalls)
        assertEquals(2, operations.startCalls)
        assertEquals("1.18.3", readMetadata().version)
        assertEquals(LocalRuntimeStatus.Ready("1.18.3", 4097), manager.state.value)
    }

    @Test
    fun `failed update and failed previous version restart becomes broken`() = runTest {
        engine.updateCheck = available("1.19.0")
        operations.startFailures += IllegalStateException("new server failed")
        operations.startFailures += IllegalStateException("restored server failed")
        val manager = manager()

        val result = manager.updateToLatest()

        assertTrue(result.isFailure)
        assertTrue(manager.state.value is LocalRuntimeStatus.Broken)
        val operation = manager.lastOperation.value as LocalRuntimeOperationResult.Failed
        assertEquals("update-recovery", operation.operation)
        assertTrue((manager.state.value as LocalRuntimeStatus.Broken).reason.contains("restored server failed"))
    }

    @Test
    fun `manual rollback starts previous version and commits transaction`() = runTest {
        writeVersion("1.19.0")
        engine.rollbackVersionValue = "1.18.3"
        val manager = manager()

        val result = manager.rollback()

        assertTrue(result.isSuccess)
        assertEquals(LocalRuntimeStatus.Ready("1.18.3", 4097), result.getOrNull())
        assertEquals(1, operations.stopCalls)
        assertEquals(1, engine.rollbackCalls)
        assertEquals(1, engine.commitCalls)
        assertEquals(
            LocalRuntimeOperationResult.RolledBack("1.19.0", "1.18.3"),
            manager.lastOperation.value
        )
    }

    @Test
    fun `failed rolled back version startup restores newer version`() = runTest {
        writeVersion("1.19.0")
        engine.rollbackVersionValue = "1.18.3"
        operations.startFailures += IllegalStateException("old server failed")
        val manager = manager()

        val result = manager.rollback()

        assertTrue(result.isFailure)
        assertEquals(1, engine.recoverCalls)
        assertEquals(2, operations.startCalls)
        assertEquals("1.19.0", readMetadata().version)
        assertEquals(LocalRuntimeStatus.Ready("1.19.0", 4097), manager.state.value)
        val operation = manager.lastOperation.value as LocalRuntimeOperationResult.RollbackFailedRestored
        assertEquals("1.18.3", operation.attemptedVersion)
        assertEquals("1.19.0", operation.restoredVersion)
        assertTrue(operation.reason.contains("old server failed"))
    }

    @Test
    fun `check update rejects missing installed runtime`() = runTest {
        runtime.resolve("metadata.json").delete()
        val manager = manager()

        val result = manager.checkForUpdate()

        assertTrue(result.isFailure)
        assertFalse(engine.checkCalls > 0)
    }

    private fun manager() = LocalRuntimeManager(
        runtimeDirectory = runtime,
        abi = "arm64-v8a",
        portProbe = { false },
        updateEngine = engine,
        runtimeOperations = operations
    )

    private fun available(version: String) = LocalRuntimeUpdateCheck.Available(
        currentVersion = readMetadata().version,
        release = LocalRuntimeRelease(
            version = version,
            releaseNotes = "notes",
            asset = LocalRuntimeReleaseAsset(
                name = "opencode-linux-arm64-musl.tar.gz",
                url = "https://github.com/anomalyco/opencode/releases/download/v$version/opencode-linux-arm64-musl.tar.gz",
                sha256 = "a".repeat(64),
                sizeBytes = 100
            )
        )
    )

    private fun writeVersion(version: String) {
        runtime.resolve("environment/rootfs/usr/local/bin/opencode").apply {
            parentFile.mkdirs()
            writeText("binary-$version")
            setExecutable(true, false)
        }
        runtime.resolve("metadata.json").writeText(Gson().toJson(metadata(version)))
    }

    private fun readMetadata(): LocalRuntimeMetadata = Gson().fromJson(
        runtime.resolve("metadata.json").readText(),
        LocalRuntimeMetadata::class.java
    )

    private fun metadata(version: String) = LocalRuntimeMetadata(
        version = version,
        port = 4097,
        installedAt = 123,
        runtimeVersion = "test",
        abi = "arm64-v8a"
    )

    private class FakeRuntimeOperations(
        private val metadataReader: () -> LocalRuntimeMetadata?
    ) : LocalRuntimeOperations {
        var stopCalls = 0
        var startCalls = 0
        var firstStartGate: CompletableDeferred<Unit>? = null
        val startFailures = ArrayDeque<Throwable>()

        override fun currentMetadata(): LocalRuntimeMetadata? = metadataReader()

        override suspend fun stop() {
            stopCalls++
        }

        override suspend fun start(): LocalRuntimeStatus.Ready {
            startCalls++
            firstStartGate.also { firstStartGate = null }?.await()
            startFailures.removeFirstOrNull()?.let { throw it }
            val metadata = requireNotNull(metadataReader())
            return LocalRuntimeStatus.Ready(metadata.version, metadata.port)
        }
    }

    private class FakeUpdateEngine(
        private val runtime: File,
        private val metadataReader: () -> LocalRuntimeMetadata,
        private val metadataWriter: (String) -> Unit
    ) : LocalRuntimeUpdateEngine {
        lateinit var updateCheck: LocalRuntimeUpdateCheck
        var rollbackVersionValue: String? = null
        var forcedRecoveryVersion: String? = null
        var checkCalls = 0
        var prepareCalls = 0
        var activateCalls = 0
        var commitCalls = 0
        var recoverCalls = 0
        var rollbackCalls = 0
        private var preMutationVersion: String? = null

        override suspend fun check(currentVersion: String, abi: String): LocalRuntimeUpdateCheck {
            checkCalls++
            return updateCheck
        }

        override suspend fun prepare(
            release: LocalRuntimeRelease,
            onProgress: (Float?, String) -> Unit
        ): PreparedRuntimeUpdate {
            prepareCalls++
            onProgress(0.5f, "download")
            onProgress(1f, "ready")
            val current = metadataReader()
            return PreparedRuntimeUpdate(
                release = release,
                candidateBinary = runtime.resolve("candidate-${release.version}"),
                candidateMetadata = current.copy(version = release.version),
                baseVersion = current.version
            )
        }

        override suspend fun activate(prepared: PreparedRuntimeUpdate): LocalRuntimeMetadata {
            activateCalls++
            val previous = metadataReader()
            preMutationVersion = previous.version
            metadataWriter(prepared.release.version)
            rollbackVersionValue = previous.version
            return previous
        }

        override suspend fun commit() {
            commitCalls++
            preMutationVersion = null
        }

        override suspend fun recover(): LocalRuntimeMetadata? {
            recoverCalls++
            val version = preMutationVersion ?: forcedRecoveryVersion ?: return null
            metadataWriter(version)
            preMutationVersion = null
            forcedRecoveryVersion = null
            return metadataReader()
        }

        override suspend fun rollback(): LocalRuntimeMetadata {
            rollbackCalls++
            val previous = metadataReader()
            preMutationVersion = previous.version
            val target = requireNotNull(rollbackVersionValue)
            metadataWriter(target)
            rollbackVersionValue = previous.version
            return metadataReader()
        }

        override suspend fun rollbackVersion(): String? = rollbackVersionValue
    }
}
