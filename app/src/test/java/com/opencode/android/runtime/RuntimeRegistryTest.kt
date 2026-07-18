package com.opencode.android.runtime

import com.opencode.android.core.api.OpenCodeAgent
import com.opencode.android.core.api.OpenCodeEvent
import com.opencode.android.core.api.OpenCodeHealth
import com.opencode.android.core.api.OpenCodeMessage
import com.opencode.android.core.api.OpenCodeSession
import com.opencode.android.core.api.PromptRequest
import com.opencode.android.core.api.ProviderCatalog
import com.opencode.android.data.connection.ConnectionProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeRegistryTest {
    @Test
    fun `loads remote targets and preserves selected runtime`() {
        val store = FakeStore(
            profiles = mutableListOf(profile("mac"), profile("linux")),
            selectedId = "linux"
        )
        val local = FakeTarget("local-android", RuntimeType.LOCAL)
        val registry = RuntimeRegistry(
            store = store,
            localTarget = local,
            remoteFactory = { FakeTarget(it.id, RuntimeType.REMOTE, it.name) }
        )

        assertEquals(listOf("local-android", "mac", "linux"), registry.targets.value.map { it.id })
        assertEquals("linux", registry.selected.value?.id)
    }

    @Test
    fun `selecting local runtime is persisted`() {
        val store = FakeStore(profiles = mutableListOf(profile("mac")), selectedId = "mac")
        val registry = registry(store)

        registry.select("local-android")

        assertEquals("local-android", store.selectedId)
        assertEquals("local-android", registry.selected.value?.id)
    }

    @Test
    fun `upsert selects first remote runtime when nothing is selected`() {
        val store = FakeStore()
        val registry = registry(store)

        registry.upsertRemote(profile("mac"))

        assertEquals(listOf("local-android", "mac"), registry.targets.value.map { it.id })
        assertEquals("mac", registry.selected.value?.id)
        assertEquals("mac", store.selectedId)
    }

    @Test
    fun `updating selected remote replaces target without losing selection`() {
        val store = FakeStore(
            profiles = mutableListOf(profile("mac", "Old name")),
            selectedId = "mac"
        )
        val registry = registry(store)

        registry.upsertRemote(profile("mac", "New name"))

        assertEquals("mac", registry.selected.value?.id)
        assertEquals("New name", registry.selected.value?.displayName)
    }

    @Test
    fun `deleting selected remote falls back to remaining remote before local`() {
        val store = FakeStore(
            profiles = mutableListOf(profile("mac"), profile("linux")),
            selectedId = "mac"
        )
        val registry = registry(store)

        registry.deleteRemote("mac")

        assertEquals("linux", registry.selected.value?.id)
        assertEquals("linux", store.selectedId)
    }

    @Test
    fun `deleting last remote clears selection rather than silently choosing unavailable local`() {
        val store = FakeStore(
            profiles = mutableListOf(profile("mac")),
            selectedId = "mac"
        )
        val registry = registry(store)

        registry.deleteRemote("mac")

        assertNull(registry.selected.value)
        assertNull(store.selectedId)
    }

    private fun registry(store: FakeStore): RuntimeRegistry = RuntimeRegistry(
        store = store,
        localTarget = FakeTarget("local-android", RuntimeType.LOCAL),
        remoteFactory = { FakeTarget(it.id, RuntimeType.REMOTE, it.name) }
    )

    private fun profile(id: String, name: String = id): ConnectionProfile = ConnectionProfile(
        id = id,
        name = name,
        baseUrl = "https://$id.example.test"
    )

    private class FakeStore(
        val profiles: MutableList<ConnectionProfile> = mutableListOf(),
        override var selectedRuntimeId: String? = null,
        selectedId: String? = null
    ) : RuntimeConnectionStore {
        init {
            selectedRuntimeId = selectedId
        }

        var selectedId: String?
            get() = selectedRuntimeId
            set(value) {
                selectedRuntimeId = value
            }

        override fun connections(): List<ConnectionProfile> = profiles.toList()

        override fun upsertConnection(profile: ConnectionProfile) {
            profiles.removeAll { it.id == profile.id }
            profiles += profile
        }

        override fun deleteConnection(id: String) {
            profiles.removeAll { it.id == id }
        }
    }

    private class FakeTarget(
        override val id: String,
        override val type: RuntimeType,
        override val displayName: String = id
    ) : RuntimeTarget {
        override val state = MutableStateFlow<RuntimeState>(RuntimeState.Disconnected)
        override val kind: BackendKind = if (type == RuntimeType.LOCAL) BackendKind.LOCAL else BackendKind.REMOTE
        override suspend fun connect(): Result<OpenCodeHealth> = Result.success(OpenCodeHealth(true, "test"))
        override fun disconnect() = Unit
        override suspend fun listWorkspaces(): List<WorkspaceRef> = emptyList()
        override suspend fun health(): OpenCodeHealth = OpenCodeHealth(true, "test")
        override suspend fun listSessions(): List<OpenCodeSession> = emptyList()
        override suspend fun createSession(title: String?): OpenCodeSession = error("unused")
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
        override fun events(): Flow<OpenCodeEvent> = emptyFlow()
    }
}
