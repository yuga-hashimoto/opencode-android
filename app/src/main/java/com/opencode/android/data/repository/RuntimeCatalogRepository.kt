package com.opencode.android.data.repository

import com.opencode.android.core.api.OpenCodeAgent
import com.opencode.android.core.api.OpenCodeHealth
import com.opencode.android.core.api.OpenCodeSession
import com.opencode.android.core.api.ProviderCatalog
import com.opencode.android.runtime.RuntimeRegistry
import com.opencode.android.runtime.RuntimeTarget
import com.opencode.android.runtime.WorkspaceRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RuntimeCatalogState(
    val runtime: RuntimeTarget? = null,
    val health: OpenCodeHealth? = null,
    val sessions: List<OpenCodeSession> = emptyList(),
    val providers: ProviderCatalog = ProviderCatalog(),
    val agents: List<OpenCodeAgent> = emptyList(),
    val workspaces: List<WorkspaceRef> = emptyList(),
    val isRefreshing: Boolean = false,
    val error: String? = null
)

class RuntimeCatalogRepository(
    private val registry: RuntimeRegistry,
    private val scope: CoroutineScope
) {
    private val mutableState = MutableStateFlow(RuntimeCatalogState(runtime = registry.selected.value))
    val state: StateFlow<RuntimeCatalogState> = mutableState.asStateFlow()
    private val refreshMutex = Mutex()

    init {
        scope.launch {
            registry.selected.collectLatest { target ->
                mutableState.value = RuntimeCatalogState(runtime = target)
                if (target != null) load(target)
            }
        }
    }

    fun refresh() {
        val target = registry.selected.value ?: return
        scope.launch { load(target) }
    }

    /** Refresh only the session list for surfaces that show recent chats. */
    fun refreshSessionsOnly() {
        val target = registry.selected.value ?: return
        scope.launch {
            runCatching { target.listSessions() }
                .onSuccess { sessions ->
                    if (registry.selected.value?.id == target.id) {
                        mutableState.update { it.copy(sessions = sessions) }
                    }
                }
        }
    }

    fun refreshProvidersOnly() {
        val target = registry.selected.value ?: return
        scope.launch {
            runCatching { target.listProviders() }
                .onSuccess { providers ->
                    if (registry.selected.value?.id == target.id) {
                        mutableState.update { it.copy(providers = providers) }
                    }
                }
        }
    }

    private suspend fun load(target: RuntimeTarget) {
        refreshMutex.withLock {
            if (registry.selected.value?.id != target.id) return
            mutableState.update { current ->
                current.copy(runtime = target, isRefreshing = true, error = null)
            }

            val connection = target.connect()
            if (connection.isFailure) {
                mutableState.value = RuntimeCatalogState(
                    runtime = target,
                    isRefreshing = false,
                    error = connection.exceptionOrNull().safeMessage()
                )
                return
            }

            val catalog = supervisorScope {
                val sessions = async { runCatching { target.listSessions() } }
                val providers = async { runCatching { target.listProviders() } }
                val agents = async { runCatching { target.listAgents() } }
                val workspaces = async { runCatching { target.listWorkspaces() } }
                LoadedCatalog(
                    sessions = sessions.await(),
                    providers = providers.await(),
                    agents = agents.await(),
                    workspaces = workspaces.await()
                )
            }

            if (registry.selected.value?.id != target.id) return
            val errors = catalog.failures()
            mutableState.value = RuntimeCatalogState(
                runtime = target,
                health = connection.getOrNull(),
                sessions = catalog.sessions.getOrDefault(emptyList()),
                providers = catalog.providers.getOrDefault(ProviderCatalog()),
                agents = catalog.agents.getOrDefault(emptyList()),
                workspaces = catalog.workspaces.getOrDefault(emptyList()),
                isRefreshing = false,
                error = errors.takeIf { it.isNotEmpty() }?.joinToString("\n")
            )
        }
    }

    private data class LoadedCatalog(
        val sessions: Result<List<OpenCodeSession>>,
        val providers: Result<ProviderCatalog>,
        val agents: Result<List<OpenCodeAgent>>,
        val workspaces: Result<List<WorkspaceRef>>
    ) {
        fun failures(): List<String> = listOfNotNull(
            sessions.exceptionOrNull()?.let { "セッション: ${it.safeMessage()}" },
            providers.exceptionOrNull()?.let { "AIサービス: ${it.safeMessage()}" },
            agents.exceptionOrNull()?.let { "エージェント: ${it.safeMessage()}" },
            workspaces.exceptionOrNull()?.let { "作業フォルダ: ${it.safeMessage()}" }
        )
    }
}

private fun Throwable?.safeMessage(): String =
    this?.message?.takeIf { it.isNotBlank() } ?: "OpenCodeへ接続できませんでした"
