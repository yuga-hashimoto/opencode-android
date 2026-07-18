package com.opencode.android.runtime.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalRuntimeCommandRunnerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `returns not installed without starting a process`() {
        val runner = LocalRuntimeCommandRunner(
            runtimeDirectory = temporaryFolder.newFolder("runtime"),
            installedRuntimeProvider = { null }
        )

        val result = runner.runShell("opencode --version")

        assertEquals(127, result.exitCode)
        assertEquals("未インストール", result.output)
    }

    @Test
    fun `rejects non positive timeout`() {
        val runner = LocalRuntimeCommandRunner(
            runtimeDirectory = temporaryFolder.newFolder("runtime-timeout"),
            installedRuntimeProvider = { null }
        )

        assertThrows(IllegalArgumentException::class.java) {
            runner.runShell("true", timeoutSeconds = 0L)
        }
    }
}
