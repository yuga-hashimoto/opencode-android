package com.opencode.android.feature.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.opencode.android.core.api.PermissionRequest
import com.opencode.android.core.api.OpenCodeModel
import com.opencode.android.core.api.OpenCodeProvider
import com.opencode.android.runtime.PermissionResponse
import org.junit.Rule
import org.junit.Test

class OpenCodeChatScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tool_card_expands_when_tapped() {
        val message = ChatMessage(
            id = "tool-1",
            text = "read_file",
            isUser = false,
            kind = ChatItemKind.TOOL,
            toolName = "read_file",
            detail = "src/Main.kt"
        )
        composeRule.setContent {
            androidx.compose.material3.MaterialTheme {
                OpenCodeChatScreen(
                    state = ChatUiState(
                        backendName = "Test · 1.0.0",
                        messages = listOf(message)
                    ),
                    providers = emptyList(),
                    agents = emptyList(),
                    workspaces = emptyList(),
                    selectedProviderId = null,
                    selectedModelId = null,
                    selectedAgentId = null,
                    onSelectModel = { _, _ -> },
                    onSelectAgent = {},
                    onSelectWorkspace = {},
                    onSendMessage = {},
                    onPermission = { _, _, _ -> },
                    onAbort = {},
                    onMic = {}
                )
            }
        }

        composeRule.onNodeWithText("read_file").assertIsDisplayed()
    }

    @Test
    fun permission_card_invokes_callback() {
        var rejected = false
        val permission = PermissionRequest(
            id = "p1",
            sessionId = "s1",
            permission = "bash",
            patterns = listOf("rm -rf /")
        )
        composeRule.setContent {
            androidx.compose.material3.MaterialTheme {
                OpenCodeChatScreen(
                    state = ChatUiState(
                        backendName = "Test · 1.0.0",
                        permissions = listOf(permission)
                    ),
                    providers = emptyList(),
                    agents = emptyList(),
                    workspaces = emptyList(),
                    selectedProviderId = null,
                    selectedModelId = null,
                    selectedAgentId = null,
                    onSelectModel = { _, _ -> },
                    onSelectAgent = {},
                    onSelectWorkspace = {},
                    onSendMessage = {},
                    onPermission = { id, response, _ ->
                        if (id == "p1" && response == PermissionResponse.REJECT) rejected = true
                    },
                    onAbort = {},
                    onMic = {}
                )
            }
        }

        composeRule.onNodeWithText("Reject").performClick()
        composeRule.waitForIdle()
        assert(rejected)
    }

    @Test
    fun composer_shows_model_variant_agent_context_and_attachment() {
        composeRule.setContent {
            androidx.compose.material3.MaterialTheme {
                OpenCodeChatScreen(
                    state = ChatUiState(
                        backendName = "Test · 1.0.0",
                        selectedModelId = "gpt-5",
                        selectedAgentId = "build",
                        selectedVariant = "high",
                        availableVariants = listOf("low", "high"),
                        contextUsagePercent = 6,
                        pendingAttachments = listOf(
                            PendingAttachment(
                                fileName = "photo.jpg",
                                mimeType = "image/jpeg",
                                bytes = byteArrayOf(1, 2, 3)
                            )
                        )
                    ),
                    providers = listOf(
                        OpenCodeProvider(
                            id = "openai",
                            models = mapOf("gpt-5" to OpenCodeModel("gpt-5", "openai", "GPT-5"))
                        )
                    ),
                    agents = emptyList(),
                    workspaces = emptyList(),
                    selectedProviderId = "openai",
                    selectedModelId = "gpt-5",
                    selectedAgentId = "build",
                    availableVariants = listOf("low", "high"),
                    selectedVariant = "high",
                    contextUsagePercent = 6,
                    onSelectModel = { _, _ -> },
                    onSelectAgent = {},
                    onSelectWorkspace = {},
                    onSendMessage = {},
                    onPermission = { _, _, _ -> },
                    onAbort = {},
                    onMic = {}
                )
            }
        }

        composeRule.onAllNodesWithText("GPT-5").assertCountEquals(1)
        composeRule.onAllNodesWithText("high").assertCountEquals(1)
        composeRule.onAllNodesWithText("build").assertCountEquals(2)
        composeRule.onAllNodesWithText("Context 6%").assertCountEquals(1)
        composeRule.onNodeWithText("photo.jpg").assertIsDisplayed()
    }
}
