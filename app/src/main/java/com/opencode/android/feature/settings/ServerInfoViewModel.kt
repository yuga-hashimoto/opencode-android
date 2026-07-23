package com.opencode.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonElement
import com.opencode.android.core.api.ConfiguredProvider
import com.opencode.android.core.api.OpenCodeCommand
import com.opencode.android.core.api.OpenCodeSkill
import com.opencode.android.runtime.RuntimeRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServerInfoUiState(
    val configJson: String? = null,
    val configProviders: List<ConfiguredProvider> = emptyList(),
    val commands: List<OpenCodeCommand> = emptyList(),
    val skills: List<OpenCodeSkill> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val configEditDraft: String? = null,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false
)

class ServerInfoViewModel(
    private val registry: RuntimeRegistry
) : ViewModel() {
    private val _state = MutableStateFlow(ServerInfoUiState())
    val state: StateFlow<ServerInfoUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val backend = registry.selected.value ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val config = backend.config()
                val providers = runCatching { backend.configProviders() }.getOrDefault(emptyList())
                val commands = runCatching { backend.commands() }.getOrDefault(emptyList())
                val skills = runCatching { backend.skills() }.getOrDefault(emptyList())
                config to Triple(providers, commands, skills)
            }.onSuccess { (config, data) ->
                val (providers, commands, skills) = data
                _state.update {
                    it.copy(
                        configJson = config.toString(),
                        configProviders = providers,
                        commands = commands,
                        skills = skills,
                        isLoading = false
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun startEditConfig() {
        _state.update { it.copy(configEditDraft = it.configJson) }
    }

    fun updateConfigDraft(value: String) {
        _state.update { it.copy(configEditDraft = value) }
    }

    fun cancelEdit() {
        _state.update { it.copy(configEditDraft = null) }
    }

    fun saveConfig() {
        val backend = registry.selected.value ?: return
        val draft = _state.value.configEditDraft ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            runCatching {
                val patch = com.google.gson.JsonParser.parseString(draft).asJsonObject
                backend.updateConfig(patch)
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        configJson = result.toString(),
                        configEditDraft = null,
                        isSaving = false,
                        saveSuccess = true
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message, isSaving = false) }
            }
        }
    }

    fun consumeSaveSuccess() {
        _state.update { it.copy(saveSuccess = false) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
