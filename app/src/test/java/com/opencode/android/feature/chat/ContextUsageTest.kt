package com.opencode.android.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContextUsageTest {
    @Test
    fun `context percentage is rounded and clamped`() {
        assertEquals(25, contextUsagePercent(250L, 1_000L))
        assertEquals(100, contextUsagePercent(1_200L, 1_000L))
    }

    @Test
    fun `context percentage is unavailable without valid inputs`() {
        assertNull(contextUsagePercent(null, 1_000L))
        assertNull(contextUsagePercent(100L, null))
        assertNull(contextUsagePercent(-1L, 1_000L))
        assertNull(contextUsagePercent(100L, 0L))
    }
}
