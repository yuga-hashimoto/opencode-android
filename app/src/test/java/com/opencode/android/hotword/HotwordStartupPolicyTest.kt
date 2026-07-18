package com.opencode.android.hotword

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HotwordStartupPolicyTest {
    @Test
    fun `boot startup is allowed only before Android 11`() {
        assertTrue(HotwordStartupPolicy.canStartFromBoot(29))
        assertFalse(HotwordStartupPolicy.canStartFromBoot(30))
        assertFalse(HotwordStartupPolicy.canStartFromBoot(34))
    }

    @Test
    fun `foreground startup requires enabled setting and microphone permission`() {
        assertTrue(HotwordStartupPolicy.canStartFromForeground(enabled = true, hasMicrophonePermission = true))
        assertFalse(HotwordStartupPolicy.canStartFromForeground(enabled = false, hasMicrophonePermission = true))
        assertFalse(HotwordStartupPolicy.canStartFromForeground(enabled = true, hasMicrophonePermission = false))
    }
}
