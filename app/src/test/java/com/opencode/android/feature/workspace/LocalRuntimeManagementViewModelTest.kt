package com.opencode.android.feature.workspace

import com.opencode.android.runtime.LocalRuntimeStatus
import com.opencode.android.runtime.local.LocalRuntimeDiagnostics
import com.opencode.android.runtime.local.LocalRuntimeProcessMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalRuntimeManagementViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load collects diagnostics`() = runTest(dispatcher) {
        val runtimeState = MutableStateFlow<LocalRuntimeStatus>(LocalRuntimeStatus.Ready("1.18.3", 4097))
        val expected = diagnostics(runtimeState.value)
        val viewModel = LocalRuntimeManagementViewModel(
            runtimeState = runtimeState,
            diagnosticsProvider = { expected },
            repairAction = {},
            deleteAction = {}
        )

        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(expected, viewModel.state.value.diagnostics)
        assertEquals(null, viewModel.state.value.error)
    }

    @Test
    fun `diagnostic failure is exposed`() = runTest(dispatcher) {
        val runtimeState = MutableStateFlow<LocalRuntimeStatus>(LocalRuntimeStatus.Stopped("1.18.3", 4097))
        val viewModel = LocalRuntimeManagementViewModel(
            runtimeState = runtimeState,
            diagnosticsProvider = { error("diagnostic failed") },
            repairAction = {},
            deleteAction = {}
        )

        advanceUntilIdle()

        assertEquals("diagnostic failed", viewModel.state.value.error)
        assertEquals(null, viewModel.state.value.diagnostics)
    }

    @Test
    fun `confirmed delete waits for not installed state and reports completion`() = runTest(dispatcher) {
        val runtimeState = MutableStateFlow<LocalRuntimeStatus>(LocalRuntimeStatus.Ready("1.18.3", 4097))
        var deleteCalls = 0
        val viewModel = LocalRuntimeManagementViewModel(
            runtimeState = runtimeState,
            diagnosticsProvider = { diagnostics(runtimeState.value) },
            repairAction = {},
            deleteAction = { deleteCalls++ }
        )
        advanceUntilIdle()

        viewModel.requestDelete()
        assertTrue(viewModel.state.value.showDeleteConfirmation)

        viewModel.confirmDelete()
        assertEquals(1, deleteCalls)
        assertTrue(viewModel.state.value.isDeleting)
        assertFalse(viewModel.state.value.showDeleteConfirmation)

        runtimeState.value = LocalRuntimeStatus.NotInstalled
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isDeleting)
        assertTrue(viewModel.state.value.deleteCompleted)
    }

    @Test
    fun `delete timeout clears deleting state and reports error`() = runTest(dispatcher) {
        val runtimeState = MutableStateFlow<LocalRuntimeStatus>(LocalRuntimeStatus.Ready("1.18.3", 4097))
        val viewModel = LocalRuntimeManagementViewModel(
            runtimeState = runtimeState,
            diagnosticsProvider = { diagnostics(runtimeState.value) },
            repairAction = {},
            deleteAction = {},
            deleteTimeoutMillis = 1_000L
        )
        advanceUntilIdle()

        viewModel.confirmDelete()
        assertTrue(viewModel.state.value.isDeleting)

        advanceTimeBy(1_000L)
        runCurrent()

        assertFalse(viewModel.state.value.isDeleting)
        assertEquals("ランタイムの削除がタイムアウトしました", viewModel.state.value.error)
    }

    @Test
    fun `repair action is invoked`() = runTest(dispatcher) {
        val runtimeState = MutableStateFlow<LocalRuntimeStatus>(LocalRuntimeStatus.Broken("broken"))
        var repairCalls = 0
        val viewModel = LocalRuntimeManagementViewModel(
            runtimeState = runtimeState,
            diagnosticsProvider = { diagnostics(runtimeState.value) },
            repairAction = { repairCalls++ },
            deleteAction = {}
        )
        advanceUntilIdle()

        viewModel.repair()

        assertEquals(1, repairCalls)
        assertNotNull(viewModel.state.value.diagnostics)
    }

    private fun diagnostics(status: LocalRuntimeStatus) = LocalRuntimeDiagnostics(
        status = status,
        version = "1.18.3",
        abi = "arm64-v8a",
        port = 4097,
        runtimeBytes = 100,
        freeBytes = 200,
        process = LocalRuntimeProcessMetrics(42, 50, 1_000),
        tools = emptyList(),
        logTail = "ok",
        collectedAtMillis = 123
    )
}
