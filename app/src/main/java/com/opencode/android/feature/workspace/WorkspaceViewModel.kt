package com.opencode.android.feature.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.android.core.api.OpenCodeApiClient
import com.opencode.android.core.api.OpenCodeHealth
import com.opencode.android.data.connection.ConnectionProfile
import com.opencode.android.data.repository.RuntimeCatalogRepository
import com.opencode.android.runtime.LocalRuntimeStatus
import com.opencode.android.runtime.RuntimeRegistry
import com.opencode.android.runtime.RuntimeState
import com.opencode.android.runtime.RuntimeType
import com.opencode.android.runtime.WorkspaceRef
import com.opencode.android.runtime.local.LocalRuntimeManager
import com.opencode.android.runtime.local.LocalRuntimeServiceController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RuntimeSummary(
    val id: String,
    val name: String,
    val type: RuntimeType,
    val state: RuntimeState,
    val selected: Boolean
)

data class WorkspaceUiState(
    val targets: List<RuntimeSummary> = emptyList(),
    val connections: List<ConnectionProfile> = emptyList(),
    val selectedRuntimeId: String? = null,
    val workspaces: List<WorkspaceRef> = emptyList(),
    val localStatus: LocalRuntimeStatus = LocalRuntimeStatus.NotInstalled,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

class WorkspaceViewModel(
    private val registry: RuntimeRegistry,
    private val catalog: RuntimeCatalogRepository,
    private val localRuntimeManager: LocalRuntimeManager,
    private val localRuntimeController: LocalRuntimeServiceController
) : ViewModel() {
    val state: StateFlow<WorkspaceUiState> = combine(
        registry.targets,
        registry.selected,
        catalog.state,
        localRuntimeManager.state
    ) { targets, selected, runtime, localStatus ->
        WorkspaceUiState(
            targets = targets.map { target ->
                RuntimeSummary(
                    id = target.id,
                    name = target.displayName,
                    type = target.type,
                    state = target.state.value,
                    selected = target.id == selected?.id
                )
            },
            connections = registry.remoteProfiles(),
            selectedRuntimeId = selected?.id,
            workspaces = runtime.workspaces,
            localStatus = localStatus,
            isRefreshing = runtime.isRefreshing,
            error = runtime.error
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WorkspaceUiState())

    init {
        viewModelScope.launch {
            localRuntimeManager.state.collect { status ->
                if (status is LocalRuntimeStatus.Ready) {
                    registry.select("local-android")
                    catalog.refresh()
                }
            }
        }
    }

    fun selectRuntime(id: String) {
        registry.select(id)
    }

    fun saveConnection(form: ConnectionFormState) {
        if (!form.canSave) return
        registry.upsertRemote(form.toProfile())
    }

    fun deleteConnection(id: String) {
        registry.deleteRemote(id)
    }

    suspend fun testConnection(form: ConnectionFormState): Result<OpenCodeHealth> {
        if (!form.canSave) {
            return Result.failure(IllegalArgumentException("接続情報が不足しています"))
        }
        return runCatching { OpenCodeApiClient(form.toProfile()).health() }
    }

    fun setupLocalRuntime() = localRuntimeController.installAndStart()

    fun startLocalRuntime() = localRuntimeController.start()

    fun stopLocalRuntime() = localRuntimeController.stop()

    fun reinstallLocalRuntime() = localRuntimeController.reinstall()

    fun refresh() {
        registry.refresh()
        catalog.refresh()
    }
}
