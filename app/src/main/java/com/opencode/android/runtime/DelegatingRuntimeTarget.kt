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
import com.opencode.android.core.api.ProviderCatalog
import kotlinx.coroutines.flow.Flow

/**
 * Shared OpenCodeBackend forwarding used by local and remote runtime targets.
 */
abstract class DelegatingRuntimeTarget(
    protected val backend: OpenCodeBackend
) : RuntimeTarget {
    override val id: String get() = backend.id
    override val displayName: String get() = backend.displayName
    override val kind: BackendKind get() = backend.kind

    override suspend fun listWorkspaces(): List<WorkspaceRef> {
        val current = runCatching { backend.pathInfo().directory }.getOrNull()
        val sessions = backend.listSessions()
        val projects = runCatching { backend.listProjects() }.getOrDefault(emptyList())
        return mergeWorkspaceRefs(current, sessions, projects)
    }

    override suspend fun health(): OpenCodeHealth = backend.health()

    override suspend fun listSessions(directory: String?): List<OpenCodeSession> {
        if (directory != null) return backend.listSessions(directory)
        val projects = runCatching { backend.listProjects() }.getOrDefault(emptyList())
        val directories = buildList<String?> {
            add(null)
            projects
                .asSequence()
                .filterNot { it.id == "global" }
                .map { it.worktree }
                .filter { it.isNotBlank() && it != "/" }
                .forEach(::add)
        }.distinct()
        val sessionLists = directories.map { scopedDirectory ->
            runCatching { backend.listSessions(scopedDirectory) }.getOrDefault(emptyList())
        }
        return mergeSessionLists(sessionLists)
    }

    override suspend fun createSession(title: String?, directory: String?): OpenCodeSession =
        backend.createSession(title, directory)
    override suspend fun listMessages(sessionId: String): List<OpenCodeMessage> =
        backend.listMessages(sessionId)
    override suspend fun listProviders(): ProviderCatalog = backend.listProviders()
    override suspend fun listAgents(): List<OpenCodeAgent> = backend.listAgents()
    override suspend fun listProjects(directory: String?): List<OpenCodeProject> =
        backend.listProjects(directory)
    override suspend fun currentProject(directory: String?): OpenCodeProject =
        backend.currentProject(directory)
    override suspend fun pathInfo(directory: String?): OpenCodePathInfo = backend.pathInfo(directory)
    override suspend fun listFiles(directory: String, path: String): List<OpenCodeFileNode> =
        backend.listFiles(directory, path)
    override suspend fun readFile(directory: String, path: String): OpenCodeFileContent =
        backend.readFile(directory, path)
    override suspend fun fileStatus(directory: String): List<OpenCodeFileChange> =
        backend.fileStatus(directory)
    override suspend fun searchText(directory: String, pattern: String): List<OpenCodeSearchMatch> =
        backend.searchText(directory, pattern)
    override suspend fun findFiles(
        directory: String,
        query: String,
        includeDirectories: Boolean?,
        type: String?,
        limit: Int?
    ): List<String> = backend.findFiles(directory, query, includeDirectories, type, limit)
    override suspend fun vcsInfo(directory: String): OpenCodeVcsInfo = backend.vcsInfo(directory)
    override suspend fun vcsStatus(directory: String): List<OpenCodeFileChange> =
        backend.vcsStatus(directory)
    override suspend fun vcsDiff(directory: String, mode: String, context: Int?): List<OpenCodeFileChange> =
        backend.vcsDiff(directory, mode, context)
    override suspend fun sessionDiff(
        sessionId: String,
        directory: String?,
        messageId: String?
    ): List<OpenCodeFileChange> = backend.sessionDiff(sessionId, directory, messageId)
    override suspend fun sessionTodo(sessionId: String, directory: String?): List<OpenCodeTodo> =
        backend.sessionTodo(sessionId, directory)
    override suspend fun sendMessage(sessionId: String, request: PromptRequest) =
        backend.sendMessage(sessionId, request)
    override suspend fun abortSession(sessionId: String): Boolean = backend.abortSession(sessionId)
    override suspend fun respondToPermission(
        sessionId: String,
        permissionId: String,
        response: PermissionResponse,
        remember: Boolean
    ): Boolean = backend.respondToPermission(sessionId, permissionId, response, remember)
    override fun events(): Flow<OpenCodeEvent> = backend.events()
}
