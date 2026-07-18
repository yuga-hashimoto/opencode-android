package com.opencode.android.runtime.local

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.opencode.android.MainActivity
import com.opencode.android.OpenCodeApplication
import com.opencode.android.R
import com.opencode.android.runtime.LocalRuntimeStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LocalRuntimeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var operation: Job? = null
    private lateinit var manager: LocalRuntimeManager

    override fun onCreate() {
        super.onCreate()
        manager = (application as OpenCodeApplication).localRuntimeManager
        createChannel()
        startForeground(NOTIFICATION_ID, notification(manager.status()))
        scope.launch {
            manager.state.collectLatest { status ->
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, notification(status))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_INSTALL_AND_START -> launchOperation { manager.installAndStart() }
            ACTION_START -> launchOperation { manager.start() }
            ACTION_REINSTALL -> launchOperation { manager.reinstall() }
            ACTION_STOP -> launchOperation {
                manager.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            null -> {
                if (manager.status() is LocalRuntimeStatus.Stopped) {
                    launchOperation { manager.start() }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        operation?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun launchOperation(block: suspend () -> Unit) {
        operation?.cancel()
        operation = scope.launch(Dispatchers.IO) { block() }
    }

    private fun notification(status: LocalRuntimeStatus): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, LocalRuntimeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val (title, text, indeterminate, progress) = when (status) {
            LocalRuntimeStatus.NotInstalled -> NotificationState("OpenCode Android", "ローカルランタイムは未導入です")
            is LocalRuntimeStatus.Installing -> NotificationState(
                "ローカルOpenCodeをセットアップ中",
                status.step,
                status.progress == null,
                ((status.progress ?: 0f) * 100).toInt()
            )
            is LocalRuntimeStatus.Starting -> NotificationState("ローカルOpenCodeを起動中", "OpenCode ${status.version}", true)
            is LocalRuntimeStatus.Stopped -> NotificationState("ローカルOpenCodeは停止中", "OpenCode ${status.version}")
            is LocalRuntimeStatus.Ready -> NotificationState("ローカルOpenCodeが稼働中", "OpenCode ${status.version} · 127.0.0.1:${status.port}")
            is LocalRuntimeStatus.Broken -> NotificationState("ローカルOpenCodeで問題が発生", status.reason)
            is LocalRuntimeStatus.UnsupportedAbi -> NotificationState("この端末では利用できません", "未対応ABI: ${status.abi}")
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(status is LocalRuntimeStatus.Ready || status is LocalRuntimeStatus.Installing || status is LocalRuntimeStatus.Starting)
            .setOnlyAlertOnce(true)
            .setProgress(if (indeterminate || progress > 0) 100 else 0, progress, indeterminate)
            .addAction(0, "停止", stopIntent)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ローカルOpenCode",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Android端末内で実行するOpenCodeの状態"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private data class NotificationState(
        val title: String,
        val text: String,
        val indeterminate: Boolean = false,
        val progress: Int = 0
    )

    companion object {
        private const val CHANNEL_ID = "local_opencode_runtime"
        private const val NOTIFICATION_ID = 4107
        const val ACTION_INSTALL_AND_START = "com.opencode.android.local.INSTALL_AND_START"
        const val ACTION_START = "com.opencode.android.local.START"
        const val ACTION_STOP = "com.opencode.android.local.STOP"
        const val ACTION_REINSTALL = "com.opencode.android.local.REINSTALL"

        fun send(context: Context, action: String) {
            val intent = Intent(context, LocalRuntimeService::class.java).setAction(action)
            if (action == ACTION_STOP) {
                context.startService(intent)
            } else {
                ContextCompat.startForegroundService(context, intent)
            }
        }
    }
}

class LocalRuntimeServiceController(private val context: Context) {
    fun installAndStart() = LocalRuntimeService.send(context, LocalRuntimeService.ACTION_INSTALL_AND_START)
    fun start() = LocalRuntimeService.send(context, LocalRuntimeService.ACTION_START)
    fun stop() = LocalRuntimeService.send(context, LocalRuntimeService.ACTION_STOP)
    fun reinstall() = LocalRuntimeService.send(context, LocalRuntimeService.ACTION_REINSTALL)
}
