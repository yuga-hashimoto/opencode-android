package com.opencode.android.feature.wakeword

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.opencode.android.MainActivity
import com.opencode.android.R
import com.opencode.android.feature.assistant.OpenCodeVoiceInteractionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WakeWordService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listenJob: Job? = null
    private var detector: OpenWakeWordDetector? = null
    private var vad: VoiceActivityDetector? = null
    private var audioRecord: AudioRecord? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        detector = OpenWakeWordDetector(this)
        vad = VoiceActivityDetector()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        startForegroundWithNotification()
        startListening()
        return START_STICKY
    }

    override fun onDestroy() {
        stopListening()
        detector?.release()
        detector = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundWithNotification() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.wake_word_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)

        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, WakeWordService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.wake_word_notification_title))
            .setContentText(getString(R.string.wake_word_notification_text))
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(tapIntent)
            .addAction(0, getString(R.string.wake_word_notification_stop), stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startListening() {
        if (listenJob?.isActive == true) return

        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
            acquire()
        }

        listenJob = scope.launch {
            val det = detector ?: return@launch
            if (!det.initialize()) {
                Log.e(TAG, "Detector initialization failed")
                stopSelf()
                return@launch
            }

            val bufferSize = maxOf(
                AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ),
                FRAME_SIZE * 2
            )

            val record = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "No microphone permission", e)
                stopSelf()
                return@launch
            }

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                record.release()
                stopSelf()
                return@launch
            }

            audioRecord = record
            record.startRecording()
            Log.i(TAG, "Wake word listening started")

            val buffer = ShortArray(FRAME_SIZE)
            val vadLocal = vad ?: VoiceActivityDetector()

            try {
                while (isActive) {
                    val read = record.read(buffer, 0, FRAME_SIZE)
                    if (read <= 0) continue

                    val samples = if (read == FRAME_SIZE) buffer else buffer.copyOf(read)

                    if (!vadLocal.isSpeech(samples)) continue

                    val result = det.processAudio(samples)
                    if (result != null) {
                        Log.i(TAG, "Wake word detected: ${result.keyword} (${result.confidence})")
                        onWakeWordDetected()
                        det.reset()
                        vadLocal.reset()
                    }
                }
            } finally {
                runCatching { record.stop() }
                record.release()
                audioRecord = null
                Log.i(TAG, "Wake word listening stopped")
            }
        }
    }

    private fun onWakeWordDetected() {
        OpenCodeVoiceInteractionService.show(this)
    }

    private fun stopListening() {
        listenJob?.cancel()
        listenJob = null
    }

    companion object {
        private const val TAG = "WakeWordService"
        private const val CHANNEL_ID = "wakeword_channel"
        private const val NOTIFICATION_ID = 9001
        private const val ACTION_STOP = "com.opencode.android.action.STOP_WAKEWORD"
        private const val SAMPLE_RATE = 16000
        private const val FRAME_SIZE = 1280
        private const val WAKELOCK_TAG = "opencode:wakeword"
        private const val WAKELOCK_TIMEOUT = 10 * 60 * 1000L

        fun start(context: Context) {
            val intent = Intent(context, WakeWordService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, WakeWordService::class.java).apply { action = ACTION_STOP }
            )
        }

        fun isRunning(context: Context): Boolean {
            val nm = context.getSystemService(NotificationManager::class.java)
            return nm.activeNotifications.any { it.id == NOTIFICATION_ID }
        }
    }
}
