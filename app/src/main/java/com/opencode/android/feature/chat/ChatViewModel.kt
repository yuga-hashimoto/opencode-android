package com.opencode.android.feature.chat

import android.speech.tts.TextToSpeech
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.android.core.api.OpenCodeEvent
import com.opencode.android.core.api.OpenCodeMessage
import com.opencode.android.core.api.OpenCodePart
import com.opencode.android.core.api.PermissionRequest
import com.opencode.android.core.api.PromptRequest
import com.opencode.android.runtime.OpenCodeBackend
import com.opencode.android.runtime.PermissionResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class ToolStatus { PENDING, RUNNING, COMPLETED, ERROR, UNKNOWN }

sealed interface ChatPart {
    val id: String

    data class Text(override val id: String, val text: String) : ChatPart
    data class Reasoning(override val id: String, val text: String) : ChatPart
    data class Tool(
        override val id: String,
        val name: String,
        val status: ToolStatus,
        val title: String? = null,
        val input: String? = null,
        val output: String? = null,
        val outputTruncated: Boolean = false,
        val error: String? = null
    ) : ChatPart
    data class Patch(override val id: String, val files: List<String>) : ChatPart
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val parts: List<ChatPart> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false
) {
    val text: String
        get() = parts.filterIsInstance<ChatPart.Text>().joinToString("") { it.text }
}

private const val MAX_TOOL_OUTPUT_CHARS = 4000

private fun OpenCodePart.toChatPart(): ChatPart? {
    val partId = id ?: return null
    val stateMap = state.orEmpty()
    return when (type) {
        "text" -> ChatPart.Text(partId, text.orEmpty())
        "reasoning" -> ChatPart.Reasoning(partId, text.orEmpty())
        "tool" -> {
            val inputText = formatToolInput(stateMap["input"] as? Map<*, *>)
            val rawOutput = stateMap["output"] as? String
            val truncated = rawOutput != null && rawOutput.length > MAX_TOOL_OUTPUT_CHARS
            ChatPart.Tool(
                id = partId,
                name = tool ?: "tool",
                status = parseToolStatus(stateMap["status"]),
                title = (stateMap["title"] as? String)?.takeIf { it.isNotBlank() }
                    ?: inputText?.lineSequence()?.firstOrNull(),
                input = inputText,
                output = if (truncated) rawOutput?.takeLast(MAX_TOOL_OUTPUT_CHARS) else rawOutput,
                outputTruncated = truncated,
                error = stateMap["error"] as? String
            )
        }
        "patch" -> ChatPart.Patch(partId, extractPatchFiles(stateMap))
        else -> null
    }
}

private fun parseToolStatus(value: Any?): ToolStatus = when (value as? String) {
    "pending" -> ToolStatus.PENDING
    "running" -> ToolStatus.RUNNING
    "completed" -> ToolStatus.COMPLETED
    "error" -> ToolStatus.ERROR
    else -> ToolStatus.UNKNOWN
}

private fun formatToolInput(input: Map<*, *>?): String? {
    if (input.isNullOrEmpty()) return null
    (input["command"] as? String)?.let { return it }
    (input["filePath"] as? String)?.let { path ->
        val extra = input.entries.filterNot { it.key == "filePath" }
        return if (extra.isEmpty()) {
            path
        } else {
            path + "\n" + extra.joinToString("\n") { (key, value) -> "$key: $value" }
        }
    }
    return input.entries.joinToString("\n") { (key, value) -> "$key: $value" }
}

private fun extractPatchFiles(state: Map<String, Any?>): List<String> {
    return when (val files = state["files"]) {
        is List<*> -> files.mapNotNull { entry ->
            when (entry) {
                is String -> entry
                is Map<*, *> -> (entry["path"] ?: entry["file"] ?: entry["filename"])?.toString()
                else -> null
            }
        }
        is Map<*, *> -> files.keys.mapNotNull { it?.toString() }
        else -> emptyList()
    }
}

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
    val selectedWorkspacePath: String? = null,
    val error: String? = null
)

class ChatViewModel(
    private val backend: OpenCodeBackend? = null,
    private val eventFlow: Flow<OpenCodeEvent>? = null,
    private val onPermissionResolved: (String) -> Unit = {}
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ChatUiState(backendName = backend?.displayName.orEmpty())
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var eventJob: Job? = null
    private var tts: TextToSpeech? = null
    private val streamedParts = mutableMapOf<String, LinkedHashMap<String, ChatPart>>()

    init {
        if (backend != null) {
            eventJob = viewModelScope.launch {
                (eventFlow ?: backend.events())
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

    fun selectWorkspace(path: String?) {
        if (_uiState.value.sessionId != null) return
        _uiState.update { it.copy(selectedWorkspacePath = path) }
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
                isThinking = false,
                isListening = false,
                partialText = "",
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

        val userMessage = ChatMessage(
            isUser = true,
            parts = listOf(ChatPart.Text(id = UUID.randomUUID().toString(), text = normalized))
        )
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
                    currentBackend.createSession(
                        title = normalized.take(60),
                        directory = _uiState.value.selectedWorkspacePath
                    )
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
                    onPermissionResolved(permissionId)
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
                _uiState.update { it.copy(isConnected = true, error = null) }
            }
            is OpenCodeEvent.MessagePartUpdated -> {
                val part = event.part
                if (part.sessionId != activeSession) return
                val messageId = part.messageId ?: part.id ?: return
                val partId = part.id ?: messageId
                val chatPart = part.toChatPart() ?: return
                val messageParts = streamedParts.getOrPut(messageId) { linkedMapOf() }
                messageParts[partId] = chatPart
                updateStreamingMessage(messageId, messageParts.values.toList())
            }
            is OpenCodeEvent.MessagePartDelta -> {
                if (event.sessionId != activeSession || event.field != "text") return
                val messageParts = streamedParts[event.messageId] ?: return
                val existing = messageParts[event.partId] ?: return
                val updatedPart = when (existing) {
                    is ChatPart.Text -> existing.copy(text = existing.text + event.delta)
                    is ChatPart.Reasoning -> existing.copy(text = existing.text + event.delta)
                    else -> return
                }
                messageParts[event.partId] = updatedPart
                updateStreamingMessage(event.messageId, messageParts.values.toList())
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

    private fun updateStreamingMessage(messageId: String, parts: List<ChatPart>) {
        _uiState.update { state ->
            val index = state.messages.indexOfFirst { it.id == messageId }
            val updated = if (index >= 0) {
                state.messages.toMutableList().apply {
                    this[index] = this[index].copy(parts = parts, isStreaming = true)
                }
            } else {
                state.messages + ChatMessage(
                    id = messageId,
                    isUser = false,
                    parts = parts,
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

    private fun toUiMessage(message: OpenCodeMessage): ChatMessage? {
        val parts = message.parts.mapNotNull { it.toChatPart() }
        if (parts.isEmpty()) return null
        return ChatMessage(
            id = message.info.id,
            isUser = message.info.role == "user",
            parts = parts,
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
