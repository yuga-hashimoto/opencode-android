package com.opencode.android.core.api

import com.google.gson.Gson
import com.google.gson.JsonObject

class OpenCodeEventParser(
    private val gson: Gson = Gson()
) {
    fun parse(json: String): OpenCodeEvent {
        val root = runCatching { gson.fromJson(json, JsonObject::class.java) }.getOrNull()
            ?: return OpenCodeEvent.Unknown("invalid", json)
        val type = root.get("type")?.asString ?: return OpenCodeEvent.Unknown("missing-type", json)
        val properties = root.getAsJsonObject("properties") ?: JsonObject()

        return runCatching {
            when (type) {
                "server.connected" -> OpenCodeEvent.ServerConnected
                "message.part.updated" -> {
                    val part = gson.fromJson(properties.getAsJsonObject("part"), OpenCodePart::class.java)
                    OpenCodeEvent.MessagePartUpdated(part)
                }
                "message.part.delta" -> OpenCodeEvent.MessagePartDelta(
                    sessionId = properties.get("sessionID").asString,
                    messageId = properties.get("messageID").asString,
                    partId = properties.get("partID").asString,
                    field = properties.get("field").asString,
                    delta = properties.get("delta").asString
                )
                "permission.asked" -> OpenCodeEvent.PermissionAsked(
                    PermissionRequest(
                        id = properties.get("id").asString,
                        sessionId = properties.get("sessionID").asString,
                        permission = properties.get("permission").asString,
                        patterns = properties.getAsJsonArray("patterns")
                            ?.mapNotNull { element -> element.takeIf { it.isJsonPrimitive }?.asString }
                            .orEmpty(),
                        metadata = properties.getAsJsonObject("metadata")
                            ?.entrySet()
                            ?.associate { (key, value) -> key to value }
                            .orEmpty()
                    )
                )
                "session.idle" -> OpenCodeEvent.SessionIdle(properties.get("sessionID").asString)
                "session.error" -> OpenCodeEvent.SessionError(
                    sessionId = properties.get("sessionID")?.takeUnless { it.isJsonNull }?.asString,
                    message = properties.get("error")?.toString()
                )
                else -> OpenCodeEvent.Unknown(type, json)
            }
        }.getOrElse { OpenCodeEvent.Unknown(type, json) }
    }
}
