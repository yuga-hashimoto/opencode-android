package com.opencode.android.runtime

import com.opencode.android.core.api.OpenCodeAgent
import com.opencode.android.core.api.OpenCodeEvent
import com.opencode.android.core.api.OpenCodeFileChange
import com.opencode.android.core.api.OpenCodeFileContent
import com.opencode.android.core.api.OpenCodeFileNode
import com.opencode.android.core.api.OpenCodeHealth
import com.opencode.android.core.api.OpenCodeMessage
import com.opencode.android.core.api.OpenCodePathInfo
import com.opencode.android.core.api.OpenCodeProject
import com.opencode.android.core.api.OpenCodeSearchMatch
import com.opencode.android.core.api.OpenCodeSession
import com.opencode.android.core.api.OpenCodeTodo
import com.opencode.android.core.api.OpenCodeVcsInfo
import com.opencode.android.core.api.PromptRequest
import com.opencode.android.core.api.ProviderAuthAuthorization
import com.opencode.android.core.api.ProviderAuthMethod
import com.opencode.android.core.api.ProviderCatalog
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
    suspend fun listSessions(directory: String? = null): List<OpenCodeSession>
    suspend fun createSession(
        title: String? = null,
        directory: String? = null
    ): OpenCodeSession
    suspend fun listMessages(sessionId: String): List<OpenCodeMessage>
    suspend fun listProviders(): ProviderCatalog
    suspend fun listAgents(): List<OpenCodeAgent>
    suspend fun providerAuthMethods(): Map<String, List<ProviderAuthMethod>> =
        unsupported("provider auth methods")
    suspend fun authorizeProvider(
        providerId: String,
        methodIndex: Int,
        inputs: Map<String, String> = emptyMap()
    ): ProviderAuthAuthorization = unsupported("provider OAuth authorization")
    suspend fun setProviderApiKey(
        providerId: String,
        apiKey: String,
        metadata: Map<String, String> = emptyMap()
    ): Boolean = unsupported("provider API authentication")
    suspend fun removeProviderAuth(providerId: String): Boolean =
        unsupported("provider authentication removal")
    suspend fun completeProviderOAuth(providerId: String, methodIndex: Int, code: String?): Boolean =
        unsupported("provider OAuth callback")
    suspend fun listProjects(directory: String? = null): List<OpenCodeProject> = unsupported("projects")
    suspend fun currentProject(directory: String? = null): OpenCodeProject = unsupported("current project")
    suspend fun pathInfo(directory: String? = null): OpenCodePathInfo = unsupported("path info")
    suspend fun listFiles(directory: String, path: String = "."): List<OpenCodeFileNode> =
        unsupported("file list")
    suspend fun readFile(directory: String, path: String): OpenCodeFileContent = unsupported("file read")
    suspend fun fileStatus(directory: String): List<OpenCodeFileChange> = unsupported("file status")
    suspend fun searchText(directory: String, pattern: String): List<OpenCodeSearchMatch> =
        unsupported("text search")
    suspend fun findFiles(
        directory: String,
        query: String,
        includeDirectories: Boolean? = null,
        type: String? = null,
        limit: Int? = null
    ): List<String> = unsupported("file search")
    suspend fun vcsInfo(directory: String): OpenCodeVcsInfo = unsupported("VCS info")
    suspend fun vcsStatus(directory: String): List<OpenCodeFileChange> = unsupported("VCS status")
    suspend fun vcsDiff(
        directory: String,
        mode: String = "git",
        context: Int? = null
    ): List<OpenCodeFileChange> = unsupported("VCS diff")
    suspend fun sessionDiff(
        sessionId: String,
        directory: String? = null,
        messageId: String? = null
    ): List<OpenCodeFileChange> = unsupported("session diff")
    suspend fun sessionTodo(
        sessionId: String,
        directory: String? = null
    ): List<OpenCodeTodo> = unsupported("session todo")
    suspend fun sendMessage(sessionId: String, request: PromptRequest)
    suspend fun abortSession(sessionId: String): Boolean
    suspend fun renameSession(sessionId: String, title: String): OpenCodeSession =
        unsupported("session rename")
    suspend fun deleteSession(sessionId: String): Boolean = unsupported("session delete")
    suspend fun respondToPermission(
        sessionId: String,
        permissionId: String,
        response: PermissionResponse,
        remember: Boolean
    ): Boolean
    fun events(): Flow<OpenCodeEvent>

    private fun unsupported(capability: String): Nothing =
        throw UnsupportedOperationException("OpenCode backend does not support $capability")
}
