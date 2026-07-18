package com.opencode.android.hotword

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.opencode.android.OpenCodeApplication

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!HotwordStartupPolicy.canStartFromBoot(android.os.Build.VERSION.SDK_INT)) return
        val app = context.applicationContext as? OpenCodeApplication ?: return
        if (!app.settings.hotwordEnabled) return
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        runCatching { HotwordService.start(context) }
    }
}
