package com.opencode.android.feature.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.android.core.api.OpenCodeSession
import com.opencode.android.core.api.PermissionRequest
import com.opencode.android.data.repository.RuntimeActivityRepository
import com.opencode.android.data.repository.RuntimeCatalogRepository
import com.opencode.android.data.repository.RuntimeEventLog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ActivityUiState(
    val sessions: List<OpenCodeSession> = emptyList(),
    val activeSessionIds: Set<String> = emptySet(),
    val permissions: List<PermissionRequest> = emptyList(),
    val logs: List<RuntimeEventLog> = emptyList(),
    val isRefreshing: Boolean = false,
    val error: String? = null
)

class ActivityViewModel(
    private val catalog: RuntimeCatalogRepository,
    activity: RuntimeActivityRepository
) : ViewModel() {
    val state: StateFlow<ActivityUiState> = combine(catalog.state, activity.state) { runtime, events ->
        ActivityUiState(
            sessions = runtime.sessions.sortedByDescending { it.time.updated ?: it.time.created },
            activeSessionIds = events.activeSessionIds,
            permissions = events.permissions,
            logs = events.logs,
            isRefreshing = runtime.isRefreshing,
            error = events.streamError ?: runtime.error
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ActivityUiState())

    fun refresh() = catalog.refresh()
}
