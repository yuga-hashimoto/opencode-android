package com.opencode.android.runtime.local

import android.content.Context
import com.google.gson.Gson
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class LocalRuntimeInstaller(
    private val context: Context,
    private val runtimeDirectory: File,
    private val abi: String,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val manifestReader: LocalRuntimeManifestReader = LocalRuntimeManifestReader(context)
) {
    data class InstalledRuntime(
        val metadata: LocalRuntimeMetadata,
        val commandSuite: EmbeddedCommandSuite.Paths,
        val rootfs: File,
        val openCode: File
    )

    suspend fun install(onProgress: (Float?, String) -> Unit = { _, _ -> }): InstalledRuntime =
        withContext(Dispatchers.IO) {
            runtimeDirectory.mkdirs()
            onProgress(0.02f, "Androidコマンド環境を準備しています")
            val commandSuite = EmbeddedCommandSuite(context, runtimeDirectory, abi).ensureInstalled()
            val manifest = manifestReader.read()
            val architecture = manifest.architecture(abi)
            val cache = File(runtimeDirectory, "cache").apply { mkdirs() }
            val staging = File(runtimeDirectory, "environment.staging")
            val active = File(runtimeDirectory, "environment")
            val rollback = File(runtimeDirectory, "environment.rollback")
            staging.deleteRecursively()
            staging.mkdirs()

            try {
                val alpineArchive = File(cache, "alpine-${manifest.alpineVersion}-$abi.tar.gz")
                download(
                    architecture.alpineUrl,
                    alpineArchive,
                    architecture.alpineSha256,
                    0.05f,
                    0.22f,
                    "Alpine Linuxをダウンロードしています",
                    onProgress
                )
                val openCodeArchive = File(cache, "opencode-${manifest.openCodeVersion}-$abi.tar.gz")
                download(
                    architecture.openCodeUrl,
                    openCodeArchive,
                    architecture.openCodeSha256,
                    0.24f,
                    0.72f,
                    "OpenCodeをダウンロードしています",
                    onProgress
                )

                val rootfs = File(staging, "rootfs").apply { mkdirs() }
                onProgress(0.75f, "Linux環境を展開しています")
                alpineArchive.inputStream().use { RuntimeArchive.extractTarGz(it, rootfs) }

                val openCodeExtract = File(staging, "opencode-extract").apply { mkdirs() }
                onProgress(0.85f, "OpenCodeを展開しています")
                openCodeArchive.inputStream().use { RuntimeArchive.extractTarGz(it, openCodeExtract) }
                val sourceBinary = openCodeExtract.walkTopDown()
                    .firstOrNull { it.isFile && it.name == "opencode" }
                    ?: error("OpenCode archive did not contain the opencode binary")
                val openCodeBinary = File(rootfs, "usr/local/bin/opencode")
                openCodeBinary.parentFile?.mkdirs()
                sourceBinary.copyTo(openCodeBinary, overwrite = true)
                require(openCodeBinary.setExecutable(true, false) || openCodeBinary.canExecute()) {
                    "Unable to mark OpenCode executable"
                }
                openCodeExtract.deleteRecursively()
                configureRootfs(rootfs, commandSuite)
                onProgress(0.91f, "Gitと開発ツールを導入しています")
                installDevelopmentTools(rootfs, commandSuite)

                val metadata = LocalRuntimeMetadata(
                    version = manifest.openCodeVersion,
                    port = manifest.port,
                    installedAt = System.currentTimeMillis(),
                    runtimeVersion = manifest.runtimeVersion,
                    abi = abi
                )
                File(staging, METADATA_FILE).writeText(Gson().toJson(metadata))
                onProgress(0.96f, "ランタイムを有効化しています")
                rollback.deleteRecursively()
                if (active.exists()) require(active.renameTo(rollback)) { "Unable to create runtime rollback" }
                require(staging.renameTo(active)) { "Unable to activate local runtime" }
                File(active, METADATA_FILE).copyTo(File(runtimeDirectory, METADATA_FILE), overwrite = true)
                rollback.deleteRecursively()
                onProgress(1f, "ローカルOpenCodeを導入しました")
                InstalledRuntime(metadata, commandSuite, File(active, "rootfs"), File(active, "rootfs/usr/local/bin/opencode"))
            } catch (error: Throwable) {
                staging.deleteRecursively()
                throw error
            }
        }

    fun installedRuntime(): InstalledRuntime? {
        val metadataFile = File(runtimeDirectory, METADATA_FILE)
        val rootfs = File(runtimeDirectory, "environment/rootfs")
        val openCode = File(rootfs, "usr/local/bin/opencode")
        if (!metadataFile.isFile || !openCode.isFile) return null
        val metadata = runCatching {
            Gson().fromJson(metadataFile.readText(), LocalRuntimeMetadata::class.java)
        }.getOrNull() ?: return null
        val commandSuite = runCatching {
            EmbeddedCommandSuite(context, runtimeDirectory, abi).ensureInstalled()
        }.getOrNull() ?: return null
        return InstalledRuntime(metadata, commandSuite, rootfs, openCode)
    }

    private fun download(
        url: String,
        destination: File,
        expectedSha256: String,
        startProgress: Float,
        endProgress: Float,
        label: String,
        onProgress: (Float?, String) -> Unit
    ) {
        if (destination.isFile) {
            runCatching { RuntimeArchive.verifySha256(destination, expectedSha256) }
                .onSuccess {
                    onProgress(endProgress, label)
                    return
                }
            destination.delete()
        }
        val partial = File(destination.parentFile, destination.name + ".partial")
        partial.delete()
        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "Download failed with HTTP ${response.code}: $url" }
            val body = requireNotNull(response.body) { "Download response had no body: $url" }
            val expectedLength = body.contentLength()
            partial.outputStream().buffered().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        val fraction = if (expectedLength > 0) downloaded.toFloat() / expectedLength else null
                        onProgress(
                            fraction?.let { startProgress + (endProgress - startProgress) * it.coerceIn(0f, 1f) },
                            label
                        )
                    }
                }
            }
        }
        RuntimeArchive.verifySha256(partial, expectedSha256)
        require(partial.renameTo(destination)) { "Unable to finalize ${destination.name}" }
        onProgress(endProgress, label)
    }

    private fun installDevelopmentTools(rootfs: File, suite: EmbeddedCommandSuite.Paths) {
        val prootTmp = File(runtimeDirectory, "proot-tmp").apply { mkdirs() }
        val command = listOf(
            suite.proot.absolutePath,
            "--kill-on-exit",
            "--link2symlink",
            "-0",
            "-r",
            rootfs.absolutePath,
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
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin /sbin/apk add --no-cache bash git curl ripgrep ca-certificates libstdc++ && /usr/sbin/update-ca-certificates"
        )
        val installLog = File(runtimeDirectory, "logs/tool-install.log").apply {
            parentFile?.mkdirs()
            delete()
        }
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.to(installLog))
            .apply {
                environment().putAll(suite.environment())
                environment()["PROOT_TMP_DIR"] = prootTmp.absolutePath
            }
            .start()
        val completed = process.waitFor(5, java.util.concurrent.TimeUnit.MINUTES)
        if (!completed) {
            process.destroyForcibly()
            error("Development tool installation timed out")
        }
        require(process.exitValue() == 0) {
            "Unable to install Git and development tools: ${installLog.readText().takeLast(4000)}"
        }
    }

    private fun configureRootfs(rootfs: File, suite: EmbeddedCommandSuite.Paths) {
        File(rootfs, "root").mkdirs()
        File(rootfs, "tmp").apply { mkdirs(); setWritable(true, false); setExecutable(true, false) }
        File(rootfs, "workspace").mkdirs()
        File(rootfs, "etc/resolv.conf").apply {
            parentFile?.mkdirs()
            writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
        }
        File(rootfs, "etc/hosts").writeText("127.0.0.1 localhost\n::1 localhost\n")
        File(rootfs, "etc/profile.d/opencode-android.sh").apply {
            parentFile?.mkdirs()
            writeText(
                "export HOME=/root\n" +
                    "export TMPDIR=/tmp\n" +
                    "export PATH=/usr/local/bin:/usr/bin:/bin\n" +
                    "export OPENCODE_CONFIG_DIR=/root/.config/opencode\n"
            )
        }
        File(rootfs, "root/.config/opencode").mkdirs()
        File(rootfs, "root/.local/share/opencode").mkdirs()
        require(suite.proot.isFile) { "PRoot launcher is unavailable" }
    }

    companion object {
        private const val METADATA_FILE = "metadata.json"
    }
}
