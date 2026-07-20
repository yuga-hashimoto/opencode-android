package com.opencode.android.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.opencode.android.MainActivity
import com.opencode.android.feature.chat.ChatHomeScreen
import com.opencode.android.feature.chat.ChatUiState
import com.opencode.android.feature.settings.SettingsScreenV2
import com.opencode.android.ui.theme.OpenCodeAndroidTheme
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class UiScreenshotInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun captureReviewedScreens() {
        useJapaneseResources()

        capture("01-chat-empty") {
            ChatHomeScreen(
                state = ChatUiState(isConnected = true),
                providers = emptyList(),
                agents = emptyList(),
                workspaces = emptyList(),
                selectedProviderId = null,
                selectedModelId = null,
                selectedAgentId = null,
                runtimeTargets = emptyList(),
                selectedRuntimeId = null,
                onSelectRuntime = {},
                onSelectModel = { _, _ -> },
                onSelectAgent = {},
                onSelectWorkspace = {},
                onSendMessage = {},
                onPermission = { _, _, _ -> },
                onAbort = {},
                onMic = {},
                onNewChat = {},
                onOpenHistory = {},
                onOpenDrawer = {}
            )
        }

        capture("02-runtime-not-ready") {
            ChatHomeScreen(
                state = ChatUiState(error = "Android local OpenCode runtime is not installed"),
                providers = emptyList(),
                agents = emptyList(),
                workspaces = emptyList(),
                selectedProviderId = null,
                selectedModelId = null,
                selectedAgentId = null,
                runtimeTargets = emptyList(),
                selectedRuntimeId = null,
                onSelectRuntime = {},
                onSelectModel = { _, _ -> },
                onSelectAgent = {},
                onSelectWorkspace = {},
                onSendMessage = {},
                onPermission = { _, _, _ -> },
                onAbort = {},
                onMic = {},
                onNewChat = {},
                onOpenHistory = {},
                onOpenDrawer = {}
            )
        }

        capture("03-drawer") {
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Open)
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = false,
                drawerContent = {
                    ModalDrawerSheet {
                        AppDrawerContent(
                            recentSessions = listOf(
                                DrawerRecentSession("1", "認証エラーの調査", "3時間前"),
                                DrawerRecentSession("2", "READMEを更新", "昨日"),
                                DrawerRecentSession("3", "テスト失敗を修正", "2日前")
                            ),
                            onNewChat = {},
                            onOpenSession = { _, _ -> },
                            onNavigate = {}
                        )
                    }
                }
            ) {
                ChatHomeScreen(
                    state = ChatUiState(isConnected = true),
                    providers = emptyList(),
                    agents = emptyList(),
                    workspaces = emptyList(),
                    selectedProviderId = null,
                    selectedModelId = null,
                    selectedAgentId = null,
                    runtimeTargets = emptyList(),
                    selectedRuntimeId = null,
                    onSelectRuntime = {},
                    onSelectModel = { _, _ -> },
                    onSelectAgent = {},
                    onSelectWorkspace = {},
                    onSendMessage = {},
                    onPermission = { _, _, _ -> },
                    onAbort = {},
                    onMic = {},
                    onNewChat = {},
                    onOpenHistory = {},
                    onOpenDrawer = {}
                )
            }
        }

        capture("04-settings") {
            SettingsScreenV2(
                assistantConfigured = true,
                notificationsEnabled = true,
                onToggleNotifications = {},
                appVersion = "0.2.0",
                onOpenDrawer = {},
                onOpenAssistantSettings = {},
                onOpenVoiceSettings = {},
                onOpenProviderSettings = {},
                onOpenLocalRuntime = {},
                onOpenRemoteConnection = {},
                onOpenWorkspaces = {},
                onOpenDiagnostics = {}
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun useJapaneseResources() {
        composeRule.activity.runOnUiThread {
            Locale.setDefault(Locale.JAPAN)
            val resources = composeRule.activity.resources
            val configuration = Configuration(resources.configuration).apply {
                setLocale(Locale.JAPAN)
            }
            resources.updateConfiguration(configuration, resources.displayMetrics)
        }
        composeRule.waitForIdle()
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.activity.setContent {
            OpenCodeAndroidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    content()
                }
            }
        }
        composeRule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(400)

        val bitmap = requireNotNull(
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        ) { "Unable to capture emulator screenshot: $name" }
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "ui-screenshots"
        ).apply { mkdirs() }
        FileOutputStream(File(directory, "$name.png")).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Unable to encode emulator screenshot: $name"
            }
        }
        bitmap.recycle()
    }
}
