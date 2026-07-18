package com.opencode.android.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.opencode.android.data.SettingsRepository
import com.opencode.android.service.HotwordService

/**
 * 起動時にホットワードサービスを開始
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed")
            
            val settings = SettingsRepository.getInstance(context)
            
            if (settings.hotwordEnabled && settings.isConfigured()) {
                Log.d(TAG, "Starting HotwordService on boot")
                HotwordService.start(context)
            }
        }
    }
}
