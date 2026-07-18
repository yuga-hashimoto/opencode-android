package com.opencode.android.runtime.local

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalRuntimeReleaseClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `compares numeric OpenCode versions`() {
        assertTrue(compareOpenCodeVersions("1.18.10", "1.18.3") > 0)
        assertTrue(compareOpenCodeVersions("v2.0.0", "1.99.99") > 0)
        assertEquals(0, compareOpenCodeVersions("v1.18.3", "1.18.3"))
        assertTrue(compareOpenCodeVersions("1.18.2", "1.18.3") < 0)
    }

    @Test
    fun `selects exact arm64 musl asset and returns available release`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                releaseJson(
                    tag = "v1.19.0",
                    notes = "release notes",
                    assets = listOf(
                        asset("opencode-linux-arm64.tar.gz", "a".repeat(64)),
                        asset("opencode-linux-arm64-musl.tar.gz", "b".repeat(64), size = 57_000_000)
                    )
                )
            )
        )
        val client = client()

        val result = client.check("1.18.3", "arm64-v8a")

        val available = result as LocalRuntimeUpdateCheck.Available
        assertEquals("1.18.3", available.currentVersion)
        assertEquals("1.19.0", available.release.version)
        assertEquals("release notes", available.release.releaseNotes)
        assertEquals("opencode-linux-arm64-musl.tar.gz", available.release.asset.name)
        assertEquals("b".repeat(64), available.release.asset.sha256)
        assertEquals(57_000_000, available.release.asset.sizeBytes)
        val request = server.takeRequest()
        assertEquals("application/vnd.github+json", request.getHeader("Accept"))
        assertEquals("OpenCode-Android", request.getHeader("User-Agent"))
    }

    @Test
    fun `returns up to date when latest is not newer`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                releaseJson(
                    tag = "v1.18.3",
                    notes = "same",
                    assets = listOf(asset("opencode-linux-x64-musl.tar.gz", "c".repeat(64)))
                )
            )
        )

        val result = client().check("v1.18.3", "x86_64")

        assertEquals(
            LocalRuntimeUpdateCheck.UpToDate(currentVersion = "1.18.3", latestVersion = "1.18.3"),
            result
        )
    }

    @Test
    fun `truncates release notes`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                releaseJson(
                    tag = "v1.19.0",
                    notes = "x".repeat(40),
                    assets = listOf(asset("opencode-linux-arm64-musl.tar.gz", "d".repeat(64)))
                )
            )
        )

        val result = client(maxReleaseNotesCharacters = 12)
            .check("1.18.3", "arm64-v8a") as LocalRuntimeUpdateCheck.Available

        assertEquals("x".repeat(12), result.release.releaseNotes)
    }

    @Test
    fun `rejects missing GitHub digest`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                releaseJson(
                    tag = "v1.19.0",
                    assets = listOf(
                        """{"name":"opencode-linux-arm64-musl.tar.gz","size":100,"browser_download_url":"https://github.com/anomalyco/opencode/releases/download/v1.19.0/opencode-linux-arm64-musl.tar.gz","digest":null}"""
                    )
                )
            )
        )

        val error = runCatching { client().check("1.18.3", "arm64-v8a") }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("digest", ignoreCase = true))
    }

    @Test
    fun `rejects non https asset URL`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                releaseJson(
                    tag = "v1.19.0",
                    assets = listOf(
                        asset(
                            name = "opencode-linux-arm64-musl.tar.gz",
                            digest = "e".repeat(64),
                            url = "http://example.com/opencode.tar.gz"
                        )
                    )
                )
            )
        )

        val error = runCatching { client().check("1.18.3", "arm64-v8a") }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("HTTPS", ignoreCase = true))
    }

    @Test
    fun `rejects unsupported ABI before network request`() = runTest {
        val error = runCatching { client().check("1.18.3", "armeabi-v7a") }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("ABI"))
        assertEquals(0, server.requestCount)
    }

    private fun client(maxReleaseNotesCharacters: Int = 12_000) = LocalRuntimeReleaseClient(
        httpClient = OkHttpClient(),
        endpoint = server.url("/repos/anomalyco/opencode/releases/latest"),
        maxReleaseNotesCharacters = maxReleaseNotesCharacters
    )

    private fun releaseJson(
        tag: String,
        notes: String = "notes",
        assets: List<String>
    ): String = """
        {
          "tag_name": "$tag",
          "body": ${jsonString(notes)},
          "assets": [${assets.joinToString(",")}]
        }
    """.trimIndent()

    private fun asset(
        name: String,
        digest: String,
        size: Long = 100,
        url: String = "https://github.com/anomalyco/opencode/releases/download/v1.19.0/$name"
    ): String = """
        {
          "name": "$name",
          "size": $size,
          "browser_download_url": "$url",
          "digest": "sha256:$digest"
        }
    """.trimIndent()

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                else -> append(character)
            }
        }
        append('"')
    }
}
