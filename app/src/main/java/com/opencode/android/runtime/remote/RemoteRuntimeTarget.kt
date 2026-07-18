package com.opencode.android.runtime.remote

import com.opencode.android.core.api.OpenCodeAgent
import com.opencode.android.core.api.OpenCodeEvent
import com.opencode.android.core.api.OpenCodeHealth
import com.opencode.android.core.api.OpenCodeMessage
import com.opencode.android.core.api.OpenCodeSession
import com.opencode.android.core.api.PromptRequest
import com.opencode.android.core.api.ProviderCatalog
import com.opencode.android.data.connection.ConnectionProfile
import com.opencode.android.runtime.BackendKind
import com.opencode.android.runtime.PermissionResponse
import com.opencode.android.runtime.RuntimeState
import com.opencode.android.runtime.RuntimeTarget
import com.opencode.android.runtime.RuntimeType
import com.opencode.android.runtime.WorkspaceRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RemoteRuntimeTarget(
    val profile: ConnectionProfile,
    private val backend: RemoteOpenCodeBackend = RemoteOpenCodeBackend(profile)
) : RuntimeTarget {
    override val id: String = profile.id
    override val displayName: String = profile.name
    override val type: RuntimeType = RuntimeType.REMOTE
    override val kind: BackendKind = BackendKind.REMOTE

    private val mutableState = MutableStateFlow<RuntimeState>(RuntimeState.Disconnected)
    override val state: StateFlow<RuntimeState> = mutableState.asStateFlow()

    override suspend fun connect(): Result<OpenCodeHealth> {
        mutableState.value = RuntimeState.Connecting
        return runCatching { backend.health() }
            .onSuccess { health ->
                mutableState.value = if (health.healthy) {
                    RuntimeState.Connected(health.version)
                } else {
                    RuntimeState.Failed("OpenCode reported an unhealthy server")
                }
            }
            .onFailure { error ->
                mutableState.value = RuntimeState.Failed(error.safeMessage())
            }
    }

    override fun disconnect() {
        mutableState.value = RuntimeState.Disconnected
    }

    override suspend fun listWorkspaces(): List<WorkspaceRef> = backend.listSessions()
        .mapNotNull { session ->
            session.directory?.takeIf { it.isNotBlank() }?.let { directory ->
                WorkspaceRef(
                    id = directory,
                    name = directory.substringAfterLast('/').ifBlank { directory },
                    path = directory
                )
            }
        }
        .distinctBy { it.path }

    override suspend fun health(): OpenCodeHealth = backend.health()
    override suspend fun listSessions(): List<OpenCodeSession> = backend.listSessions()
    override suspend fun createSession(title: String?): OpenCodeSession = backend.createSession(title)
    override suspend fun listMessages(sessionId: String): List<OpenCodeMessage> = backend.listMessages(sessionId)
    override suspend fun listProviders(): ProviderCatalog = backend.listProviders()
    override suspend fun listAgents(): List<OpenCodeAgent> = backend.listAgents()
    override suspend fun sendMessage(sessionId: String, request: PromptRequest) = backend.sendMessage(sessionId, request)
    override suspend fun abortSession(sessionId: String): Boolean = backend.abortSession(sessionId)
    override suspend fun respondToPermission(
        sessionId: String,
        permissionId: String,
        response: PermissionResponse,
        remember: Boolean
    ): Boolean = backend.respondToPermission(sessionId, permissionId, response, remember)

    override fun events(): Flow<OpenCodeEvent> = backend.events()

    private fun Throwable.safeMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: "OpenCode connection failed"
}
