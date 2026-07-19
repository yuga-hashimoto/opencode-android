package com.opencode.android.feature.chat

import android.speech.tts.TextToSpeech
import java.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.android.core.api.OpenCodeEvent
import com.opencode.android.core.api.OpenCodeMessage
import com.opencode.android.core.api.OpenCodePart
import com.opencode.android.core.api.PermissionRequest
import com.opencode.android.core.api.PromptAttachment
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

enum class ChatItemKind {
    TEXT,
    TOOL,
    COMMAND,
    REASONING
}

data class PendingAttachment(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray
) {
    override fun equals(other: Any?): Boolean =
        other is PendingAttachment && id == other.id

    override fun hashCode(): Int = id.hashCode()
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val kind: ChatItemKind = ChatItemKind.TEXT,
    val toolName: String? = null,
    val detail: String? = null,
    val expandedByDefault: Boolean = false
)

data class ChatUiState(
    val backendName: String = "",
    val sessionId: String? = null,
    val sessionTitle: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val permissions: List<PermissionRequest> = emptyList(),
    val pendingAttachments: List<PendingAttachment> = emptyList(),
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
    private val streamedParts = mutableMapOf<String, LinkedHashMap<String, String>>()
    private val textPartIds = mutableSetOf<String>()
    private val toolPartIds = mutableSetOf<String>()

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
        textPartIds.clear()
        toolPartIds.clear()
        _uiState.update {
            it.copy(
                sessionId = sessionId,
                sessionTitle = title,
                isLoadingHistory = true,
                messages = emptyList(),
                permissions = emptyList(),
                pendingAttachments = emptyList(),
                error = null
            )
        }
        viewModelScope.launch {
            runCatching { currentBackend.listMessages(sessionId) }
                .onSuccess { messages ->
                    _uiState.update {
                        it.copy(
                            isLoadingHistory = false,
                            messages = messages.flatMap(::toUiMessages)
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
        textPartIds.clear()
        toolPartIds.clear()
        _uiState.update {
            it.copy(
                sessionId = null,
                sessionTitle = "",
                messages = emptyList(),
                permissions = emptyList(),
                pendingAttachments = emptyList(),
                isRunning = false,
                isThinking = false,
                isListening = false,
                partialText = "",
                error = null
            )
        }
    }

    fun addAttachment(fileName: String, mimeType: String, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        if (bytes.size > MAX_ATTACHMENT_BYTES) {
            _uiState.update { it.copy(error = "Attachment exceeds ${MAX_ATTACHMENT_BYTES / 1024}KB limit") }
            return
        }
        if (_uiState.value.pendingAttachments.size >= MAX_ATTACHMENTS) {
            _uiState.update { it.copy(error = "At most $MAX_ATTACHMENTS attachments allowed") }
            return
        }
        _uiState.update {
            it.copy(
                pendingAttachments = it.pendingAttachments + PendingAttachment(
                    fileName = fileName,
                    mimeType = mimeType.ifBlank { "application/octet-stream" },
                    bytes = bytes
                ),
                error = null
            )
        }
    }

    fun removeAttachment(id: String) {
        _uiState.update {
            it.copy(pendingAttachments = it.pendingAttachments.filterNot { item -> item.id == id })
        }
    }

    fun sendMessage(text: String) {
        val normalized = text.trim()
        val attachments = _uiState.value.pendingAttachments
        if (normalized.isEmpty() && attachments.isEmpty()) return
        val currentBackend = backend
        if (currentBackend == null) {
            _uiState.update { it.copy(error = "OpenCode connection is not configured") }
            return
        }

        val displayText = buildString {
            if (normalized.isNotEmpty()) append(normalized)
            if (attachments.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append(attachments.joinToString("\n") { "📎 ${it.fileName}" })
            }
        }
        val userMessage = ChatMessage(text = displayText, isUser = true)
        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                pendingAttachments = emptyList(),
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
                        title = (normalized.ifBlank { attachments.firstOrNull()?.fileName.orEmpty() }).take(60),
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
                        text = normalized.ifBlank { "Please review the attached file(s)." },
                        providerId = _uiState.value.selectedProviderId,
                        modelId = _uiState.value.selectedModelId,
                        agent = _uiState.value.selectedAgentId,
                        attachments = attachments.map {
                            PromptAttachment(
                                fileName = it.fileName,
                                mimeType = it.mimeType,
                                base64Data = Base64.getEncoder().encodeToString(it.bytes)
                            )
                        }
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
                when {
                    part.type == "text" -> {
                        val messageId = part.messageId ?: part.id ?: return
                        val partId = part.id ?: messageId
                        textPartIds += partId
                        val messageParts = streamedParts.getOrPut(messageId) { linkedMapOf() }
                        messageParts[partId] = part.text.orEmpty()
                        updateStreamingMessage(messageId, messageParts.values.joinToString(""))
                    }
                    part.type == "tool" || part.type == "tool-invocation" || part.type == "tool-result" -> {
                        upsertToolCard(part)
                    }
                    part.type == "reasoning" -> {
                        upsertReasoningCard(part)
                    }
                }
            }
            is OpenCodeEvent.MessagePartDelta -> {
                if (
                    event.sessionId != activeSession ||
                    event.field != "text" ||
                    event.partId !in textPartIds
                ) return
                val messageParts = streamedParts.getOrPut(event.messageId) { linkedMapOf() }
                messageParts[event.partId] = messageParts[event.partId].orEmpty() + event.delta
                updateStreamingMessage(event.messageId, messageParts.values.joinToString(""))
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

    private fun upsertToolCard(part: OpenCodePart) {
        val id = part.id ?: part.callID ?: return
        toolPartIds += id
        val title = part.tool ?: part.type
        val detail = formatToolState(part.state)
        val kind = if (
            title.contains("bash", ignoreCase = true) ||
            title.contains("shell", ignoreCase = true) ||
            title.contains("command", ignoreCase = true)
        ) {
            ChatItemKind.COMMAND
        } else {
            ChatItemKind.TOOL
        }
        _uiState.update { state ->
            val existing = state.messages.indexOfFirst { it.id == id }
            val card = ChatMessage(
                id = id,
                text = title,
                isUser = false,
                kind = kind,
                toolName = title,
                detail = detail,
                isStreaming = true,
                expandedByDefault = false
            )
            val messages = if (existing >= 0) {
                state.messages.toMutableList().apply { this[existing] = card }
            } else {
                state.messages + card
            }
            state.copy(messages = messages, isRunning = true, isThinking = false)
        }
    }

    private fun upsertReasoningCard(part: OpenCodePart) {
        val id = part.id ?: part.messageId ?: return
        val text = part.text.orEmpty()
        if (text.isBlank()) return
        _uiState.update { state ->
            val existing = state.messages.indexOfFirst { it.id == id }
            val card = ChatMessage(
                id = id,
                text = "Reasoning",
                isUser = false,
                kind = ChatItemKind.REASONING,
                detail = text,
                isStreaming = true,
                expandedByDefault = false
            )
            val messages = if (existing >= 0) {
                state.messages.toMutableList().apply { this[existing] = card }
            } else {
                state.messages + card
            }
            state.copy(messages = messages, isRunning = true, isThinking = false)
        }
    }

    private fun formatToolState(state: Map<String, com.google.gson.JsonElement>?): String? {
        if (state.isNullOrEmpty()) return null
        val preferredKeys = listOf("status", "input", "output", "error", "title", "command", "stdout", "stderr")
        val lines = preferredKeys.mapNotNull { key ->
            state[key]?.let { value ->
                val raw = if (value.isJsonPrimitive && value.asString != null) value.asString else value.toString()
                val rendered = raw.trim()
                if (rendered.isEmpty()) null else "$key: $rendered"
            }
        }
        return (lines.ifEmpty {
            state.entries.map { "${it.key}: ${it.value}" }
        }).joinToString("\n").take(4_000)
    }

    private fun updateStreamingMessage(messageId: String, text: String) {
        _uiState.update { state ->
            val index = state.messages.indexOfFirst { it.id == messageId }
            val updated = if (index >= 0) {
                state.messages.toMutableList().apply {
                    this[index] = this[index].copy(text = text, isStreaming = true, kind = ChatItemKind.TEXT)
                }
            } else {
                state.messages + ChatMessage(
                    id = messageId,
                    text = text,
                    isUser = false,
                    isStreaming = true,
                    kind = ChatItemKind.TEXT
                )
            }
            state.copy(
                messages = updated,
                isRunning = true,
                isThinking = false
            )
        }
    }

    private fun toUiMessages(message: OpenCodeMessage): List<ChatMessage> {
        val items = mutableListOf<ChatMessage>()
        val text = message.text
        if (text.isNotBlank()) {
            items += ChatMessage(
                id = message.info.id,
                text = text,
                isUser = message.info.role == "user",
                timestamp = message.info.time.created,
                kind = ChatItemKind.TEXT
            )
        }
        message.parts.forEach { part ->
            when (part.type) {
                "tool", "tool-invocation", "tool-result" -> {
                    val id = part.id ?: "${message.info.id}-tool-${part.tool}"
                    items += ChatMessage(
                        id = id,
                        text = part.tool ?: part.type,
                        isUser = false,
                        timestamp = message.info.time.created,
                        kind = ChatItemKind.TOOL,
                        toolName = part.tool,
                        detail = formatToolState(part.state)
                    )
                }
                "reasoning" -> {
                    if (!part.text.isNullOrBlank()) {
                        items += ChatMessage(
                            id = part.id ?: "${message.info.id}-reasoning",
                            text = "Reasoning",
                            isUser = false,
                            timestamp = message.info.time.created,
                            kind = ChatItemKind.REASONING,
                            detail = part.text
                        )
                    }
                }
            }
        }
        return items
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

    companion object {
        private const val MAX_ATTACHMENTS = 5
        private const val MAX_ATTACHMENT_BYTES = 512 * 1024
    }
}
