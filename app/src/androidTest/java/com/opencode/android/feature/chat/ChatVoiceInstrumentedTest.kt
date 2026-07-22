package com.opencode.android.feature.chat

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.opencode.android.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatVoiceInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun listeningComposerShowsWaveformAndProcessingFallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.setContent {
            ChatHomeScreen(
                state = ChatUiState(
                    isListening = true,
                    voiceLevel = 0.64f
                ),
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
                onSelectQuestionAnswer = { _, _, _ -> },
                onSubmitQuestion = {},
                onSendMessage = {},
                onPermission = { _, _, _ -> },
                onAbort = {},
                onMic = {},
                onNewChat = {},
                onOpenHistory = {},
                onOpenLocalSetup = {},
                onOpenRemoteSetup = {},
                onOpenDrawer = {}
            )
        }

        composeRule.onNodeWithText(context.getString(R.string.voice_state_listening)).assertIsDisplayed()
        composeRule.onNodeWithTag("chat-voice-waveform").assertIsDisplayed()
        repeat(7) { index ->
            composeRule.onNodeWithTag("chat-voice-waveform-bar-$index").assertIsDisplayed()
        }

        composeRule.setContent {
            ChatHomeScreen(
                state = ChatUiState(isSpeechProcessing = true),
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
                onSelectQuestionAnswer = { _, _, _ -> },
                onSubmitQuestion = {},
                onSendMessage = {},
                onPermission = { _, _, _ -> },
                onAbort = {},
                onMic = {},
                onNewChat = {},
                onOpenHistory = {},
                onOpenLocalSetup = {},
                onOpenRemoteSetup = {},
                onOpenDrawer = {}
            )
        }

        composeRule.onNodeWithTag("chat-voice-processing").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.processing)).assertIsDisplayed()
    }
}
