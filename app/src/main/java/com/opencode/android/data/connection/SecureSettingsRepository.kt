package com.opencode.android.data.connection

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureSettingsRepository(context: Context) {
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
    fun connections(): List<ConnectionProfile> = runCatching {
        ConnectionProfileCodec.decode(preferences.getString(KEY_CONNECTIONS, "[]").orEmpty())
    }.getOrDefault(emptyList())

    @Synchronized
    fun upsertConnection(profile: ConnectionProfile) {
        val updated = connections().filterNot { it.id == profile.id } + profile
        preferences.edit().putString(KEY_CONNECTIONS, ConnectionProfileCodec.encode(updated)).apply()
        if (selectedConnectionId.isNullOrBlank()) selectedConnectionId = profile.id
    }

    @Synchronized
    fun deleteConnection(id: String) {
        val updated = connections().filterNot { it.id == id }
        preferences.edit().putString(KEY_CONNECTIONS, ConnectionProfileCodec.encode(updated)).apply()
        if (selectedConnectionId == id) selectedConnectionId = updated.firstOrNull()?.id
    }

    var selectedConnectionId: String?
        get() = preferences.getString(KEY_SELECTED_CONNECTION, null)
        set(value) = preferences.edit().putString(KEY_SELECTED_CONNECTION, value).apply()

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
    }
}
