package com.opencode.android.feature.workspace

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class TabType { AGENT, FILE, TERMINAL }

data class WorkspaceTab(
    val id: String,
    val type: TabType,
    val title: String,
    val sessionId: String? = null,
    val filePath: String? = null
)

class WorkspaceTabManager {
    private val _tabs = MutableStateFlow<List<WorkspaceTab>>(emptyList())
    val tabs: StateFlow<List<WorkspaceTab>> = _tabs.asStateFlow()

    private val _selectedTabId = MutableStateFlow<String?>(null)
    val selectedTabId: StateFlow<String?> = _selectedTabId.asStateFlow()

    fun addTab(tab: WorkspaceTab) {
        _tabs.update { current ->
            val trimmed = if (current.size >= MAX_TABS) {
                val evict = current.firstOrNull { it.id != _selectedTabId.value } ?: current.first()
                current.filterNot { it.id == evict.id }
            } else {
                current
            }
            trimmed + tab
        }
        _selectedTabId.value = tab.id
    }

    fun removeTab(tabId: String) {
        val current = _tabs.value
        val index = current.indexOfFirst { it.id == tabId }
        if (index < 0) return
        val updated = current.filterNot { it.id == tabId }
        _tabs.value = updated
        if (_selectedTabId.value == tabId) {
            _selectedTabId.value = updated.getOrNull(index.coerceAtMost(updated.lastIndex))?.id
        }
    }

    fun selectTab(tabId: String) {
        if (_tabs.value.any { it.id == tabId }) {
            _selectedTabId.value = tabId
        }
    }

    fun renameTab(tabId: String, newTitle: String) {
        _tabs.update { current ->
            current.map { if (it.id == tabId) it.copy(title = newTitle) else it }
        }
    }

    fun closeOthers(tabId: String) {
        _tabs.update { current -> current.filter { it.id == tabId } }
        if (_tabs.value.any { it.id == tabId }) {
            _selectedTabId.value = tabId
        }
    }

    companion object {
        const val MAX_TABS = 5
    }
}
