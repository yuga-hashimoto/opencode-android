package com.opencode.android.runtime

import com.opencode.android.core.api.OpenCodeHealth
import com.opencode.android.core.api.OpenCodeProject
import com.opencode.android.core.api.OpenCodeSession
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

internal fun mergeWorkspaceRefs(
    currentDirectory: String?,
    sessions: List<OpenCodeSession>,
    projects: List<OpenCodeProject>
): List<WorkspaceRef> {
    val workspaces = linkedMapOf<String, WorkspaceRef>()

    fun add(path: String?, preferredName: String? = null) {
        val normalized = path?.trim()?.takeIf { it.isNotEmpty() && it != "/" } ?: return
        val fallbackName = normalized
            .trimEnd('/', '\\')
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .ifBlank { normalized }
        val existing = workspaces[normalized]
        val name = preferredName?.trim()?.takeIf(String::isNotEmpty)
            ?: existing?.name
            ?: fallbackName
        workspaces[normalized] = WorkspaceRef(
            id = normalized,
            name = name,
            path = normalized
        )
    }

    add(currentDirectory)
    sessions.forEach { add(it.directory) }
    projects
        .asSequence()
        .filterNot { it.id == "global" }
        .forEach { add(it.worktree, it.name) }

    return workspaces.values.toList()
}

internal fun mergeSessionLists(
    sessionLists: List<List<OpenCodeSession>>
): List<OpenCodeSession> {
    val byId = linkedMapOf<String, OpenCodeSession>()
    sessionLists.flatten().forEach { session ->
        val existing = byId[session.id]
        val sessionUpdatedAt = session.time.updated ?: session.time.created
        val existingUpdatedAt = existing?.let { it.time.updated ?: it.time.created } ?: Long.MIN_VALUE
        if (existing == null || sessionUpdatedAt >= existingUpdatedAt) {
            byId[session.id] = session
        }
    }
    return byId.values.sortedByDescending { it.time.updated ?: it.time.created }
}

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
