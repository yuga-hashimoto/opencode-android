package com.opencode.android.feature.connection

import com.opencode.android.feature.workspace.ConnectionFormState
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID

/**
 * Connection payload format:
 * opencode://connect?url=http://host:4096&name=Mac&user=opencode&password=secret&lan=1
 *
 * Implemented over java.net.URI-style parsing so it works in both Android and JVM unit tests.
 */
object OpenCodeConnectionQr {
    fun encode(form: ConnectionFormState): String {
        val params = buildList {
            add("url" to form.baseUrl.trim())
            add("name" to form.name.trim().ifBlank { "OpenCode" })
            add("user" to form.username.trim().ifBlank { "opencode" })
            if (form.password.isNotBlank()) add("password" to form.password)
            if (form.allowInsecureLan) add("lan" to "1")
        }
        val query = params.joinToString("&") { (k, v) ->
            "$k=${URLEncoder.encode(v, "UTF-8")}"
        }
        return "opencode://connect?$query"
    }

    fun decode(raw: String): Result<ConnectionFormState> = runCatching {
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty()) { "QR payload is empty" }

        when {
            trimmed.startsWith("opencode://", ignoreCase = true) -> {
                val queryStart = trimmed.indexOf('?')
                val query = if (queryStart >= 0) trimmed.substring(queryStart + 1) else ""
                val params = parseQuery(query)
                val url = params["url"] ?: params["baseUrl"]
                    ?: error("QR payload is missing url")
                ConnectionFormState(
                    id = UUID.randomUUID().toString(),
                    name = params["name"].orEmpty().ifBlank { "Discovered OpenCode" },
                    baseUrl = url,
                    username = params["user"] ?: params["username"] ?: "opencode",
                    password = params["password"] ?: params["pass"] ?: "",
                    allowInsecureLan = (params["lan"] == "1") ||
                        (params["allowInsecureLan"] == "1")
                )
            }
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> {
                val scheme = trimmed.substringBefore("://", "http").lowercase()
                ConnectionFormState(
                    id = UUID.randomUUID().toString(),
                    name = hostFromUrl(trimmed) ?: "OpenCode",
                    baseUrl = trimmed,
                    username = "opencode",
                    allowInsecureLan = scheme == "http"
                )
            }
            else -> {
                // Treat anything else as a bare host.
                ConnectionFormState(
                    id = UUID.randomUUID().toString(),
                    name = trimmed,
                    baseUrl = trimmed,
                    username = "opencode",
                    allowInsecureLan = true
                )
            }
        }
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split('&').mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx < 0) null else {
                URLDecoder.decode(pair.substring(0, idx), "UTF-8") to
                    URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
            }
        }.toMap()
    }

    private fun hostFromUrl(url: String): String? =
        runCatching {
            val afterScheme = url.substringAfter("://")
            afterScheme.substringBefore('/').substringBefore(':')
        }.getOrNull()
}
