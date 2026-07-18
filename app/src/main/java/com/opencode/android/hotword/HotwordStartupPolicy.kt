package com.opencode.android.hotword

object HotwordStartupPolicy {
    fun canStartFromBoot(sdkInt: Int): Boolean = sdkInt < 30

    fun canStartFromForeground(
        enabled: Boolean,
        hasMicrophonePermission: Boolean
    ): Boolean = enabled && hasMicrophonePermission
}
