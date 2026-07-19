package com.opencode.android.feature.chat

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
import com.opencode.android.data.connection.ConnectionProfile
import com.opencode.android.runtime.BackendKind
import com.opencode.android.runtime.PermissionResponse
import com.opencode.android.runtime.RuntimeConnectionStore
import com.opencode.android.runtime.RuntimeRegistry
import com.opencode.android.runtime.RuntimeState
import com.opencode.android.runtime.RuntimeTarget
import com.opencode.android.runtime.RuntimeType
import com.opencode.android.runtime.WorkspaceRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionHandoffRemoteTest {

    @Test
    fun `handoff creates session on target and sends transcript prompt`() = runBlocking {
        val target = RecordingTarget("target", "Mac")
        val source = RecordingTarget("source", "Phone", RuntimeType.LOCAL)
        val store = FakeStore()
        val registry = RuntimeRegistry(
            store = store,
            localTarget = source,
            remoteFactory = { target }
        )
        registry.upsertRemote(
            ConnectionProfile(
                id = "target",
                name = "Mac",
                baseUrl = "https://target.example.test"
            )
        )

        val pack = SessionHandoffPackage(
            sourceRuntimeId = "source",
            sourceRuntimeName = "Phone",
            sessionId = "s1",
            sessionTitle = "Bugfix",
            directory = "/repo",
            transcript = "### user\nFix it",
            messageCount = 1
        )

        val result = SessionHandoff.handoff(registry, pack, "target")
        assertTrue(result.isSuccess)
        assertEquals("sess-created", result.getOrThrow())
        assertEquals(1, target.connectCalls)
        assertEquals(1, target.createdSessions.size)
        assertEquals("/repo", target.createdSessions.single().second)
        assertEquals(1, target.sentPrompts.size)
        assertTrue(target.sentPrompts.single().second.text.contains("Bugfix"))
        assertTrue(target.sentPrompts.single().second.text.contains("Fix it"))
    }

    private class RecordingTarget(
        override val id: String,
        override val displayName: String,
        type: RuntimeType = RuntimeType.REMOTE
    ) : RuntimeTarget {
        override val type = type
        override val kind = if (type == RuntimeType.LOCAL) BackendKind.LOCAL else BackendKind.REMOTE
        override val state = MutableStateFlow<RuntimeState>(RuntimeState.Connected("1.0.0"))
        var connectCalls = 0
        val createdSessions = mutableListOf<Pair<String, String?>>()
        val sentPrompts = mutableListOf<Pair<String, PromptRequest>>()

        override suspend fun connect(): Result<OpenCodeHealth> {
            connectCalls++
            return Result.success(OpenCodeHealth(true, "1.0.0"))
        }
        override fun disconnect() = Unit
        override suspend fun listWorkspaces(): List<WorkspaceRef> = emptyList()
        override suspend fun health(): OpenCodeHealth = OpenCodeHealth(true, "1.0.0")
        override suspend fun listSessions(directory: String?): List<OpenCodeSession> = emptyList()
        override suspend fun createSession(title: String?, directory: String?): OpenCodeSession =
            OpenCodeSession(
                id = "sess-created",
                directory = directory,
                title = title.orEmpty()
            ).also { createdSessions += it.id to directory }
        override suspend fun listMessages(sessionId: String): List<OpenCodeMessage> = emptyList()
        override suspend fun listProviders(): ProviderCatalog = ProviderCatalog()
        override suspend fun listAgents(): List<OpenCodeAgent> = emptyList()
        override suspend fun listProjects(directory: String?): List<OpenCodeProject> = emptyList()
        override suspend fun currentProject(directory: String?): OpenCodeProject =
            OpenCodeProject(id = "p", worktree = directory.orEmpty())
        override suspend fun pathInfo(directory: String?): OpenCodePathInfo =
            OpenCodePathInfo("", "", "", "", directory.orEmpty())
        override suspend fun listFiles(directory: String, path: String): List<OpenCodeFileNode> = emptyList()
        override suspend fun readFile(directory: String, path: String): OpenCodeFileContent =
            OpenCodeFileContent("text", "", null)
        override suspend fun fileStatus(directory: String): List<OpenCodeFileChange> = emptyList()
        override suspend fun searchText(directory: String, pattern: String): List<OpenCodeSearchMatch> = emptyList()
        override suspend fun findFiles(
            directory: String,
            query: String,
            includeDirectories: Boolean?,
            type: String?,
            limit: Int?
        ): List<String> = emptyList()
        override suspend fun vcsInfo(directory: String): OpenCodeVcsInfo = OpenCodeVcsInfo()
        override suspend fun vcsStatus(directory: String): List<OpenCodeFileChange> = emptyList()
        override suspend fun vcsDiff(directory: String, mode: String, context: Int?): List<OpenCodeFileChange> = emptyList()
        override suspend fun sessionDiff(
            sessionId: String,
            directory: String?,
            messageId: String?
        ): List<OpenCodeFileChange> = emptyList()
        override suspend fun sessionTodo(sessionId: String, directory: String?): List<OpenCodeTodo> = emptyList()
        override suspend fun sendMessage(sessionId: String, request: PromptRequest) {
            sentPrompts += sessionId to request
        }
        override suspend fun abortSession(sessionId: String): Boolean = true
        override suspend fun respondToPermission(
            sessionId: String,
            permissionId: String,
            response: PermissionResponse,
            remember: Boolean
        ): Boolean = true
        override fun events(): Flow<OpenCodeEvent> = emptyFlow()
    }

    private class FakeStore : RuntimeConnectionStore {
        override var selectedRuntimeId: String? = null
        private val profiles = mutableListOf<ConnectionProfile>()
        override fun connections(): List<ConnectionProfile> = profiles.toList()
        override fun upsertConnection(profile: ConnectionProfile) {
            profiles.removeAll { it.id == profile.id }
            profiles += profile
        }
        override fun deleteConnection(id: String) {
            profiles.removeAll { it.id == id }
        }
    }
}
