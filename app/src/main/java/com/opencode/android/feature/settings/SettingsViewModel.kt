package com.opencode.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.android.core.api.OpenCodeAgent
import com.opencode.android.core.api.OpenCodeProvider
import com.opencode.android.core.api.ProviderAuthMethod
import com.opencode.android.data.connection.SecureSettingsRepository
import com.opencode.android.data.repository.RuntimeCatalogRepository
import com.opencode.android.data.settings.AppPreferencesRepository
import com.opencode.android.runtime.BackendKind
import com.opencode.android.runtime.RuntimeRegistry
import com.opencode.android.runtime.local.LocalProviderCredentialStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val providers: List<OpenCodeProvider> = emptyList(),
    val availableProviders: List<OpenCodeProvider> = emptyList(),
    val connectedProviderIds: Set<String> = emptySet(),
    val agents: List<OpenCodeAgent> = emptyList(),
    val assistantProviders: List<OpenCodeProvider> = emptyList(),
    val assistantAgents: List<OpenCodeAgent> = emptyList(),
    val providerId: String? = null,
    val modelId: String? = null,
    val agentId: String? = null,
    val ttsEnabled: Boolean = true,
    val continuousConversation: Boolean = false,
    val runtimeOptions: List<Pair<String, String>> = emptyList(),
    val assistantRuntimeId: String? = null,
    val assistantWorkspacePath: String? = null,
    val assistantProviderId: String? = null,
    val assistantModelId: String? = null,
    val assistantAgentId: String? = null,
    val isLoadingAssistantCatalog: Boolean = false,
    val assistantCatalogError: String? = null,
    val openCodeVersion: String? = null,
    val recentModels: List<Pair<String, String>> = emptyList(),
    val autoAllowReadOnlyTools: Boolean = false,
    val themeMode: String? = null,
    val dynamicColorEnabled: Boolean = false,
    val providerAuthMethods: Map<String, List<ProviderAuthMethod>> = emptyMap(),
    val oauthMessage: String? = null,
    val providerAuthDialog: ProviderAuthDialogState? = null,
    val providerAuthNotice: ProviderAuthNotice? = null
)

