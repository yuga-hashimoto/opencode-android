package com.opencode.android.data.connection

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.opencode.android.runtime.RuntimeConnectionStore

class SecureSettingsRepository(context: Context) : RuntimeConnectionStore {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val preferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    @Synchronized
    override fun connections(): List<ConnectionProfile> = runCatching {
        ConnectionProfileCodec.decode(preferences.getString(KEY_CONNECTIONS, "[]").orEmpty())
    }.getOrDefault(emptyList())

    @Synchronized
    override fun upsertConnection(profile: ConnectionProfile) {
        val updated = connections().filterNot { it.id == profile.id } + profile
        preferences.edit().putString(KEY_CONNECTIONS, ConnectionProfileCodec.encode(updated)).apply()
        if (selectedConnectionId.isNullOrBlank()) selectedConnectionId = profile.id
    }

    @Synchronized
    override fun deleteConnection(id: String) {
        val updated = connections().filterNot { it.id == id }
        preferences.edit().putString(KEY_CONNECTIONS, ConnectionProfileCodec.encode(updated)).apply()
        if (selectedConnectionId == id) selectedConnectionId = updated.firstOrNull()?.id
    }

    var selectedConnectionId: String?
        get() = preferences.getString(KEY_SELECTED_CONNECTION, null)
        set(value) = preferences.edit().putString(KEY_SELECTED_CONNECTION, value).apply()

    override var selectedRuntimeId: String?
        get() = selectedConnectionId
        set(value) {
            selectedConnectionId = value
        }

    fun selectedConnection(): ConnectionProfile? =
        selectedConnectionId?.let { selected -> connections().firstOrNull { it.id == selected } }

    var ttsEnabled: Boolean
        get() = preferences.getBoolean(KEY_TTS_ENABLED, true)
        set(value) = preferences.edit().putBoolean(KEY_TTS_ENABLED, value).apply()

    var continuousConversation: Boolean
        get() = preferences.getBoolean(KEY_CONTINUOUS_CONVERSATION, false)
        set(value) = preferences.edit().putBoolean(KEY_CONTINUOUS_CONVERSATION, value).apply()

    var assistantSessionId: String?
        get() = preferences.getString(KEY_ASSISTANT_SESSION_ID, null)
        set(value) = preferences.edit().putString(KEY_ASSISTANT_SESSION_ID, value).apply()

    var selectedProviderId: String?
        get() = preferences.getString(KEY_PROVIDER_ID, null)
        set(value) = preferences.edit().putString(KEY_PROVIDER_ID, value).apply()

    var selectedModelId: String?
        get() = preferences.getString(KEY_MODEL_ID, null)
        set(value) = preferences.edit().putString(KEY_MODEL_ID, value).apply()

    var selectedAgentId: String?
        get() = preferences.getString(KEY_AGENT_ID, null)
        set(value) = preferences.edit().putString(KEY_AGENT_ID, value).apply()

    var providerApiKeys: Map<String, String>
        get() = providerApiKeys()
        set(value) {
            preferences.edit()
                .putString(
                    KEY_PROVIDER_API_KEYS,
                    com.opencode.android.runtime.local.LocalProviderCredentialStore.encodeMap(value)
                )
                .apply()
        }

    fun providerApiKeys(): Map<String, String> =
        com.opencode.android.runtime.local.LocalProviderCredentialStore.decodeMap(
            preferences.getString(KEY_PROVIDER_API_KEYS, null)
        )

    var managedProviderApiKeyIds: Set<String>
        get() = preferences.getStringSet(KEY_MANAGED_PROVIDER_API_KEY_IDS, emptySet())
            .orEmpty()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
        set(value) {
            preferences.edit()
                .putStringSet(
                    KEY_MANAGED_PROVIDER_API_KEY_IDS,
                    value.map(String::trim).filter(String::isNotEmpty).toSet()
                )
                .apply()
        }

    var assistantRuntimeId: String?
        get() = preferences.getString(KEY_ASSISTANT_RUNTIME_ID, null)
        set(value) = preferences.edit().putString(KEY_ASSISTANT_RUNTIME_ID, value).apply()

    var assistantWorkspacePath: String?
        get() = preferences.getString(KEY_ASSISTANT_WORKSPACE_PATH, null)
        set(value) = preferences.edit().putString(KEY_ASSISTANT_WORKSPACE_PATH, value).apply()

    var assistantProviderId: String?
        get() = preferences.getString(KEY_ASSISTANT_PROVIDER_ID, null)
        set(value) = preferences.edit().putString(KEY_ASSISTANT_PROVIDER_ID, value).apply()

    var assistantModelId: String?
        get() = preferences.getString(KEY_ASSISTANT_MODEL_ID, null)
        set(value) = preferences.edit().putString(KEY_ASSISTANT_MODEL_ID, value).apply()

    var assistantAgentId: String?
        get() = preferences.getString(KEY_ASSISTANT_AGENT_ID, null)
        set(value) = preferences.edit().putString(KEY_ASSISTANT_AGENT_ID, value).apply()

    var assistantSessionProfileKey: String?
        get() = preferences.getString(KEY_ASSISTANT_SESSION_PROFILE_KEY, null)
        set(value) = preferences.edit().putString(KEY_ASSISTANT_SESSION_PROFILE_KEY, value).apply()

    var safWorkspaceUris: List<String>
        get() = preferences.getString(KEY_SAF_WORKSPACE_URIS, null)
            ?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        set(value) {
            preferences.edit()
                .putString(KEY_SAF_WORKSPACE_URIS, value.joinToString("\n"))
                .apply()
        }

    companion object {
        private const val PREFS_NAME = "opencode_android_secure_settings"
        private const val KEY_CONNECTIONS = "connections"
        private const val KEY_SELECTED_CONNECTION = "selected_connection"
        private const val KEY_TTS_ENABLED = "tts_enabled"
        private const val KEY_CONTINUOUS_CONVERSATION = "continuous_conversation"
        private const val KEY_ASSISTANT_SESSION_ID = "assistant_session_id"
        private const val KEY_PROVIDER_ID = "provider_id"
        private const val KEY_MODEL_ID = "model_id"
        private const val KEY_AGENT_ID = "agent_id"
        private const val KEY_PROVIDER_API_KEYS = "provider_api_keys"
        private const val KEY_MANAGED_PROVIDER_API_KEY_IDS = "managed_provider_api_key_ids"
        private const val KEY_ASSISTANT_RUNTIME_ID = "assistant_runtime_id"
        private const val KEY_ASSISTANT_WORKSPACE_PATH = "assistant_workspace_path"
        private const val KEY_ASSISTANT_PROVIDER_ID = "assistant_provider_id"
        private const val KEY_ASSISTANT_MODEL_ID = "assistant_model_id"
        private const val KEY_ASSISTANT_AGENT_ID = "assistant_agent_id"
        private const val KEY_ASSISTANT_SESSION_PROFILE_KEY = "assistant_session_profile_key"
        private const val KEY_SAF_WORKSPACE_URIS = "saf_workspace_uris"
    }
}
