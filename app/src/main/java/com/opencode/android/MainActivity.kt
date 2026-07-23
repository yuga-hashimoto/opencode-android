package com.opencode.android

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.opencode.android.ui.OpenCodeApp

class MainActivity : ComponentActivity() {
    private var targetSessionId by mutableStateOf<String?>(null)
    private var deepLinkConnectionUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDeepLink(intent)
        setContent {
            OpenCodeApp(
                onOpenAssistantSettings = ::openAssistantSettings,
                targetSessionId = targetSessionId,
                deepLinkConnectionUrl = deepLinkConnectionUrl
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        intent ?: return
        intent.getStringExtra("target_session_id")?.let { id ->
            targetSessionId = id
        }
        intent.data?.let { uri ->
            if (uri.scheme == "opencode" && uri.host == "connect") {
                val host = uri.getQueryParameter("host").orEmpty()
                val port = uri.getQueryParameter("port").orEmpty()
                val token = uri.getQueryParameter("token").orEmpty()
                val url = buildString {
                    append("https://")
                    append(host)
                    if (port.isNotBlank()) append(":").append(port)
                    if (token.isNotBlank()) append("?token=").append(Uri.encode(token))
                }
                deepLinkConnectionUrl = url
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
