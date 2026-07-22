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
            val helper = GitCredentialHelper(root)
            val file = helper.install()

            assertTrue(file.isFile)
            assertTrue(file.path.endsWith("usr/local/bin/git-credential-opencode"))
            assertTrue(file.readText().contains("x-access-token"))
            assertFalse(file.readText().contains("secret"))
            val gitConfig = root.resolve("root/.gitconfig")
            assertTrue(gitConfig.isFile)
            assertTrue(gitConfig.readText().contains("helper = opencode"))
            assertFalse(gitConfig.readText().contains("secret"))

            helper.remove()
            assertFalse(file.exists())
            assertFalse(gitConfig.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
