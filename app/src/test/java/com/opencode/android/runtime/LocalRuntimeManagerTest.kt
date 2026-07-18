package com.opencode.android.runtime

import com.opencode.android.backend.LocalRuntimeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalRuntimeManagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `arm64 without metadata is not installed`() {
        val manager = LocalRuntimeManager(
            runtimeDirectory = temporaryFolder.root,
            abi = "arm64-v8a",
            portProbe = { false }
        )

        assertEquals(LocalRuntimeStatus.NotInstalled, manager.status())
    }

    @Test
    fun `unsupported abi is reported`() {
        val manager = LocalRuntimeManager(
            runtimeDirectory = temporaryFolder.root,
            abi = "armeabi-v7a",
            portProbe = { false }
        )

        assertEquals(LocalRuntimeStatus.UnsupportedAbi("armeabi-v7a"), manager.status())
    }

    @Test
    fun `corrupt metadata is broken`() {
        temporaryFolder.newFile("metadata.json").writeText("not-json")
        val manager = LocalRuntimeManager(
            runtimeDirectory = temporaryFolder.root,
            abi = "arm64-v8a",
            portProbe = { false }
        )

        assertTrue(manager.status() is LocalRuntimeStatus.Broken)
    }

    @Test
    fun `healthy metadata and port probe are ready`() {
        temporaryFolder.newFile("metadata.json").writeText(
            """{"version":"1.17.20","port":4096,"installedAt":123}"""
        )
        val manager = LocalRuntimeManager(
            runtimeDirectory = temporaryFolder.root,
            abi = "arm64-v8a",
            portProbe = { port -> port == 4096 }
        )

        assertEquals(LocalRuntimeStatus.Ready("1.17.20", 4096), manager.status())
    }

    @Test
    fun `metadata without a listening server is broken`() {
        temporaryFolder.newFile("metadata.json").writeText(
            """{"version":"1.17.20","port":4096,"installedAt":123}"""
        )
        val manager = LocalRuntimeManager(
            runtimeDirectory = temporaryFolder.root,
            abi = "arm64-v8a",
            portProbe = { false }
        )

        assertTrue(manager.status() is LocalRuntimeStatus.Broken)
    }
}
