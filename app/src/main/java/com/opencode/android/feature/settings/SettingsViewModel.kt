package com.opencode.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.android.core.api.OpenCodeAgent
import com.opencode.android.core.api.OpenCodeProvider
import com.opencode.android.core.api.ProviderAuthAuthorization
import com.opencode.android.core.api.ProviderAuthMethod
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
import kotlinx.coroutines.launch

data class SettingsUiState(
    val providers: List<OpenCodeProvider> = emptyList(),
    val agents: List<OpenCodeAgent> = emptyList(),
    val assistantProviders: List<OpenCodeProvider> = emptyList(),
    val assistantAgents: List<OpenCodeAgent> = emptyList(),
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
    val assistantProviderId: String? = null,
    val assistantModelId: String? = null,
    val assistantAgentId: String? = null,
    val isLoadingAssistantCatalog: Boolean = false,
    val assistantCatalogError: String? = null,
    val openCodeVersion: String? = null,
    val credentialMessage: String? = null,
    val recentModels: List<Pair<String, String>> = emptyList(),
    val autoAllowReadOnlyTools: Boolean = false,
    val themeMode: String? = null,
    val dynamicColorEnabled: Boolean = false,
    val providerAuthMethods: Map<String, List<ProviderAuthMethod>> = emptyMap(),
    val oauthProviderId: String? = null,
    val oauthMethodIndex: Int? = null,
    val oauthAuthorization: ProviderAuthAuthorization? = null,
    val oauthMessage: String? = null
)

