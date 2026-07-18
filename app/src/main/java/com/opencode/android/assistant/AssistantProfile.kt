package com.opencode.android.assistant

import com.opencode.android.api.OpenCodeAgent
import com.opencode.android.api.OpenCodeProvider
import com.opencode.android.data.ConnectionProfile

data class AssistantProfile(
    val backendId: String?,
    val providerId: String? = null,
    val modelId: String? = null,
    val agentId: String? = null,
    val sessionId: String? = null,
    val continuousConversation: Boolean = false,
    val speakResponses: Boolean = true
)

data class ResolvedAssistantProfile(
    val connection: ConnectionProfile,
    val providerId: String?,
    val modelId: String?,
    val agentId: String?,
    val sessionId: String?,
    val continuousConversation: Boolean,
    val speakResponses: Boolean
)

object AssistantProfileResolver {
    fun resolve(
        profile: AssistantProfile,
        connections: List<ConnectionProfile>,
        providers: List<OpenCodeProvider>,
        defaultModels: Map<String, String>,
        agents: List<OpenCodeAgent>
    ): Result<ResolvedAssistantProfile> = runCatching {
        val connection = connections.firstOrNull { it.id == profile.backendId }
            ?: throw IllegalStateException("The selected OpenCode connection no longer exists")

        val provider = profile.providerId
            ?.let { id -> providers.firstOrNull { it.id == id } }
            ?: providers.firstOrNull { it.id == "opencode" }
            ?: providers.firstOrNull()

        val modelId = profile.modelId
            ?.takeIf { configured -> provider?.models?.containsKey(configured) == true }
            ?: provider?.let { defaultModels[it.id] }
                ?.takeIf { configured -> provider.models.containsKey(configured) }
            ?: provider?.models?.values?.firstOrNull()?.id

        val agentId = profile.agentId
            ?.takeIf { configured -> agents.any { it.name == configured } }
            ?: agents.firstOrNull { it.name == "build" }?.name
            ?: agents.firstOrNull()?.name

        ResolvedAssistantProfile(
            connection = connection,
            providerId = provider?.id,
            modelId = modelId,
            agentId = agentId,
            sessionId = resolveSessionId(profile),
            continuousConversation = profile.continuousConversation,
            speakResponses = profile.speakResponses
        )
    }

    fun resolveSessionId(profile: AssistantProfile): String? =
        profile.sessionId.takeIf { profile.continuousConversation }
}
