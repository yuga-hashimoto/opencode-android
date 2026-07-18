package com.opencode.android.runtime.local

import com.opencode.android.core.api.OpenCodeAgent
import com.opencode.android.core.api.OpenCodeEvent
import com.opencode.android.core.api.OpenCodeHealth
import com.opencode.android.core.api.OpenCodeMessage
import com.opencode.android.core.api.OpenCodeSession
import com.opencode.android.core.api.PromptRequest
import com.opencode.android.core.api.ProviderCatalog
import com.opencode.android.runtime.BackendKind
import com.opencode.android.runtime.LocalRuntimeStatus
import com.opencode.android.runtime.PermissionResponse
import com.opencode.android.runtime.RuntimeState
import com.opencode.android.runtime.RuntimeTarget
import com.opencode.android.runtime.RuntimeType
import com.opencode.android.runtime.WorkspaceRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocalRuntimeTarget(
    private val runtimeManager: LocalRuntimeManager,
    private val backend: LocalOpenCodeBackend = LocalOpenCodeBackend(runtimeManager)
) : RuntimeTarget {
    override val id: String = "local-android"
    override val displayName: String = "このAndroid端末"
    override val type: RuntimeType = RuntimeType.LOCAL
    override val kind: BackendKind = BackendKind.LOCAL

    private val mutableState = MutableStateFlow(mapStatus(runtimeManager.status()))
    override val state: StateFlow<RuntimeState> = mutableState.asStateFlow()

    fun refreshLocalState(): RuntimeState = mapStatus(runtimeManager.status()).also {
        mutableState.value = it
    }

    override suspend fun connect(): Result<OpenCodeHealth> {
        val localStatus = runtimeManager.status()
        if (localStatus !is LocalRuntimeStatus.Ready) {
            val state = mapStatus(localStatus)
            mutableState.value = state
            return Result.failure(IllegalStateException(state.describe()))
        }

        mutableState.value = RuntimeState.Connecting
        return runCatching { backend.health() }
            .onSuccess { health ->
                mutableState.value = if (health.healthy) {
                    RuntimeState.Connected(health.version)
                } else {
                    RuntimeState.Failed("ローカルOpenCodeが正常状態を返しませんでした")
                }
            }
            .onFailure { error ->
                mutableState.value = RuntimeState.Failed(error.message ?: "ローカルOpenCodeへ接続できません")
            }
    }

    override fun disconnect() {
        mutableState.value = mapStatus(runtimeManager.status())
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

    private fun mapStatus(status: LocalRuntimeStatus): RuntimeState = when (status) {
        LocalRuntimeStatus.NotInstalled -> RuntimeState.Unavailable("ローカルランタイムは未インストールです")
        is LocalRuntimeStatus.UnsupportedAbi -> RuntimeState.Unavailable("未対応ABI: ${status.abi}")
        is LocalRuntimeStatus.Installing -> RuntimeState.Connecting
        is LocalRuntimeStatus.Broken -> RuntimeState.Failed(status.reason)
        is LocalRuntimeStatus.Ready -> RuntimeState.Disconnected
    }

    private fun RuntimeState.describe(): String = when (this) {
        RuntimeState.Disconnected -> "ローカルランタイムは停止しています"
        RuntimeState.Connecting -> "ローカルランタイムへ接続中です"
        is RuntimeState.Connected -> "OpenCode $version"
        is RuntimeState.Unavailable -> reason
        is RuntimeState.Failed -> message
    }
}
