package com.opencode.android.assistant

import com.opencode.android.api.OpenCodeAgent
import com.opencode.android.api.OpenCodeModel
import com.opencode.android.api.OpenCodeProvider
import com.opencode.android.data.ConnectionProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantProfileTest {
    private val connection = ConnectionProfile(
        id = "mac",
        name = "Mac mini",
        baseUrl = "http://192.168.1.10:4096/",
        allowInsecureLan = true
    )
    private val provider = OpenCodeProvider(
        id = "opencode",
        name = "OpenCode Zen",
        models = mapOf("free" to OpenCodeModel("free", "opencode", "Free"))
    )
    private val agents = listOf(OpenCodeAgent(name = "build"))

    @Test
    fun `missing model and agent use server defaults`() {
        val result = AssistantProfileResolver.resolve(
            profile = AssistantProfile(backendId = "mac"),
            connections = listOf(connection),
            providers = listOf(provider),
            defaultModels = mapOf("opencode" to "free"),
            agents = agents
        ).getOrThrow()

        assertEquals("mac", result.connection.id)
        assertEquals("opencode", result.providerId)
        assertEquals("free", result.modelId)
        assertEquals("build", result.agentId)
    }

    @Test
    fun `deleted backend produces configuration error`() {
        val result = AssistantProfileResolver.resolve(
            profile = AssistantProfile(backendId = "deleted"),
            connections = listOf(connection),
            providers = listOf(provider),
            defaultModels = emptyMap(),
            agents = agents
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `continuous mode retains existing session while single shot starts fresh`() {
        val continuous = AssistantProfile(
            backendId = "mac",
            continuousConversation = true,
            sessionId = "s1"
        )
        val singleShot = continuous.copy(continuousConversation = false)

        assertEquals("s1", AssistantProfileResolver.resolveSessionId(continuous))
        assertNull(AssistantProfileResolver.resolveSessionId(singleShot))
    }
}
