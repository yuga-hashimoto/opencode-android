package com.opencode.android.feature.activity

import com.opencode.android.core.api.OpenCodeAgent
import com.opencode.android.core.api.OpenCodeEvent
import com.opencode.android.core.api.OpenCodeFileChange
import com.opencode.android.core.api.OpenCodeHealth
import com.opencode.android.core.api.OpenCodeMessage
import com.opencode.android.core.api.OpenCodeSession
import com.opencode.android.core.api.OpenCodeTime
import com.opencode.android.core.api.OpenCodeTodo
import com.opencode.android.core.api.PromptRequest
import com.opencode.android.core.api.ProviderCatalog
import com.opencode.android.runtime.BackendKind
import com.opencode.android.runtime.OpenCodeBackend
import com.opencode.android.runtime.PermissionResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads todo and diff for selected session`() = runTest(dispatcher) {
        val session = OpenCodeSession(
            id = "ses_1",
            title = "Implement feature",
            directory = "/repo",
            time = OpenCodeTime(created = 1)
        )
        val viewModel = SessionDetailViewModel(FakeBackend(), session)

        advanceUntilIdle()

        assertEquals("Run tests", viewModel.state.value.todos.single().content)
        assertEquals("src/Main.kt", viewModel.state.value.diff.single().displayPath)
        assertFalse(viewModel.state.value.isLoading)
    }

    private class FakeBackend : OpenCodeBackend {
        override val id: String = "fake"
        override val displayName: String = "Fake"
        override val kind: BackendKind = BackendKind.REMOTE

        override suspend fun health(): OpenCodeHealth = OpenCodeHealth(true, "1.18.3")
        override suspend fun listSessions(directory: String?): List<OpenCodeSession> = emptyList()
        override suspend fun createSession(title: String?, directory: String?): OpenCodeSession = error("unused")
        override suspend fun listMessages(sessionId: String): List<OpenCodeMessage> = emptyList()
        override suspend fun listProviders(): ProviderCatalog = ProviderCatalog()
        override suspend fun listAgents(): List<OpenCodeAgent> = emptyList()
        override suspend fun sessionDiff(
            sessionId: String,
            directory: String?,
            messageId: String?
        ): List<OpenCodeFileChange> = listOf(
            OpenCodeFileChange(
                file = "src/Main.kt",
                patch = "@@ -1 +1 @@\n-old\n+new",
                additions = 1.0,
                deletions = 1.0,
                status = "modified"
            )
        )
        override suspend fun sessionTodo(sessionId: String, directory: String?): List<OpenCodeTodo> = listOf(
            OpenCodeTodo("Run tests", "in_progress", "high")
        )
        override suspend fun sendMessage(sessionId: String, request: PromptRequest) = Unit
        override suspend fun abortSession(sessionId: String): Boolean = true
        override suspend fun respondToPermission(
            sessionId: String,
            permissionId: String,
            response: PermissionResponse,
            remember: Boolean
        ): Boolean = true
        override fun events(): Flow<OpenCodeEvent> = emptyFlow()
    }
}
