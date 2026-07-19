package com.opencode.android.feature.home

import com.opencode.android.runtime.RuntimeState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewModelTest {
    @Test
    fun `home is connected when target state is connected without catalog health`() {
        assertTrue(HomeUiState(runtimeState = RuntimeState.Connected("1.2.3")).connected)
    }

    @Test
    fun `home is disconnected when target is unavailable and health is absent`() {
        assertFalse(HomeUiState(runtimeState = RuntimeState.Disconnected).connected)
    }
}
