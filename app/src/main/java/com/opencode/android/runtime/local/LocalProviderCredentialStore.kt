package com.opencode.android.runtime.local

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.opencode.android.data.connection.SecureSettingsRepository
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Stores provider API keys in encrypted preferences and synchronizes only
 * app-managed provider entries into OpenCode's auth.json before local startup.
 * Existing OAuth and user-managed entries are preserved.
 */
class LocalProviderCredentialStore(
    private val load: () -> Map<String, String>,
    private val save: (Map<String, String>) -> Unit,
    private val loadManagedProviderIds: () -> Set<String> = { load().keys },
    private val saveManagedProviderIds: (Set<String>) -> Unit = {},
    private val gson: Gson = Gson()
) {
    constructor(settings: SecureSettingsRepository, gson: Gson = Gson()) : this(
        load = { settings.providerApiKeys() },
        save = { settings.providerApiKeys = it },
        loadManagedProviderIds = {
            if (settings.hasManagedProviderApiKeyIds) {
                settings.managedProviderApiKeyIds
            } else {
                settings.providerApiKeys().keys
            }
        },
        saveManagedProviderIds = { settings.managedProviderApiKeyIds = it },
        gson = gson
    )

    fun credentials(): Map<String, String> = load()

    fun managedProviderIds(): Set<String> = loadManagedProviderIds()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()

    fun setCredential(providerId: String, apiKey: String?) {
        val normalizedId = providerId.trim()
        require(normalizedId.isNotEmpty()) { "Provider id is required" }

        val updatedCredentials = credentials().toMutableMap()
        val normalizedKey = apiKey?.trim().orEmpty()
        if (normalizedKey.isEmpty()) {
            updatedCredentials.remove(normalizedId)
        } else {
            updatedCredentials[normalizedId] = normalizedKey
        }
        val updatedManagedIds = managedProviderIds() + normalizedId

        save(updatedCredentials)
        saveManagedProviderIds(updatedManagedIds)
    }

    fun clearCredential(providerId: String) = setCredential(providerId, null)

    fun unmanageProvider(providerId: String) {
        val normalizedId = providerId.trim()
        if (normalizedId.isEmpty()) return
        saveManagedProviderIds(managedProviderIds() - normalizedId)
    }

    fun hasCredential(providerId: String): Boolean =
        !credentials()[providerId.trim()].isNullOrBlank()

    fun syncToRuntime(rootfs: File): File {
        val authDir = File(rootfs, "root/.local/share/opencode").apply { mkdirs() }
        val authFile = File(authDir, "auth.json")
        val managedIds = managedProviderIds()
        if (managedIds.isEmpty()) return authFile

        val payload = readExistingPayload(authFile)
        val currentCredentials = credentials()
        managedIds.forEach { providerId ->
            val apiKey = currentCredentials[providerId]?.trim().orEmpty()
            if (apiKey.isEmpty()) {
                payload.remove(providerId)
            } else {
                payload.add(providerId, JsonObject().apply {
                    addProperty("type", "api")
                    addProperty("key", apiKey)
                })
            }
        }
        writeAtomically(authFile, gson.toJson(payload))
        return authFile
    }

    private fun readExistingPayload(authFile: File): JsonObject {
        if (!authFile.isFile) return JsonObject()
        return runCatching {
            val parsed = JsonParser.parseString(authFile.readText())
            require(parsed.isJsonObject) { "OpenCode auth.json must contain a JSON object" }
            parsed.asJsonObject.deepCopy()
        }.getOrElse { error ->
            throw IllegalStateException(
                "Existing OpenCode auth.json is invalid and was not modified",
                error
            )
        }
    }

    private fun writeAtomically(destination: File, content: String) {
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        temporary.delete()
        try {
            FileOutputStream(temporary).use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            temporary.setReadable(true, true)
            temporary.setWritable(true, true)
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
            destination.setReadable(true, true)
            destination.setWritable(true, true)
        } finally {
            temporary.delete()
        }
    }

    companion object {
        fun decodeMap(raw: String?, gson: Gson = Gson()): Map<String, String> {
            if (raw.isNullOrBlank()) return emptyMap()
            val type = object : TypeToken<Map<String, String>>() {}.type
            return runCatching {
                gson.fromJson<Map<String, String>>(raw, type).orEmpty()
                    .mapKeys { it.key.trim() }
                    .filter { it.key.isNotEmpty() && it.value.isNotBlank() }
                    .mapValues { it.value.trim() }
            }.getOrDefault(emptyMap())
        }

        fun encodeMap(map: Map<String, String>, gson: Gson = Gson()): String =
            gson.toJson(map)
    }
}
