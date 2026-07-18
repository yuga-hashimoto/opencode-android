package com.opencode.android.assistant

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.opencode.android.OpenCodeApplication
import com.opencode.android.api.OpenCodeEvent
import com.opencode.android.api.PermissionRequest
import com.opencode.android.api.PromptRequest
import com.opencode.android.backend.PermissionResponse
import com.opencode.android.backend.RemoteOpenCodeBackend
import com.opencode.android.speech.SpeechRecognizerManager
import com.opencode.android.speech.SpeechResult
import com.opencode.android.speech.TTSManager
import com.opencode.android.ui.theme.OpenCodeAndroidTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class OpenCodeVoiceSession(context: Context) : VoiceInteractionSession(context),
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()

    private val app = context.applicationContext as OpenCodeApplication
    private val settings = app.settings
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var speech: SpeechRecognizerManager
    private lateinit var tts: TTSManager
    private var backend: RemoteOpenCodeBackend? = null
    private var eventJob: Job? = null
    private var listeningJob: Job? = null
    private var sessionId: String? = null
    private val responseParts = linkedMapOf<String, String>()

    private val assistantState = mutableStateOf(VoiceState.IDLE)
    private val userText = mutableStateOf("")
    private val responseText = mutableStateOf("")
    private val partialText = mutableStateOf("")
    private val errorText = mutableStateOf<String?>(null)
    private val permissionRequest = mutableStateOf<PermissionRequest?>(null)

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        speech = SpeechRecognizerManager(context)
        tts = TTSManager(context)
    }

    override fun onCreateContentView(): View = ComposeView(context).apply {
        setViewTreeLifecycleOwner(this@OpenCodeVoiceSession)
        setViewTreeSavedStateRegistryOwner(this@OpenCodeVoiceSession)
        setViewTreeViewModelStoreOwner(this@OpenCodeVoiceSession)
        setContent {
            OpenCodeAndroidTheme {
                VoiceAssistantSurface(
                    state = assistantState.value,
                    userText = userText.value,
                    responseText = responseText.value,
                    partialText = partialText.value,
                    error = errorText.value,
                    permission = permissionRequest.value,
                    onClose = ::finish,
                    onRetry = ::startListening,
                    onPermission = ::respondPermission
                )
            }
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val connection = settings.selectedConnection()
        if (connection == null) {
            assistantState.value = VoiceState.ERROR
            errorText.value = "OpenCodeのPC接続先を設定してください。"
            return
        }

        backend = RemoteOpenCodeBackend(connection)
        sessionId = settings.assistantSessionId.takeIf { settings.continuousConversation }
        startEventCollection()
        startListening()
    }

    override fun onHide() {
        listeningJob?.cancel()
        eventJob?.cancel()
        speech.destroy()
        tts.stop()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onHide()
    }

    override fun onDestroy() {
        scope.cancel()
        speech.destroy()
        tts.shutdown()
        viewModelStore.clear()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    private fun startEventCollection() {
        val activeBackend = backend ?: return
        eventJob?.cancel()
        eventJob = scope.launch {
            activeBackend.events()
                .catch { error -> showError(error.message ?: "OpenCodeイベント接続に失敗しました") }
                .collect { event ->
                    when (event) {
                        is OpenCodeEvent.MessagePartUpdated -> {
                            if (event.part.sessionId == sessionId && event.part.type == "text") {
                                val partKey = event.part.id ?: event.part.messageId ?: "text"
                                responseParts[partKey] = event.part.text.orEmpty()
                                responseText.value = responseParts.values.joinToString("")
                                assistantState.value = VoiceState.THINKING
                            }
                        }
                        is OpenCodeEvent.PermissionAsked -> {
                            if (event.request.sessionId == sessionId) {
                                permissionRequest.value = event.request
                                assistantState.value = VoiceState.PERMISSION
                            }
                        }
                        is OpenCodeEvent.SessionIdle -> {
                            if (event.sessionId == sessionId) onResponseComplete()
                        }
                        is OpenCodeEvent.SessionError -> {
                            if (event.sessionId == null || event.sessionId == sessionId) {
                                showError(event.message ?: "OpenCodeの処理に失敗しました")
                            }
                        }
                        OpenCodeEvent.ServerConnected,
                        is OpenCodeEvent.Unknown -> Unit
                    }
                }
        }
    }

    private fun startListening() {
        if (backend == null) return
        listeningJob?.cancel()
        userText.value = ""
        responseParts.clear()
        responseText.value = ""
        partialText.value = ""
        errorText.value = null
        permissionRequest.value = null
        assistantState.value = VoiceState.LISTENING

        listeningJob = scope.launch {
            speech.startListening(language = "ja-JP").collect { result ->
                when (result) {
                    SpeechResult.Ready,
                    SpeechResult.Listening -> assistantState.value = VoiceState.LISTENING
                    SpeechResult.Processing -> assistantState.value = VoiceState.THINKING
                    is SpeechResult.PartialResult -> partialText.value = result.text
                    is SpeechResult.Result -> {
                        userText.value = result.text
                        partialText.value = ""
                        sendToOpenCode(result.text)
                    }
                    is SpeechResult.Error -> showError(result.message)
                    is SpeechResult.RmsChanged -> Unit
                }
            }
        }
    }

    private fun sendToOpenCode(text: String) {
        val activeBackend = backend ?: return
        assistantState.value = VoiceState.THINKING
        responseText.value = ""
        scope.launch {
            runCatching {
                val targetSessionId = sessionId ?: activeBackend.createSession("Voice: ${text.take(48)}").id
                sessionId = targetSessionId
                if (settings.continuousConversation) settings.assistantSessionId = targetSessionId
                activeBackend.sendMessage(
                    targetSessionId,
                    PromptRequest(
                        text = text,
                        providerId = settings.selectedProviderId,
                        modelId = settings.selectedModelId,
                        agent = settings.selectedAgentId
                    )
                )
            }.onFailure { showError(it.message ?: "OpenCodeへ送信できませんでした") }
        }
    }

    private fun onResponseComplete() {
        val answer = responseText.value.trim()
        if (answer.isEmpty()) {
            showError("OpenCodeから回答テキストを取得できませんでした")
            return
        }
        scope.launch {
            assistantState.value = VoiceState.SPEAKING
            if (settings.ttsEnabled) tts.speak(answer)
            assistantState.value = VoiceState.DONE
            if (settings.continuousConversation) {
                delay(300)
                startListening()
            }
        }
    }

    private fun respondPermission(response: PermissionResponse) {
        val activeBackend = backend ?: return
        val request = permissionRequest.value ?: return
        scope.launch {
            runCatching {
                activeBackend.respondToPermission(
                    sessionId = request.sessionId,
                    permissionId = request.id,
                    response = response,
                    remember = response == PermissionResponse.ALWAYS
                )
            }.onSuccess {
                permissionRequest.value = null
                assistantState.value = VoiceState.THINKING
            }.onFailure { showError(it.message ?: "承認結果を送信できませんでした") }
        }
    }

    private fun showError(message: String) {
        assistantState.value = VoiceState.ERROR
        errorText.value = message
    }
}

private enum class VoiceState {
    IDLE,
    LISTENING,
    THINKING,
    PERMISSION,
    SPEAKING,
    DONE,
    ERROR
}

@Composable
private fun VoiceAssistantSurface(
    state: VoiceState,
    userText: String,
    responseText: String,
    partialText: String,
    error: String?,
    permission: PermissionRequest?,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onPermission: (PermissionResponse) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("OpenCode Android", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("ホームアシスト", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "閉じる")
            }
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        ) {
            if (state == VoiceState.THINKING || state == VoiceState.SPEAKING) {
                CircularProgressIndicator(modifier = Modifier.padding(24.dp))
            } else {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }

        Text(
            text = when (state) {
                VoiceState.LISTENING -> "話してください"
                VoiceState.THINKING -> "OpenCodeが処理しています"
                VoiceState.PERMISSION -> "操作の承認が必要です"
                VoiceState.SPEAKING -> "回答を読み上げています"
                VoiceState.DONE -> "完了"
                VoiceState.ERROR -> "エラー"
                VoiceState.IDLE -> "準備中"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        val spoken = partialText.ifBlank { userText }
        if (spoken.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(spoken, modifier = Modifier.padding(14.dp))
            }
        }

        if (responseText.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(responseText, modifier = Modifier.padding(14.dp))
            }
        }

        permission?.let { request ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null)
                        Spacer(Modifier.padding(horizontal = 5.dp))
                        Text(request.permission, fontWeight = FontWeight.SemiBold)
                    }
                    request.patterns.forEach {
                        Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { onPermission(PermissionResponse.REJECT) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("拒否") }
                        FilledTonalButton(
                            onClick = { onPermission(PermissionResponse.ONCE) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("今回だけ許可") }
                        Button(
                            onClick = { onPermission(PermissionResponse.ALWAYS) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("常に許可") }
                    }
                }
            }
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Button(onClick = onRetry) { Text("もう一度話す") }
        }
        Spacer(Modifier.height(8.dp))
    }
}
