package com.opencode.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.android.core.api.OpenCodeAgent
import com.opencode.android.core.api.OpenCodeProvider
import com.opencode.android.data.repository.RuntimeCatalogRepository
import com.opencode.android.data.settings.AppPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SettingsUiState(
    val providers: List<OpenCodeProvider> = emptyList(),
    val agents: List<OpenCodeAgent> = emptyList(),
    val providerId: String? = null,
    val modelId: String? = null,
    val agentId: String? = null,
    val ttsEnabled: Boolean = true,
    val continuousConversation: Boolean = false
)

class SettingsViewModel(
    catalog: RuntimeCatalogRepository,
    private val preferences: AppPreferencesRepository
) : ViewModel() {
    val state: StateFlow<SettingsUiState> = combine(catalog.state, preferences.state) { runtime, prefs ->
        val connected = runtime.providers.connected.toSet()
        SettingsUiState(
            providers = runtime.providers.all.filter { it.id in connected },
            agents = runtime.agents.filter { it.mode == null || it.mode == "primary" },
            providerId = prefs.providerId,
            modelId = prefs.modelId,
            agentId = prefs.agentId,
            ttsEnabled = prefs.ttsEnabled,
            continuousConversation = prefs.continuousConversation
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun selectModel(providerId: String, modelId: String) =
        preferences.selectModel(providerId, modelId)

    fun selectAgent(agentId: String) = preferences.selectAgent(agentId)
    fun setTtsEnabled(enabled: Boolean) = preferences.setTtsEnabled(enabled)
    fun setContinuousConversation(enabled: Boolean) = preferences.setContinuousConversation(enabled)
}
