package com.opencode.android.runtime.local

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.opencode.android.data.connection.SecureSettingsRepository
import java.io.File

/**
 * Stores provider API keys in encrypted preferences and syncs them into the local
 * OpenCode auth.json before the runtime starts.
 *
 * Format matches OpenCode auth entries: `{ "providerId": { "type": "api", "key": "..." } }`.
 */
class LocalProviderCredentialStore(
    private val load: () -> Map<String, String>,
    private val save: (Map<String, String>) -> Unit,
    private val gson: Gson = Gson()
) {
    constructor(settings: SecureSettingsRepository, gson: Gson = Gson()) : this(
        load = { settings.providerApiKeys() },
        save = { settings.providerApiKeys = it },
        gson = gson
    )

    fun credentials(): Map<String, String> = load()

    fun setCredential(providerId: String, apiKey: String?) {
        val normalizedId = providerId.trim()
        require(normalizedId.isNotEmpty()) { "Provider id is required" }
        val updated = credentials().toMutableMap()
        val key = apiKey?.trim().orEmpty()
        if (key.isEmpty()) {
            updated.remove(normalizedId)
        } else {
            updated[normalizedId] = key
        }
        save(updated)
    }

    fun clearCredential(providerId: String) = setCredential(providerId, null)

    fun hasCredential(providerId: String): Boolean =
        !credentials()[providerId.trim()].isNullOrBlank()

    fun syncToRuntime(rootfs: File): File {
        val authDir = File(rootfs, "root/.local/share/opencode").apply { mkdirs() }
        val authFile = File(authDir, "auth.json")
        val payload = JsonObject()
        credentials().forEach { (providerId, apiKey) ->
            payload.add(providerId, JsonObject().apply {
                addProperty("type", "api")
                addProperty("key", apiKey)
            })
        }
        authFile.writeText(gson.toJson(payload))
        return authFile
    }

    companion object {
        fun decodeMap(raw: String?, gson: Gson = Gson()): Map<String, String> {
            if (raw.isNullOrBlank()) return emptyMap()
            val type = object : TypeToken<Map<String, String>>() {}.type
            return runCatching {
                gson.fromJson<Map<String, String>>(raw, type).orEmpty()
                    .mapKeys { it.key.trim() }
                    .filter { it.key.isNotEmpty() && !it.value.isNullOrBlank() }
                    .mapValues { it.value.trim() }
            }.getOrDefault(emptyMap())
        }

        fun encodeMap(map: Map<String, String>, gson: Gson = Gson()): String =
            gson.toJson(map)
    }
}
