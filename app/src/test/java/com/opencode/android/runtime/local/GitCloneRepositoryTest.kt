package com.opencode.android.runtime.local

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitCloneRepositoryTest {

    @Test
    fun `clone rejects path traversal in name`() {
        val runtimeDir = createTempDirectory("clone-test").toFile()
        try {
            val repo = GitCloneRepository(
                runtimeDirectory = runtimeDir,
                installedRuntimeProvider = { null }
            )
            val result = repo.clone("https://github.com/user/repo.git", "../../etc/evil")
            assertEquals(127, result.exitCode)
        } finally {
            runtimeDir.deleteRecursively()
        }
    }

    @Test
    fun `clone sanitizes special characters in name`() {
        val runtimeDir = createTempDirectory("clone-test").toFile()
        try {
            val repo = GitCloneRepository(
                runtimeDirectory = runtimeDir,
                installedRuntimeProvider = { null }
            )
            val result = repo.clone("https://github.com/user/repo.git", "my repo/feature")
            assertEquals(127, result.exitCode)
        } finally {
            runtimeDir.deleteRecursively()
        }
    }

    @Test
    fun `clone rejects blank name after sanitization`() {
        val runtimeDir = createTempDirectory("clone-test").toFile()
        try {
            val repo = GitCloneRepository(
                runtimeDirectory = runtimeDir,
                installedRuntimeProvider = { null }
            )
            val result = repo.clone("https://github.com/user/repo.git", "...")
            assertEquals(1, result.exitCode)
            assertTrue(result.output.contains("Invalid repository name"))
        } finally {
            runtimeDir.deleteRecursively()
        }
    }
}
