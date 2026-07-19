package com.opencode.android.feature.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AssistantExecutionProfileTest {
    @Test
    fun `assistant model and agent override chat defaults`() {
        val profile = resolveAssistantExecutionProfile(
            runtimeId = "remote-mac",
            assistantWorkspacePath = " /repo ",
            assistantProviderId = "opencode",
            assistantModelId = "deepseek-v4-flash",
            assistantAgentId = "plan",
            fallbackProviderId = "openai",
            fallbackModelId = "gpt-5.6",
            fallbackAgentId = "build"
        )

        assertEquals("remote-mac", profile.runtimeId)
        assertEquals("/repo", profile.workspacePath)
        assertEquals("opencode", profile.providerId)
        assertEquals("deepseek-v4-flash", profile.modelId)
        assertEquals("plan", profile.agentId)
    }

    @Test
    fun `incomplete assistant model selection falls back as a pair`() {
        val profile = resolveAssistantExecutionProfile(
            runtimeId = "local-android",
            assistantWorkspacePath = null,
            assistantProviderId = "opencode",
            assistantModelId = null,
            assistantAgentId = null,
            fallbackProviderId = "openai",
            fallbackModelId = "gpt-5.6",
            fallbackAgentId = "build"
        )

        assertEquals("openai", profile.providerId)
        assertEquals("gpt-5.6", profile.modelId)
        assertEquals("build", profile.agentId)
    }

    @Test
    fun `session is reused only for same profile and continuous mode`() {
        val first = resolveAssistantExecutionProfile(
            runtimeId = "local-android",
            assistantWorkspacePath = "/workspace",
            assistantProviderId = "openai",
            assistantModelId = "gpt-5.6",
            assistantAgentId = "build",
            fallbackProviderId = null,
            fallbackModelId = null,
            fallbackAgentId = null
        )
        val changed = first.copy(modelId = "gpt-5.6-mini")

        assertEquals("session-1", reusableAssistantSessionId(true, "session-1", first.sessionKey, first))
        assertNull(reusableAssistantSessionId(false, "session-1", first.sessionKey, first))
        assertNull(reusableAssistantSessionId(true, "session-1", first.sessionKey, changed))
        assertNotEquals(first.sessionKey, changed.sessionKey)
    }
}
