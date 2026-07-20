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
                hasLocalProviderCredential = false,
                hasRemoteConnection = false
            )
        )
    }

    @Test
    fun configuredRemoteIsUsableWithoutLocalRuntime() {
        assertTrue(
            hasUsableRuntimeSetup(
                localRuntimeStatus = LocalRuntimeStatus.NotInstalled,
                hasLocalProviderCredential = false,
                hasRemoteConnection = true
            )
        )
    }

    @Test
    fun installedLocalRuntimeStillRequiresProviderCredential() {
        assertFalse(
            hasUsableRuntimeSetup(
                localRuntimeStatus = LocalRuntimeStatus.Stopped("1.0.0", 4097),
                hasLocalProviderCredential = false,
                hasRemoteConnection = false
            )
        )
    }

    @Test
    fun installedLocalRuntimeWithCredentialIsUsable() {
        assertTrue(
            hasUsableRuntimeSetup(
                localRuntimeStatus = LocalRuntimeStatus.Stopped("1.0.0", 4097),
                hasLocalProviderCredential = true,
                hasRemoteConnection = false
            )
        )
    }

    @Test
    fun brokenLocalRuntimeCannotBypassOnboarding() {
        assertFalse(
            hasUsableRuntimeSetup(
                localRuntimeStatus = LocalRuntimeStatus.Broken("metadata missing"),
                hasLocalProviderCredential = true,
                hasRemoteConnection = false
            )
        )
    }
}
