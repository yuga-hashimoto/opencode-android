package com.opencode.android.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.opencode.android.OpenCodeApplication
import com.opencode.android.runtime.PermissionResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PermissionActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != RuntimeNotificationHelper.ACTION_PERMISSION_RESPONSE) return
        val sessionId = intent.getStringExtra(RuntimeNotificationHelper.EXTRA_SESSION_ID) ?: return
        val permissionId = intent.getStringExtra(RuntimeNotificationHelper.EXTRA_PERMISSION_ID) ?: return
        val responseValue = intent.getStringExtra(RuntimeNotificationHelper.EXTRA_PERMISSION_RESPONSE) ?: return
        val remember = intent.getBooleanExtra(RuntimeNotificationHelper.EXTRA_PERMISSION_REMEMBER, false)

        val response = PermissionResponse.entries.firstOrNull { it.apiValue == responseValue } ?: return
        val app = context.applicationContext as? OpenCodeApplication ?: return
        val pending = goAsync()
        scope.launch {
            try {
                val backend = app.runtimeRegistry.selected.value ?: return@launch
                val ok = backend.respondToPermission(sessionId, permissionId, response, remember)
                if (ok) {
                    app.activityRepository.resolvePermission(permissionId)
                    app.notifications.cancelPermission(permissionId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
