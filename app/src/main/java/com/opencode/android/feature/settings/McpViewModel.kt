package com.opencode.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import com.opencode.android.core.api.McpServer
import com.opencode.android.runtime.RuntimeRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class McpUiState(
    val servers: List<McpServer> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showAddDialog: Boolean = false,
    val addName: String = "",
    val addCommand: String = "",
    val addUrl: String = "",
    val isAdding: Boolean = false
)

class McpViewModel(
    private val registry: RuntimeRegistry
) : ViewModel() {
    private val _state = MutableStateFlow(McpUiState())
    val state: StateFlow<McpUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val backend = registry.selected.value ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { backend.mcpServers() }
                .onSuccess { servers ->
                    _state.update { it.copy(servers = servers, isLoading = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message, isLoading = false) }
                }
        }
    }

    fun connect(name: String) {
        val backend = registry.selected.value ?: return
        viewModelScope.launch {
            runCatching { backend.connectMcpServer(name) }
                .onSuccess { refresh() }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun disconnect(name: String) {
        val backend = registry.selected.value ?: return
        viewModelScope.launch {
            runCatching { backend.disconnectMcpServer(name) }
                .onSuccess { refresh() }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun removeAuth(name: String) {
        val backend = registry.selected.value ?: return
        viewModelScope.launch {
            runCatching { backend.removeMcpAuth(name) }
                .onSuccess { refresh() }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun showAddDialog() {
        _state.update { it.copy(showAddDialog = true, addName = "", addCommand = "", addUrl = "") }
    }

    fun dismissAddDialog() {
        _state.update { it.copy(showAddDialog = false) }
    }

    fun updateAddName(value: String) {
        _state.update { it.copy(addName = value) }
    }

    fun updateAddCommand(value: String) {
        _state.update { it.copy(addCommand = value) }
    }

    fun updateAddUrl(value: String) {
        _state.update { it.copy(addUrl = value) }
    }

    fun addServer() {
        val backend = registry.selected.value ?: return
        val current = _state.value
        if (current.addName.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isAdding = true) }
            val body = JsonObject().apply {
                addProperty("name", current.addName.trim())
                if (current.addUrl.isNotBlank()) {
                    addProperty("type", "remote")
                    addProperty("url", current.addUrl.trim())
                } else if (current.addCommand.isNotBlank()) {
                    addProperty("type", "local")
                    addProperty("command", current.addCommand.trim())
                }
            }
            runCatching { backend.addMcpServer(body) }
                .onSuccess {
                    _state.update { it.copy(showAddDialog = false, isAdding = false) }
                    refresh()
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message, isAdding = false) }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
