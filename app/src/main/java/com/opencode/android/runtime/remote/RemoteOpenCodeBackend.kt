package com.opencode.android.runtime.remote

import com.opencode.android.core.api.OpenCodeAgent
import com.opencode.android.core.api.OpenCodeApiClient
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
import com.opencode.android.data.connection.ConnectionProfile
import com.opencode.android.runtime.BackendKind
import com.opencode.android.runtime.OpenCodeBackend
import com.opencode.android.runtime.PermissionResponse
import kotlinx.coroutines.flow.Flow

class RemoteOpenCodeBackend(
    private val profile: ConnectionProfile,
    private val client: OpenCodeApiClient = OpenCodeApiClient(profile)
) : OpenCodeBackend {
    override val id: String = profile.id
    override val displayName: String = profile.name
    override val kind: BackendKind = BackendKind.REMOTE

    override suspend fun health(): OpenCodeHealth = client.health()
    override suspend fun listSessions(directory: String?): List<OpenCodeSession> =
        client.sessions(directory)
    override suspend fun createSession(title: String?, directory: String?): OpenCodeSession =
        client.createSession(title, directory)
    override suspend fun listMessages(sessionId: String): List<OpenCodeMessage> = client.messages(sessionId)
    override suspend fun listProviders(): ProviderCatalog = client.providers()
    override suspend fun listAgents(): List<OpenCodeAgent> = client.agents()
    override suspend fun listProjects(directory: String?): List<OpenCodeProject> = client.projects(directory)
    override suspend fun currentProject(directory: String?): OpenCodeProject = client.currentProject(directory)
    override suspend fun pathInfo(directory: String?): OpenCodePathInfo = client.pathInfo(directory)
    override suspend fun listFiles(directory: String, path: String): List<OpenCodeFileNode> =
        client.files(directory, path)
    override suspend fun readFile(directory: String, path: String): OpenCodeFileContent =
        client.fileContent(directory, path)
    override suspend fun fileStatus(directory: String): List<OpenCodeFileChange> = client.fileStatus(directory)
    override suspend fun searchText(directory: String, pattern: String): List<OpenCodeSearchMatch> =
        client.searchText(directory, pattern)
    override suspend fun findFiles(
        directory: String,
        query: String,
        includeDirectories: Boolean?,
        type: String?,
        limit: Int?
    ): List<String> = client.findFiles(directory, query, includeDirectories, type, limit)
    override suspend fun vcsInfo(directory: String): OpenCodeVcsInfo = client.vcsInfo(directory)
    override suspend fun vcsStatus(directory: String): List<OpenCodeFileChange> = client.vcsStatus(directory)
    override suspend fun vcsDiff(directory: String, mode: String, context: Int?): List<OpenCodeFileChange> =
        client.vcsDiff(directory, mode, context)
    override suspend fun sessionDiff(
        sessionId: String,
        directory: String?,
        messageId: String?
    ): List<OpenCodeFileChange> = client.sessionDiff(sessionId, directory, messageId)
    override suspend fun sessionTodo(sessionId: String, directory: String?): List<OpenCodeTodo> =
        client.sessionTodo(sessionId, directory)
    override suspend fun sendMessage(sessionId: String, request: PromptRequest) =
        client.promptAsync(sessionId, request)
    override suspend fun abortSession(sessionId: String): Boolean = client.abortSession(sessionId)
    override suspend fun respondToPermission(
        sessionId: String,
        permissionId: String,
        response: PermissionResponse,
        remember: Boolean
    ): Boolean = client.respondPermission(
        sessionId = sessionId,
        permissionId = permissionId,
        response = response.apiValue,
        remember = remember
    )
    override fun events(): Flow<OpenCodeEvent> = client.events()
}
