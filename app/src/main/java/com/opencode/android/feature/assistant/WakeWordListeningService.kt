package com.opencode.android.feature.assistant

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.opencode.android.MainActivity
import com.opencode.android.OpenCodeApplication
import com.opencode.android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal fun matchesWakePhrase(
    recognizedText: String,
    phrases: List<String>
): Boolean {
    val recognized = normalizeWakeText(recognizedText)
    if (recognized.isEmpty()) return false
    return phrases.any { phrase ->
        val normalizedPhrase = normalizeWakeText(phrase)
        normalizedPhrase.isNotEmpty() &&
            (recognized == normalizedPhrase ||
                recognized.startsWith("$normalizedPhrase ") ||
                recognized.endsWith(" $normalizedPhrase") ||
                recognized.contains(" $normalizedPhrase "))
    }
}

private fun normalizeWakeText(value: String): String = value
    .lowercase()
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    .trim()
    .replace(Regex("\\s+"), " ")

class WakeWordListeningService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listeningJob: Job? = null
    private lateinit var speech: SpeechRecognizerManager
    private lateinit var app: OpenCodeApplication

    override fun onCreate() {
        super.onCreate()
        app = application as OpenCodeApplication
        speech = SpeechRecognizerManager(applicationContext)
        createChannel()
        startForeground(NOTIFICATION_ID, notification("ウェイクワードを準備しています"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopListeningService()
            ACTION_START, null -> startListeningLoop()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        listeningJob?.cancel()
        speech.destroy()
        app.settings.wakeWordListeningEnabled = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startListeningLoop() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifyAndStop("マイク権限が必要です")
            return
        }
        val pack = app.wakeWordPackManager.installed()
        if (pack == null) {
            notifyAndStop("ウェイクワードパックが未導入です")
            return
        }
        app.settings.wakeWordListeningEnabled = true
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification("「${pack.phrases.first()}」を待機中"))
        listeningJob?.cancel()
        listeningJob = scope.launch {
            var retryDelay = INITIAL_RETRY_DELAY_MILLIS
            while (isActive) {
                var wakeDetected = false
                runCatching {
                    speech.startListening(pack.languageTag).collect { result ->
                        when (result) {
                            is SpeechResult.Result -> {
                                if (matchesWakePhrase(result.text, pack.phrases)) {
                                    wakeDetected = true
                                }
                            }
                            is SpeechResult.Error -> throw IllegalStateException(result.message)
                            else -> Unit
                        }
                    }
                }.onFailure {
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MILLIS)
                }.onSuccess {
                    retryDelay = INITIAL_RETRY_DELAY_MILLIS
                }

                if (wakeDetected) {
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, notification("ホームアシストを起動しました"))
                    OpenCodeVoiceInteractionService.show(this@WakeWordListeningService)
                    delay(ASSISTANT_RESUME_DELAY_MILLIS)
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, notification("「${pack.phrases.first()}」を待機中"))
                } else {
                    delay(BETWEEN_LISTENS_DELAY_MILLIS)
                }
            }
        }
    }

    private fun stopListeningService() {
        listeningJob?.cancel()
        app.settings.wakeWordListeningEnabled = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notifyAndStop(message: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification(message, ongoing = false))
        app.settings.wakeWordListeningEnabled = false
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun notification(
        text: String,
        ongoing: Boolean = true
    ): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, WakeWordListeningService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("OpenCodeウェイクワード")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .addAction(0, "停止", stopIntent)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ウェイクワード待機",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "マイクを使用してユーザーが導入したウェイクワードを待機します"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "opencode_wake_word"
        private const val NOTIFICATION_ID = 4207
        private const val INITIAL_RETRY_DELAY_MILLIS = 1_000L
        private const val MAX_RETRY_DELAY_MILLIS = 30_000L
        private const val BETWEEN_LISTENS_DELAY_MILLIS = 500L
        private const val ASSISTANT_RESUME_DELAY_MILLIS = 30_000L
        const val ACTION_START = "com.opencode.android.wakeword.START"
        const val ACTION_STOP = "com.opencode.android.wakeword.STOP"

        fun start(context: Context) {
            require(
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
            ) {
                "Microphone permission is required before starting wake-word listening"
            }
            ContextCompat.startForegroundService(
                context,
                Intent(context, WakeWordListeningService::class.java).setAction(ACTION_START)
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, WakeWordListeningService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}

class WakeWordListeningController(private val context: Context) {
    fun start() = WakeWordListeningService.start(context)
    fun stop() = WakeWordListeningService.stop(context)
}
