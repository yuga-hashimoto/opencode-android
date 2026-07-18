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
import com.opencode.android.ui.AppViewModel
import com.opencode.android.ui.OpenCodeApp
import com.opencode.android.ui.theme.OpenCodeAndroidTheme

class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRuntimePermissions()

        setContent {
            OpenCodeAndroidTheme {
                OpenCodeApp(
                    appViewModel = appViewModel,
                    onOpenAssistantSettings = ::openAssistantSettings,
                    onHotwordChanged = ::setHotwordServiceEnabled,
                    onMicRequested = {
                        Toast.makeText(
                            this,
                            getString(R.string.mic_permission_required),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
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
        Toast.makeText(
            this,
            if (enabled) R.string.hotword_started else R.string.hotword_stopped,
            Toast.LENGTH_SHORT
        ).show()
    }
}
