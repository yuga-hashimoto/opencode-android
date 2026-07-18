package com.opencode.android.backend

import com.opencode.android.api.OpenCodeAgent
import com.opencode.android.api.OpenCodeApiClient
import com.opencode.android.api.OpenCodeEvent
import com.opencode.android.api.OpenCodeHealth
import com.opencode.android.api.OpenCodeMessage
import com.opencode.android.api.OpenCodeSession
import com.opencode.android.api.PromptRequest
import com.opencode.android.api.ProviderCatalog
import com.opencode.android.data.ConnectionProfile
import kotlinx.coroutines.flow.Flow

class RemoteOpenCodeBackend(
    private val profile: ConnectionProfile,
    private val client: OpenCodeApiClient = OpenCodeApiClient(profile)
) : OpenCodeBackend {
    override val id: String = profile.id
    override val displayName: String = profile.name
    override val kind: BackendKind = BackendKind.REMOTE

    override suspend fun health(): OpenCodeHealth = client.health()
    override suspend fun listSessions(): List<OpenCodeSession> = client.sessions()
    override suspend fun createSession(title: String?): OpenCodeSession = client.createSession(title)
    override suspend fun listMessages(sessionId: String): List<OpenCodeMessage> = client.messages(sessionId)
    override suspend fun listProviders(): ProviderCatalog = client.providers()
    override suspend fun listAgents(): List<OpenCodeAgent> = client.agents()
    override suspend fun sendMessage(sessionId: String, request: PromptRequest) =
        client.promptAsync(sessionId, request)
    override suspend fun abortSession(sessionId: String): Boolean = client.abortSession(sessionId)
    override suspend fun respondToPermission(
        sessionId: String,
        permissionId: String,
        response: PermissionResponse,
        remember: Boolean
    ): Boolean = client.respondPermission(sessionId, permissionId, response.apiValue, remember)
    override fun events(): Flow<OpenCodeEvent> = client.events()
}
