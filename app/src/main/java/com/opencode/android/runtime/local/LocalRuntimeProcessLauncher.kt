package com.opencode.android.runtime.local

import java.io.File
import java.util.concurrent.TimeUnit

class LocalRuntimeProcessLauncher(
    private val runtimeDirectory: File,
    private val portProbe: (Int) -> Boolean
) {
    @Volatile
    private var process: Process? = null

    @Synchronized
    fun start(runtime: LocalRuntimeInstaller.InstalledRuntime): Process {
        process?.takeIf { it.isAlive }?.let { return it }
        val rootfs = runtime.rootfs
        val suite = runtime.commandSuite
        val port = runtime.metadata.port
        val logs = File(runtimeDirectory, "logs").apply { mkdirs() }
        val logFile = File(logs, "opencode-local.log")
        val workspace = File(runtimeDirectory, "workspace").apply { mkdirs() }
        val prootTmp = File(runtimeDirectory, "proot-tmp").apply { mkdirs() }

        val command = buildList {
            add(suite.proot.absolutePath)
            add("--kill-on-exit")
            add("--link2symlink")
            add("-0")
            add("-r")
            add(rootfs.absolutePath)
            add("-b")
            add("/dev")
            add("-b")
            add("/proc")
            add("-b")
            add("/sys")
            add("-b")
            add("${workspace.absolutePath}:/workspace")
            add("-w")
            add("/root")
            add("/usr/local/bin/opencode")
            add("serve")
            add("--hostname")
            add("127.0.0.1")
            add("--port")
            add(port.toString())
        }

        val builder = ProcessBuilder(command)
            .directory(runtimeDirectory)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
        builder.environment().apply {
            putAll(suite.environment())
            put("PROOT_TMP_DIR", prootTmp.absolutePath)
            put("OPENCODE_CONFIG_DIR", "/root/.config/opencode")
            put("OPENCODE_DISABLE_AUTOUPDATE", "true")
        }
        val started = builder.start()
        process = started
        waitUntilReady(started, port, logFile)
        return started
    }

    @Synchronized
    fun stop() {
        val current = process ?: return
        current.destroy()
        if (!current.waitFor(3, TimeUnit.SECONDS)) {
            current.destroyForcibly()
            current.waitFor(2, TimeUnit.SECONDS)
        }
        process = null
    }

    fun isRunning(): Boolean = process?.isAlive == true

    private fun waitUntilReady(process: Process, port: Int, logFile: File) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (System.nanoTime() < deadline) {
            if (!process.isAlive) {
                error("Local OpenCode exited during startup: ${tail(logFile)}")
            }
            if (portProbe(port)) return
            Thread.sleep(250)
        }
        process.destroyForcibly()
        error("Local OpenCode did not become ready on port $port: ${tail(logFile)}")
    }

    private fun tail(file: File): String = runCatching {
        file.readLines().takeLast(20).joinToString("\n")
    }.getOrDefault("No runtime log was produced")
}
