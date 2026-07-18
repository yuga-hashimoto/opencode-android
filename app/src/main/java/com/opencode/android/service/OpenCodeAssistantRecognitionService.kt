package com.opencode.android.service

import android.content.Intent
import android.speech.RecognitionService
import android.util.Log

/**
 * Minimal RecognitionService required for VoiceInteractionService to function correctly.
 */
class OpenCodeAndroidRecognitionService : RecognitionService() {
    companion object {
        private const val TAG = "OpenCodeAndroidRec"
    }

    override fun onStartListening(intent: Intent?, listener: Callback?) {
        Log.d(TAG, "onStartListening")
        // No-op: Actual recognition is handled in OpenCodeSession
    }

    override fun onCancel(listener: Callback?) {
        Log.d(TAG, "onCancel")
    }

    override fun onStopListening(listener: Callback?) {
        Log.d(TAG, "onStopListening")
    }
}
