package com.opencode.android.feature.chat

import com.opencode.android.core.api.OpenCodeAgent
import com.opencode.android.core.api.OpenCodeEvent
import com.opencode.android.core.api.OpenCodeHealth
import com.opencode.android.core.api.OpenCodeMessage
import com.opencode.android.core.api.OpenCodeSession
import com.opencode.android.core.api.OpenCodeTime
import com.opencode.android.core.api.PromptRequest
import com.opencode.android.core.api.ProviderCatalog
import com.opencode.android.core.api.QuestionOption
import com.opencode.android.core.api.QuestionPrompt
import com.opencode.android.core.api.QuestionRequest
import com.opencode.android.runtime.BackendKind
import com.opencode.android.runtime.OpenCodeBackend
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
class ChatViewModelQuestionTest {
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
    fun `question event is shown only for active session`() = runTest(dispatcher) {
        val backend = FakeBackend()
        val viewModel = ChatViewModel(backend)

        viewModel.openSession("session-1")
        advanceUntilIdle()

        backend.events.emit(OpenCodeEvent.QuestionAsked(request(id = "q-1", sessionId = "session-2")))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.pendingQuestions.isEmpty())
    }

    @Test
    fun `single select answer replaces prior selection`() = runTest(dispatcher) {
        val backend = FakeBackend()
        val viewModel = ChatViewModel(backend)

        viewModel.openSession("session-1")
        advanceUntilIdle()
        backend.events.emit(
            OpenCodeEvent.QuestionAsked(
                request(
                    id = "q-1",
                    sessionId = "session-1",
                    options = listOf("src", "docs")
                )
            )
        )
        advanceUntilIdle()

        viewModel.selectQuestionAnswer("q-1", 0, "src")
        viewModel.selectQuestionAnswer("q-1", 0, "docs")

        assertEquals(listOf("docs"), viewModel.uiState.value.pendingQuestions.single().selectedAnswers.single())
    }

    @Test
    fun `successful answer removes pending question`() = runTest(dispatcher) {
        val backend = FakeBackend()
        val viewModel = ChatViewModel(backend)

        viewModel.openSession("session-1")
        advanceUntilIdle()
        backend.events.emit(
            OpenCodeEvent.QuestionAsked(
                request(
                    id = "q-1",
                    sessionId = "session-1",
                    options = listOf("src", "docs")
                )
            )
        )
        advanceUntilIdle()

        viewModel.selectQuestionAnswer("q-1", 0, "src")
        viewModel.submitQuestion("q-1")
        advanceUntilIdle()

        assertEquals(listOf(listOf("src")), backend.answeredQuestions.single().answers)
        assertTrue(viewModel.uiState.value.pendingQuestions.isEmpty())
    }

    @Test
    fun `failed answer keeps question and trims fallback input`() = runTest(dispatcher) {
        val backend = FakeBackend(answerResult = false)
        val viewModel = ChatViewModel(backend)

        viewModel.openSession("session-1")
        advanceUntilIdle()
        backend.events.emit(
            OpenCodeEvent.QuestionAsked(
                request(
                    id = "q-1",
                    sessionId = "session-1",
                    question = "Type a folder",
                    options = emptyList(),
                    placeholder = "src/main"
                )
            )
        )
        advanceUntilIdle()

        viewModel.selectQuestionAnswer("q-1", 0, "   src/main   ")
        viewModel.submitQuestion("q-1")
        advanceUntilIdle()

        assertEquals(listOf(listOf("src/main")), backend.answeredQuestions.single().answers)
        val pending = viewModel.uiState.value.pendingQuestions.single()
        assertEquals(listOf("src/main"), pending.selectedAnswers.single())
        assertEquals("OpenCode question failed", pending.error)
        assertFalse(pending.isSubmitting)
    }

    private fun request(
        id: String,
        sessionId: String,
        question: String = "Pick a folder",
        options: List<String> = listOf("src"),
        placeholder: String? = null
    ) = QuestionRequest(
        id = id,
        sessionId = sessionId,
        multiple = false,
        questions = listOf(
            QuestionPrompt(
                question = question,
                header = "Folder",
                options = options.map(::QuestionOption),
                placeholder = placeholder
            )
        )
    )

    private class FakeBackend(
        private val answerResult: Boolean = true
    ) : OpenCodeBackend {
        override val id: String = "fake"
        override val displayName: String = "Fake"
        override val kind: BackendKind = BackendKind.REMOTE
        val events = MutableSharedFlow<OpenCodeEvent>(extraBufferCapacity = 20)
        val answeredQuestions = mutableListOf<AnswerRecord>()

        override suspend fun health(): OpenCodeHealth = OpenCodeHealth(true, "test")
        override suspend fun listSessions(directory: String?): List<OpenCodeSession> = emptyList()
        override suspend fun createSession(title: String?, directory: String?): OpenCodeSession =
            OpenCodeSession(
                id = "session-1",
                directory = directory,
                title = title.orEmpty(),
                time = OpenCodeTime(created = 1)
            )

        override suspend fun listMessages(sessionId: String): List<OpenCodeMessage> = emptyList()
        override suspend fun listProviders(): ProviderCatalog = ProviderCatalog()
        override suspend fun listAgents(): List<OpenCodeAgent> = emptyList()
        override suspend fun sendMessage(sessionId: String, request: PromptRequest) = Unit
        override suspend fun abortSession(sessionId: String): Boolean = true
        override suspend fun respondToPermission(
            sessionId: String,
            permissionId: String,
            response: com.opencode.android.runtime.PermissionResponse,
            remember: Boolean
        ): Boolean = true

        override suspend fun answerQuestion(
            sessionId: String,
            requestId: String,
            answers: List<List<String>>
        ): Boolean {
            answeredQuestions += AnswerRecord(sessionId, requestId, answers)
            return answerResult
        }

        override fun events(): Flow<OpenCodeEvent> = events
    }

    private data class AnswerRecord(
        val sessionId: String,
        val requestId: String,
        val answers: List<List<String>>
    )
}
