package com.opencode.android.feature.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.android.runtime.LocalRuntimeStatus
import com.opencode.android.runtime.local.LocalRuntimeDiagnostics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LocalRuntimeManagementUiState(
    val diagnostics: LocalRuntimeDiagnostics? = null,
    val isLoading: Boolean = true,
    val isDeleting: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val deleteCompleted: Boolean = false,
    val error: String? = null
)

class LocalRuntimeManagementViewModel(
    runtimeState: StateFlow<LocalRuntimeStatus>,
    private val diagnosticsProvider: suspend () -> LocalRuntimeDiagnostics,
    private val repairAction: () -> Unit,
    private val deleteAction: () -> Unit,
    private val deleteTimeoutMillis: Long = 30_000L
) : ViewModel() {
    init {
        require(deleteTimeoutMillis > 0L)
    }

    private val mutableState = MutableStateFlow(LocalRuntimeManagementUiState())
    val state: StateFlow<LocalRuntimeManagementUiState> = mutableState.asStateFlow()
    private var deleteTimeoutJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            runtimeState.collect { status ->
                if (status is LocalRuntimeStatus.NotInstalled && mutableState.value.isDeleting) {
                    deleteTimeoutJob?.cancel()
                    deleteTimeoutJob = null
                    mutableState.update {
                        it.copy(
                            isDeleting = false,
                            deleteCompleted = true,
                            diagnostics = it.diagnostics?.copy(status = status)
                        )
                    }
                }
            }
        }
    }

    fun refresh() {
        mutableState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { diagnosticsProvider() }
                .onSuccess { diagnostics ->
                    mutableState.update {
                        it.copy(
                            diagnostics = diagnostics,
                            isLoading = false,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(
                            diagnostics = null,
                            isLoading = false,
                            error = error.message?.takeIf(String::isNotBlank)
                                ?: "ローカルランタイムの診断に失敗しました"
                        )
                    }
                }
        }
    }

    fun repair() {
        repairAction()
    }

    fun requestDelete() {
        mutableState.update { it.copy(showDeleteConfirmation = true) }
    }

    fun dismissDelete() {
        mutableState.update { it.copy(showDeleteConfirmation = false) }
    }

    fun confirmDelete() {
        mutableState.update {
            it.copy(
                showDeleteConfirmation = false,
                isDeleting = true,
                error = null
            )
        }
        runCatching(deleteAction)
            .onSuccess { startDeleteTimeout() }
            .onFailure { error ->
                deleteTimeoutJob?.cancel()
                deleteTimeoutJob = null
                mutableState.update {
                    it.copy(
                        isDeleting = false,
                        error = error.message?.takeIf(String::isNotBlank)
                            ?: "ローカルランタイムを削除できません"
                    )
                }
            }
    }

    private fun startDeleteTimeout() {
        deleteTimeoutJob?.cancel()
        deleteTimeoutJob = viewModelScope.launch {
            delay(deleteTimeoutMillis)
            if (mutableState.value.isDeleting) {
                mutableState.update {
                    it.copy(
                        isDeleting = false,
                        error = "ランタイムの削除がタイムアウトしました"
                    )
                }
            }
            deleteTimeoutJob = null
        }
    }

    fun consumeDeleteCompleted() {
        mutableState.update { it.copy(deleteCompleted = false) }
    }
}
