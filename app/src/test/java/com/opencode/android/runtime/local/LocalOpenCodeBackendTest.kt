package com.opencode.android.runtime.local

import com.opencode.android.data.connection.ConnectionProfile
import com.opencode.android.runtime.remote.RemoteOpenCodeBackend
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalOpenCodeBackendTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `reuses backend for same port and rebuilds after invalidate or port change`() {
        var port = 4097
        val created = mutableListOf<RemoteOpenCodeBackend>()
        val backend = LocalOpenCodeBackend(
            portProvider = { port },
            backendFactory = { profile ->
                RemoteOpenCodeBackend(profile).also { created += it }
            }
        )

        val first = backend.delegate()
        val second = backend.delegate()
        assertSame(first, second)
        assertEquals(1, created.size)

        backend.invalidate()
        val third = backend.delegate()
        assertNotSame(first, third)
        assertEquals(2, created.size)

        port = 4098
        val fourth = backend.delegate()
        assertNotSame(third, fourth)
        assertEquals(3, created.size)
    }

    @Test
    fun `delegates answerQuestion through remote backend`() = runBlocking {
        server.enqueue(MockResponse().setBody("true"))

        val backend = LocalOpenCodeBackend(
            portProvider = { server.port },
            backendFactory = { profile ->
                RemoteOpenCodeBackend(
                    profile.copy(baseUrl = server.url("/").toString())
                )
            }
        )

        assertTrue(backend.answerQuestion("s1", "q-1", listOf(listOf("src"), listOf("docs", "tests"))))

        val request = server.takeRequest()
        assertEquals("/session/s1/question/q-1", request.path)
        assertEquals("""{"answers":[["src"],["docs","tests"]]}""", request.body.readUtf8())
    }
}
