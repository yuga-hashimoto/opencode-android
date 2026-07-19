package com.opencode.android.feature.workspace

import com.opencode.android.core.security.OpenCodeUrl
import com.opencode.android.data.connection.ConnectionProfile
import com.opencode.android.feature.connection.DiscoveredOpenCodeService
import com.opencode.android.feature.connection.OpenCodeConnectionQr
import java.util.UUID

internal fun resolveQrConnectionForm(
    raw: String,
    existingProfiles: List<ConnectionProfile>
): Result<ConnectionFormState> = OpenCodeConnectionQr.decode(raw).map { decoded ->
    val existing = existingProfiles.firstOrNull { profile ->
        normalizeEndpoint(profile.baseUrl) == decoded.normalizedUrl
    }
    if (existing == null) {
        decoded
    } else {
        decoded.copy(
            id = existing.id,
            username = decoded.username.ifBlank { existing.username },
            password = decoded.password.ifBlank { existing.password.orEmpty() },
            allowInsecureLan = decoded.allowInsecureLan || existing.allowInsecureLan,
            testSucceeded = existing.baseUrl == decoded.normalizedUrl
        )
    }
}

internal fun profileFromDiscoveredService(
    service: DiscoveredOpenCodeService,
    existingProfiles: List<ConnectionProfile>,
    idFactory: () -> String = { UUID.randomUUID().toString() }
): ConnectionProfile {
    val normalized = OpenCodeUrl.normalize(service.baseUrl).getOrThrow()
    val existing = existingProfiles.firstOrNull { profile ->
        normalizeEndpoint(profile.baseUrl) == normalized.toString()
    }
    return ConnectionProfile(
        id = existing?.id ?: idFactory(),
        name = service.name.trim().ifBlank { existing?.name ?: normalized.host },
        baseUrl = normalized.toString(),
        username = existing?.username?.takeIf(String::isNotBlank) ?: "opencode",
        password = existing?.password,
        allowInsecureLan = normalized.scheme == "http"
    )
}

private fun normalizeEndpoint(raw: String): String? =
    OpenCodeUrl.normalize(raw).getOrNull()?.toString()
