package com.opencode.android.runtime.local

import com.opencode.android.core.api.OpenCodeHealth
import com.opencode.android.runtime.DelegatingRuntimeTarget
import com.opencode.android.runtime.LocalRuntimeStatus
import com.opencode.android.runtime.RuntimeState
import com.opencode.android.runtime.RuntimeType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LocalRuntimeTarget(
    private val runtimeManager: LocalRuntimeManager,
    private val localBackend: LocalOpenCodeBackend = LocalOpenCodeBackend(runtimeManager)
) : DelegatingRuntimeTarget(localBackend) {
    override val id: String = "local-android"
    override val displayName: String = "このAndroid端末"
    override val type: RuntimeType = RuntimeType.LOCAL

    private val mutableState = MutableStateFlow(mapStatus(runtimeManager.status()))
    override val state: StateFlow<RuntimeState> = mutableState.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            runtimeManager.state.collect { status ->
                if (status !is LocalRuntimeStatus.Ready) {
                    localBackend.invalidate()
                }
                mutableState.value = mapStatus(status)
            }
        }
    }

    fun refreshLocalState(): RuntimeState = mapStatus(runtimeManager.status()).also {
        mutableState.value = it
    }

    override suspend fun connect(): Result<OpenCodeHealth> {
        val localStatus = runtimeManager.status()
        if (localStatus !is LocalRuntimeStatus.Ready) {
            val state = mapStatus(localStatus)
            mutableState.value = state
            return Result.failure(IllegalStateException(state.describe()))
        }

        mutableState.value = RuntimeState.Connecting
        return runCatching { backend.health() }
            .onSuccess { health ->
                mutableState.value = if (health.healthy) {
                    RuntimeState.Connected(health.version)
                } else {
                    RuntimeState.Failed("ローカルOpenCodeが正常状態を返しませんでした")
                }
            }
            .onFailure { error ->
                mutableState.value = RuntimeState.Failed(error.message ?: "ローカルOpenCodeへ接続できません")
            }
    }

    override fun disconnect() {
        mutableState.value = mapStatus(runtimeManager.status())
    }

    private fun mapStatus(status: LocalRuntimeStatus): RuntimeState = when (status) {
        LocalRuntimeStatus.NotInstalled -> RuntimeState.Unavailable("ローカルランタイムは未インストールです")
        is LocalRuntimeStatus.UnsupportedAbi -> RuntimeState.Unavailable("未対応ABI: ${status.abi}")
        is LocalRuntimeStatus.Installing -> RuntimeState.Connecting
        is LocalRuntimeStatus.Starting -> RuntimeState.Connecting
        is LocalRuntimeStatus.Updating -> RuntimeState.Connecting
        is LocalRuntimeStatus.Stopped -> RuntimeState.Disconnected
        is LocalRuntimeStatus.Broken -> RuntimeState.Failed(status.reason)
        is LocalRuntimeStatus.Ready -> RuntimeState.Connected(status.version)
    }

    private fun RuntimeState.describe(): String = when (this) {
        RuntimeState.Disconnected -> "ローカルランタイムは停止しています"
        RuntimeState.Connecting -> "ローカルランタイムへ接続中です"
        is RuntimeState.Connected -> "OpenCode $version"
        is RuntimeState.Unavailable -> reason
        is RuntimeState.Failed -> message
    }
}
