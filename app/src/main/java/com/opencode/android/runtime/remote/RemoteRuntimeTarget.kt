package com.opencode.android.runtime.remote

import com.opencode.android.core.api.OpenCodeHealth
import com.opencode.android.data.connection.ConnectionProfile
import com.opencode.android.runtime.DelegatingRuntimeTarget
import com.opencode.android.runtime.RuntimeState
import com.opencode.android.runtime.RuntimeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RemoteRuntimeTarget(
    val profile: ConnectionProfile,
    private val remoteBackend: RemoteOpenCodeBackend = RemoteOpenCodeBackend(profile)
) : DelegatingRuntimeTarget(remoteBackend) {
    override val id: String = profile.id
    override val displayName: String = profile.name
    override val type: RuntimeType = RuntimeType.REMOTE

    private val mutableState = MutableStateFlow<RuntimeState>(RuntimeState.Disconnected)
    override val state: StateFlow<RuntimeState> = mutableState.asStateFlow()

    override suspend fun connect(): Result<OpenCodeHealth> {
        mutableState.value = RuntimeState.Connecting
        return runCatching { backend.health() }
            .onSuccess { health ->
                mutableState.value = if (health.healthy) {
                    RuntimeState.Connected(health.version)
                } else {
                    RuntimeState.Failed("OpenCode reported an unhealthy server")
                }
            }
            .onFailure { error ->
                mutableState.value = RuntimeState.Failed(error.safeMessage())
            }
    }

    override fun disconnect() {
        mutableState.value = RuntimeState.Disconnected
    }

    private fun Throwable.safeMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: "OpenCode connection failed"
}
