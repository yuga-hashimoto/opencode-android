package com.opencode.android.feature.assistant

import java.security.MessageDigest

data class AssistantExecutionProfile(
    val runtimeId: String,
    val workspacePath: String?,
    val providerId: String?,
    val modelId: String?,
    val agentId: String?
) {
    init {
        require(runtimeId.isNotBlank()) { "Assistant runtime id is required" }
        require((providerId == null) == (modelId == null)) {
            "Assistant provider and model must be set together"
        }
    }

    val sessionKey: String
        get() {
            val payload = listOf(
                runtimeId,
                workspacePath.orEmpty(),
                providerId.orEmpty(),
                modelId.orEmpty(),
                agentId.orEmpty()
            ).joinToString("\u0000")
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(payload.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
}

internal fun resolveAssistantExecutionProfile(
    runtimeId: String,
    assistantWorkspacePath: String?,
    assistantProviderId: String?,
    assistantModelId: String?,
    assistantAgentId: String?,
    fallbackProviderId: String?,
    fallbackModelId: String?,
    fallbackAgentId: String?
): AssistantExecutionProfile {
    val explicitProvider = assistantProviderId?.trim()?.takeIf(String::isNotEmpty)
    val explicitModel = assistantModelId?.trim()?.takeIf(String::isNotEmpty)
    val fallbackProvider = fallbackProviderId?.trim()?.takeIf(String::isNotEmpty)
    val fallbackModel = fallbackModelId?.trim()?.takeIf(String::isNotEmpty)
    val selectedProvider = if (explicitProvider != null && explicitModel != null) {
        explicitProvider
    } else {
        fallbackProvider?.takeIf { fallbackModel != null }
    }
    val selectedModel = if (explicitProvider != null && explicitModel != null) {
        explicitModel
    } else {
        fallbackModel?.takeIf { fallbackProvider != null }
    }
    return AssistantExecutionProfile(
        runtimeId = runtimeId.trim(),
        workspacePath = assistantWorkspacePath?.trim()?.takeIf(String::isNotEmpty),
        providerId = selectedProvider,
        modelId = selectedModel,
        agentId = assistantAgentId?.trim()?.takeIf(String::isNotEmpty)
            ?: fallbackAgentId?.trim()?.takeIf(String::isNotEmpty)
    )
}

internal fun reusableAssistantSessionId(
    continuousConversation: Boolean,
    storedSessionId: String?,
    storedProfileKey: String?,
    activeProfile: AssistantExecutionProfile
): String? = storedSessionId
    ?.takeIf { continuousConversation && storedProfileKey == activeProfile.sessionKey }
