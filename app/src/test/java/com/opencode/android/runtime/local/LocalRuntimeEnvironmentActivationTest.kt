package com.opencode.android.runtime.local

import com.google.gson.Gson
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalRuntimeEnvironmentActivationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `failed staging activation restores previous environment`() {
        val root = temporaryFolder.newFolder("runtime")
        val active = root.resolve("environment").apply {
            mkdirs()
            resolve("marker.txt").writeText("old")
        }
        val staging = root.resolve("environment.staging").apply {
            mkdirs()
            resolve("marker.txt").writeText("new")
        }
        val rollback = root.resolve("environment.rollback")
        var failed = false

        val error = runCatching {
            activateRuntimeEnvironment(
                active = active,
                staging = staging,
                rollback = rollback,
                moveDirectory = { source, destination ->
                    if (!failed && source.name == "environment.staging") {
                        failed = true
                        error("simulated activation failure")
                    }
                    require(source.renameTo(destination)) {
                        "move failed: $source -> $destination"
                    }
                },
                finalizeActivation = { error("must not finalize") }
            )
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("simulated"))
        assertEquals("old", active.resolve("marker.txt").readText())
        assertFalse(rollback.exists())
        assertTrue(staging.isDirectory)
        assertEquals("new", staging.resolve("marker.txt").readText())
    }

    @Test
    fun `finalization failure restores previous environment and removes failed active`() {
        val root = temporaryFolder.newFolder("runtime-finalizer")
        val active = root.resolve("environment").apply {
            mkdirs()
            resolve("marker.txt").writeText("old")
        }
        val staging = root.resolve("environment.staging").apply {
            mkdirs()
            resolve("marker.txt").writeText("new")
        }
        val rollback = root.resolve("environment.rollback")

        val error = runCatching {
            activateRuntimeEnvironment(
                active = active,
                staging = staging,
                rollback = rollback,
                finalizeActivation = { error("metadata finalization failed") }
            )
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("metadata"))
        assertEquals("old", active.resolve("marker.txt").readText())
        assertFalse(rollback.exists())
        assertFalse(staging.exists())
    }

    @Test
    fun `interrupted activation restores rollback environment and top metadata`() {
        val root = temporaryFolder.newFolder("runtime-interrupted")
        val active = root.resolve("environment").apply {
            mkdirs()
            resolve("marker.txt").writeText("new")
            resolve("metadata.json").writeText(Gson().toJson(metadata("1.19.0")))
        }
        val rollback = root.resolve("environment.rollback").apply {
            mkdirs()
            resolve("marker.txt").writeText("old")
            resolve("metadata.json").writeText(Gson().toJson(metadata("1.18.3")))
        }
        val topMetadata = root.resolve("metadata.json").apply {
            writeText(Gson().toJson(metadata("1.19.0")))
        }

        val recovered = recoverInterruptedRuntimeEnvironment(
            active = active,
            rollback = rollback,
            topLevelMetadata = topMetadata
        )

        assertTrue(recovered)
        assertEquals("old", active.resolve("marker.txt").readText())
        assertEquals(
            "1.18.3",
            Gson().fromJson(topMetadata.readText(), LocalRuntimeMetadata::class.java).version
        )
        assertFalse(rollback.exists())
        assertFalse(root.resolve("environment.failed").exists())
    }

    @Test
    fun `successful activation removes rollback only after finalization`() {
        val root = temporaryFolder.newFolder("runtime-success")
        val active = root.resolve("environment").apply {
            mkdirs()
            resolve("marker.txt").writeText("old")
        }
        val staging = root.resolve("environment.staging").apply {
            mkdirs()
            resolve("marker.txt").writeText("new")
        }
        val rollback = root.resolve("environment.rollback")
        var observedRollback = false

        activateRuntimeEnvironment(
            active = active,
            staging = staging,
            rollback = rollback,
            finalizeActivation = {
                observedRollback = rollback.resolve("marker.txt").readText() == "old"
                assertEquals("new", it.resolve("marker.txt").readText())
            }
        )

        assertTrue(observedRollback)
        assertEquals("new", active.resolve("marker.txt").readText())
        assertFalse(rollback.exists())
        assertFalse(staging.exists())
    }

    private fun metadata(version: String) = LocalRuntimeMetadata(
        version = version,
        port = 4097,
        installedAt = 123,
        runtimeVersion = "2026.07.18.1",
        abi = "arm64-v8a"
    )
}
