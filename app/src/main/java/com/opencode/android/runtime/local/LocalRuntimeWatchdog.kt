package com.opencode.android.runtime.local

import com.opencode.android.runtime.LocalRuntimeStatus

internal class LocalRuntimeWatchdog(
    private val failureThreshold: Int = 3
) {
    private var consecutiveFailures = 0

    init {
        require(failureThreshold > 0)
    }

    fun observe(status: LocalRuntimeStatus): Boolean {
        if (status is LocalRuntimeStatus.Stopped) {
            consecutiveFailures++
            if (consecutiveFailures >= failureThreshold) {
                consecutiveFailures = 0
                return true
            }
            return false
        }

        consecutiveFailures = 0
        return false
    }
}
