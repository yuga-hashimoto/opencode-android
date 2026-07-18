package com.opencode.android.core.api

import com.google.gson.JsonParser
import com.opencode.android.data.connection.ConnectionProfile
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenCodeApiClientTest {
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
    fun `health uses official endpoint and basic authentication`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"healthy":true,"version":"1.2.3"}"""))
        val client = client(password = "secret")

        val result = client.health()

        assertTrue(result.healthy)
        assertEquals("1.2.3", result.version)
        val request = server.takeRequest()
        assertEquals("/global/health", request.path)
        assertEquals(Credentials.basic("opencode", "secret"), request.getHeader("Authorization"))
    }

    @Test
    fun `lists sessions and creates session using official endpoints`() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"id":"s1","title":"Existing","directory":"/repo","time":{"created":1,"updated":2}}]"""))
        server.enqueue(MockResponse().setBody("""{"id":"s2","title":"New","directory":"/repo","time":{"created":3,"updated":3}}"""))
        val client = client()

        val sessions = client.sessions()
        val created = client.createSession("New")

        assertEquals("s1", sessions.single().id)
        assertEquals("s2", created.id)
        assertEquals("/session", server.takeRequest().path)
        val createRequest = server.takeRequest()
        assertEquals("/session", createRequest.path)
        assertEquals("New", JsonParser.parseString(createRequest.body.readUtf8()).asJsonObject["title"].asString)
    }

    @Test
    fun `sends asynchronous prompt with selected model agent and text part`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        val client = client()

        client.promptAsync(
            sessionId = "s1",
            request = PromptRequest(
                providerId = "opencode",
                modelId = "deepseek-v4-flash-free",
                agent = "build",
                text = "hello"
            )
        )

        val request = server.takeRequest()
        assertEquals("/session/s1/prompt_async", request.path)
        val json = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("build", json["agent"].asString)
        assertEquals("opencode", json.getAsJsonObject("model")["providerID"].asString)
        assertEquals("deepseek-v4-flash-free", json.getAsJsonObject("model")["modelID"].asString)
        assertEquals("hello", json.getAsJsonArray("parts")[0].asJsonObject["text"].asString)
    }

    @Test
    fun `responds to permission request`() = runBlocking {
        server.enqueue(MockResponse().setBody("true"))
        val client = client()

        val result = client.respondPermission("s1", "perm1", "once")

        assertTrue(result)
        val request = server.takeRequest()
        assertEquals("/session/s1/permissions/perm1", request.path)
        val json = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("once", json["response"].asString)
        assertTrue(!json.has("remember"))
    }

    @Test
    fun `event stream reconnects after the server closes the connection`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"type\":\"server.connected\",\"properties\":{}}\n\n")
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END)
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"type\":\"session.idle\",\"properties\":{\"sessionID\":\"s1\"}}\n\n")
        )

        val events = withTimeout(3_000) {
            client().events().take(2).toList()
        }

        assertTrue(events[0] is OpenCodeEvent.ServerConnected)
        assertEquals("s1", (events[1] as OpenCodeEvent.SessionIdle).sessionId)
        assertEquals("/event", server.takeRequest().path)
        assertEquals("/event", server.takeRequest().path)
    }

    @Test
    fun `redacts response body from authentication errors`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("token=super-secret"))

        val error = org.junit.Assert.assertThrows(OpenCodeApiException::class.java) {
            runBlocking { client().health() }
        }

        assertEquals(401, error.statusCode)
        assertTrue(error.message.orEmpty().contains("HTTP 401"))
        assertTrue(!error.message.orEmpty().contains("super-secret"))
    }

    private fun client(password: String? = null): OpenCodeApiClient {
        val profile = ConnectionProfile(
            id = "test",
            name = "Test",
            baseUrl = server.url("/").toString(),
            username = "opencode",
            password = password,
            allowInsecureLan = true
        )
        return OpenCodeApiClient(profile, OkHttpClient())
    }
}
