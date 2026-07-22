package com.opencode.android.runtime.remote

import com.opencode.android.core.api.PromptRequest
import com.opencode.android.data.connection.ConnectionProfile
import com.opencode.android.runtime.PermissionResponse
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RemoteOpenCodeBackendTest {
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
    fun `delegates health session prompt and permission operations to server`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"healthy":true,"version":"1.17.20"}"""))
        server.enqueue(MockResponse().setBody("""{"id":"s1","title":"Mobile","time":{"created":1,"updated":1}}"""))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setBody("true"))

        val backend = RemoteOpenCodeBackend(
            ConnectionProfile(
                id = "mac",
                name = "Mac mini",
                baseUrl = server.url("/").toString(),
                username = "opencode",
                password = "pw",
                allowInsecureLan = true
            )
        )

        assertEquals("1.17.20", backend.health().version)
        val session = backend.createSession("Mobile")
        backend.sendMessage(
            session.id,
            PromptRequest(text = "hello", agent = "build")
        )
        assertTrue(backend.respondToPermission(session.id, "p1", PermissionResponse.ONCE, false))

        assertEquals("/global/health", server.takeRequest().path)
        assertEquals("/session", server.takeRequest().path)
        assertEquals("/session/s1/prompt_async", server.takeRequest().path)
        assertEquals("/session/s1/permissions/p1", server.takeRequest().path)
    }

    @Test
    fun `delegates answerQuestion to server`() = runBlocking {
        server.enqueue(MockResponse().setBody("true"))

        val backend = RemoteOpenCodeBackend(
            ConnectionProfile(
                id = "mac",
                name = "Mac mini",
                baseUrl = server.url("/").toString(),
                username = "opencode",
                password = "pw",
                allowInsecureLan = true
            )
        )

        assertTrue(backend.answerQuestion("s1", "q-1", listOf(listOf("src"), listOf("docs", "tests"))))

        val request = server.takeRequest()
        assertEquals("/session/s1/question/q-1", request.path)
        assertEquals("""{"answers":[["src"],["docs","tests"]]}""", request.body.readUtf8())
    }
}
