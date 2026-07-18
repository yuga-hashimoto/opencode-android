package com.opencode.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.opencode.android.hotword.HotwordService
import com.opencode.android.ui.AppViewModel
import com.opencode.android.ui.OpenCodeApp
import com.opencode.android.ui.theme.OpenCodeAndroidTheme

class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true &&
            (application as OpenCodeApplication).settings.hotwordEnabled
        ) {
            runCatching { HotwordService.start(this) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRuntimePermissions()

        setContent {
            OpenCodeAndroidTheme {
                OpenCodeApp(
                    appViewModel = appViewModel,
                    onOpenAssistantSettings = ::openAssistantSettings,
                    onHotwordChanged = ::setHotwordServiceEnabled
                )
            }
        }
    }

    private fun requestRuntimePermissions() {
        val permissions = buildList {
            if (ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissions.isNotEmpty()) permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun openAssistantSettings() {
        val candidates = listOf(
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        )
        val opened = candidates.any { intent ->
            runCatching {
                startActivity(intent)
                true
            }.getOrDefault(false)
        }
        if (!opened) {
            Toast.makeText(this, R.string.could_not_open_settings, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setHotwordServiceEnabled(enabled: Boolean) {
        if (enabled) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                runCatching { HotwordService.start(this) }
                    .onFailure {
                        Toast.makeText(this, it.message ?: "Unable to start wake word", Toast.LENGTH_LONG).show()
                    }
            } else {
                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            }
        } else {
            HotwordService.stop(this)
        }

        Toast.makeText(
            this,
            if (enabled) R.string.hotword_started else R.string.hotword_stopped,
            Toast.LENGTH_SHORT
        ).show()
    }
}
