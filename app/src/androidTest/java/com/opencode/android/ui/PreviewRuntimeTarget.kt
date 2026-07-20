package com.opencode.android.ui

import com.opencode.android.core.api.OpenCodeAgent
import com.opencode.android.core.api.OpenCodeEvent
import com.opencode.android.core.api.OpenCodeHealth
import com.opencode.android.core.api.OpenCodeMessage
import com.opencode.android.core.api.OpenCodeSession
import com.opencode.android.core.api.PromptRequest
import com.opencode.android.core.api.ProviderCatalog
import com.opencode.android.runtime.BackendKind
import com.opencode.android.runtime.PermissionResponse
import com.opencode.android.runtime.RuntimeState
import com.opencode.android.runtime.RuntimeTarget
import com.opencode.android.runtime.RuntimeType
import com.opencode.android.runtime.WorkspaceRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

internal class PreviewRuntimeTarget(
    override val id: String,
    override val displayName: String,
    override val type: RuntimeType
) : RuntimeTarget {
    override val kind: BackendKind = if (type == RuntimeType.LOCAL) BackendKind.LOCAL else BackendKind.REMOTE
    private val mutableState = MutableStateFlow<RuntimeState>(RuntimeState.Connected("1.0.0"))
    override val state: StateFlow<RuntimeState> = mutableState

    override suspend fun connect(): Result<OpenCodeHealth> = Result.success(health())
    override fun disconnect() = Unit
    override suspend fun listWorkspaces(): List<WorkspaceRef> = emptyList()
    override suspend fun health(): OpenCodeHealth = OpenCodeHealth(healthy = true, version = "1.0.0")
    override suspend fun listSessions(directory: String?): List<OpenCodeSession> = emptyList()
    override suspend fun createSession(title: String?, directory: String?): OpenCodeSession =
        OpenCodeSession(id = "preview", title = title.orEmpty(), directory = directory)
    override suspend fun listMessages(sessionId: String): List<OpenCodeMessage> = emptyList()
    override suspend fun listProviders(): ProviderCatalog = ProviderCatalog()
    override suspend fun listAgents(): List<OpenCodeAgent> = emptyList()
    override suspend fun sendMessage(sessionId: String, request: PromptRequest) = Unit
    override suspend fun abortSession(sessionId: String): Boolean = true
    override suspend fun respondToPermission(
        sessionId: String,
        permissionId: String,
        response: PermissionResponse,
        remember: Boolean
    ): Boolean = true
    override fun events(): Flow<OpenCodeEvent> = emptyFlow()
}
