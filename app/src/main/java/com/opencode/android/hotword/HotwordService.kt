package com.opencode.android.hotword

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.opencode.android.MainActivity
import com.opencode.android.OpenCodeApplication
import com.opencode.android.R
import com.opencode.android.assistant.OpenCodeVoiceInteractionService
import com.opencode.android.data.SecureSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.FileOutputStream

class HotwordService : Service(), RecognitionListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var settings: SecureSettingsRepository
    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var paused = false
    private var loading = false
    private var triggered = false

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PAUSE_HOTWORD -> pauseDetection()
                ACTION_RESUME_HOTWORD -> resumeDetection()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = (application as OpenCodeApplication).settings
        createChannel()
        val filter = IntentFilter().apply {
            addAction(ACTION_PAUSE_HOTWORD)
            addAction(ACTION_RESUME_HOTWORD)
        }
        ContextCompat.registerReceiver(
            this,
            controlReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val hasMicrophonePermission = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!settings.hotwordEnabled || !hasMicrophonePermission) {
            stopSelf()
            return START_NOT_STICKY
        }

        return runCatching {
            startForeground(NOTIFICATION_ID, createNotification())
            ensureModelAndListen()
            START_STICKY
        }.getOrElse { error ->
            Log.e(TAG, "Unable to start wake-word foreground service", error)
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { unregisterReceiver(controlReceiver) }
        stopSpeechService()
        runCatching { model?.close() }
        model = null
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureModelAndListen() {
        if (model != null) {
            startDetection()
            return
        }
        if (loading) return
        loading = true
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val modelDirectory = File(filesDir, MODEL_DIRECTORY)
                    if (!File(modelDirectory, "am/final.mdl").exists()) {
                        copyAssetFolder("model", modelDirectory)
                    }
                    Model(modelDirectory.absolutePath)
                }
            }
            loading = false
            loaded.onSuccess {
                model = it
                startDetection()
            }.onFailure {
                Log.e(TAG, "Unable to initialize Vosk model", it)
                stopSelf()
            }
        }
    }

    private fun startDetection() {
        if (paused || !settings.hotwordEnabled || speechService != null) return
        val activeModel = model ?: return
        runCatching {
            val grammar = WakeWordConfig.grammarJson(settings.wakeWord)
            val recognizer = Recognizer(activeModel, SAMPLE_RATE, grammar)
            speechService = SpeechService(recognizer, SAMPLE_RATE).also {
                it.startListening(this)
            }
            triggered = false
        }.onFailure {
            Log.e(TAG, "Unable to start wake-word recognition", it)
            scheduleRestart()
        }
    }

    private fun pauseDetection() {
        paused = true
        stopSpeechService()
    }

    private fun resumeDetection() {
        if (!settings.hotwordEnabled) return
        paused = false
        triggered = false
        scope.launch {
            delay(450)
            ensureModelAndListen()
        }
    }

    private fun stopSpeechService() {
        speechService?.let {
            runCatching { it.stop() }
            runCatching { it.shutdown() }
        }
        speechService = null
    }

    private fun scheduleRestart() {
        stopSpeechService()
        if (paused || !settings.hotwordEnabled) return
        scope.launch {
            delay(2_000)
            startDetection()
        }
    }

    override fun onPartialResult(hypothesis: String?) {
        detect(hypothesis)
    }

    override fun onResult(hypothesis: String?) {
        detect(hypothesis)
    }

    override fun onFinalResult(hypothesis: String?) {
        detect(hypothesis)
        if (!triggered) scheduleRestart()
    }

    override fun onError(exception: Exception?) {
        Log.w(TAG, "Wake-word recognizer stopped", exception)
        scheduleRestart()
    }

    override fun onTimeout() {
        scheduleRestart()
    }

    private fun detect(hypothesis: String?) {
        if (paused || triggered || hypothesis.isNullOrBlank()) return
        val text = runCatching { JSONObject(hypothesis).optString("text") }.getOrDefault("")
        val phrase = WakeWordConfig.normalize(settings.wakeWord)
        if (text.lowercase().contains(phrase)) {
            triggered = true
            pauseDetection()
            OpenCodeVoiceInteractionService.show(this)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val phrase = WakeWordConfig.normalize(settings.wakeWord)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText("マイク使用中・「$phrase」で起動")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun copyAssetFolder(assetPath: String, destination: File) {
        val children = assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            destination.parentFile?.mkdirs()
            assets.open(assetPath).use { input ->
                FileOutputStream(destination).use { output -> input.copyTo(output) }
            }
            return
        }
        destination.mkdirs()
        children.forEach { child ->
            copyAssetFolder("$assetPath/$child", File(destination, child))
        }
    }

    companion object {
        private const val TAG = "OpenCodeHotword"
        private const val CHANNEL_ID = "opencode_hotword"
        private const val NOTIFICATION_ID = 4101
        private const val MODEL_DIRECTORY = "vosk-hotword-model"
        private const val SAMPLE_RATE = 16_000.0f

        const val ACTION_PAUSE_HOTWORD = "com.opencode.android.action.PAUSE_HOTWORD"
        const val ACTION_RESUME_HOTWORD = "com.opencode.android.action.RESUME_HOTWORD"

        fun start(context: Context) {
            val intent = Intent(context, HotwordService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HotwordService::class.java))
        }
    }
}
