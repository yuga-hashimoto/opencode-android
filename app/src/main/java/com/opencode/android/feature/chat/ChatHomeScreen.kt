package com.opencode.android.feature.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.opencode.android.R
import com.opencode.android.core.api.OpenCodeAgent
import com.opencode.android.core.api.OpenCodeProvider
import com.opencode.android.runtime.PermissionResponse
import com.opencode.android.runtime.RuntimeTarget
import com.opencode.android.runtime.WorkspaceRef
import com.opencode.android.ui.components.StatusChip
import com.opencode.android.ui.theme.OpenCodeAndroidTheme

/**
 * ChatGPT-like chat home screen: hamburger + centered title top bar, empty-state
 * suggestions or the message timeline, and a rounded composer with model/agent/
 * workspace chips. Message rendering (bubbles, tool/reasoning cards, permission
 * cards) is reused from OpenCodeChatScreen.kt via the now package-visible
 * MessageBubble / AssistantTimeline / PermissionCard composables.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var overflowExpanded by remember { mutableStateOf(false) }
    var titleMenuExpanded by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(state.messages.size, state.permissions.size) {
        val totalItems = state.messages.size + state.permissions.size
        if (totalItems > 0) listState.animateScrollToItem(totalItems - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { titleMenuExpanded = true }
                ) {
                    Text(
                        text = stringResource(R.string.chat_home_title),
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    DropdownMenu(expanded = titleMenuExpanded, onDismissRequest = { titleMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.new_chat)) },
                            onClick = { titleMenuExpanded = false; onNewChat() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.session_history)) },
                            onClick = { titleMenuExpanded = false; onOpenHistory() }
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu_description))
                }
            },
            actions = {
                IconButton(onClick = onNewChat) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_chat))
                }
                Box {
                    IconButton(onClick = { overflowExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.chat_options_description))
                    }
                    DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.new_chat)) },
                            onClick = { overflowExpanded = false; onNewChat() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.session_history)) },
                            onClick = { overflowExpanded = false; onOpenHistory() }
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        Box(modifier = Modifier.weight(1f)) {
            val showEmptyState = state.messages.isEmpty() && state.permissions.isEmpty() &&
                !state.isLoadingHistory && state.error == null
            if (showEmptyState) {
                EmptyChatState(onSuggestionClick = { text -> input = text })
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        if (message.isUser) MessageBubble(message) else AssistantTimeline(message)
                    }
                    items(state.permissions, key = { "permission-${it.id}" }) { permission ->
                        PermissionCard(permission, onPermission)
                    }
                    if (state.isThinking) {
                        item { StatusChip(text = stringResource(R.string.thinking), active = true) }
                    }
                    state.error?.let { error ->
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                )
                            ) {
                                Text(
                                    text = error,
                                    modifier = Modifier.padding(14.dp),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        ChatComposer(
            input = input,
            onInputChange = { input = it },
            isRunning = state.isRunning,
            onSend = {
                if (input.isNotBlank()) {
                    onSendMessage(input)
                    input = ""
                }
            },
            onAbort = onAbort,
            onMic = onMic,
            modelLabel = selectedModelId ?: stringResource(R.string.opencode_default_value),
            onModelChipClick = { showModelPicker = true },
            agents = agents,
            selectedAgentId = selectedAgentId,
            onSelectAgent = onSelectAgent,
            workspaces = workspaces,
            selectedWorkspacePath = state.selectedWorkspacePath,
            workspaceSelectable = state.sessionId == null,
            onSelectWorkspace = onSelectWorkspace
        )
    }

    if (showModelPicker) {
        ModelAndRuntimePickerSheet(
            sheetState = sheetState,
            runtimeTargets = runtimeTargets,
            selectedRuntimeId = selectedRuntimeId,
            onSelectRuntime = onSelectRuntime,
            providers = providers,
            selectedProviderId = selectedProviderId,
            selectedModelId = selectedModelId,
            onSelectModel = onSelectModel,
            onDismiss = { showModelPicker = false }
        )
    }
}

@Composable
private fun EmptyChatState(onSuggestionClick: (String) -> Unit) {
    val suggestion1 = stringResource(R.string.suggestion_implement_title)
    val suggestion2 = stringResource(R.string.suggestion_debug_title)
    val suggestion3 = stringResource(R.string.suggestion_organize_title)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        ) {
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(18.dp)
                    .size(36.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.chat_build_headline),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.chat_build_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SuggestionCard(
                icon = Icons.Default.Build,
                title = suggestion1,
                subtitle = stringResource(R.string.suggestion_implement_subtitle),
                onClick = { onSuggestionClick(suggestion1) }
            )
            SuggestionCard(
                icon = Icons.Default.BugReport,
                title = suggestion2,
                subtitle = stringResource(R.string.suggestion_debug_subtitle),
                onClick = { onSuggestionClick(suggestion2) }
            )
            SuggestionCard(
                icon = Icons.Default.Folder,
                title = suggestion3,
                subtitle = stringResource(R.string.suggestion_organize_subtitle),
                onClick = { onSuggestionClick(suggestion3) }
            )
        }
    }
}

@Composable
private fun SuggestionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChatComposer(
    input: String,
    onInputChange: (String) -> Unit,
    isRunning: Boolean,
    onSend: () -> Unit,
    onAbort: () -> Unit,
    onMic: () -> Unit,
    modelLabel: String,
    onModelChipClick: () -> Unit,
    agents: List<OpenCodeAgent>,
    selectedAgentId: String?,
    onSelectAgent: (String) -> Unit,
    workspaces: List<WorkspaceRef>,
    selectedWorkspacePath: String?,
    workspaceSelectable: Boolean,
    onSelectWorkspace: (String?) -> Unit
) {
    var attachExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(6.dp)) {
                TextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.chat_message_placeholder)) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    maxLines = 6
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        IconButton(onClick = { attachExpanded = true }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.attach_button_description))
                        }
                        DropdownMenu(expanded = attachExpanded, onDismissRequest = { attachExpanded = false }) {
                            // TODO: implement real attachments (files, images, camera capture)
                            // once the backend supports multipart prompt attachments.
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.attach_menu_stub)) },
                                onClick = { attachExpanded = false },
                                enabled = false
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onMic) {
                        Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.voice))
                    }
                    Spacer(Modifier.width(4.dp))
                    if (isRunning) {
                        FilledIconButton(onClick = onAbort) {
                            Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.stop_run))
                        }
                    } else {
                        FilledIconButton(onClick = onSend, enabled = input.isNotBlank()) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send_description))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChipButton(label = modelLabel, onClick = onModelChipClick)
            AgentChip(agents = agents, selectedAgentId = selectedAgentId, onSelect = onSelectAgent)
            WorkspaceChip(
                workspaces = workspaces,
                selectedPath = selectedWorkspacePath,
                enabled = workspaceSelectable,
                onSelect = onSelectWorkspace
            )
        }
    }
}

@Composable
private fun ChipButton(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(100.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AgentChip(
    agents: List<OpenCodeAgent>,
    selectedAgentId: String?,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ChipButton(label = selectedAgentId ?: "build", onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            agents.forEach { agent ->
                DropdownMenuItem(
                    text = { Text(agent.name) },
                    onClick = { onSelect(agent.name); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun WorkspaceChip(
    workspaces: List<WorkspaceRef>,
    selectedPath: String?,
    enabled: Boolean,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = workspaces.firstOrNull { it.path == selectedPath }
    Box {
        Surface(
            modifier = Modifier.clickable(enabled = enabled) { expanded = true },
            shape = RoundedCornerShape(100.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(
                    text = selected?.name ?: stringResource(R.string.default_folder),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.default_folder)) },
                onClick = { onSelect(null); expanded = false }
            )
            workspaces.forEach { workspace ->
                DropdownMenuItem(
                    text = { Text(workspace.name) },
                    onClick = { onSelect(workspace.path); expanded = false }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatHomeScreenEmptyPreview() {
    OpenCodeAndroidTheme {
        ChatHomeScreen(
            state = ChatUiState(backendName = "OpenCode · 1.0.0", isConnected = true),
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
