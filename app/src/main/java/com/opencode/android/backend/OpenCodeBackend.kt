package com.opencode.android.backend

import com.opencode.android.api.OpenCodeAgent
import com.opencode.android.api.OpenCodeEvent
import com.opencode.android.api.OpenCodeHealth
import com.opencode.android.api.OpenCodeMessage
import com.opencode.android.api.OpenCodeSession
import com.opencode.android.api.PromptRequest
import com.opencode.android.api.ProviderCatalog
import kotlinx.coroutines.flow.Flow

enum class BackendKind {
    LOCAL,
    REMOTE
}

enum class PermissionResponse(val apiValue: String) {
    ONCE("once"),
    ALWAYS("always"),
    REJECT("reject")
}

interface OpenCodeBackend {
    val id: String
    val displayName: String
    val kind: BackendKind

    suspend fun health(): OpenCodeHealth
    suspend fun listSessions(): List<OpenCodeSession>
    suspend fun createSession(title: String? = null): OpenCodeSession
    suspend fun listMessages(sessionId: String): List<OpenCodeMessage>
    suspend fun listProviders(): ProviderCatalog
    suspend fun listAgents(): List<OpenCodeAgent>
    suspend fun sendMessage(sessionId: String, request: PromptRequest)
    suspend fun abortSession(sessionId: String): Boolean
    suspend fun respondToPermission(
        sessionId: String,
        permissionId: String,
        response: PermissionResponse,
        remember: Boolean
    ): Boolean
    fun events(): Flow<OpenCodeEvent>
}
