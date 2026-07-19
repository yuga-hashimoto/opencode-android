package com.opencode.android.runtime.local

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
import com.opencode.android.core.api.ProviderCatalog
import com.opencode.android.core.api.ProviderAuthAuthorization
import com.opencode.android.core.api.ProviderAuthMethod
import com.opencode.android.data.connection.ConnectionProfile
import com.opencode.android.runtime.BackendKind
import com.opencode.android.runtime.OpenCodeBackend
import com.opencode.android.runtime.PermissionResponse
import com.opencode.android.runtime.remote.RemoteOpenCodeBackend
import kotlinx.coroutines.flow.Flow

class LocalOpenCodeBackend(
    private val portProvider: () -> Int?,
    private val backendFactory: (ConnectionProfile) -> RemoteOpenCodeBackend = { profile ->
        RemoteOpenCodeBackend(profile)
    }
) : OpenCodeBackend {
    constructor(runtimeManager: LocalRuntimeManager) : this(
        portProvider = runtimeManager::installedPort
    )

    override val id: String = "local-android"
    override val displayName: String = "Android local"
    override val kind: BackendKind = BackendKind.LOCAL

    @Volatile
    private var cached: CachedDelegate? = null

    internal fun delegate(): RemoteOpenCodeBackend {
        val port = portProvider()
            ?: error("Android local OpenCode runtime is not installed")
        cached?.takeIf { it.port == port }?.let { return it.backend }

        val backend = backendFactory(
            ConnectionProfile(
                id = id,
                name = displayName,
                baseUrl = "http://127.0.0.1:$port/",
                username = "opencode",
                allowInsecureLan = true
            )
        )
        cached = CachedDelegate(port, backend)
        return backend
    }

    fun invalidate() {
        cached = null
    }

    override suspend fun health(): OpenCodeHealth = delegate().health()
    override suspend fun listSessions(directory: String?): List<OpenCodeSession> =
        delegate().listSessions(directory)
    override suspend fun createSession(title: String?, directory: String?): OpenCodeSession =
        delegate().createSession(title, directory)
    override suspend fun listMessages(sessionId: String): List<OpenCodeMessage> = delegate().listMessages(sessionId)
    override suspend fun deleteSession(sessionId: String): Boolean = delegate().deleteSession(sessionId)
    override suspend fun renameSession(sessionId: String, title: String): OpenCodeSession =
        delegate().renameSession(sessionId, title)
    override suspend fun listCommands(): List<com.opencode.android.core.api.OpenCodeCommand> =
        delegate().listCommands()
    override suspend fun listProviders(): ProviderCatalog = delegate().listProviders()
    override suspend fun listAgents(): List<OpenCodeAgent> = delegate().listAgents()
    override suspend fun providerAuthMethods(): Map<String, List<ProviderAuthMethod>> = delegate().providerAuthMethods()
    override suspend fun authorizeProvider(providerId: String, methodIndex: Int): ProviderAuthAuthorization =
        delegate().authorizeProvider(providerId, methodIndex)
    override suspend fun completeProviderOAuth(providerId: String, methodIndex: Int, code: String?): Boolean =
        delegate().completeProviderOAuth(providerId, methodIndex, code)
    override suspend fun listProjects(directory: String?): List<OpenCodeProject> =
        delegate().listProjects(directory)
    override suspend fun currentProject(directory: String?): OpenCodeProject =
        delegate().currentProject(directory)
    override suspend fun pathInfo(directory: String?): OpenCodePathInfo = delegate().pathInfo(directory)
    override suspend fun listFiles(directory: String, path: String): List<OpenCodeFileNode> =
        delegate().listFiles(directory, path)
    override suspend fun readFile(directory: String, path: String): OpenCodeFileContent =
        delegate().readFile(directory, path)
    override suspend fun fileStatus(directory: String): List<OpenCodeFileChange> =
        delegate().fileStatus(directory)
    override suspend fun searchText(directory: String, pattern: String): List<OpenCodeSearchMatch> =
        delegate().searchText(directory, pattern)
    override suspend fun findFiles(
        directory: String,
        query: String,
        includeDirectories: Boolean?,
        type: String?,
        limit: Int?
    ): List<String> = delegate().findFiles(directory, query, includeDirectories, type, limit)
    override suspend fun vcsInfo(directory: String): OpenCodeVcsInfo = delegate().vcsInfo(directory)
    override suspend fun vcsStatus(directory: String): List<OpenCodeFileChange> =
        delegate().vcsStatus(directory)
    override suspend fun vcsDiff(directory: String, mode: String, context: Int?): List<OpenCodeFileChange> =
        delegate().vcsDiff(directory, mode, context)
    override suspend fun sessionDiff(
        sessionId: String,
        directory: String?,
        messageId: String?
    ): List<OpenCodeFileChange> = delegate().sessionDiff(sessionId, directory, messageId)
    override suspend fun sessionTodo(sessionId: String, directory: String?): List<OpenCodeTodo> =
        delegate().sessionTodo(sessionId, directory)
    override suspend fun sendMessage(sessionId: String, request: PromptRequest) = delegate().sendMessage(sessionId, request)
    override suspend fun abortSession(sessionId: String): Boolean = delegate().abortSession(sessionId)
    override suspend fun respondToPermission(
        sessionId: String,
        permissionId: String,
        response: PermissionResponse,
        remember: Boolean
    ): Boolean = delegate().respondToPermission(sessionId, permissionId, response, remember)
    override fun events(): Flow<OpenCodeEvent> = delegate().events()

    private data class CachedDelegate(
        val port: Int,
        val backend: RemoteOpenCodeBackend
    )
}
