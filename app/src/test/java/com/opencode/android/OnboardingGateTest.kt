package com.opencode.android

import com.opencode.android.runtime.LocalRuntimeStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingGateTest {
    @Test
    fun missingRuntimeAndRemoteRequiresOnboarding() {
        assertFalse(
            hasUsableRuntimeSetup(
                localRuntimeStatus = LocalRuntimeStatus.NotInstalled,
                hasRemoteConnection = false
            )
        )
    }

    @Test
    fun configuredRemoteIsUsableWithoutLocalRuntime() {
        assertTrue(
            hasUsableRuntimeSetup(
                localRuntimeStatus = LocalRuntimeStatus.NotInstalled,
                hasRemoteConnection = true
            )
        )
    }

    @Test
    fun installedLocalRuntimeIsUsableWithoutProviderCredential() {
        assertTrue(
            hasUsableRuntimeSetup(
                localRuntimeStatus = LocalRuntimeStatus.Stopped("1.0.0", 4097),
                hasRemoteConnection = false
            )
        )
    }

    @Test
    fun readyLocalRuntimeIsUsable() {
        assertTrue(
            hasUsableRuntimeSetup(
                localRuntimeStatus = LocalRuntimeStatus.Ready("1.0.0", 4097),
                hasRemoteConnection = false
            )
        )
    }

    @Test
    fun brokenLocalRuntimeCannotBypassOnboarding() {
        assertFalse(
            hasUsableRuntimeSetup(
                localRuntimeStatus = LocalRuntimeStatus.Broken("metadata missing"),
                hasRemoteConnection = false
            )
        )
    }
}
