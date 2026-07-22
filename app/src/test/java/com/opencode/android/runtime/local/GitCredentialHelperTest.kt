package com.opencode.android.runtime.local

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitCredentialHelperTest {
    @Test
    fun `install writes executable helper and remove deletes it`() {
        val root = createTempDirectory("git-helper").toFile()
        try {
            val helper = GitCredentialHelper(root) { "test-token" }
            val file = helper.install()

            assertTrue(file.isFile)
            assertTrue(file.path.endsWith("usr/local/bin/git-credential-opencode"))
            assertTrue(file.readText().contains("x-access-token"))
            assertFalse(file.readText().contains("secret"))
            val gitConfig = root.resolve("root/.gitconfig")
            assertTrue(gitConfig.isFile)
            assertTrue(gitConfig.readText().contains("helper = opencode"))
            assertFalse(gitConfig.readText().contains("\\t"))
            val gitCredentials = root.resolve("root/.git-credentials")
            assertTrue(gitCredentials.isFile)
            assertTrue(gitCredentials.readText().contains("x-access-token:test-token@github.com"))

            helper.remove()
            assertFalse(file.exists())
            assertFalse(gitConfig.exists())
            assertFalse(gitCredentials.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `install repairs malformed gitconfig`() {
        val root = createTempDirectory("git-helper").toFile()
        try {
            val gitConfig = root.resolve("root/.gitconfig")
            gitConfig.parentFile?.mkdirs()
            gitConfig.writeText("[credential \"https://github.com\"]\n\\thelper = opencode\n")

            GitCredentialHelper(root) { "test-token" }.install()

            assertFalse(gitConfig.readText().contains("\\t"))
            assertTrue(gitConfig.readText().contains("\thelper = opencode"))
        } finally {
            root.deleteRecursively()
        }
    }
}
