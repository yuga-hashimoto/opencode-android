package com.opencode.android.feature.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

object ResumeCommandHelper {

    fun buildCommand(sessionId: String, runtimeType: String = "local"): String =
        if (runtimeType == "remote") {
            "opencode --session $sessionId --resume --remote"
        } else {
            "opencode --session $sessionId --resume"
        }

    fun copyToClipboard(context: Context, sessionId: String, runtimeType: String = "local") {
        val command = buildCommand(sessionId, runtimeType)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Resume command", command))
        Toast.makeText(context, "Resume command copied", Toast.LENGTH_SHORT).show()
    }
}
