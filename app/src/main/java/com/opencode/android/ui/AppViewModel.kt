package com.opencode.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.android.OpenCodeApplication
import com.opencode.android.core.api.OpenCodeAgent
import com.opencode.android.core.api.OpenCodeApiClient
import com.opencode.android.core.api.OpenCodeHealth
import com.opencode.android.core.api.OpenCodeProvider
import com.opencode.android.core.api.OpenCodeSession
import com.opencode.android.runtime.OpenCodeBackend
import com.opencode.android.data.connection.ConnectionProfile
import com.opencode.android.feature.workspace.ConnectionFormState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppUiState(
    val connections: List<ConnectionProfile> = emptyList(),
    val selectedConnectionId: String? = null,
    val backend: OpenCodeBackend? = null,
    val health: OpenCodeHealth? = null,
    val sessions: List<OpenCodeSession> = emptyList(),
    val providers: List<OpenCodeProvider> = emptyList(),
    val connectedProviderIds: Set<String> = emptySet(),
    val agents: List<OpenCodeAgent> = emptyList(),
    val selectedProviderId: String? = null,
    val selectedModelId: String? = null,
    val selectedAgentId: String? = null,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val ttsEnabled: Boolean = true,
    val continuousConversation: Boolean = false
) {
    val selectedConnection: ConnectionProfile?
        get() = connections.firstOrNull { it.id == selectedConnectionId }

    val selectedProvider: OpenCodeProvider?
        get() = providers.firstOrNull { it.id == selectedProviderId }
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as OpenCodeApplication
    private val repository = app.repository
    private val settings = app.settings

    private val _uiState = MutableStateFlow(
        AppUiState(
            connections = repository.connections(),
            selectedConnectionId = settings.selectedConnectionId,
            backend = repository.selectedBackend.value,
            selectedProviderId = settings.selectedProviderId,
            selectedModelId = settings.selectedModelId,
            selectedAgentId = settings.selectedAgentId,
            ttsEnabled = settings.ttsEnabled,
            continuousConversation = settings.continuousConversation
        )
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.selectedBackend.collect { backend ->
                _uiState.update {
                    it.copy(
                        connections = repository.connections(),
                        selectedConnectionId = settings.selectedConnectionId,
                        backend = backend,
                        health = null,
                        sessions = emptyList(),
                        providers = emptyList(),
                        connectedProviderIds = emptySet(),
                        agents = emptyList(),
                        error = null
                    )
                }
                refresh()
            }
        }
    }

    fun refresh() {
        val backend = _uiState.value.backend ?: run {
            _uiState.update { it.copy(isRefreshing = false) }
            return
        }
        if (_uiState.value.isRefreshing) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            runCatching {
                coroutineScope {
                    val health = async { backend.health() }
                    val sessions = async { backend.listSessions() }
                    val providers = async { backend.listProviders() }
                    val agents = async { backend.listAgents() }
                    DashboardData(
                        health = health.await(),
                        sessions = sessions.await(),
                        providers = providers.await(),
                        agents = agents.await()
                    )
                }
            }.onSuccess { data ->
                val connected = data.providers.connected.toSet()
                val availableProviders = data.providers.all
                    .filter { it.id in connected }
                    .sortedBy { it.name.lowercase() }
                val providerId = resolveProviderId(
                    configured = settings.selectedProviderId,
                    connectedIds = connected,
                    providers = availableProviders
                )
                val modelId = resolveModelId(
                    configured = settings.selectedModelId,
                    providerId = providerId,
                    catalogDefaults = data.providers.default,
                    providers = availableProviders
                )
                val agentId = settings.selectedAgentId
                    ?.takeIf { configured -> data.agents.any { it.name == configured } }
                    ?: data.agents.firstOrNull { it.name == "build" }?.name
                    ?: data.agents.firstOrNull()?.name

                settings.selectedProviderId = providerId
                settings.selectedModelId = modelId
                settings.selectedAgentId = agentId

                _uiState.update {
                    it.copy(
                        health = data.health,
                        sessions = data.sessions.sortedByDescending { session -> session.time.updated ?: session.time.created },
                        providers = availableProviders,
                        connectedProviderIds = connected,
                        agents = data.agents.filter { agent -> agent.mode == null || agent.mode == "primary" },
                        selectedProviderId = providerId,
                        selectedModelId = modelId,
                        selectedAgentId = agentId,
                        isRefreshing = false,
                        error = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isRefreshing = false, error = error.safeMessage())
                }
            }
        }
    }

    suspend fun testConnection(form: ConnectionFormState): Result<OpenCodeHealth> {
        if (!form.canSave) return Result.failure(IllegalArgumentException("Connection settings are incomplete"))
        return runCatching { OpenCodeApiClient(form.toProfile()).health() }
    }

    fun saveConnection(form: ConnectionFormState) {
        if (!form.canSave) return
        repository.upsertConnection(form.toProfile())
        repository.selectConnection(form.id)
        _uiState.update {
            it.copy(
                connections = repository.connections(),
                selectedConnectionId = form.id,
                backend = repository.selectedBackend.value
            )
        }
    }

    fun deleteConnection(id: String) {
        repository.deleteConnection(id)
        _uiState.update {
            it.copy(
                connections = repository.connections(),
                selectedConnectionId = settings.selectedConnectionId,
                backend = repository.selectedBackend.value
            )
        }
    }

    fun selectConnection(id: String) {
        repository.selectConnection(id)
        _uiState.update {
            it.copy(
                selectedConnectionId = id,
                backend = repository.selectedBackend.value
            )
        }
    }

    fun selectModel(providerId: String, modelId: String) {
        settings.selectedProviderId = providerId
        settings.selectedModelId = modelId
        _uiState.update {
            it.copy(selectedProviderId = providerId, selectedModelId = modelId)
        }
    }

    fun selectAgent(agentId: String) {
        settings.selectedAgentId = agentId
        _uiState.update { it.copy(selectedAgentId = agentId) }
    }

    fun setTtsEnabled(enabled: Boolean) {
        settings.ttsEnabled = enabled
        _uiState.update { it.copy(ttsEnabled = enabled) }
    }

    fun setContinuousConversation(enabled: Boolean) {
        settings.continuousConversation = enabled
        _uiState.update { it.copy(continuousConversation = enabled) }
    }

    private fun resolveProviderId(
        configured: String?,
        connectedIds: Set<String>,
        providers: List<OpenCodeProvider>
    ): String? = configured?.takeIf { it in connectedIds }
        ?: "opencode".takeIf { it in connectedIds }
        ?: providers.firstOrNull()?.id

    private fun resolveModelId(
        configured: String?,
        providerId: String?,
        catalogDefaults: Map<String, String>,
        providers: List<OpenCodeProvider>
    ): String? {
        val provider = providers.firstOrNull { it.id == providerId } ?: return null
        return configured?.takeIf { it in provider.models }
            ?: catalogDefaults[provider.id]?.takeIf { it in provider.models }
            ?: provider.models.values.firstOrNull()?.id
    }

    private fun Throwable.safeMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: "OpenCode connection failed"

    private data class DashboardData(
        val health: OpenCodeHealth,
        val sessions: List<OpenCodeSession>,
        val providers: com.opencode.android.core.api.ProviderCatalog,
        val agents: List<OpenCodeAgent>
    )
}
