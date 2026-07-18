package com.opencode.android.runtime.local

import com.opencode.android.runtime.LocalRuntimeStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRuntimeWatchdogTest {
    @Test
    fun `restarts only after consecutive stopped observations`() {
        val watchdog = LocalRuntimeWatchdog(failureThreshold = 3)
        val stopped = LocalRuntimeStatus.Stopped("1.18.3", 4097)

        assertFalse(watchdog.observe(stopped))
        assertFalse(watchdog.observe(stopped))
        assertTrue(watchdog.observe(stopped))
        assertFalse(watchdog.observe(stopped))
    }

    @Test
    fun `healthy and transitional states reset failure counter`() {
        val watchdog = LocalRuntimeWatchdog(failureThreshold = 2)
        val stopped = LocalRuntimeStatus.Stopped("1.18.3", 4097)

        assertFalse(watchdog.observe(stopped))
        assertFalse(watchdog.observe(LocalRuntimeStatus.Ready("1.18.3", 4097)))
        assertFalse(watchdog.observe(stopped))
        assertFalse(watchdog.observe(LocalRuntimeStatus.Starting("1.18.3", 4097)))
        assertFalse(watchdog.observe(stopped))
    }

    @Test
    fun `broken and unsupported states never trigger automatic restart`() {
        val watchdog = LocalRuntimeWatchdog(failureThreshold = 1)

        assertFalse(watchdog.observe(LocalRuntimeStatus.Broken("corrupt")))
        assertFalse(watchdog.observe(LocalRuntimeStatus.UnsupportedAbi("armeabi-v7a")))
        assertFalse(watchdog.observe(LocalRuntimeStatus.NotInstalled))
    }
}
