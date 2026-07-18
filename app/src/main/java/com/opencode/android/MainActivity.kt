package com.opencode.android

import android.Manifest
import android.app.role.RoleManager
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
import com.opencode.android.hotword.HotwordStartupPolicy
import com.opencode.android.ui.AppViewModel
import com.opencode.android.ui.OpenCodeApp
import com.opencode.android.ui.theme.OpenCodeAndroidTheme

class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val microphoneGranted = result[Manifest.permission.RECORD_AUDIO] == true ||
            hasMicrophonePermission()
        val hotwordEnabled = (application as OpenCodeApplication).settings.hotwordEnabled

        if (HotwordStartupPolicy.canStartFromForeground(hotwordEnabled, microphoneGranted)) {
            startHotwordAndReport()
        } else if (hotwordEnabled && !microphoneGranted) {
            appViewModel.setHotwordEnabled(false)
            Toast.makeText(this, R.string.mic_permission_required, Toast.LENGTH_LONG).show()
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

    override fun onPostResume() {
        super.onPostResume()
        restoreHotwordIfEnabled()
    }

    private fun requestRuntimePermissions() {
        val permissions = buildList {
            if (!hasMicrophonePermission()) {
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
        val roleOpened = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) == true) {
                runCatching {
                    startActivity(roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
                    true
                }.getOrDefault(false)
            } else {
                false
            }
        } else {
            false
        }

        if (roleOpened) return

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
        if (!enabled) {
            HotwordService.stop(this)
            Toast.makeText(this, R.string.hotword_stopped, Toast.LENGTH_SHORT).show()
            return
        }

        if (!hasMicrophonePermission()) {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            return
        }

        startHotwordAndReport()
    }

    private fun restoreHotwordIfEnabled() {
        val enabled = (application as OpenCodeApplication).settings.hotwordEnabled
        if (HotwordStartupPolicy.canStartFromForeground(enabled, hasMicrophonePermission())) {
            runCatching { HotwordService.start(this) }
                .onFailure { appViewModel.setHotwordEnabled(false) }
        }
    }

    private fun startHotwordAndReport() {
        runCatching { HotwordService.start(this) }
            .onSuccess {
                Toast.makeText(this, R.string.hotword_started, Toast.LENGTH_SHORT).show()
            }
            .onFailure { error ->
                appViewModel.setHotwordEnabled(false)
                Toast.makeText(
                    this,
                    error.message ?: getString(R.string.hotword_start_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
