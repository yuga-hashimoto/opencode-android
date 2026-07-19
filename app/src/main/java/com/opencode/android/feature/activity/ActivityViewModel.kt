package com.opencode.android.feature.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.android.core.api.OpenCodeSession
import com.opencode.android.core.api.PermissionRequest
import com.opencode.android.data.repository.RuntimeActivityRepository
import com.opencode.android.data.repository.RuntimeCatalogRepository
import com.opencode.android.data.repository.RuntimeEventLog
import com.opencode.android.runtime.PermissionResponse
import com.opencode.android.runtime.RuntimeRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActivityUiState(
    val sessions: List<OpenCodeSession> = emptyList(),
    val activeSessionIds: Set<String> = emptySet(),
    val permissions: List<PermissionRequest> = emptyList(),
    val logs: List<RuntimeEventLog> = emptyList(),
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val permissionBusyIds: Set<String> = emptySet()
)

class ActivityViewModel(
    private val catalog: RuntimeCatalogRepository,
    private val activity: RuntimeActivityRepository,
    private val registry: RuntimeRegistry,
    private val onPermissionResolved: (String) -> Unit = {}
) : ViewModel() {
    private val permissionBusyIds = MutableStateFlow<Set<String>>(emptySet())
    private val actionError = MutableStateFlow<String?>(null)

    val state: StateFlow<ActivityUiState> = combine(
        catalog.state,
        activity.state,
        permissionBusyIds,
        actionError
    ) { runtime, events, busy, actionErr ->
        ActivityUiState(
            sessions = runtime.sessions.sortedByDescending { it.time.updated ?: it.time.created },
            activeSessionIds = events.activeSessionIds,
            permissions = events.permissions,
            logs = events.logs,
            isRefreshing = runtime.isRefreshing,
            error = actionErr ?: events.streamError ?: runtime.error,
            permissionBusyIds = busy
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ActivityUiState())

    fun refresh() = catalog.refresh()

    fun respondToPermission(
        permissionId: String,
        response: PermissionResponse,
        remember: Boolean
    ) {
        val permission = activity.state.value.permissions.firstOrNull { it.id == permissionId } ?: return
        val backend = registry.selected.value ?: return
        permissionBusyIds.update { it + permissionId }
        actionError.value = null
        viewModelScope.launch {
            runCatching {
                backend.respondToPermission(
                    permission.sessionId,
                    permission.id,
                    response,
                    remember
                )
            }.onSuccess { accepted ->
                if (accepted) {
                    activity.resolvePermission(permissionId)
                    onPermissionResolved(permissionId)
                } else {
                    actionError.value = "Permission response was not accepted"
                }
            }.onFailure { error ->
                actionError.value = error.message ?: "Permission response failed"
            }
            permissionBusyIds.update { it - permissionId }
        }
    }

    fun renameSession(sessionId: String, title: String) {
        val backend = registry.selected.value ?: return
        actionError.value = null
        viewModelScope.launch {
            runCatching { backend.renameSession(sessionId, title) }
                .onSuccess { catalog.refresh() }
                .onFailure { error ->
                    actionError.value = error.message ?: "Failed to rename session"
                }
        }
    }

    fun deleteSession(sessionId: String) {
        val backend = registry.selected.value ?: return
        actionError.value = null
        viewModelScope.launch {
            runCatching { backend.deleteSession(sessionId) }
                .onSuccess { catalog.refresh() }
                .onFailure { error ->
                    actionError.value = error.message ?: "Failed to delete session"
                }
        }
    }
}
