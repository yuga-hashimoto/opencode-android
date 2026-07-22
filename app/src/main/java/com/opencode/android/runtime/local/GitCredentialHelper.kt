package com.opencode.android.runtime.local

import java.io.File

/** Writes an app-managed git credential helper script without embedding tokens in it. */
class GitCredentialHelper(private val rootfs: File) {
    private val helperFile
        get() = File(rootfs, "usr/local/bin/git-credential-opencode")
    private val gitConfigFile
        get() = File(rootfs, "root/.gitconfig")

    fun install(): File {
        helperFile.parentFile?.mkdirs()
        helperFile.writeText(
            """#!/data/data/com.termux/files/usr/bin/sh
            case "${'$'}1" in
              get)
                read protocol
                read host
                token="${'$'}OPENCODE_GITHUB_TOKEN"
                [ -n "${'$'}token" ] && printf 'protocol=%s\\nhost=%s\\nusername=x-access-token\\npassword=%s\\n\\n' "${'$'}protocol" "${'$'}host" "${'$'}token"
                ;;
            esac
            """.trimIndent()
        )
        helperFile.setExecutable(true, true)
        helperFile.setReadable(true, true)
        installGitHubCredentialConfig()
        return helperFile
    }

    fun remove() {
        helperFile.delete()
        removeGitHubCredentialConfig()
    }

    private fun installGitHubCredentialConfig() {
        val existing = gitConfigFile.takeIf(File::isFile)?.readText().orEmpty()
        if (existing.contains(GITHUB_CREDENTIAL_SECTION)) return
        gitConfigFile.parentFile?.mkdirs()
        gitConfigFile.writeText(
            listOf(existing.trimEnd(), GITHUB_CREDENTIAL_SECTION)
                .filter(String::isNotEmpty)
                .joinToString("\n\n", postfix = "\n")
        )
    }

    private fun removeGitHubCredentialConfig() {
        if (!gitConfigFile.isFile) return
        val remaining = gitConfigFile.readText().replace(GITHUB_CREDENTIAL_SECTION, "").trim()
        if (remaining.isEmpty()) gitConfigFile.delete() else gitConfigFile.writeText("$remaining\n")
    }

    private companion object {
        const val GITHUB_CREDENTIAL_SECTION = """[credential "https://github.com"]
\thelper = opencode"""
    }
}
