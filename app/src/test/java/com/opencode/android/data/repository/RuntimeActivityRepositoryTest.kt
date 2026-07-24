package com.opencode.android.data.repository

import com.opencode.android.core.api.OpenCodeAgent
import com.opencode.android.core.api.OpenCodeEvent
import com.opencode.android.core.api.OpenCodeHealth
import com.opencode.android.core.api.OpenCodeMessage
import com.opencode.android.core.api.OpenCodeSession
import com.opencode.android.core.api.PromptRequest
import com.opencode.android.core.api.ProviderCatalog
import com.opencode.android.data.connection.ConnectionProfile
import com.opencode.android.runtime.BackendKind
import com.opencode.android.runtime.PermissionResponse
import com.opencode.android.runtime.RuntimeConnectionStore
import com.opencode.android.runtime.RuntimeRegistry
import com.opencode.android.runtime.RuntimeState
import com.opencode.android.runtime.RuntimeTarget
import com.opencode.android.runtime.RuntimeType
import com.opencode.android.runtime.WorkspaceRef
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RuntimeActivityRepositoryTest {
    @Test
    fun `does not open events until selected runtime is connected`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val target = FakeTarget()
        val registry = RuntimeRegistry(
            store = FakeStore(selectedRuntimeId = target.id),
            localTarget = target,
            remoteFactory = { error("unused") }
        )
        val repository = RuntimeActivityRepository(registry, TestScope(dispatcher))

        advanceUntilIdle()

        assertEquals(0, target.eventCalls)
        assertTrue(repository.state.value.logs.isEmpty())

        target.state.value = RuntimeState.Connected("1.18.3")
        advanceUntilIdle()
        assertEquals(1, target.eventCalls)

        target.eventFlow.emit(OpenCodeEvent.ServerConnected)
        advanceUntilIdle()

        assertEquals("イベント接続", repository.state.value.logs.single().title)
    }

    @Test
    fun `retries events after stream failure while runtime stays connected`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val target = FakeTarget().apply {
            eventFlows += flow { throw IllegalStateException("stream dropped") }
            eventFlows += eventFlow
        }
        val registry = RuntimeRegistry(
            store = FakeStore(selectedRuntimeId = target.id),
            localTarget = target,
            remoteFactory = { error("unused") }
        )
        val repository = RuntimeActivityRepository(
            registry = registry,
            scope = TestScope(dispatcher),
            retryDelayMillis = 1L
        )

        target.state.value = RuntimeState.Connected("1.18.3")
        advanceUntilIdle()

        assertEquals(2, target.eventCalls)
        assertEquals("stream dropped", repository.state.value.streamError)

        target.eventFlow.emit(OpenCodeEvent.ServerConnected)
        advanceUntilIdle()

        assertEquals(null, repository.state.value.streamError)
        assertEquals("イベント接続", repository.state.value.logs.single().title)
    }

    @Test
    fun `a brief Connecting blip does not restart the event stream`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val target = FakeTarget()
        val registry = RuntimeRegistry(
            store = FakeStore(selectedRuntimeId = target.id),
            localTarget = target,
            remoteFactory = { error("unused") }
        )
        RuntimeActivityRepository(registry, TestScope(dispatcher))

        target.state.value = RuntimeState.Connected("1.18.3")
        advanceUntilIdle()
        assertEquals(1, target.eventCalls)

        // A health recheck on an already-connected runtime dips back to Connecting and returns;
        // the existing SSE connection must survive it rather than being torn down and reopened.
        target.state.value = RuntimeState.Connecting
        advanceUntilIdle()
        target.state.value = RuntimeState.Connected("1.18.3")
        advanceUntilIdle()

        assertEquals(1, target.eventCalls)
    }

    @Test
    fun `going disconnected then reconnecting does reopen the stream`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val target = FakeTarget()
        val registry = RuntimeRegistry(
            store = FakeStore(selectedRuntimeId = target.id),
            localTarget = target,
            remoteFactory = { error("unused") }
        )
        RuntimeActivityRepository(registry, TestScope(dispatcher))

        target.state.value = RuntimeState.Connected("1.18.3")
        advanceUntilIdle()
        assertEquals(1, target.eventCalls)

        target.state.value = RuntimeState.Disconnected
        advanceUntilIdle()
        target.state.value = RuntimeState.Connected("1.18.3")
        advanceUntilIdle()

        assertEquals(2, target.eventCalls)
    }

    private class FakeStore(
        override var selectedRuntimeId: String?
    ) : RuntimeConnectionStore {
        override fun connections(): List<ConnectionProfile> = emptyList()
        override fun upsertConnection(profile: ConnectionProfile) = Unit
        override fun deleteConnection(id: String) = Unit
    }

    private class FakeTarget : RuntimeTarget {
        override val id: String = "local-android"
        override val displayName: String = "このAndroid端末"
        override val kind: BackendKind = BackendKind.LOCAL
        override val type: RuntimeType = RuntimeType.LOCAL
        override val state = MutableStateFlow<RuntimeState>(RuntimeState.Disconnected)
        val eventFlow = MutableSharedFlow<OpenCodeEvent>(extraBufferCapacity = 8)
        val eventFlows = ArrayDeque<Flow<OpenCodeEvent>>()
        var eventCalls = 0

        override suspend fun connect(): Result<OpenCodeHealth> =
            Result.failure(IllegalStateException("not connected"))
        override fun disconnect() = Unit
        override suspend fun listWorkspaces(): List<WorkspaceRef> = emptyList()
        override suspend fun health(): OpenCodeHealth = error("unused")
        override suspend fun listSessions(directory: String?): List<OpenCodeSession> = emptyList()
        override suspend fun createSession(title: String?, directory: String?): OpenCodeSession = error("unused")
        override suspend fun listMessages(sessionId: String): List<OpenCodeMessage> = emptyList()
        override suspend fun listProviders(): ProviderCatalog = ProviderCatalog()
        override suspend fun listAgents(): List<OpenCodeAgent> = emptyList()
        override suspend fun sendMessage(sessionId: String, request: PromptRequest) = Unit
        override suspend fun abortSession(sessionId: String): Boolean = true
        override suspend fun respondToPermission(
            sessionId: String,
            permissionId: String,
            response: PermissionResponse,
            remember: Boolean
        ): Boolean = true

        override fun events(): Flow<OpenCodeEvent> {
            check(state.value is RuntimeState.Connected) { "runtime is not connected" }
            eventCalls++
            return eventFlows.removeFirstOrNull() ?: eventFlow
        }
    }
}
