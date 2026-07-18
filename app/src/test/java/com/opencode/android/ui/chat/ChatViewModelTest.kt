package com.opencode.android.ui.chat

import com.opencode.android.api.OpenCodeAgent
import com.opencode.android.api.OpenCodeEvent
import com.opencode.android.api.OpenCodeHealth
import com.opencode.android.api.OpenCodeMessage
import com.opencode.android.api.OpenCodePart
import com.opencode.android.api.OpenCodeSession
import com.opencode.android.api.OpenCodeTime
import com.opencode.android.api.PermissionRequest
import com.opencode.android.api.PromptRequest
import com.opencode.android.api.ProviderCatalog
import com.opencode.android.backend.BackendKind
import com.opencode.android.backend.OpenCodeBackend
import com.opencode.android.backend.PermissionResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
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
    fun `sending blank input does nothing`() = runTest(dispatcher) {
        val backend = FakeBackend()
        val viewModel = ChatViewModel(backend)

        viewModel.sendMessage("   ")
        advanceUntilIdle()

        assertEquals(0, backend.createSessionCalls)
        assertEquals(0, backend.sentPrompts.size)
        assertTrue(viewModel.uiState.value.messages.isEmpty())
    }

    @Test
    fun `sending text creates a session and shows user message immediately`() = runTest(dispatcher) {
        val backend = FakeBackend()
        val viewModel = ChatViewModel(backend)

        viewModel.sendMessage("Hello")
        advanceUntilIdle()

        assertEquals(1, backend.createSessionCalls)
        assertEquals("s1", viewModel.uiState.value.sessionId)
        assertEquals("Hello", viewModel.uiState.value.messages.single().text)
        assertTrue(viewModel.uiState.value.messages.single().isUser)
        assertEquals("Hello", backend.sentPrompts.single().second.text)
    }

    @Test
    fun `streamed text is finalized when session becomes idle`() = runTest(dispatcher) {
        val backend = FakeBackend()
        val viewModel = ChatViewModel(backend)
        viewModel.sendMessage("Hello")
        advanceUntilIdle()

        backend.events.emit(
            OpenCodeEvent.MessagePartUpdated(
                OpenCodePart(
                    id = "p1",
                    sessionId = "s1",
                    messageId = "m-assistant",
                    type = "text",
                    text = "Hi from OpenCode"
                )
            )
        )
        advanceUntilIdle()

        val streaming = viewModel.uiState.value.messages.last()
        assertFalse(streaming.isUser)
        assertTrue(streaming.isStreaming)
        assertEquals("Hi from OpenCode", streaming.text)

        backend.events.emit(OpenCodeEvent.SessionIdle("s1"))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.messages.last().isStreaming)
        assertFalse(viewModel.uiState.value.isRunning)
    }

    @Test
    fun `permission event becomes approval card and successful response removes it`() = runTest(dispatcher) {
        val backend = FakeBackend()
        val viewModel = ChatViewModel(backend)
        viewModel.sendMessage("Check git")
        advanceUntilIdle()

        backend.events.emit(
            OpenCodeEvent.PermissionAsked(
                PermissionRequest(
                    id = "perm1",
                    sessionId = "s1",
                    permission = "bash",
                    patterns = listOf("git status")
                )
            )
        )
        advanceUntilIdle()
        assertEquals("perm1", viewModel.uiState.value.permissions.single().id)

        viewModel.respondToPermission("perm1", PermissionResponse.ONCE, remember = false)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.permissions.isEmpty())
        assertEquals(PermissionResponse.ONCE, backend.permissionResponses.single().third)
    }

    @Test
    fun `abort stops current session and clears running state`() = runTest(dispatcher) {
        val backend = FakeBackend()
        val viewModel = ChatViewModel(backend)
        viewModel.sendMessage("Long task")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isRunning)

        viewModel.abort()
        advanceUntilIdle()

        assertEquals(listOf("s1"), backend.abortedSessions)
        assertFalse(viewModel.uiState.value.isRunning)
    }

    private class FakeBackend : OpenCodeBackend {
        override val id: String = "fake"
        override val displayName: String = "Fake"
        override val kind: BackendKind = BackendKind.REMOTE
        val events = MutableSharedFlow<OpenCodeEvent>(extraBufferCapacity = 20)
        var createSessionCalls = 0
        val sentPrompts = mutableListOf<Pair<String, PromptRequest>>()
        val permissionResponses = mutableListOf<PermissionRecord>()
        val abortedSessions = mutableListOf<String>()

        override suspend fun health(): OpenCodeHealth = OpenCodeHealth(true, "test")
        override suspend fun listSessions(): List<OpenCodeSession> = emptyList()
        override suspend fun createSession(title: String?): OpenCodeSession {
            createSessionCalls++
            return OpenCodeSession(id = "s1", title = title ?: "", time = OpenCodeTime(created = 1))
        }
        override suspend fun listMessages(sessionId: String): List<OpenCodeMessage> = emptyList()
        override suspend fun listProviders(): ProviderCatalog = ProviderCatalog()
        override suspend fun listAgents(): List<OpenCodeAgent> = emptyList()
        override suspend fun sendMessage(sessionId: String, request: PromptRequest) {
            sentPrompts += sessionId to request
        }
        override suspend fun abortSession(sessionId: String): Boolean {
            abortedSessions += sessionId
            return true
        }
        override suspend fun respondToPermission(
            sessionId: String,
            permissionId: String,
            response: PermissionResponse,
            remember: Boolean
        ): Boolean {
            permissionResponses += PermissionRecord(sessionId, permissionId, response, remember)
            return true
        }
        override fun events(): Flow<OpenCodeEvent> = events
    }

    private data class PermissionRecord(
        val sessionId: String,
        val permissionId: String,
        val third: PermissionResponse,
        val remember: Boolean
    )
}
