package com.opencode.android.runtime.local

import com.opencode.android.core.api.OpenCodeAgent
import com.opencode.android.core.api.OpenCodeEvent
import com.opencode.android.core.api.OpenCodeHealth
import com.opencode.android.core.api.OpenCodeMessage
import com.opencode.android.core.api.OpenCodeSession
import com.opencode.android.core.api.PromptRequest
import com.opencode.android.core.api.ProviderCatalog
import com.opencode.android.data.connection.ConnectionProfile
import com.opencode.android.runtime.BackendKind
import com.opencode.android.runtime.LocalRuntimeStatus
import com.opencode.android.runtime.OpenCodeBackend
import com.opencode.android.runtime.PermissionResponse
import com.opencode.android.runtime.remote.RemoteOpenCodeBackend
import kotlinx.coroutines.flow.Flow

class LocalOpenCodeBackend(
    private val runtimeManager: LocalRuntimeManager
) : OpenCodeBackend {
    override val id: String = "local-android"
    override val displayName: String = "Android local"
    override val kind: BackendKind = BackendKind.LOCAL

    private fun delegate(): RemoteOpenCodeBackend {
        val status = runtimeManager.status()
        require(status is LocalRuntimeStatus.Ready) {
            "Android local OpenCode runtime is not ready: $status"
        }
        return RemoteOpenCodeBackend(
            ConnectionProfile(
                id = id,
                name = displayName,
                baseUrl = "http://127.0.0.1:${status.port}/",
                username = "opencode",
                allowInsecureLan = true
            )
        )
    }

    override suspend fun health(): OpenCodeHealth = delegate().health()
    override suspend fun listSessions(): List<OpenCodeSession> = delegate().listSessions()
    override suspend fun createSession(title: String?): OpenCodeSession = delegate().createSession(title)
    override suspend fun listMessages(sessionId: String): List<OpenCodeMessage> = delegate().listMessages(sessionId)
    override suspend fun listProviders(): ProviderCatalog = delegate().listProviders()
    override suspend fun listAgents(): List<OpenCodeAgent> = delegate().listAgents()
    override suspend fun sendMessage(sessionId: String, request: PromptRequest) = delegate().sendMessage(sessionId, request)
    override suspend fun abortSession(sessionId: String): Boolean = delegate().abortSession(sessionId)
    override suspend fun respondToPermission(
        sessionId: String,
        permissionId: String,
        response: PermissionResponse,
        remember: Boolean
    ): Boolean = delegate().respondToPermission(sessionId, permissionId, response, remember)
    override fun events(): Flow<OpenCodeEvent> = delegate().events()
}
