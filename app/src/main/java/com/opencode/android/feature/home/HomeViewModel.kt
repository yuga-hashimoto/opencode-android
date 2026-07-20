package com.opencode.android.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.android.core.api.OpenCodeSession
import com.opencode.android.data.repository.RuntimeCatalogRepository
import com.opencode.android.data.settings.AppPreferencesRepository
import com.opencode.android.runtime.RuntimeState
import com.opencode.android.runtime.RuntimeType
import com.opencode.android.runtime.WorkspaceRef
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val runtimeId: String? = null,
    val runtimeName: String = "",
    val runtimeType: RuntimeType? = null,
    val runtimeState: RuntimeState = RuntimeState.Disconnected,
    val version: String? = null,
    val workspace: WorkspaceRef? = null,
    val sessions: List<OpenCodeSession> = emptyList(),
    val providerId: String? = null,
    val modelId: String? = null,
    val agentId: String? = null,
    val isRefreshing: Boolean = false,
    val error: String? = null
) {
    val connected: Boolean get() = version != null
}

class HomeViewModel(
    private val catalog: RuntimeCatalogRepository,
    preferences: AppPreferencesRepository
) : ViewModel() {
    val state: StateFlow<HomeUiState> = combine(catalog.state, preferences.state) { runtime, prefs ->
        HomeUiState(
            runtimeId = runtime.runtime?.id,
            runtimeName = runtime.runtime?.displayName.orEmpty(),
            runtimeType = runtime.runtime?.type,
            runtimeState = runtime.runtime?.state?.value ?: RuntimeState.Disconnected,
            version = runtime.health?.version,
            workspace = runtime.workspaces.firstOrNull(),
            sessions = runtime.sessions.sortedByDescending { it.time.updated ?: it.time.created },
            providerId = prefs.providerId,
            modelId = prefs.modelId,
            agentId = prefs.agentId,
            isRefreshing = runtime.isRefreshing,
            error = runtime.error
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        HomeUiState()
    )

    fun refresh() = catalog.refresh()
}
