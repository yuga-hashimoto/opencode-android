package com.opencode.android.runtime

import com.opencode.android.core.api.OpenCodeHealth
import kotlinx.coroutines.flow.StateFlow

enum class RuntimeType {
    LOCAL,
    REMOTE
}

sealed interface RuntimeState {
    data object Disconnected : RuntimeState
    data object Connecting : RuntimeState
    data class Connected(val version: String) : RuntimeState
    data class Unavailable(val reason: String) : RuntimeState
    data class Failed(val message: String) : RuntimeState
}

data class WorkspaceRef(
    val id: String,
    val name: String,
    val path: String
)

interface RuntimeTarget : OpenCodeBackend {
    val type: RuntimeType
    val state: StateFlow<RuntimeState>

    suspend fun connect(): Result<OpenCodeHealth>
    fun disconnect()
    suspend fun listWorkspaces(): List<WorkspaceRef>
}

interface RuntimeConnectionStore {
    var selectedRuntimeId: String?

    fun connections(): List<com.opencode.android.data.connection.ConnectionProfile>
    fun upsertConnection(profile: com.opencode.android.data.connection.ConnectionProfile)
    fun deleteConnection(id: String)
}
