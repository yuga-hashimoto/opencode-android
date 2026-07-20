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
import com.opencode.android.core.api.ProviderAuthAuthorization
import com.opencode.android.core.api.ProviderAuthMethod
import com.opencode.android.core.api.ProviderCatalog
import com.opencode.android.runtime.BackendKind
import com.opencode.android.runtime.LocalRuntimeStatus
import com.opencode.android.runtime.PermissionResponse
import com.opencode.android.runtime.RuntimeState
import com.opencode.android.runtime.RuntimeTarget
import com.opencode.android.runtime.RuntimeType
import com.opencode.android.runtime.WorkspaceRef
import com.opencode.android.runtime.mergeSessionLists
import com.opencode.android.runtime.mergeWorkspaceRefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LocalRuntimeTarget(
    private val runtimeManager: LocalRuntimeManager,
    private val backend: LocalOpenCodeBackend = LocalOpenCodeBackend(runtimeManager)
) : RuntimeTarget {
    override val id: String = "local-android"
    override val displayName: String = "このAndroid端末"
    override val type: RuntimeType = RuntimeType.LOCAL
    override val kind: BackendKind = BackendKind.LOCAL

    private val mutableState = MutableStateFlow(mapStatus(runtimeManager.status()))
    override val state: StateFlow<RuntimeState> = mutableState.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            runtimeManager.state.collect { status ->
                if (status !is LocalRuntimeStatus.Ready) {
                    backend.invalidate()
                }
                mutableState.value = mapStatus(status)
            }
        }
    }

    fun refreshLocalState(): RuntimeState = mapStatus(runtimeManager.status()).also {
        mutableState.value = it
    }

    override suspend fun connect(): Result<OpenCodeHealth> {
        val localStatus = runtimeManager.status()
        if (localStatus !is LocalRuntimeStatus.Ready) {
            val state = mapStatus(localStatus)
            mutableState.value = state
            return Result.failure(IllegalStateException(state.describe()))
        }

        mutableState.value = RuntimeState.Connecting
        return runCatching { backend.health() }
            .onSuccess { health ->
                mutableState.value = if (health.healthy) {
                    RuntimeState.Connected(health.version)
                } else {
                    RuntimeState.Failed("ローカルOpenCodeが正常状態を返しませんでした")
                }
            }
            .onFailure { error ->
                mutableState.value = RuntimeState.Failed(error.message ?: "ローカルOpenCodeへ接続できません")
            }
    }

    override fun disconnect() {
        mutableState.value = mapStatus(runtimeManager.status())
    }

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
    override suspend fun listMessages(sessionId: String): List<OpenCodeMessage> = backend.listMessages(sessionId)
    override suspend fun listProviders(): ProviderCatalog = backend.listProviders()
    override suspend fun listAgents(): List<OpenCodeAgent> = backend.listAgents()
    override suspend fun providerAuthMethods(): Map<String, List<ProviderAuthMethod>> =
        backend.providerAuthMethods()
    override suspend fun authorizeProvider(
        providerId: String,
        methodIndex: Int,
        inputs: Map<String, String>
    ): ProviderAuthAuthorization = backend.authorizeProvider(providerId, methodIndex, inputs)
    override suspend fun setProviderApiKey(
        providerId: String,
        apiKey: String,
        metadata: Map<String, String>
    ): Boolean = backend.setProviderApiKey(providerId, apiKey, metadata)
    override suspend fun removeProviderAuth(providerId: String): Boolean =
        backend.removeProviderAuth(providerId)
    override suspend fun completeProviderOAuth(
        providerId: String,
        methodIndex: Int,
        code: String?
    ): Boolean = backend.completeProviderOAuth(providerId, methodIndex, code)
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
    override suspend fun sendMessage(sessionId: String, request: PromptRequest) = backend.sendMessage(sessionId, request)
    override suspend fun abortSession(sessionId: String): Boolean = backend.abortSession(sessionId)
    override suspend fun renameSession(sessionId: String, title: String): OpenCodeSession =
        backend.renameSession(sessionId, title)
    override suspend fun deleteSession(sessionId: String): Boolean = backend.deleteSession(sessionId)
    override suspend fun respondToPermission(
        sessionId: String,
        permissionId: String,
        response: PermissionResponse,
        remember: Boolean
    ): Boolean = backend.respondToPermission(sessionId, permissionId, response, remember)
    override fun events(): Flow<OpenCodeEvent> = backend.events()

    private fun mapStatus(status: LocalRuntimeStatus): RuntimeState = when (status) {
        LocalRuntimeStatus.NotInstalled -> RuntimeState.Unavailable("ローカルランタイムは未インストールです")
        is LocalRuntimeStatus.UnsupportedAbi -> RuntimeState.Unavailable("未対応ABI: ${status.abi}")
        is LocalRuntimeStatus.Installing -> RuntimeState.Connecting
        is LocalRuntimeStatus.Starting -> RuntimeState.Connecting
        is LocalRuntimeStatus.Updating -> RuntimeState.Connecting
        is LocalRuntimeStatus.Stopped -> RuntimeState.Disconnected
        is LocalRuntimeStatus.Broken -> RuntimeState.Failed(status.reason)
        is LocalRuntimeStatus.Ready -> RuntimeState.Connected(status.version)
    }

    private fun RuntimeState.describe(): String = when (this) {
        RuntimeState.Disconnected -> "ローカルランタイムは停止しています"
        RuntimeState.Connecting -> "ローカルランタイムへ接続中です"
        is RuntimeState.Connected -> "OpenCode $version"
        is RuntimeState.Unavailable -> reason
        is RuntimeState.Failed -> message
    }
}
