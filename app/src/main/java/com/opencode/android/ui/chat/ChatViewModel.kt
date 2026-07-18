package com.opencode.android.ui.chat

import android.speech.tts.TextToSpeech
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.android.api.OpenCodeEvent
import com.opencode.android.api.OpenCodeMessage
import com.opencode.android.api.PermissionRequest
import com.opencode.android.api.PromptRequest
import com.opencode.android.backend.OpenCodeBackend
import com.opencode.android.backend.PermissionResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false
)

data class ChatUiState(
    val backendName: String = "",
    val sessionId: String? = null,
    val sessionTitle: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val permissions: List<PermissionRequest> = emptyList(),
    val isConnected: Boolean = false,
    val isRunning: Boolean = false,
    val isLoadingHistory: Boolean = false,
    val isListening: Boolean = false,
    val isThinking: Boolean = false,
    val isSpeaking: Boolean = false,
    val partialText: String = "",
    val selectedProviderId: String? = null,
    val selectedModelId: String? = null,
    val selectedAgentId: String? = null,
    val error: String? = null
)

class ChatViewModel(
    private val backend: OpenCodeBackend? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ChatUiState(backendName = backend?.displayName.orEmpty())
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var eventJob: Job? = null
    private var tts: TextToSpeech? = null
    private val streamedParts = mutableMapOf<String, LinkedHashMap<String, String>>()

    init {
        if (backend != null) {
            eventJob = viewModelScope.launch {
                backend.events()
                    .catch { error ->
                        _uiState.update { it.copy(error = error.safeMessage()) }
                    }
                    .collect(::handleEvent)
            }
            viewModelScope.launch {
                runCatching { backend.health() }
                    .onSuccess { health ->
                        _uiState.update {
                            it.copy(
                                isConnected = health.healthy,
                                backendName = "${backend.displayName} · ${health.version}"
                            )
                        }
                    }
                    .onFailure { error ->
                        _uiState.update { it.copy(error = error.safeMessage()) }
                    }
            }
        }
    }

    fun selectConfiguration(providerId: String?, modelId: String?, agentId: String?) {
        _uiState.update {
            it.copy(
                selectedProviderId = providerId,
                selectedModelId = modelId,
                selectedAgentId = agentId
            )
        }
    }

    fun openSession(sessionId: String, title: String = "") {
        val currentBackend = backend ?: return
        streamedParts.clear()
        _uiState.update {
            it.copy(
                sessionId = sessionId,
                sessionTitle = title,
                isLoadingHistory = true,
                messages = emptyList(),
                permissions = emptyList(),
                error = null
            )
        }
        viewModelScope.launch {
            runCatching { currentBackend.listMessages(sessionId) }
                .onSuccess { messages ->
                    _uiState.update {
                        it.copy(
                            isLoadingHistory = false,
                            messages = messages.mapNotNull(::toUiMessage)
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoadingHistory = false, error = error.safeMessage())
                    }
                }
        }
    }

    fun newSession() {
        streamedParts.clear()
        _uiState.update {
            it.copy(
                sessionId = null,
                sessionTitle = "",
                messages = emptyList(),
                permissions = emptyList(),
                isRunning = false,
                error = null
            )
        }
    }

    fun sendMessage(text: String) {
        val normalized = text.trim()
        if (normalized.isEmpty()) return
        val currentBackend = backend
        if (currentBackend == null) {
            _uiState.update { it.copy(error = "OpenCode connection is not configured") }
            return
        }

        val userMessage = ChatMessage(text = normalized, isUser = true)
        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                isRunning = true,
                isThinking = true,
                partialText = "",
                error = null
            )
        }

        viewModelScope.launch {
            runCatching {
                val existingSessionId = _uiState.value.sessionId
                val session = if (existingSessionId == null) {
                    currentBackend.createSession(normalized.take(60))
                } else {
                    null
                }
                val targetSessionId = existingSessionId ?: requireNotNull(session).id
                if (session != null) {
                    _uiState.update {
                        it.copy(sessionId = session.id, sessionTitle = session.title)
                    }
                }
                currentBackend.sendMessage(
                    targetSessionId,
                    PromptRequest(
                        text = normalized,
                        providerId = _uiState.value.selectedProviderId,
                        modelId = _uiState.value.selectedModelId,
                        agent = _uiState.value.selectedAgentId
                    )
                )
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        isThinking = false,
                        error = error.safeMessage()
                    )
                }
            }
        }
    }

    fun respondToPermission(
        permissionId: String,
        response: PermissionResponse,
        remember: Boolean
    ) {
        val currentBackend = backend ?: return
        val permission = _uiState.value.permissions.firstOrNull { it.id == permissionId } ?: return
        viewModelScope.launch {
            runCatching {
                currentBackend.respondToPermission(
                    permission.sessionId,
                    permission.id,
                    response,
                    remember
                )
            }.onSuccess { accepted ->
                if (accepted) {
                    _uiState.update { state ->
                        state.copy(permissions = state.permissions.filterNot { it.id == permissionId })
                    }
                }
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.safeMessage()) }
            }
        }
    }

    fun abort() {
        val currentBackend = backend ?: return
        val sessionId = _uiState.value.sessionId ?: return
        viewModelScope.launch {
            runCatching { currentBackend.abortSession(sessionId) }
                .onSuccess {
                    _uiState.update {
                        it.copy(isRunning = false, isThinking = false)
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.safeMessage()) }
                }
        }
    }

    private fun handleEvent(event: OpenCodeEvent) {
        val activeSession = _uiState.value.sessionId
        when (event) {
            OpenCodeEvent.ServerConnected -> {
                _uiState.update { it.copy(isConnected = true) }
            }
            is OpenCodeEvent.MessagePartUpdated -> {
                val part = event.part
                if (part.sessionId != activeSession || part.type != "text") return
                val messageId = part.messageId ?: part.id ?: return
                val partId = part.id ?: messageId
                val messageParts = streamedParts.getOrPut(messageId) { linkedMapOf() }
                messageParts[partId] = part.text.orEmpty()
                val text = messageParts.values.joinToString("")
                _uiState.update { state ->
                    val index = state.messages.indexOfFirst { it.id == messageId }
                    val updated = if (index >= 0) {
                        state.messages.toMutableList().apply {
                            this[index] = this[index].copy(text = text, isStreaming = true)
                        }
                    } else {
                        state.messages + ChatMessage(
                            id = messageId,
                            text = text,
                            isUser = false,
                            isStreaming = true
                        )
                    }
                    state.copy(
                        messages = updated,
                        isRunning = true,
                        isThinking = false
                    )
                }
            }
            is OpenCodeEvent.PermissionAsked -> {
                if (event.request.sessionId != activeSession) return
                _uiState.update { state ->
                    state.copy(
                        permissions = state.permissions.filterNot { it.id == event.request.id } + event.request,
                        isThinking = false
                    )
                }
            }
            is OpenCodeEvent.SessionIdle -> {
                if (event.sessionId != activeSession) return
                streamedParts.clear()
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { message ->
                            if (message.isStreaming) message.copy(isStreaming = false) else message
                        },
                        isRunning = false,
                        isThinking = false
                    )
                }
            }
            is OpenCodeEvent.SessionError -> {
                if (event.sessionId != null && event.sessionId != activeSession) return
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        isThinking = false,
                        error = event.message ?: "OpenCode session failed"
                    )
                }
            }
            is OpenCodeEvent.Unknown -> Unit
        }
    }

    private fun toUiMessage(message: OpenCodeMessage): ChatMessage? {
        val text = message.text
        if (text.isBlank()) return null
        return ChatMessage(
            id = message.info.id,
            text = text,
            isUser = message.info.role == "user",
            timestamp = message.info.time.created,
            isStreaming = false
        )
    }

    fun setTTS(textToSpeech: TextToSpeech) {
        tts = textToSpeech
    }

    fun startListening() {
        _uiState.update { it.copy(isListening = true, partialText = "", error = null) }
    }

    fun updateSpeechPartial(text: String) {
        _uiState.update { it.copy(isListening = true, partialText = text) }
    }

    fun reportSpeechError(message: String) {
        _uiState.update { it.copy(isListening = false, partialText = "", error = message) }
    }

    fun stopListening() {
        _uiState.update { it.copy(isListening = false, partialText = "") }
    }

    fun stopSpeaking() {
        tts?.stop()
        _uiState.update { it.copy(isSpeaking = false) }
    }

    override fun onCleared() {
        eventJob?.cancel()
        tts?.stop()
        tts = null
        super.onCleared()
    }

    private fun Throwable.safeMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: "OpenCode operation failed"
}
