package com.opencode.android.feature.chat

import com.opencode.android.core.api.OpenCodeAgent
import com.opencode.android.core.api.OpenCodeEvent
import com.opencode.android.core.api.OpenCodeHealth
import com.opencode.android.core.api.OpenCodeMessage
import com.opencode.android.core.api.OpenCodeModelVariant
import com.opencode.android.core.api.OpenCodePart
import com.opencode.android.core.api.OpenCodeSession
import com.opencode.android.core.api.OpenCodeTime
import com.opencode.android.core.api.OpenCodeTokenUsage
import com.opencode.android.core.api.PermissionRequest
import com.opencode.android.core.api.PromptRequest
import com.opencode.android.core.api.ProviderCatalog
import com.opencode.android.runtime.BackendKind
import com.opencode.android.runtime.OpenCodeBackend
import com.opencode.android.runtime.PermissionResponse
import com.google.gson.Gson
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
    private val gson = Gson()
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
    fun `selected workspace is used when creating a new session`() = runTest(dispatcher) {
        val backend = FakeBackend()
        val viewModel = ChatViewModel(backend)

        viewModel.selectWorkspace("/root/demo")
        viewModel.sendMessage("Work here")
        advanceUntilIdle()

        assertEquals("/root/demo", backend.lastCreateDirectory)
        assertEquals("/root/demo", viewModel.uiState.value.selectedWorkspacePath)
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
    fun `multiple streamed text parts are combined into one assistant message`() = runTest(dispatcher) {
        val backend = FakeBackend()
        val viewModel = ChatViewModel(backend)
        viewModel.sendMessage("Hello")
        advanceUntilIdle()

        backend.events.emit(
            OpenCodeEvent.MessagePartUpdated(
                OpenCodePart(
                    id = "part-1",
                    sessionId = "s1",
                    messageId = "m-assistant",
                    type = "text",
                    text = "First paragraph."
                )
            )
        )
        backend.events.emit(
            OpenCodeEvent.MessagePartUpdated(
                OpenCodePart(
                    id = "part-2",
                    sessionId = "s1",
                    messageId = "m-assistant",
                    type = "text",
                    text = "\nSecond paragraph."
                )
            )
        )
        advanceUntilIdle()

        val assistantMessages = viewModel.uiState.value.messages.filterNot { it.isUser }
        assertEquals(1, assistantMessages.size)
        assertEquals("First paragraph.\nSecond paragraph.", assistantMessages.single().text)
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
    fun `attachment only message is encoded and sent`() = runTest(dispatcher) {
        val backend = FakeBackend()
        val viewModel = ChatViewModel(backend)

        viewModel.addAttachment("sample.txt", "text/plain", "hello".toByteArray())
        viewModel.sendMessage("")
        advanceUntilIdle()

        assertEquals(1, backend.createSessionCalls)
        val request = backend.sentPrompts.single().second
        assertEquals("Please review the attached file(s).", request.text)
        assertEquals(1, request.attachments.size)
        assertEquals("sample.txt", request.attachments.single().fileName)
        assertEquals("aGVsbG8=", request.attachments.single().base64Data)
        assertTrue(viewModel.uiState.value.pendingAttachments.isEmpty())
        assertTrue(viewModel.uiState.value.messages.single().text.contains("sample.txt"))
    }

    @Test
    fun `selected variant is included in sent prompt`() = runTest(dispatcher) {
        val backend = FakeBackend()
        val viewModel = ChatViewModel(backend)

        viewModel.selectModelMetadata(mapOf("high" to OpenCodeModelVariant()), null)
        viewModel.selectVariant("high")
        viewModel.sendMessage("think")
        advanceUntilIdle()

        assertEquals("high", backend.sentPrompts.single().second.variant)
    }

    @Test
    fun `sent user message retains attachment preview data`() = runTest(dispatcher) {
        val viewModel = ChatViewModel(FakeBackend())
        viewModel.addAttachment("photo.jpg", "image/jpeg", byteArrayOf(1, 2, 3))

        viewModel.sendMessage("look")
        advanceUntilIdle()

        assertEquals("photo.jpg", viewModel.uiState.value.messages.single().attachments.single().fileName)
    }

    @Test
    fun `history exposes context usage when model limit and input tokens exist`() = runTest(dispatcher) {
        val backend = FakeBackend().apply {
            history = listOf(
                OpenCodeMessage(
                    info = com.opencode.android.core.api.OpenCodeMessageInfo(
                        id = "m1",
                        sessionId = "s1",
                        role = "assistant",
                        tokens = OpenCodeTokenUsage(input = 250)
                    )
                )
            )
        }
        val viewModel = ChatViewModel(backend)
        viewModel.selectModelMetadata(emptyMap(), 1_000L)

        viewModel.openSession("s1", "Existing")
        advanceUntilIdle()

        assertEquals(25, viewModel.uiState.value.contextUsagePercent)
    }

    @Test
    fun `oversized attachment is rejected without changing pending list`() = runTest(dispatcher) {
        val viewModel = ChatViewModel(FakeBackend())

        viewModel.addAttachment(
            fileName = "large.bin",
            mimeType = "application/octet-stream",
            bytes = ByteArray(512 * 1024 + 1)
        )

        assertTrue(viewModel.uiState.value.pendingAttachments.isEmpty())
        assertTrue(viewModel.uiState.value.error.orEmpty().contains("512KB"))
    }

    @Test
    fun `tool and command events create structured activity cards`() = runTest(dispatcher) {
        val backend = FakeBackend()
        val viewModel = ChatViewModel(backend)
        viewModel.sendMessage("Run status")
        advanceUntilIdle()

        backend.events.emit(
            OpenCodeEvent.MessagePartUpdated(
                OpenCodePart(
                    id = "tool-1",
                    sessionId = "s1",
                    messageId = "assistant-1",
                    type = "tool",
                    tool = "bash",
                    state = mapOf("command" to gson.toJsonTree("git status"), "stdout" to gson.toJsonTree("clean"))
                )
            )
        )
        advanceUntilIdle()

        val card = viewModel.uiState.value.messages.last()
        assertEquals(ChatItemKind.COMMAND, card.kind)
        assertEquals("bash", card.toolName)
        assertTrue(card.detail.orEmpty().contains("git status"))
        assertTrue(card.detail.orEmpty().contains("clean"))
    }

    @Test
    fun `historical tool and reasoning parts become separate cards`() = runTest(dispatcher) {
        val backend = FakeBackend().apply {
            history = listOf(
                OpenCodeMessage(
                    info = com.opencode.android.core.api.OpenCodeMessageInfo(
                        id = "m1",
                        sessionId = "s1",
                        role = "assistant",
                        time = OpenCodeTime(created = 2)
                    ),
                    parts = listOf(
                        OpenCodePart(id = "reason-1", type = "reasoning", text = "considering"),
                        OpenCodePart(
                            id = "tool-1",
                            type = "tool",
                            tool = "read",
                            state = mapOf("status" to gson.toJsonTree("completed"))
                        )
                    )
                )
            )
        }
        val viewModel = ChatViewModel(backend)

        viewModel.openSession("s1", "Existing")
        advanceUntilIdle()

        assertEquals(listOf(ChatItemKind.REASONING, ChatItemKind.TOOL), viewModel.uiState.value.messages.map { it.kind })
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
        var history: List<OpenCodeMessage> = emptyList()
        var createSessionCalls = 0
        var lastCreateDirectory: String? = null
        val sentPrompts = mutableListOf<Pair<String, PromptRequest>>()
        val permissionResponses = mutableListOf<PermissionRecord>()
        val abortedSessions = mutableListOf<String>()

        override suspend fun health(): OpenCodeHealth = OpenCodeHealth(true, "test")
        override suspend fun listSessions(directory: String?): List<OpenCodeSession> = emptyList()
        override suspend fun createSession(title: String?, directory: String?): OpenCodeSession {
            createSessionCalls++
            lastCreateDirectory = directory
            return OpenCodeSession(
                id = "s1",
                title = title ?: "",
                directory = directory,
                time = OpenCodeTime(created = 1)
            )
        }
        override suspend fun listMessages(sessionId: String): List<OpenCodeMessage> = history
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
