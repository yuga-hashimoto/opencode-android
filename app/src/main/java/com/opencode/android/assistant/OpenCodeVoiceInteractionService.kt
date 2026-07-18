package com.opencode.android.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.util.Log

class OpenCodeVoiceInteractionService : VoiceInteractionService() {
    private var ready = false
    private var pendingShow = false

    override fun onReady() {
        super.onReady()
        ready = true
        if (pendingShow) {
            pendingShow = false
            showAssistant()
        }
    }

    override fun onShutdown() {
        ready = false
        super.onShutdown()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHOW_ASSISTANT) showAssistant()
        return START_STICKY
    }

    private fun showAssistant() {
        if (!ready) {
            pendingShow = true
            return
        }
        runCatching {
            showSession(Bundle(), VoiceInteractionSession.SHOW_WITH_ASSIST)
        }.onFailure { Log.e(TAG, "Unable to show OpenCode assistant", it) }
    }

    companion object {
        private const val TAG = "OpenCodeVoiceService"
        const val ACTION_SHOW_ASSISTANT = "com.opencode.android.action.SHOW_ASSISTANT"

        fun show(context: Context) {
            context.startService(
                Intent(context, OpenCodeVoiceInteractionService::class.java).apply {
                    action = ACTION_SHOW_ASSISTANT
                }
            )
        }
    }
}
