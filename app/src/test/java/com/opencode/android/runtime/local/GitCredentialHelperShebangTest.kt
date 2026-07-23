package com.opencode.android.runtime.local

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertTrue
import org.junit.Test

class GitCredentialHelperShebangTest {

    @Test
    fun `install writes bin-sh shebang not termux path`() {
        val root = createTempDirectory("git-shebang").toFile()
        try {
            val helper = GitCredentialHelper(root) { "token" }
            val file = helper.install()
            val content = file.readText()
            assertTrue(content.startsWith("#!/bin/sh"))
            assertTrue(!content.contains("com.termux"))
        } finally {
            root.deleteRecursively()
        }
    }
}
