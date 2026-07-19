package com.opencode.android.feature.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.android.core.api.OpenCodeApiClient
import com.opencode.android.core.api.OpenCodeHealth
import com.opencode.android.data.connection.ConnectionProfile
import com.opencode.android.data.repository.RuntimeCatalogRepository
import com.opencode.android.feature.connection.DiscoveredOpenCodeService
import com.opencode.android.feature.connection.OpenCodeNsdDiscovery
import com.opencode.android.runtime.LocalRuntimeStatus
import com.opencode.android.runtime.RuntimeRegistry
import com.opencode.android.runtime.RuntimeState
import com.opencode.android.runtime.RuntimeType
import com.opencode.android.runtime.WorkspaceRef
import com.opencode.android.runtime.local.LocalRuntimeManager
import com.opencode.android.runtime.local.LocalRuntimeServiceController
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    val discovered: List<DiscoveredOpenCodeService> = emptyList(),
    val isRefreshing: Boolean = false,
    val error: String? = null
)

sealed interface WorkspaceEvent {
    val message: String

    data class OpenEditor(val form: ConnectionFormState, override val message: String) : WorkspaceEvent
    data class Info(override val message: String) : WorkspaceEvent
}

class WorkspaceViewModel(
    private val registry: RuntimeRegistry,
    private val catalog: RuntimeCatalogRepository,
    private val localRuntimeManager: LocalRuntimeManager,
    private val localRuntimeController: LocalRuntimeServiceController,
    private val nsdDiscovery: OpenCodeNsdDiscovery? = null
) : ViewModel() {
    private val mutableDiscovered = MutableStateFlow<List<DiscoveredOpenCodeService>>(emptyList())
    private val mutableEvents = MutableSharedFlow<WorkspaceEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val events: SharedFlow<WorkspaceEvent> = mutableEvents.asSharedFlow()

    val state: StateFlow<WorkspaceUiState> = combine(
        registry.targets,
        registry.selected,
        catalog.state,
        localRuntimeManager.state,
        mutableDiscovered
    ) { targets, selected, runtime, localStatus, discovered ->
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
            discovered = discovered,
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
        nsdDiscovery?.let { discovery ->
            viewModelScope.launch {
                discovery.discover().collect { services ->
                    mutableDiscovered.value = services
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
        emit(WorkspaceEvent.Info("Saved ${form.name}"))
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

    fun applyQrOrLink(raw: String) {
        val parsed = com.opencode.android.feature.connection.OpenCodeConnectionQr.decode(raw)
        parsed.fold(
            onSuccess = { form ->
                val existing = registry.remoteProfiles().firstOrNull { it.baseUrl == form.normalizedUrl }
                val merged = if (existing != null) form.copy(id = existing.id) else form
                emit(WorkspaceEvent.OpenEditor(merged, "Recognized ${merged.baseUrl}"))
            },
            onFailure = { error ->
                emit(WorkspaceEvent.Info(error.message ?: "Could not parse QR or link"))
            }
        )
    }

    fun addDiscovered(service: DiscoveredOpenCodeService) {
        val profile = ConnectionProfile(
            id = java.util.UUID.randomUUID().toString(),
            name = service.name,
            baseUrl = service.baseUrl,
            username = "opencode",
            allowInsecureLan = true
        )
        registry.upsertRemote(profile)
        emit(WorkspaceEvent.Info("Added ${service.name}"))
    }

    private fun emit(event: WorkspaceEvent) {
        mutableEvents.tryEmit(event)
    }
}