class SettingsViewModel(
    private val catalog: RuntimeCatalogRepository,
    private val preferences: AppPreferencesRepository,
    private val credentials: LocalProviderCredentialStore,
    private val settings: SecureSettingsRepository,
    private val registry: RuntimeRegistry
) : ViewModel() {
    private val credentialTick = MutableStateFlow(0)
    private val assistantCatalog = MutableStateFlow(AssistantCatalogState())
    private val oauthState = MutableStateFlow(OAuthState())
    private var providerAuthJob: Job? = null

    private data class AssistantCatalogState(
        val runtimeId: String? = null,
        val providers: List<OpenCodeProvider> = emptyList(),
        val agents: List<OpenCodeAgent> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null
    )

    private data class DraftState(
        val tick: Int,
        val assistantCatalog: AssistantCatalogState
    )

    private data class OAuthState(
        val methods: Map<String, List<ProviderAuthMethod>> = emptyMap(),
        val dialog: ProviderAuthDialogState? = null,
        val notice: ProviderAuthNotice? = null,
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
        combine(credentialTick, assistantCatalog) { tick, assistant ->
            DraftState(tick, assistant)
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
        SettingsUiState(
            providers = providerList,
            availableProviders = runtime.providers.all,
            connectedProviderIds = connected,
            agents = runtime.agents.filter { it.mode == null || it.mode == "primary" },
            assistantProviders = draft.assistantCatalog.providers,
            assistantAgents = draft.assistantCatalog.agents,
            providerId = prefs.providerId,
            modelId = prefs.modelId,
            agentId = prefs.agentId,
            ttsEnabled = prefs.ttsEnabled,
            continuousConversation = prefs.continuousConversation,
            runtimeOptions = targets.map { it.id to it.displayName },
            assistantRuntimeId = settings.assistantRuntimeId ?: selected?.id,
            assistantWorkspacePath = settings.assistantWorkspacePath,
            assistantProviderId = settings.assistantProviderId ?: prefs.providerId,
            assistantModelId = settings.assistantModelId ?: prefs.modelId,
            assistantAgentId = settings.assistantAgentId ?: prefs.agentId,
            isLoadingAssistantCatalog = draft.assistantCatalog.isLoading,
            assistantCatalogError = draft.assistantCatalog.error,
            openCodeVersion = runtime.health?.version,
            recentModels = prefs.recentModels,
            autoAllowReadOnlyTools = settings.autoAllowReadOnlyTools,
            themeMode = prefs.themeMode,
            dynamicColorEnabled = prefs.dynamicColorEnabled,
            providerAuthMethods = oauth.methods,
            oauthMessage = oauth.message,
            providerAuthDialog = oauth.dialog,
            providerAuthNotice = oauth.notice
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

    fun openProviderAuth(providerId: String) {
        val methods = oauthState.value.methods[providerId].orEmpty()
        if (methods.isEmpty()) return
        val providerName = state.value.availableProviders
            .firstOrNull { it.id == providerId }
            ?.name
            ?: providerId
        oauthState.update {
            it.copy(
                dialog = ProviderAuthDialogState(
                    providerId = providerId,
                    providerName = providerName,
                    methods = methods
                ),
                notice = null,
                message = null
            )
        }
    }

    fun selectProviderAuthMethod(methodIndex: Int) {
        val current = oauthState.value.dialog ?: return
        val method = current.methods.getOrNull(methodIndex) ?: return
        val updated = current.copy(
            methodIndex = methodIndex,
            inputs = emptyMap(),
            apiKey = "",
            authorization = null,
            failed = false,
            error = null
        )
        oauthState.update { it.copy(dialog = updated, notice = null, message = null) }
        if (method.type == "oauth" && method.prompts.isEmpty()) submitProviderAuth()
    }

    fun updateProviderAuthInput(key: String, value: String) {
        oauthState.update { current ->
            val dialog = current.dialog ?: return@update current
            current.copy(
                dialog = dialog.copy(
                    inputs = dialog.inputs + (key to value),
                    failed = false,
                    error = null
                )
            )
        }
    }

    fun updateProviderApiKey(value: String) {
        oauthState.update { current ->
            val dialog = current.dialog ?: return@update current
            current.copy(dialog = dialog.copy(apiKey = value, failed = false, error = null))
        }
    }

    fun submitProviderAuth() {
        val dialog = oauthState.value.dialog ?: return
        val methodIndex = dialog.methodIndex ?: return
        val method = dialog.selectedMethod ?: return
        if (dialog.isSubmitting || providerAuthJob?.isActive == true || !dialog.promptsComplete) return
        when (method.type) {
            "api" -> submitProviderApiKey(dialog)
            "oauth" -> beginProviderOAuth(dialog, methodIndex)
        }
    }

    private fun submitProviderApiKey(dialog: ProviderAuthDialogState) {
        val target = registry.selected.value ?: return
        val apiKey = dialog.apiKey.trim()
        if (apiKey.isEmpty()) return
        providerAuthJob = viewModelScope.launch {
            oauthState.update {
                it.copy(dialog = dialog.copy(isSubmitting = true, failed = false, error = null))
            }
            runCatching { target.setProviderApiKey(dialog.providerId, apiKey, dialog.inputs) }
                .onSuccess { completed ->
                    if (completed) {
                        credentials.unmanageProvider(dialog.providerId)
                        finishProviderAuth(ProviderAuthNotice.CONNECTED)
                    } else {
                        updateProviderAuthError(null)
                    }
                }
                .onFailure(::updateProviderAuthError)
        }
    }

    private fun beginProviderOAuth(dialog: ProviderAuthDialogState, methodIndex: Int) {
        val target = registry.selected.value ?: return
        providerAuthJob = viewModelScope.launch {
            oauthState.update {
                it.copy(dialog = dialog.copy(isSubmitting = true, failed = false, error = null))
            }
            runCatching { target.authorizeProvider(dialog.providerId, methodIndex, dialog.inputs) }
                .onSuccess { authorization ->
                    credentials.unmanageProvider(dialog.providerId)
                    val authorized = dialog.copy(
                        authorization = authorization,
                        isSubmitting = authorization.method == "auto",
                        failed = false,
                        error = null
                    )
                    oauthState.update { it.copy(dialog = authorized, notice = null, message = null) }
                    if (authorization.method == "auto") {
                        runCatching { target.completeProviderOAuth(dialog.providerId, methodIndex, null) }
                            .onSuccess { completed ->
                                if (completed) finishProviderAuth(ProviderAuthNotice.CONNECTED)
                                else updateProviderAuthError(null)
                            }
                            .onFailure(::updateProviderAuthError)
                    }
                }
                .onFailure(::updateProviderAuthError)
        }
    }

    fun completeProviderOAuth(code: String) {
        val dialog = oauthState.value.dialog ?: return
        val methodIndex = dialog.methodIndex ?: return
        if (dialog.authorization?.method != "code" || code.isBlank()) return
        val target = registry.selected.value ?: return
        if (providerAuthJob?.isActive == true) return
        providerAuthJob = viewModelScope.launch {
            oauthState.update {
                it.copy(dialog = dialog.copy(isSubmitting = true, failed = false, error = null))
            }
            runCatching { target.completeProviderOAuth(dialog.providerId, methodIndex, code.trim()) }
                .onSuccess { completed ->
                    if (completed) finishProviderAuth(ProviderAuthNotice.CONNECTED)
                    else updateProviderAuthError(null)
                }
                .onFailure(::updateProviderAuthError)
        }
    }

    fun disconnectProvider(providerId: String) {
        val target = registry.selected.value ?: return
        if (providerAuthJob?.isActive == true) return
        providerAuthJob = viewModelScope.launch {
            runCatching { target.removeProviderAuth(providerId) }
                .onSuccess { removed ->
                    if (removed) {
                        if (target.kind == BackendKind.LOCAL) credentials.clearCredential(providerId)
                        finishProviderAuth(ProviderAuthNotice.DISCONNECTED)
                    }
                }
                .onFailure { error ->
                    oauthState.update { it.copy(message = error.message?.takeIf(String::isNotBlank)) }
                }
        }
    }

    fun dismissProviderAuth() {
        providerAuthJob?.cancel()
        providerAuthJob = null
        oauthState.update { it.copy(dialog = null, message = null) }
    }

    fun consumeProviderAuthNotice() {
        oauthState.update { it.copy(notice = null) }
    }

    fun reportOAuthError(message: String) {
        providerAuthJob?.cancel()
        updateProviderAuthError(message.takeIf(String::isNotBlank))
    }

    private fun finishProviderAuth(notice: ProviderAuthNotice) {
        providerAuthJob = null
        oauthState.update { it.copy(dialog = null, notice = notice, message = null) }
        credentialTick.update { it + 1 }
        catalog.refresh()
        refreshProviderAuth()
    }

    private fun updateProviderAuthError(error: Throwable) {
        updateProviderAuthError(error.message?.takeIf(String::isNotBlank))
    }

    private fun updateProviderAuthError(message: String?) {
        providerAuthJob = null
        oauthState.update { current ->
            current.copy(
                dialog = current.dialog?.copy(
                    isSubmitting = false,
                    failed = true,
                    error = message
                ),
                message = if (current.dialog == null) message else null
            )
        }
    }

    fun refreshProviderAuth() {
        val target = registry.selected.value ?: run {
            oauthState.value = OAuthState()
            return
        }
        viewModelScope.launch {
            runCatching { target.providerAuthMethods() }
                .onSuccess { methods ->
                    oauthState.update { current -> current.copy(methods = methods, message = null) }
                }
                .onFailure { error ->
                    oauthState.update { current ->
                        current.copy(
                            methods = emptyMap(),
                            message = error.message?.takeIf(String::isNotBlank)
                        )
                    }
                }
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
