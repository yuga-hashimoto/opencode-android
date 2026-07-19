package com.opencode.android.runtime.local

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalRuntimeUpdaterInstrumentedTest {
    private lateinit var runtimeDirectory: File

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runtimeDirectory = File(context.filesDir, "runtime-updater-instrumentation")
        runtimeDirectory.deleteRecursively()
        writeActiveRuntime(version = "1.18.3", binary = "old-binary")
    }

    @After
    fun tearDown() {
        runtimeDirectory.deleteRecursively()
    }

    @Test
    fun activationAndRollbackRotateFilesOnAndroidAppStorage() = runBlocking {
        val abi = supportedUpdateAbi()
        val updater = LocalRuntimeUpdater(
            runtimeDirectory = runtimeDirectory,
            abi = abi,
            freeBytesProvider = { Long.MAX_VALUE },
            downloadAsset = { _, destination, progress ->
                destination.parentFile?.mkdirs()
                destination.writeText("archive")
                progress(1f)
            },
            extractArchive = { _, destination ->
                destination.resolve("nested/opencode").apply {
                    parentFile?.mkdirs()
                    writeText("new-binary")
                }
            },
            candidateVersionProvider = { binary ->
                when (binary.readText()) {
                    "old-binary" -> "1.18.3"
                    "new-binary" -> "1.19.0"
                    else -> error("Unexpected test binary: ${binary.name}")
                }
            },
            accessCoordinator = LocalRuntimeAccessCoordinator(),
            nowMillis = { 999L }
        )

        val prepared = updater.prepare(release(abi))
        val previous = updater.activate(prepared)

        assertEquals("1.18.3", previous.version)
        assertEquals("new-binary", activeBinary().readText())
        assertEquals("old-binary", rollbackBinary().readText())
        assertEquals("1.19.0", activeMetadata().version)
        assertEquals("1.18.3", rollbackMetadata().version)
        assertTrue(activeBinary().canExecute())
        assertTrue(runtimeDirectory.resolve("update-transaction.json").isFile)

        updater.commitActivation()
        assertFalse(runtimeDirectory.resolve("update-transaction.json").exists())

        val restored = updater.rollback()

        assertEquals("1.18.3", restored.version)
        assertEquals("old-binary", activeBinary().readText())
        assertEquals("new-binary", rollbackBinary().readText())
        assertEquals("1.18.3", activeMetadata().version)
        assertEquals("1.19.0", rollbackMetadata().version)
        assertTrue(runtimeDirectory.resolve("rollback-transaction.json").isFile)

        updater.commitActivation()
        assertFalse(runtimeDirectory.resolve("rollback-transaction.json").exists())
    }

    private fun release(abi: String) = LocalRuntimeRelease(
        version = "1.19.0",
        releaseNotes = "Instrumentation update",
        asset = LocalRuntimeReleaseAsset(
            name = when (abi) {
                "arm64-v8a" -> "opencode-linux-arm64-musl.tar.gz"
                "x86_64" -> "opencode-linux-x64-musl.tar.gz"
                else -> error("Unsupported test ABI: $abi")
            },
            url = "https://github.com/anomalyco/opencode/releases/download/v1.19.0/test.tar.gz",
            sha256 = "a".repeat(64),
            sizeBytes = 100L
        )
    )

    private fun supportedUpdateAbi(): String = Build.SUPPORTED_ABIS
        .firstOrNull { it == "arm64-v8a" || it == "x86_64" }
        ?: error("Instrumentation device has no supported update ABI: ${Build.SUPPORTED_ABIS.joinToString()}")

    private fun writeActiveRuntime(version: String, binary: String) {
        activeBinary().apply {
            parentFile?.mkdirs()
            writeText(binary)
            assertTrue(setExecutable(true, false) || canExecute())
        }
        metadataFile().writeText(Gson().toJson(metadata(version)))
    }

    private fun metadata(version: String) = LocalRuntimeMetadata(
        version = version,
        port = 4097,
        installedAt = 123L,
        runtimeVersion = "2026.07.19.1",
        abi = supportedUpdateAbi()
    )

    private fun activeMetadata(): LocalRuntimeMetadata =
        Gson().fromJson(metadataFile().readText(), LocalRuntimeMetadata::class.java)

    private fun rollbackMetadata(): LocalRuntimeMetadata =
        Gson().fromJson(rollbackMetadataFile().readText(), LocalRuntimeMetadata::class.java)

    private fun activeBinary() =
        runtimeDirectory.resolve("environment/rootfs/usr/local/bin/opencode")

    private fun rollbackBinary() =
        runtimeDirectory.resolve("environment/rootfs/usr/local/bin/opencode.rollback")

    private fun metadataFile() = runtimeDirectory.resolve("metadata.json")

    private fun rollbackMetadataFile() = runtimeDirectory.resolve("metadata.rollback.json")
}
