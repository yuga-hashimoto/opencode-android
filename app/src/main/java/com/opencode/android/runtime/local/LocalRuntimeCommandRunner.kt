package com.opencode.android.runtime.local

import java.io.File
import java.util.concurrent.TimeUnit

class LocalRuntimeCommandRunner(
    private val runtimeDirectory: File,
    private val installedRuntimeProvider: () -> LocalRuntimeInstaller.InstalledRuntime?,
    private val timeoutSeconds: Long = 15L,
    private val maxOutputCharacters: Int = 4_000
) {
    init {
        require(timeoutSeconds > 0)
        require(maxOutputCharacters > 0)
    }

    @Synchronized
    fun run(definition: LocalRuntimeToolDefinition): LocalRuntimeCommandResult {
        val runtime = installedRuntimeProvider()
            ?: return LocalRuntimeCommandResult(127, "未インストール")
        val prootTmp = File(runtimeDirectory, "proot-tmp").apply { mkdirs() }
        val outputFile = File.createTempFile("diagnostic-", ".log", File(runtimeDirectory, "logs").apply { mkdirs() })
        return try {
            val command = listOf(
                runtime.commandSuite.proot.absolutePath,
                "--kill-on-exit",
                "--link2symlink",
                "-0",
                "-r",
                runtime.rootfs.absolutePath,
                "-b",
                "/dev",
                "-b",
                "/proc",
                "-b",
                "/sys",
                "-w",
                "/root",
                "/bin/sh",
                "-lc",
                definition.command
            )
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.to(outputFile))
                .apply {
                    environment().clear()
                    environment().putAll(localRuntimeEnvironment(runtime.commandSuite.environment(), prootTmp))
                }
                .start()
            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
                LocalRuntimeCommandResult(124, "確認がタイムアウトしました")
            } else {
                LocalRuntimeCommandResult(
                    exitCode = process.exitValue(),
                    output = outputFile.readText().takeLast(maxOutputCharacters)
                )
            }
        } finally {
            outputFile.delete()
        }
    }
}
