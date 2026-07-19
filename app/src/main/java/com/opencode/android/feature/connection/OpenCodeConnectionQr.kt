package com.opencode.android.feature.connection

import com.opencode.android.core.security.OpenCodeUrl
import com.opencode.android.feature.workspace.ConnectionFormState
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Connection payload format:
 * opencode://connect?url=http%3A%2F%2Fhost%3A4096%2F&name=Mac&user=opencode&password=secret&lan=1
 */
object OpenCodeConnectionQr {
    fun encode(form: ConnectionFormState): String {
        val normalized = OpenCodeUrl.normalize(form.baseUrl).getOrThrow()
        require(normalized.username.isEmpty() && normalized.password.isEmpty()) {
            "Endpoint URL must not contain credentials"
        }
        if (normalized.scheme == "http") {
            require(form.allowInsecureLan) {
                "LAN HTTP must be explicitly allowed"
            }
        }
        val parameters = linkedMapOf(
            "url" to normalized.toString(),
            "name" to form.name.trim().ifBlank { normalized.host },
            "user" to form.username.trim().ifBlank { "opencode" }
        )
        if (form.password.isNotBlank()) parameters["password"] = form.password
        if (normalized.scheme == "http") parameters["lan"] = "1"
        return "opencode://connect?" + parameters.entries.joinToString("&") { (key, value) ->
            "${encodeComponent(key)}=${encodeComponent(value)}"
        }
    }

    fun decode(raw: String): Result<ConnectionFormState> = runCatching {
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty()) { "QR payload is empty" }

        if (trimmed.startsWith("opencode://", ignoreCase = true)) {
            decodeOpenCodeUri(trimmed)
        } else {
            decodeEndpoint(trimmed, explicitLanPermission = null)
        }
    }

    private fun decodeOpenCodeUri(raw: String): ConnectionFormState {
        val uri = URI(raw)
        require(uri.scheme.equals("opencode", ignoreCase = true)) { "Unsupported QR payload" }
        require(uri.host.equals("connect", ignoreCase = true) || uri.authority.equals("connect", ignoreCase = true)) {
            "Unsupported OpenCode QR action"
        }
        val parameters = parseQuery(uri.rawQuery)
        val endpoint = parameters["url"] ?: parameters["baseUrl"]
            ?: error("QR payload is missing url")
        val explicitLan = parameters["lan"] == "1" ||
            parameters["allowInsecureLan"].equals("true", ignoreCase = true)
        val base = decodeEndpoint(endpoint, explicitLanPermission = explicitLan)
        return base.copy(
            name = parameters["name"].orEmpty().trim().ifBlank { base.name },
            username = (parameters["user"] ?: parameters["username"])
                .orEmpty()
                .trim()
                .ifBlank { "opencode" },
            password = (parameters["password"] ?: parameters["pass"]).orEmpty()
        )
    }

    private fun decodeEndpoint(
        rawEndpoint: String,
        explicitLanPermission: Boolean?
    ): ConnectionFormState {
        val endpoint = OpenCodeUrl.normalize(rawEndpoint).getOrThrow()
        require(endpoint.username.isEmpty() && endpoint.password.isEmpty()) {
            "Endpoint URL must not contain credentials"
        }
        val isHttp = endpoint.scheme == "http"
        if (isHttp && explicitLanPermission == false) {
            error("LAN HTTP must be explicitly allowed by the QR payload")
        }
        return ConnectionFormState(
            id = UUID.randomUUID().toString(),
            name = endpoint.host.ifBlank { "OpenCode" },
            baseUrl = endpoint.toString(),
            username = "opencode",
            allowInsecureLan = isHttp
        )
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split('&')
            .filter(String::isNotBlank)
            .associate { pair ->
                val separator = pair.indexOf('=')
                val key = if (separator >= 0) pair.substring(0, separator) else pair
                val value = if (separator >= 0) pair.substring(separator + 1) else ""
                decodeComponent(key) to decodeComponent(value)
            }
    }

    private fun encodeComponent(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun decodeComponent(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}
