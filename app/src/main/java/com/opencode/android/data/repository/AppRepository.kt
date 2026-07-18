package com.opencode.android.data.repository

import com.opencode.android.data.connection.ConnectionProfile
import com.opencode.android.runtime.RuntimeRegistry
import com.opencode.android.runtime.RuntimeTarget
import kotlinx.coroutines.flow.StateFlow

/**
 * Temporary compatibility facade while feature ViewModels migrate to RuntimeRegistry directly.
 */
class AppRepository(
    val runtimeRegistry: RuntimeRegistry
) {
    val selectedBackend: StateFlow<RuntimeTarget?> = runtimeRegistry.selected

    fun connections(): List<ConnectionProfile> = runtimeRegistry.remoteProfiles()

    fun selectConnection(id: String?) {
        runtimeRegistry.select(id)
    }

    fun upsertConnection(profile: ConnectionProfile) {
        runtimeRegistry.upsertRemote(profile)
    }

    fun deleteConnection(id: String) {
        runtimeRegistry.deleteRemote(id)
    }
}
