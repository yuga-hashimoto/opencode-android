package com.opencode.android

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.opencode.android.ui.OpenCodeApp
import com.opencode.android.ui.theme.OpenCodeAndroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OpenCodeAndroidTheme {
                OpenCodeApp(onOpenAssistantSettings = ::openAssistantSettings)
            }
        }
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

        val opened = listOf(
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        ).any { intent ->
            runCatching {
                startActivity(intent)
                true
            }.getOrDefault(false)
        }

        if (!opened) {
            Toast.makeText(this, R.string.could_not_open_settings, Toast.LENGTH_SHORT).show()
        }
    }
}
