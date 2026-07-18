package com.opencode.android.core.security

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object OpenCodeUrl {
    fun normalize(raw: String): Result<HttpUrl> = runCatching {
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty()) { "Endpoint is required" }

        val withScheme = if (SCHEME_PATTERN.containsMatchIn(trimmed)) {
            trimmed
        } else {
            "http://$trimmed"
        }

        val parsed = withScheme.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid OpenCode endpoint")

        require(parsed.scheme == "http" || parsed.scheme == "https") {
            "Only HTTP and HTTPS endpoints are supported"
        }
        require(parsed.host.isNotBlank()) { "Endpoint host is required" }

        if (parsed.scheme == "http") {
            require(isTrustedCleartextHost(parsed.host)) {
                "Cleartext HTTP is allowed only for localhost, LAN, .local, and Tailscale addresses"
            }
        }

        parsed.newBuilder().apply {
            if (!parsed.encodedPath.endsWith('/')) {
                addPathSegment("")
            }
        }.build()
    }

    internal fun isTrustedCleartextHost(host: String): Boolean {
        val normalized = host.lowercase().removeSurrounding("[", "]")
        if (normalized == "localhost" || normalized.endsWith(".local")) return true
        if (":" in normalized &&
            (normalized == "::1" || normalized.startsWith("fc") || normalized.startsWith("fd"))
        ) return true

        val octets = normalized.split('.').mapNotNull { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false

        return when {
            octets[0] == 10 -> true
            octets[0] == 127 -> true
            octets[0] == 169 && octets[1] == 254 -> true
            octets[0] == 172 && octets[1] in 16..31 -> true
            octets[0] == 192 && octets[1] == 168 -> true
            octets[0] == 100 && octets[1] in 64..127 -> true
            else -> false
        }
    }

    private val SCHEME_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
}
