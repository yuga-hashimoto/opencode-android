package com.opencode.android.data.repository

import com.opencode.android.core.api.OpenCodeAgent
import com.opencode.android.core.api.OpenCodeEvent
import com.opencode.android.core.api.OpenCodeHealth
import com.opencode.android.core.api.OpenCodeMessage
import com.opencode.android.core.api.OpenCodeModel
import com.opencode.android.core.api.OpenCodeProvider
import com.opencode.android.core.api.OpenCodeSession
import com.opencode.android.core.api.OpenCodeTime
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RuntimeCatalogRepositoryTest {
    @Test
    fun `loads selected runtime catalog and workspaces`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val target = FakeTarget("mac")
        val registry = RuntimeRegistry(
            store = FakeStore(selectedRuntimeId = "mac"),
            localTarget = FakeTarget("local-android", RuntimeType.LOCAL),
            remoteFactory = { target }
        )
        val repository = RuntimeCatalogRepository(
            registry = registry,
            scope = TestScope(dispatcher)
        )

        advanceUntilIdle()

        val state = repository.state.value
        assertEquals("mac", state.runtime?.id)
        assertEquals("1.18.3", state.health?.version)
        assertEquals(listOf("s1"), state.sessions.map { it.id })
        assertEquals(listOf("opencode"), state.providers.connected)
        assertEquals(listOf("build"), state.agents.map { it.name })
        assertEquals(listOf("/workspace/app"), state.workspaces.map { it.path })
        assertFalse(state.isRefreshing)
        assertNull(state.error)
    }

    @Test
    fun `switching runtime clears previous catalog and loads the new target`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mac = FakeTarget("mac", version = "1.18.3")
        val linux = FakeTarget("linux", version = "1.18.4")
        val store = FakeStore(
            profiles = listOf(profile("mac"), profile("linux")),
            selectedRuntimeId = "mac"
        )
        val registry = RuntimeRegistry(
            store = store,
            localTarget = FakeTarget("local-android", RuntimeType.LOCAL),
            remoteFactory = { if (it.id == "mac") mac else linux }
        )
        val repository = RuntimeCatalogRepository(registry, TestScope(dispatcher))
        advanceUntilIdle()

        registry.select("linux")
        advanceUntilIdle()

        assertEquals("linux", repository.state.value.runtime?.id)
        assertEquals("1.18.4", repository.state.value.health?.version)
    }

    @Test
    fun `connection failure is exposed without stale data`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val failing = FakeTarget("mac", connectError = IllegalStateException("offline"))
        val registry = RuntimeRegistry(
            store = FakeStore(selectedRuntimeId = "mac"),
            localTarget = FakeTarget("local-android", RuntimeType.LOCAL),
            remoteFactory = { failing }
        )
        val repository = RuntimeCatalogRepository(registry, TestScope(dispatcher))

        advanceUntilIdle()

        val state = repository.state.value
        assertEquals("offline", state.error)
        assertTrue(state.sessions.isEmpty())
        assertTrue(state.providers.all.isEmpty())
        assertFalse(state.isRefreshing)
    }

    private fun profile(id: String) = ConnectionProfile(
        id = id,
        name = id,
        baseUrl = "https://$id.example.test"
    )

    private class FakeStore(
        profiles: List<ConnectionProfile> = listOf(profileStatic("mac")),
        override var selectedRuntimeId: String? = null
    ) : RuntimeConnectionStore {
        private val values = profiles.toMutableList()
        override fun connections(): List<ConnectionProfile> = values.toList()
        override fun upsertConnection(profile: ConnectionProfile) {
            values.removeAll { it.id == profile.id }
            values += profile
        }
        override fun deleteConnection(id: String) {
            values.removeAll { it.id == id }
        }

        companion object {
            private fun profileStatic(id: String) = ConnectionProfile(
                id = id,
                name = id,
                baseUrl = "https://$id.example.test"
            )
        }
    }

    private class FakeTarget(
        override val id: String,
        override val type: RuntimeType = RuntimeType.REMOTE,
        private val version: String = "1.18.3",
        private val connectError: Throwable? = null
    ) : RuntimeTarget {
        override val displayName: String = id
        override val kind: BackendKind = if (type == RuntimeType.LOCAL) BackendKind.LOCAL else BackendKind.REMOTE
        override val state = MutableStateFlow<RuntimeState>(RuntimeState.Disconnected)

        override suspend fun connect(): Result<OpenCodeHealth> = connectError?.let(Result.Companion::failure)
            ?: Result.success(OpenCodeHealth(true, version))
        override fun disconnect() = Unit
        override suspend fun health(): OpenCodeHealth = OpenCodeHealth(true, version)
        override suspend fun listSessions(directory: String?): List<OpenCodeSession> = listOf(
            OpenCodeSession(id = "s1", title = "Test", directory = "/workspace/app", time = OpenCodeTime(created = 1))
        )
        override suspend fun createSession(title: String?, directory: String?): OpenCodeSession = error("unused")
        override suspend fun listMessages(sessionId: String): List<OpenCodeMessage> = emptyList()
        override suspend fun listProviders(): ProviderCatalog = ProviderCatalog(
            all = listOf(
                OpenCodeProvider(
                    id = "opencode",
                    name = "OpenCode Zen",
                    models = mapOf("big-pickle" to OpenCodeModel("big-pickle", "opencode", "Big Pickle"))
                )
            ),
            default = mapOf("opencode" to "big-pickle"),
            connected = listOf("opencode")
        )
        override suspend fun listAgents(): List<OpenCodeAgent> = listOf(OpenCodeAgent("build", mode = "primary"))
        override suspend fun listWorkspaces(): List<WorkspaceRef> = listOf(
            WorkspaceRef("/workspace/app", "app", "/workspace/app")
        )
        override suspend fun sendMessage(sessionId: String, request: PromptRequest) = Unit
        override suspend fun abortSession(sessionId: String): Boolean = true
        override suspend fun respondToPermission(
            sessionId: String,
            permissionId: String,
            response: PermissionResponse,
            remember: Boolean
        ): Boolean = true
        override fun events(): Flow<OpenCodeEvent> = emptyFlow()
    }
}
