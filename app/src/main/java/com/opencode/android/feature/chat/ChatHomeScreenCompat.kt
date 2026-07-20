package com.opencode.android.feature.chat

import androidx.compose.runtime.Composable
import com.opencode.android.core.api.OpenCodeAgent
import com.opencode.android.core.api.OpenCodeProvider
import com.opencode.android.runtime.PermissionResponse
import com.opencode.android.runtime.RuntimeTarget
import com.opencode.android.runtime.WorkspaceRef

/**
 * Compatibility overload for call sites that have not yet supplied direct setup navigation.
 * Production navigation is upgraded in the same UI-polish round.
 */
@Composable
fun ChatHomeScreen(
    state: ChatUiState,
    providers: List<OpenCodeProvider>,
    agents: List<OpenCodeAgent>,
    workspaces: List<WorkspaceRef>,
    selectedProviderId: String?,
    selectedModelId: String?,
    selectedAgentId: String?,
    runtimeTargets: List<RuntimeTarget>,
    selectedRuntimeId: String?,
    onSelectRuntime: (String) -> Unit,
    onSelectModel: (String, String) -> Unit,
    onSelectAgent: (String) -> Unit,
    onSelectWorkspace: (String?) -> Unit,
    onSendMessage: (String) -> Unit,
    onPermission: (String, PermissionResponse, Boolean) -> Unit,
    onAbort: () -> Unit,
    onMic: () -> Unit,
    onNewChat: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDrawer: () -> Unit
) = ChatHomeScreen(
    state = state,
    providers = providers,
    agents = agents,
    workspaces = workspaces,
    selectedProviderId = selectedProviderId,
    selectedModelId = selectedModelId,
    selectedAgentId = selectedAgentId,
    runtimeTargets = runtimeTargets,
    selectedRuntimeId = selectedRuntimeId,
    onSelectRuntime = onSelectRuntime,
    onSelectModel = onSelectModel,
    onSelectAgent = onSelectAgent,
    onSelectWorkspace = onSelectWorkspace,
    onSendMessage = onSendMessage,
    onPermission = onPermission,
    onAbort = onAbort,
    onMic = onMic,
    onNewChat = onNewChat,
    onOpenHistory = onOpenHistory,
    onOpenLocalSetup = {},
    onOpenRemoteSetup = {},
    onOpenDrawer = onOpenDrawer
)
