package com.opencode.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.android.core.api.OpenCodeAgent
import com.opencode.android.core.api.OpenCodeProvider
import com.opencode.android.data.connection.SecureSettingsRepository
import com.opencode.android.data.repository.RuntimeCatalogRepository
import com.opencode.android.data.settings.AppPreferencesRepository
import com.opencode.android.runtime.RuntimeRegistry
import com.opencode.android.runtime.local.LocalProviderCredentialStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class SettingsUiState(
    val providers: List<OpenCodeProvider> = emptyList(),
    val agents: List<OpenCodeAgent> = emptyList(),
    val providerId: String? = null,
    val modelId: String? = null,
    val agentId: String? = null,
    val ttsEnabled: Boolean = true,
    val continuousConversation: Boolean = false,
    val credentialStatuses: Map<String, Boolean> = emptyMap(),
    val draftProviderId: String = "",
    val draftApiKey: String = "",
    val runtimeOptions: List<Pair<String, String>> = emptyList(),
    val assistantRuntimeId: String? = null,
    val assistantWorkspacePath: String? = null,
    val openCodeVersion: String? = null,
    val credentialMessage: String? = null
)

class SettingsViewModel(
    catalog: RuntimeCatalogRepository,
    private val preferences: AppPreferencesRepository,
    private val credentials: LocalProviderCredentialStore,
    private val settings: SecureSettingsRepository,
    registry: RuntimeRegistry
) : ViewModel() {
    private val credentialTick = MutableStateFlow(0)
    private val draftProviderId = MutableStateFlow("")
    private val draftApiKey = MutableStateFlow("")
    private val credentialMessage = MutableStateFlow<String?>(null)

    private data class DraftState(
        val providerId: String,
        val apiKey: String,
        val message: String?,
        val tick: Int
    )

    val state: StateFlow<SettingsUiState> = combine(
        combine(catalog.state, preferences.state, registry.targets, registry.selected) { runtime, prefs, targets, selected ->
            listOf(runtime, prefs, targets, selected)
        },
        combine(credentialTick, draftProviderId, draftApiKey, credentialMessage) { tick, provider, key, message ->
            DraftState(provider, key, message, tick)
        }
    ) { core, draft ->
        val runtime = core[0] as com.opencode.android.data.repository.RuntimeCatalogState
        val prefs = core[1] as com.opencode.android.data.settings.AppPreferences
        @Suppress("UNCHECKED_CAST")
        val targets = core[2] as List<com.opencode.android.runtime.RuntimeTarget>
        val selected = core[3] as com.opencode.android.runtime.RuntimeTarget?
        val connected = runtime.providers.connected.toSet()
        val providerList = runtime.providers.all.filter { it.id in connected }
        val keys = credentials.credentials()
        SettingsUiState(
            providers = providerList,
            agents = runtime.agents.filter { it.mode == null || it.mode == "primary" },
            providerId = prefs.providerId,
            modelId = prefs.modelId,
            agentId = prefs.agentId,
            ttsEnabled = prefs.ttsEnabled,
            continuousConversation = prefs.continuousConversation,
            credentialStatuses = (providerList.map { it.id } + keys.keys)
                .distinct()
                .associateWith { credentials.hasCredential(it) },
            draftProviderId = draft.providerId.ifBlank { prefs.providerId.orEmpty() },
            draftApiKey = draft.apiKey,
            runtimeOptions = targets.map { it.id to it.displayName },
            assistantRuntimeId = settings.assistantRuntimeId ?: selected?.id,
            assistantWorkspacePath = settings.assistantWorkspacePath,
            openCodeVersion = runtime.health?.version,
            credentialMessage = draft.message
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun selectModel(providerId: String, modelId: String) =
        preferences.selectModel(providerId, modelId)

    fun selectAgent(agentId: String) = preferences.selectAgent(agentId)
    fun setTtsEnabled(enabled: Boolean) = preferences.setTtsEnabled(enabled)
    fun setContinuousConversation(enabled: Boolean) = preferences.setContinuousConversation(enabled)

    fun updateDraftProviderId(value: String) {
        draftProviderId.value = value
    }

    fun updateDraftApiKey(value: String) {
        draftApiKey.value = value
    }

    fun saveApiKey() {
        val providerId = draftProviderId.value.ifBlank { state.value.providerId }.orEmpty()
        val key = draftApiKey.value
        if (providerId.isBlank() || key.isBlank()) {
            credentialMessage.value = "Provider id and API key are required"
            return
        }
        runCatching {
            credentials.setCredential(providerId, key)
            draftApiKey.value = ""
            credentialMessage.value = "API key saved for $providerId"
            credentialTick.update { it + 1 }
        }.onFailure { error ->
            credentialMessage.value = error.message ?: "Failed to save API key"
        }
    }

    fun clearApiKey(providerId: String) {
        credentials.clearCredential(providerId)
        credentialMessage.value = "API key cleared for $providerId"
        credentialTick.update { it + 1 }
    }

    fun setAssistantRuntimeId(runtimeId: String?) {
        settings.assistantRuntimeId = runtimeId
        credentialTick.update { it + 1 }
    }

    fun setAssistantWorkspacePath(path: String?) {
        settings.assistantWorkspacePath = path?.trim()?.ifBlank { null }
        credentialTick.update { it + 1 }
    }
}
