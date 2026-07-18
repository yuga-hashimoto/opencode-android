package com.opencode.android.feature.workspace

import com.opencode.android.core.security.OpenCodeUrl
import com.opencode.android.data.connection.ConnectionProfile
import java.util.UUID

data class ConnectionFormState(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val baseUrl: String = "",
    val username: String = "opencode",
    val password: String = "",
    val allowInsecureLan: Boolean = false,
    val isTesting: Boolean = false,
    val testMessage: String? = null,
    val testSucceeded: Boolean = false
) {
    val normalizedUrl: String?
        get() = OpenCodeUrl.normalize(baseUrl).getOrNull()?.toString()

    val canSave: Boolean
        get() {
            val url = OpenCodeUrl.normalize(baseUrl).getOrNull() ?: return false
            val cleartextAllowed = url.scheme == "https" || allowInsecureLan
            return name.isNotBlank() && cleartextAllowed
        }

    fun toProfile(): ConnectionProfile = ConnectionProfile(
        id = id,
        name = name.trim(),
        baseUrl = requireNotNull(normalizedUrl),
        username = username.trim().ifBlank { "opencode" },
        password = password.takeIf { it.isNotBlank() },
        allowInsecureLan = allowInsecureLan
    )

    companion object {
        fun from(profile: ConnectionProfile): ConnectionFormState = ConnectionFormState(
            id = profile.id,
            name = profile.name,
            baseUrl = profile.baseUrl,
            username = profile.username,
            password = profile.password.orEmpty(),
            allowInsecureLan = profile.allowInsecureLan,
            testSucceeded = true
        )
    }
}
