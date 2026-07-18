package com.opencode.android.runtime

import com.opencode.android.data.connection.ConnectionProfile
import com.opencode.android.runtime.remote.RemoteRuntimeTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RuntimeRegistry(
    private val store: RuntimeConnectionStore,
    private val localTarget: RuntimeTarget,
    private val remoteFactory: (ConnectionProfile) -> RuntimeTarget = { profile ->
        RemoteRuntimeTarget(profile)
    }
) {
    private val mutableTargets = MutableStateFlow(buildTargets())
    val targets: StateFlow<List<RuntimeTarget>> = mutableTargets.asStateFlow()

    private val mutableSelected = MutableStateFlow(resolveSelected(mutableTargets.value))
    val selected: StateFlow<RuntimeTarget?> = mutableSelected.asStateFlow()

    fun remoteProfiles(): List<ConnectionProfile> = store.connections()

    fun select(id: String?) {
        val target = id?.let { targetId -> mutableTargets.value.firstOrNull { it.id == targetId } }
        require(id == null || target != null) { "Unknown runtime target: $id" }
        store.selectedRuntimeId = id
        mutableSelected.value = target
    }

    fun upsertRemote(profile: ConnectionProfile) {
        val selectedBefore = store.selectedRuntimeId
        store.upsertConnection(profile)
        rebuildTargets()

        when {
            selectedBefore == profile.id -> select(profile.id)
            selectedBefore == null -> select(profile.id)
            else -> mutableSelected.value = resolveSelected(mutableTargets.value)
        }
    }

    fun deleteRemote(id: String) {
        val wasSelected = store.selectedRuntimeId == id
        store.deleteConnection(id)
        rebuildTargets()

        if (wasSelected) {
            val fallback = store.connections().firstOrNull()?.id
            select(fallback)
        } else {
            mutableSelected.value = resolveSelected(mutableTargets.value)
        }
    }

    fun refresh() {
        rebuildTargets()
        mutableSelected.value = resolveSelected(mutableTargets.value)
    }

    private fun buildTargets(): List<RuntimeTarget> = buildList {
        add(localTarget)
        addAll(store.connections().map(remoteFactory))
    }

    private fun rebuildTargets() {
        mutableTargets.value = buildTargets()
    }

    private fun resolveSelected(targets: List<RuntimeTarget>): RuntimeTarget? =
        store.selectedRuntimeId?.let { selectedId -> targets.firstOrNull { it.id == selectedId } }
}
