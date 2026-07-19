package com.opencode.android.core.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.opencode.android.MainActivity
import com.opencode.android.R
import com.opencode.android.core.api.PermissionRequest
import com.opencode.android.runtime.PermissionResponse

class RuntimeNotificationHelper(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    init {
        ensureChannels()
    }

    fun notifyPermission(request: PermissionRequest) {
        if (!canPostNotifications()) return
        val openIntent = pendingActivityIntent(
            requestCode = request.id.hashCode(),
            extras = mapOf(
                EXTRA_OPEN_ACTIVITY to true,
                EXTRA_SESSION_ID to request.sessionId
            )
        )
        val allowOnce = permissionActionIntent(request, PermissionResponse.ONCE, remember = false)
        val allowAlways = permissionActionIntent(request, PermissionResponse.ALWAYS, remember = true)
        val reject = permissionActionIntent(request, PermissionResponse.REJECT, remember = false)

        val notification = NotificationCompat.Builder(context, CHANNEL_APPROVALS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.notification_approval_title))
            .setContentText(request.permission)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    buildString {
                        append(request.permission)
                        if (request.patterns.isNotEmpty()) {
                            append('\n')
                            append(request.patterns.joinToString("\n"))
                        }
                    }
                )
            )
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, context.getString(R.string.allow_once), allowOnce)
            .addAction(0, context.getString(R.string.always_allow), allowAlways)
            .addAction(0, context.getString(R.string.reject), reject)
            .build()

        safeNotify(permissionNotificationId(request.id), notification)
    }

    fun notifySessionComplete(sessionId: String) {
        if (!canPostNotifications()) return
        val intent = pendingActivityIntent(
            requestCode = sessionId.hashCode(),
            extras = mapOf(
                EXTRA_OPEN_CHAT to true,
                EXTRA_SESSION_ID to sessionId
            )
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(context.getString(R.string.notification_complete_title))
            .setContentText(context.getString(R.string.notification_complete_body, sessionId.take(12)))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        safeNotify(statusNotificationId(sessionId, "done"), notification)
    }

    fun notifySessionError(sessionId: String?, message: String?) {
        if (!canPostNotifications()) return
        val intent = pendingActivityIntent(
            requestCode = (sessionId ?: "error").hashCode(),
            extras = mapOf(
                EXTRA_OPEN_ACTIVITY to true,
                EXTRA_SESSION_ID to (sessionId.orEmpty())
            )
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.notification_error_title))
            .setContentText(message ?: context.getString(R.string.notification_error_body))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        safeNotify(statusNotificationId(sessionId ?: "error", "err"), notification)
    }

    fun cancelPermission(permissionId: String) {
        manager.cancel(permissionNotificationId(permissionId))
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun safeNotify(id: Int, notification: Notification) {
        if (!canPostNotifications()) return
        runCatching { manager.notify(id, notification) }
    }

    private fun permissionActionIntent(
        request: PermissionRequest,
        response: PermissionResponse,
        remember: Boolean
    ): PendingIntent {
        val intent = Intent(context, PermissionActionReceiver::class.java).apply {
            action = ACTION_PERMISSION_RESPONSE
            putExtra(EXTRA_SESSION_ID, request.sessionId)
            putExtra(EXTRA_PERMISSION_ID, request.id)
            putExtra(EXTRA_PERMISSION_RESPONSE, response.apiValue)
            putExtra(EXTRA_PERMISSION_REMEMBER, remember)
        }
        val requestCode = (request.id + response.apiValue).hashCode()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun pendingActivityIntent(requestCode: Int, extras: Map<String, Any>): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            extras.forEach { (key, value) ->
                when (value) {
                    is Boolean -> putExtra(key, value)
                    is String -> putExtra(key, value)
                    is Int -> putExtra(key, value)
                }
            }
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_APPROVALS,
                context.getString(R.string.notification_channel_approvals),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                context.getString(R.string.notification_channel_status),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private fun permissionNotificationId(permissionId: String): Int =
        20_000 + (permissionId.hashCode() and 0x0FFF)

    private fun statusNotificationId(sessionId: String, kind: String): Int =
        30_000 + ((sessionId + kind).hashCode() and 0x0FFF)

    companion object {
        const val CHANNEL_APPROVALS = "opencode_approvals"
        const val CHANNEL_STATUS = "opencode_status"
        const val ACTION_PERMISSION_RESPONSE = "com.opencode.android.PERMISSION_RESPONSE"
        const val EXTRA_OPEN_ACTIVITY = "open_activity"
        const val EXTRA_OPEN_CHAT = "open_chat"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_PERMISSION_ID = "permission_id"
        const val EXTRA_PERMISSION_RESPONSE = "permission_response"
        const val EXTRA_PERMISSION_REMEMBER = "permission_remember"
    }
}
