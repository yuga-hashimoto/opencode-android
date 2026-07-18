package com.opencode.android.data.repository

import com.opencode.android.core.api.OpenCodeEvent
import com.opencode.android.core.api.PermissionRequest
import com.opencode.android.runtime.RuntimeRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RuntimeEventLog(
    val timestamp: Long = System.currentTimeMillis(),
    val title: String,
    val detail: String? = null,
    val sessionId: String? = null
)

data class RuntimeActivityState(
    val activeSessionIds: Set<String> = emptySet(),
    val permissions: List<PermissionRequest> = emptyList(),
    val logs: List<RuntimeEventLog> = emptyList(),
    val streamError: String? = null
)

class RuntimeActivityRepository(
    registry: RuntimeRegistry,
    scope: CoroutineScope
) {
    private val mutableState = MutableStateFlow(RuntimeActivityState())
    val state: StateFlow<RuntimeActivityState> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<OpenCodeEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<OpenCodeEvent> = mutableEvents.asSharedFlow()

    init {
        scope.launch {
            registry.selected.collectLatest { target ->
                mutableState.value = RuntimeActivityState()
                if (target == null) return@collectLatest
                target.events()
                    .catch { error ->
                        mutableState.update {
                            it.copy(streamError = error.message ?: "OpenCodeイベント接続に失敗しました")
                        }
                    }
                    .collect { event ->
                        mutableEvents.emit(event)
                        handle(event)
                    }
            }
        }
    }

    fun resolvePermission(permissionId: String) {
        mutableState.update { current ->
            current.copy(permissions = current.permissions.filterNot { it.id == permissionId })
        }
    }

    private fun handle(event: OpenCodeEvent) {
        when (event) {
            OpenCodeEvent.ServerConnected -> appendLog("イベント接続", "OpenCodeのリアルタイムイベントへ接続しました")
            is OpenCodeEvent.MessagePartUpdated -> {
                val sessionId = event.part.sessionId ?: return
                mutableState.update { current ->
                    current.copy(activeSessionIds = current.activeSessionIds + sessionId)
                }
                when (event.part.type) {
                    "tool", "tool-invocation" -> appendLog("ツール実行", event.part.tool, sessionId)
                    "reasoning" -> appendLog("推論", null, sessionId)
                }
            }
            is OpenCodeEvent.MessagePartDelta -> {
                mutableState.update { current ->
                    current.copy(activeSessionIds = current.activeSessionIds + event.sessionId)
                }
            }
            is OpenCodeEvent.PermissionAsked -> {
                mutableState.update { current ->
                    current.copy(
                        permissions = current.permissions.filterNot { it.id == event.request.id } + event.request,
                        activeSessionIds = current.activeSessionIds + event.request.sessionId
                    )
                }
                appendLog("承認待ち", event.request.permission, event.request.sessionId)
            }
            is OpenCodeEvent.SessionIdle -> {
                mutableState.update { current ->
                    current.copy(activeSessionIds = current.activeSessionIds - event.sessionId)
                }
                appendLog("実行完了", null, event.sessionId)
            }
            is OpenCodeEvent.SessionError -> {
                event.sessionId?.let { sessionId ->
                    mutableState.update { current ->
                        current.copy(activeSessionIds = current.activeSessionIds - sessionId)
                    }
                }
                appendLog("実行エラー", event.message, event.sessionId)
            }
            is OpenCodeEvent.Unknown -> appendLog("未対応イベント", event.type)
        }
    }

    private fun appendLog(title: String, detail: String? = null, sessionId: String? = null) {
        mutableState.update { current ->
            current.copy(
                logs = (listOf(RuntimeEventLog(title = title, detail = detail, sessionId = sessionId)) + current.logs)
                    .take(MAX_LOGS)
            )
        }
    }

    companion object {
        private const val MAX_LOGS = 100
    }
}
