package com.opencode.android.api

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class OpenCodeHealth(
    val healthy: Boolean,
    val version: String
)

data class OpenCodeTime(
    val created: Long = 0L,
    val updated: Long? = null,
    val completed: Long? = null
)

data class OpenCodeSession(
    val id: String,
    val slug: String? = null,
    @SerializedName("projectID") val projectId: String? = null,
    val directory: String? = null,
    val path: String? = null,
    val title: String = "",
    val version: String? = null,
    val time: OpenCodeTime = OpenCodeTime()
)

data class OpenCodeModelReference(
    @SerializedName("providerID") val providerId: String,
    @SerializedName("modelID") val modelId: String
)

data class OpenCodeMessageInfo(
    val id: String,
    @SerializedName("sessionID") val sessionId: String,
    val role: String,
    val time: OpenCodeTime = OpenCodeTime(),
    val agent: String? = null,
    val model: OpenCodeModelReference? = null
)

data class OpenCodePart(
    val id: String? = null,
    @SerializedName("sessionID") val sessionId: String? = null,
    @SerializedName("messageID") val messageId: String? = null,
    val type: String,
    val text: String? = null,
    val tool: String? = null,
    val callID: String? = null,
    val state: Map<String, Any?>? = null
)

data class OpenCodeMessage(
    val info: OpenCodeMessageInfo,
    val parts: List<OpenCodePart> = emptyList()
) {
    val text: String
        get() = parts.filter { it.type == "text" }.mapNotNull { it.text }.joinToString("")
}

data class OpenCodeModel(
    val id: String,
    @SerializedName("providerID") val providerId: String? = null,
    val name: String = id,
    val status: String? = null
)

data class OpenCodeProvider(
    val id: String,
    val name: String = id,
    val models: Map<String, OpenCodeModel> = emptyMap()
)

data class ProviderCatalog(
    val all: List<OpenCodeProvider> = emptyList(),
    val default: Map<String, String> = emptyMap(),
    val connected: List<String> = emptyList()
)

data class OpenCodeAgent(
    val name: String,
    val description: String? = null,
    val mode: String? = null,
    val native: Boolean = false
)

data class PromptRequest(
    val text: String,
    val providerId: String? = null,
    val modelId: String? = null,
    val agent: String? = null,
    val noReply: Boolean = false
)

data class PermissionRequest(
    val id: String,
    val sessionId: String,
    val permission: String,
    val patterns: List<String> = emptyList(),
    val metadata: Map<String, JsonElement> = emptyMap()
)

sealed interface OpenCodeEvent {
    data object ServerConnected : OpenCodeEvent
    data class MessagePartUpdated(val part: OpenCodePart) : OpenCodeEvent
    data class PermissionAsked(val request: PermissionRequest) : OpenCodeEvent
    data class SessionIdle(val sessionId: String) : OpenCodeEvent
    data class SessionError(val sessionId: String?, val message: String?) : OpenCodeEvent
    data class Unknown(val type: String, val rawJson: String) : OpenCodeEvent
}

class OpenCodeApiException(
    val statusCode: Int,
    message: String
) : Exception(message)
