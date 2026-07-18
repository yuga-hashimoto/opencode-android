package com.opencode.android.service

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * Service that hosts the VoiceInteractionSession.
 */
class OpenCodeAndroidSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return OpenCodeSession(this)
    }
}
