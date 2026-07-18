package com.opencode.android.assistant

import android.content.Intent
import android.speech.RecognitionService

class OpenCodeRecognitionService : RecognitionService() {
    override fun onStartListening(intent: Intent?, listener: Callback?) = Unit
    override fun onCancel(listener: Callback?) = Unit
    override fun onStopListening(listener: Callback?) = Unit
}
