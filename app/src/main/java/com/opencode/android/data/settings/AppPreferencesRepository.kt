package com.opencode.android.data.settings

import com.opencode.android.core.api.OpenCodeAgent
import com.opencode.android.core.api.ProviderCatalog
import com.opencode.android.data.connection.SecureSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AppPreferences(
    val providerId: String? = null,
    val modelId: String? = null,
    val agentId: String? = null,
    val ttsEnabled: Boolean = true,
    val continuousConversation: Boolean = false,
    val wakeWordEnabled: Boolean = false,
    val autoAcceptPermissions: Boolean = false,
    val favoriteModelKeys: Set<String> = emptySet(),
    val recentModelKeys: List<String> = emptyList(),
    val theme: String = "dark",
    val uiFontSize: Int = 16,
    val codeFontSize: Int = 12,
    val syntaxTheme: String = "one-dark",
    val toolCallDetailLevel: String = "detailed",
    val autoExpandReasoning: Boolean = false,
    val sendBehavior: String = "interrupt",
    val sidebarGrouping: String = "project",
    val workspaceTitleSource: String = "title",
    val language: String = "system",
    val liveTranscriptEnabled: Boolean = false
)

class AppPreferencesRepository(
    private val settings: SecureSettingsRepository
) {
    private val mutableState = MutableStateFlow(
        AppPreferences(
            providerId = settings.selectedProviderId,
            modelId = settings.selectedModelId,
            agentId = settings.selectedAgentId,
            ttsEnabled = settings.ttsEnabled,
            continuousConversation = settings.continuousConversation,
            wakeWordEnabled = settings.wakeWordEnabled,
            autoAcceptPermissions = settings.autoAcceptPermissions,
            favoriteModelKeys = settings.favoriteModelKeys,
            recentModelKeys = settings.recentModelKeys,
            theme = settings.theme,
            uiFontSize = settings.uiFontSize,
            codeFontSize = settings.codeFontSize,
            syntaxTheme = settings.syntaxTheme,
            toolCallDetailLevel = settings.toolCallDetailLevel,
            autoExpandReasoning = settings.autoExpandReasoning,
            sendBehavior = settings.sendBehavior,
            sidebarGrouping = settings.sidebarGrouping,
            workspaceTitleSource = settings.workspaceTitleSource,
            language = settings.language,
            liveTranscriptEnabled = settings.liveTranscriptEnabled
        )
    )
    val state: StateFlow<AppPreferences> = mutableState.asStateFlow()

    fun selectModel(providerId: String?, modelId: String?) {
        settings.selectedProviderId = providerId
        settings.selectedModelId = modelId
        mutableState.update { it.copy(providerId = providerId, modelId = modelId) }
        if (providerId != null && modelId != null) {
            val key = "$providerId/$modelId"
            val updated = (listOf(key) + settings.recentModelKeys.filterNot { it == key }).take(5)
            settings.recentModelKeys = updated
            mutableState.update { it.copy(recentModelKeys = updated) }
        }
    }

    fun selectAgent(agentId: String?) {
        settings.selectedAgentId = agentId
        mutableState.update { it.copy(agentId = agentId) }
    }

    fun reconcile(catalog: ProviderCatalog, agents: List<OpenCodeAgent>) {
        val current = mutableState.value
        val connected = catalog.connected.toSet()
        val providers = catalog.all.filter { it.id in connected }
        val providerId = current.providerId?.takeIf { it in connected }
            ?: "opencode".takeIf { it in connected }
            ?: providers.firstOrNull()?.id
        val provider = providers.firstOrNull { it.id == providerId }
        val modelId = current.modelId?.takeIf { it in provider?.models.orEmpty() }
            ?: providerId?.let { catalog.default[it] }?.takeIf { it in provider?.models.orEmpty() }
            ?: provider?.models?.values
                ?.firstOrNull { it.status == null || it.status == "active" }
                ?.id
        val primaryAgents = agents.filter { it.mode == null || it.mode == "primary" }
        val agentId = current.agentId?.takeIf { selected -> primaryAgents.any { it.name == selected } }
            ?: primaryAgents.firstOrNull { it.name == "build" }?.name
            ?: primaryAgents.firstOrNull()?.name

        if (providerId != current.providerId || modelId != current.modelId) {
            selectModel(providerId, modelId)
        }
        if (agentId != current.agentId) {
            selectAgent(agentId)
        }
    }

    fun setTtsEnabled(enabled: Boolean) {
        settings.ttsEnabled = enabled
        mutableState.update { it.copy(ttsEnabled = enabled) }
    }

    fun setContinuousConversation(enabled: Boolean) {
        settings.continuousConversation = enabled
        mutableState.update { it.copy(continuousConversation = enabled) }
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        settings.wakeWordEnabled = enabled
        mutableState.update { it.copy(wakeWordEnabled = enabled) }
    }

    fun setAutoAcceptPermissions(enabled: Boolean) {
        settings.autoAcceptPermissions = enabled
        mutableState.update { it.copy(autoAcceptPermissions = enabled) }
    }

    fun toggleFavoriteModel(providerId: String, modelId: String) {
        val key = "$providerId/$modelId"
        val current = mutableState.value.favoriteModelKeys
        val updated = if (key in current) current - key else current + key
        settings.favoriteModelKeys = updated
        mutableState.update { it.copy(favoriteModelKeys = updated) }
    }

    fun setTheme(theme: String) {
        settings.theme = theme
        mutableState.update { it.copy(theme = theme) }
    }

    fun setUiFontSize(size: Int) {
        settings.uiFontSize = size
        mutableState.update { it.copy(uiFontSize = size) }
    }

    fun setCodeFontSize(size: Int) {
        settings.codeFontSize = size
        mutableState.update { it.copy(codeFontSize = size) }
    }

    fun setSyntaxTheme(theme: String) {
        settings.syntaxTheme = theme
        mutableState.update { it.copy(syntaxTheme = theme) }
    }

    fun setToolCallDetailLevel(level: String) {
        settings.toolCallDetailLevel = level
        mutableState.update { it.copy(toolCallDetailLevel = level) }
    }

    fun setAutoExpandReasoning(enabled: Boolean) {
        settings.autoExpandReasoning = enabled
        mutableState.update { it.copy(autoExpandReasoning = enabled) }
    }

    fun setSendBehavior(behavior: String) {
        settings.sendBehavior = behavior
        mutableState.update { it.copy(sendBehavior = behavior) }
    }

    fun setSidebarGrouping(grouping: String) {
        settings.sidebarGrouping = grouping
        mutableState.update { it.copy(sidebarGrouping = grouping) }
    }

    fun setWorkspaceTitleSource(source: String) {
        settings.workspaceTitleSource = source
        mutableState.update { it.copy(workspaceTitleSource = source) }
    }

    fun setLanguage(language: String) {
        settings.language = language
        mutableState.update { it.copy(language = language) }
    }

    fun setLiveTranscriptEnabled(enabled: Boolean) {
        settings.liveTranscriptEnabled = enabled
        mutableState.update { it.copy(liveTranscriptEnabled = enabled) }
    }
}