class SettingsViewModel(
    catalog: RuntimeCatalogRepository,
    private val preferences: AppPreferencesRepository,
    private val credentials: LocalProviderCredentialStore,
    private val settings: SecureSettingsRepository,
    private val registry: RuntimeRegistry
) : ViewModel() {
    private val credentialTick = MutableStateFlow(0)
    private val draftProviderId = MutableStateFlow("")
    private val draftApiKey = MutableStateFlow("")
    private val credentialMessage = MutableStateFlow<String?>(null)
    private val assistantCatalog = MutableStateFlow(AssistantCatalogState())
    private val oauthState = MutableStateFlow(OAuthState())

    private data class AssistantCatalogState(
        val runtimeId: String? = null,
        val providers: List<OpenCodeProvider> = emptyList(),
        val agents: List<OpenCodeAgent> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null
    )

    private data class DraftState(
        val providerId: String,
        val apiKey: String,
        val message: String?,
        val tick: Int,
        val assistantCatalog: AssistantCatalogState
    )

    private data class OAuthState(
        val methods: Map<String, List<ProviderAuthMethod>> = emptyMap(),
        val providerId: String? = null,
        val methodIndex: Int? = null,
        val authorization: ProviderAuthAuthorization? = null,
        val message: String? = null
    )

    init {
        viewModelScope.launch {
            registry.targets.collect {
                refreshAssistantCatalog()
            }
        }
        viewModelScope.launch {
            registry.selected.collect {
                refreshProviderAuth()
            }
        }
    }

    val state: StateFlow<SettingsUiState> = combine(
        combine(catalog.state, preferences.state, registry.targets, registry.selected) { runtime, prefs, targets, selected ->
            listOf(runtime, prefs, targets, selected)
        },
        combine(
            credentialTick,
            draftProviderId,
            draftApiKey,
            credentialMessage,
            assistantCatalog
        ) { tick, provider, key, message, assistant ->
            DraftState(provider, key, message, tick, assistant)
        },
        oauthState
    ) { core, draft, oauth ->
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
            assistantProviders = draft.assistantCatalog.providers,
            assistantAgents = draft.assistantCatalog.agents,
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
            assistantProviderId = settings.assistantProviderId ?: prefs.providerId,
            assistantModelId = settings.assistantModelId ?: prefs.modelId,
            assistantAgentId = settings.assistantAgentId ?: prefs.agentId,
            isLoadingAssistantCatalog = draft.assistantCatalog.isLoading,
            assistantCatalogError = draft.assistantCatalog.error,
            openCodeVersion = runtime.health?.version,
            credentialMessage = draft.message,
            recentModels = prefs.recentModels,
            autoAllowReadOnlyTools = settings.autoAllowReadOnlyTools,
            themeMode = prefs.themeMode,
            dynamicColorEnabled = prefs.dynamicColorEnabled,
            providerAuthMethods = oauth.methods,
            oauthProviderId = oauth.providerId,
            oauthMethodIndex = oauth.methodIndex,
            oauthAuthorization = oauth.authorization,
            oauthMessage = oauth.message
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun selectModel(providerId: String, modelId: String) =
        preferences.selectModel(providerId, modelId)

    fun selectAgent(agentId: String) = preferences.selectAgent(agentId)
    fun setTtsEnabled(enabled: Boolean) = preferences.setTtsEnabled(enabled)
    fun setContinuousConversation(enabled: Boolean) = preferences.setContinuousConversation(enabled)

    fun setAutoAllowReadOnlyTools(enabled: Boolean) {
        settings.autoAllowReadOnlyTools = enabled
        credentialTick.update { it + 1 }
    }

    fun setThemeMode(mode: String?) = preferences.setThemeMode(mode)
    fun setDynamicColorEnabled(enabled: Boolean) = preferences.setDynamicColorEnabled(enabled)
    fun replayOnboarding() = preferences.resetOnboarding()

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

    fun refreshProviderAuth() {
        val target = registry.selected.value ?: return
        viewModelScope.launch {
            runCatching { target.providerAuthMethods() }
                .onSuccess { methods ->
                    oauthState.update { it.copy(methods = methods, message = null) }
                }
                .onFailure { error ->
                    oauthState.update {
                        it.copy(
                            methods = emptyMap(),
                            message = error.message?.takeIf(String::isNotBlank)
                                ?: "Provider authentication methods are unavailable"
                        )
                    }
                }
        }
    }

    fun startOAuth(providerId: String, methodIndex: Int) {
        val target = registry.selected.value ?: run {
            oauthState.update { it.copy(message = "Select an OpenCode runtime first") }
            return
        }
        viewModelScope.launch {
            runCatching { target.authorizeProvider(providerId, methodIndex) }
                .onSuccess { authorization ->
                    credentials.unmanageProvider(providerId)
                    oauthState.update {
                        it.copy(
                            providerId = providerId,
                            methodIndex = methodIndex,
                            authorization = authorization,
                            message = null
                        )
                    }
                }
                .onFailure { error ->
                    oauthState.update {
                        it.copy(message = error.message?.takeIf(String::isNotBlank) ?: "OAuth authorization failed")
                    }
                }
        }
    }

    fun completeOAuth(providerId: String, methodIndex: Int, code: String?) {
        val target = registry.selected.value ?: return
        viewModelScope.launch {
            runCatching { target.completeProviderOAuth(providerId, methodIndex, code) }
                .onSuccess { completed ->
                    if (completed) {
                        oauthState.value = OAuthState(
                            methods = oauthState.value.methods,
                            message = "OAuth authentication completed for $providerId"
                        )
                        credentialTick.update { it + 1 }
                        refreshProviderAuth()
                    } else {
                        oauthState.update { it.copy(message = "OAuth authentication was not completed") }
                    }
                }
                .onFailure { error ->
                    oauthState.update {
                        it.copy(message = error.message?.takeIf(String::isNotBlank) ?: "OAuth callback failed")
                    }
                }
        }
    }

    fun consumeOAuthAuthorization() {
        oauthState.update { it.copy(authorization = null) }
    }

    fun dismissOAuth() {
        oauthState.update { it.copy(providerId = null, methodIndex = null, authorization = null) }
    }

    fun reportOAuthError(message: String) {
        oauthState.update {
            it.copy(message = message.takeIf(String::isNotBlank) ?: "Unable to open the OAuth browser")
        }
    }

    fun setAssistantRuntimeId(runtimeId: String?) {
        settings.assistantRuntimeId = runtimeId?.trim()?.ifBlank { null }
        invalidateAssistantSession()
        credentialTick.update { it + 1 }
        refreshAssistantCatalog()
    }

    fun setAssistantWorkspacePath(path: String?) {
        settings.assistantWorkspacePath = path?.trim()?.ifBlank { null }
        invalidateAssistantSession()
        credentialTick.update { it + 1 }
    }

    fun setAssistantModel(providerId: String, modelId: String) {
        val provider = providerId.trim()
        val model = modelId.trim()
        require(provider.isNotEmpty() && model.isNotEmpty()) {
            "Assistant provider and model are required"
        }
        settings.assistantProviderId = provider
        settings.assistantModelId = model
        invalidateAssistantSession()
        credentialTick.update { it + 1 }
    }

    fun setAssistantAgent(agentId: String?) {
        settings.assistantAgentId = agentId?.trim()?.ifBlank { null }
        invalidateAssistantSession()
        credentialTick.update { it + 1 }
    }

    fun useChatDefaultsForAssistant() {
        settings.assistantProviderId = null
        settings.assistantModelId = null
        settings.assistantAgentId = null
        invalidateAssistantSession()
        credentialTick.update { it + 1 }
    }

    private fun refreshAssistantCatalog() {
        val runtimeId = settings.assistantRuntimeId ?: registry.selected.value?.id
        val target = registry.targets.value.firstOrNull { it.id == runtimeId }
        if (target == null) {
            assistantCatalog.value = AssistantCatalogState(
                runtimeId = runtimeId,
                error = if (runtimeId == null) null else "ホームアシストの実行先が見つかりません"
            )
            return
        }
        assistantCatalog.value = AssistantCatalogState(runtimeId = target.id, isLoading = true)
        viewModelScope.launch {
            val result = runCatching {
                target.connect().getOrThrow()
                val providers = target.listProviders()
                val connected = providers.connected.toSet()
                val providerList = providers.all
                    .filter { connected.isEmpty() || it.id in connected }
                val agents = target.listAgents()
                    .filter { it.mode == null || it.mode == "primary" }
                providerList to agents
            }
            if (assistantCatalog.value.runtimeId != target.id) return@launch
            assistantCatalog.value = result.fold(
                onSuccess = { (providers, agents) ->
                    AssistantCatalogState(
                        runtimeId = target.id,
                        providers = providers,
                        agents = agents,
                        isLoading = false
                    )
                },
                onFailure = { error ->
                    AssistantCatalogState(
                        runtimeId = target.id,
                        isLoading = false,
                        error = error.message?.takeIf(String::isNotBlank)
                            ?: "ホームアシスト用モデルを取得できませんでした"
                    )
                }
            )
        }
    }

    private fun invalidateAssistantSession() {
        settings.assistantSessionId = null
        settings.assistantSessionProfileKey = null
    }
}
