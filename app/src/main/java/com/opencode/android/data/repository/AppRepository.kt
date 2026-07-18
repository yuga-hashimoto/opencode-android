package com.opencode.android.data.repository

import com.opencode.android.data.connection.ConnectionProfile
import com.opencode.android.data.connection.SecureSettingsRepository
import com.opencode.android.runtime.OpenCodeBackend
import com.opencode.android.runtime.remote.RemoteOpenCodeBackend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppRepository(
    private val settings: SecureSettingsRepository
) {
    private val _selectedBackend = MutableStateFlow(createSelectedBackend())
    val selectedBackend: StateFlow<OpenCodeBackend?> = _selectedBackend.asStateFlow()

    fun connections(): List<ConnectionProfile> = settings.connections()

    fun selectConnection(id: String?) {
        settings.selectedConnectionId = id
        _selectedBackend.value = createSelectedBackend()
    }

    fun upsertConnection(profile: ConnectionProfile) {
        settings.upsertConnection(profile)
        if (settings.selectedConnectionId == profile.id || settings.selectedConnectionId == null) {
            settings.selectedConnectionId = profile.id
            _selectedBackend.value = RemoteOpenCodeBackend(profile)
        }
    }

    fun deleteConnection(id: String) {
        settings.deleteConnection(id)
        _selectedBackend.value = createSelectedBackend()
    }

    private fun createSelectedBackend(): OpenCodeBackend? =
        settings.selectedConnection()?.let(::RemoteOpenCodeBackend)
}
