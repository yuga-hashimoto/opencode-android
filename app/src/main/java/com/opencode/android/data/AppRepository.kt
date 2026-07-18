package com.opencode.android.data

import com.opencode.android.backend.OpenCodeBackend
import com.opencode.android.backend.RemoteOpenCodeBackend
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
