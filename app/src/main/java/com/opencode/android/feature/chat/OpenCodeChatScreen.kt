package com.opencode.android.feature.chat

import android.graphics.BitmapFactory
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.opencode.android.R
import com.opencode.android.core.api.OpenCodeAgent
import com.opencode.android.core.api.OpenCodeFileChange
import com.opencode.android.core.api.OpenCodeProvider
import com.opencode.android.core.api.PermissionRequest
import com.opencode.android.runtime.PermissionResponse
import com.opencode.android.runtime.WorkspaceRef
import com.opencode.android.ui.components.DiffView
import com.opencode.android.ui.components.MarkdownText
import com.opencode.android.ui.components.SectionCard
import com.opencode.android.ui.components.StatusChip
import com.opencode.android.ui.theme.OpenCodeWarning
import kotlinx.coroutines.launch

@Composable
fun OpenCodeChatScreen(
    state: ChatUiState,
    providers: List<OpenCodeProvider>,
    agents: List<OpenCodeAgent>,
    workspaces: List<WorkspaceRef>,
    selectedProviderId: String?,
    selectedModelId: String?,
    selectedAgentId: String?,
    availableVariants: List<String> = emptyList(),
    selectedVariant: String? = null,
    contextUsagePercent: Int? = null,
    recentModels: List<Pair<String, String>> = emptyList(),
    sessions: List<com.opencode.android.core.api.OpenCodeSession> = emptyList(),
    onSelectModel: (String, String) -> Unit,
    onSelectAgent: (String) -> Unit,
    onSelectVariant: (String?) -> Unit = {},
    onSelectWorkspace: (String?) -> Unit,
    onSendMessage: (String) -> Unit,
    onPermission: (String, PermissionResponse, Boolean) -> Unit,
    onAbort: () -> Unit,
    onMic: () -> Unit,
    onAttach: () -> Unit = {},
    onRemoveAttachment: (String) -> Unit = {},
    onNewChat: () -> Unit = {},
    onSelectSession: (String, String) -> Unit = { _, _ -> },
    onRenameSession: (String, String) -> Unit = { _, _ -> },
    onDeleteSession: (String) -> Unit = {},
    onOpenAdditionalSettings: () -> Unit = {}
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val isNearBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index
            lastVisibleIndex == null || lastVisibleIndex >= layoutInfo.totalItemsCount - 2
        }
    }

    LaunchedEffect(state.messages.size, state.permissions.size) {
        val totalItems = state.messages.size + state.permissions.size
        if (totalItems == 0) return@LaunchedEffect
        val lastMessageIsUser = state.messages.lastOrNull()?.isUser == true
        if (isNearBottom || lastMessageIsUser) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    val commandQuery = input.takeIf { it.startsWith("/") && !it.contains(" ") }?.removePrefix("/")
    val commandSuggestions = remember(commandQuery, state.commands) {
        commandQuery?.let { query ->
            state.commands.filter { it.name.startsWith(query, ignoreCase = true) }.take(8)
        }.orEmpty()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ChatHeader(
            state = state,
            sessions = sessions,
            onNewChat = onNewChat,
            onSelectSession = onSelectSession,
            onRenameSession = onRenameSession,
            onDeleteSession = onDeleteSession
        )

        if (state.backendName.isBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                SectionCard {
                    Text(stringResource(R.string.no_connection_chat))
                }
            }
            return
        }

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.messages.isEmpty() && !state.isLoadingHistory) {
                    item {
                        SectionCard {
                            Text(
                                text = "OpenCode",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "実装、調査、ファイル操作などを依頼できます。実行中のツール操作や承認要求もここに表示されます。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                items(state.messages, key = { it.id }) { message ->
                    MessageBubble(message)
                }

                items(state.permissions, key = { "permission-${it.id}" }) { permission ->
                    PermissionCard(permission, onPermission)
                }

                if (state.isThinking) {
                    item {
                        StatusChip(text = stringResource(R.string.thinking), active = true)
                    }
                }
                state.error?.let { error ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(text = error, color = MaterialTheme.colorScheme.error)
                                val lastUserMessage = state.messages.lastOrNull { it.isUser }?.text
                                if (!lastUserMessage.isNullOrBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    TextButton(onClick = { onSendMessage(lastUserMessage) }) {
                                        Text(stringResource(R.string.retry))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!isNearBottom && (state.messages.isNotEmpty() || state.permissions.isNotEmpty())) {
                val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
                FloatingActionButton(
                    onClick = {
                        val totalItems = state.messages.size + state.permissions.size
                        if (totalItems > 0) {
                            coroutineScope.launch { listState.animateScrollToItem(totalItems - 1) }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = stringResource(R.string.jump_to_latest))
                }
            }
        }

        if (commandSuggestions.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column {
                    commandSuggestions.forEach { command ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { input = "/${command.name} " }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "/${command.name}",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                            command.description?.takeIf(String::isNotBlank)?.let { description ->
                                Text(
                                    description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.pendingAttachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.pendingAttachments.forEach { attachment ->
                    AttachmentCard(
                        attachment = attachment,
                        onRemove = { onRemoveAttachment(attachment.id) }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onAttach) {
                Icon(Icons.Default.AttachFile, contentDescription = stringResource(R.string.attach_file))
            }
            IconButton(onClick = onMic) {
                Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.voice))
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.message_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (input.isNotBlank() || state.pendingAttachments.isNotEmpty()) {
                            onSendMessage(input)
                            input = ""
                        }
                    }
                ),
                maxLines = 5,
                shape = RoundedCornerShape(18.dp)
            )
            if (state.isRunning) {
                IconButton(onClick = onAbort) {
                    Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.stop_run))
                }
            } else {
                IconButton(
                    onClick = {
                        if (input.isNotBlank() || state.pendingAttachments.isNotEmpty()) {
                            onSendMessage(input)
                            input = ""
                        }
                    },
                    enabled = input.isNotBlank() || state.pendingAttachments.isNotEmpty()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send_description))
                }
            }
        }

        ComposerControls(
            providers = providers,
            agents = agents,
            workspaces = workspaces,
            selectedProviderId = selectedProviderId,
            selectedModelId = selectedModelId,
            selectedAgentId = selectedAgentId,
            selectedWorkspacePath = state.selectedWorkspacePath,
            workspaceSelectionEnabled = state.sessionId == null,
            availableVariants = availableVariants.ifEmpty { state.availableVariants },
            selectedVariant = selectedVariant ?: state.selectedVariant,
            contextUsagePercent = contextUsagePercent ?: state.contextUsagePercent,
            recentModels = recentModels,
            onSelectModel = onSelectModel,
            onSelectAgent = onSelectAgent,
            onSelectVariant = onSelectVariant,
            onSelectWorkspace = onSelectWorkspace,
            onOpenAdditionalSettings = onOpenAdditionalSettings
        )
    }
}

@Composable
private fun ChatHeader(
    state: ChatUiState,
    sessions: List<com.opencode.android.core.api.OpenCodeSession>,
    onNewChat: () -> Unit,
    onSelectSession: (String, String) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onDeleteSession: (String) -> Unit
) {
    var showSessionSwitcher by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { showSessionSwitcher = true }
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.sessionTitle.ifBlank { stringResource(R.string.new_chat) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = stringResource(R.string.switch_session),
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
                Text(
                    text = buildString {
                        append(state.backendName)
                        if (state.totalTokens > 0) {
                            append(" · ")
                            append(formatTokenCount(state.totalTokens))
                            append(" tok")
                        }
                        if (state.totalCost > 0) {
                            append(" · $")
                            append("%.4f".format(state.totalCost))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            StatusChip(
                text = if (state.isConnected) stringResource(R.string.active) else stringResource(R.string.not_set),
                active = state.isConnected
            )
        }
        if (showSessionSwitcher) {
            SessionSwitcherSheet(
                sessions = sessions,
                currentSessionId = state.sessionId,
                onSelect = onSelectSession,
                onNewChat = onNewChat,
                onRename = onRenameSession,
                onDelete = onDeleteSession,
                onDismiss = { showSessionSwitcher = false }
            )
        }
        if (state.todos.isNotEmpty()) {
            TodoProgressStrip(state.todos)
        }
    }
}

@Composable
private fun TodoProgressStrip(todos: List<com.opencode.android.core.api.OpenCodeTodo>) {
    val completed = todos.count { it.status == "completed" }
    val total = todos.size
    val current = todos.firstOrNull { it.status == "in_progress" }?.content
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LinearProgressIndicator(
                progress = if (total == 0) 0f else completed.toFloat() / total,
                modifier = Modifier.weight(0.3f)
            )
            Text(
                text = buildString {
                    append("$completed/$total")
                    if (!current.isNullOrBlank()) {
                        append(" ▸ ")
                        append(current)
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(0.7f)
            )
        }
    }
}

@Composable
private fun ComposerControls(
    providers: List<OpenCodeProvider>,
    agents: List<OpenCodeAgent>,
    workspaces: List<WorkspaceRef>,
    selectedProviderId: String?,
    selectedModelId: String?,
    selectedAgentId: String?,
    selectedWorkspacePath: String?,
    workspaceSelectionEnabled: Boolean,
    availableVariants: List<String>,
    selectedVariant: String?,
    contextUsagePercent: Int?,
    recentModels: List<Pair<String, String>>,
    onSelectModel: (String, String) -> Unit,
    onSelectAgent: (String) -> Unit,
    onSelectVariant: (String?) -> Unit,
    onSelectWorkspace: (String?) -> Unit,
    onOpenAdditionalSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModelSelector(
                providers = providers,
                providerId = selectedProviderId,
                modelId = selectedModelId,
                recentModels = recentModels,
                onSelect = onSelectModel,
                modifier = Modifier.widthIn(min = 150.dp)
            )
            VariantSelector(
                variants = availableVariants,
                selectedVariant = selectedVariant,
                onSelect = onSelectVariant,
                modifier = Modifier.widthIn(min = 120.dp)
            )
            AgentSelector(
                agents = agents,
                selectedAgentId = selectedAgentId,
                onSelect = onSelectAgent,
                modifier = Modifier.widthIn(min = 130.dp)
            )
            WorkspaceSelector(
                workspaces = workspaces,
                selectedPath = selectedWorkspacePath,
                enabled = workspaceSelectionEnabled,
                onSelect = onSelectWorkspace,
                modifier = Modifier.widthIn(min = 180.dp)
            )
            IconButton(onClick = onOpenAdditionalSettings) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.additional_settings)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (contextUsagePercent != null) {
                    stringResource(R.string.context_usage, contextUsagePercent)
                } else {
                    stringResource(R.string.context_unavailable)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = selectedAgentId ?: "build",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VariantSelector(
    variants: List<String>,
    selectedVariant: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = variants.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(stringResource(R.string.variant), style = MaterialTheme.typography.labelSmall)
                Text(selectedVariant ?: "Default", maxLines = 1, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Default") },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            variants.forEach { variant ->
                DropdownMenuItem(
                    text = { Text(variant) },
                    onClick = {
                        onSelect(variant)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun WorkspaceSelector(
    workspaces: List<WorkspaceRef>,
    selectedPath: String?,
    enabled: Boolean,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = workspaces.firstOrNull { it.path == selectedPath }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled && workspaces.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Folder, contentDescription = null)
            Spacer(Modifier.padding(horizontal = 4.dp))
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text("作業フォルダ", style = MaterialTheme.typography.labelSmall)
                Text(
                    selected?.name ?: selectedPath ?: "既定のフォルダ",
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text("既定のフォルダ")
                        Text(
                            "OpenCodeサーバーの現在位置を使用",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            workspaces.forEach { workspace ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(workspace.name, maxLines = 1)
                            Text(
                                workspace.path,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    },
                    onClick = {
                        onSelect(workspace.path)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ModelSelector(
    providers: List<OpenCodeProvider>,
    providerId: String?,
    modelId: String?,
    recentModels: List<Pair<String, String>>,
    onSelect: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    val provider = providers.firstOrNull { it.id == providerId }
    val selectedModelName = provider?.models?.get(modelId)?.name ?: modelId
    Box(modifier) {
        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(stringResource(R.string.model), style = MaterialTheme.typography.labelSmall)
                Text(selectedModelName ?: "Default", maxLines = 1, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
    }
    if (showPicker) {
        ModelPickerSheet(
            providers = providers,
            recentModels = recentModels,
            onSelect = onSelect,
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
private fun AgentSelector(
    agents: List<OpenCodeAgent>,
    selectedAgentId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(stringResource(R.string.agent), style = MaterialTheme.typography.labelSmall)
                Text(selectedAgentId ?: "build", maxLines = 1, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            agents.forEach { agent ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(agent.name)
                            agent.description?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                        }
                    },
                    onClick = {
                        onSelect(agent.name)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(message: ChatMessage) {
    if (!message.isUser && message.kind == ChatItemKind.DIFF_SUMMARY) {
        DiffSummaryCard(message)
        return
    }
    if (!message.isUser && message.kind != ChatItemKind.TEXT) {
        ToolOrCommandCard(message)
        return
    }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var showActions by remember(message.id) { mutableStateOf(false) }
    val bubbleColor = if (message.isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (message.isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = if (message.isUser) 340.dp else 520.dp)
                .combinedClickable(onClick = {}, onLongClick = { showActions = true }),
            shape = if (message.isUser) {
                RoundedCornerShape(20.dp, 20.dp, 5.dp, 20.dp)
            } else {
                RoundedCornerShape(20.dp, 20.dp, 20.dp, 5.dp)
            },
            color = bubbleColor,
            contentColor = contentColor
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (message.attachments.isNotEmpty()) {
                    AttachmentStrip(message.attachments, contentColor)
                    Spacer(Modifier.height(8.dp))
                }
                MarkdownText(text = message.text, contentColor = contentColor)
                if (message.isStreaming) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.processing),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.copy_message)) },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    onClick = {
                        clipboard.setText(AnnotatedString(message.text))
                        showActions = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.share_message)) },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                    onClick = {
                        showActions = false
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, message.text)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, null))
                    }
                )
            }
        }
    }
}

@Composable
private fun AttachmentStrip(attachments: List<PendingAttachment>, contentColor: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        attachments.forEach { attachment ->
            AttachmentCard(
                attachment = attachment,
                onRemove = null,
                contentColor = contentColor
            )
        }
    }
}

@Composable
private fun AttachmentCard(
    attachment: PendingAttachment,
    onRemove: (() -> Unit)?,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    val bitmap = remember(attachment.id) {
        if (attachment.mimeType.startsWith("image/")) {
            BitmapFactory.decodeByteArray(attachment.bytes, 0, attachment.bytes.size)
        } else {
            null
        }
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.attachment_preview, attachment.fileName),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .height(42.dp)
                        .widthIn(min = 42.dp, max = 64.dp)
                )
            } else {
                Icon(Icons.Default.Description, contentDescription = null)
            }
            Text(
                attachment.fileName,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
            onRemove?.let {
                IconButton(onClick = it) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.delete))
                }
            }
        }
    }
}

@Composable
private fun DiffSummaryCard(message: ChatMessage) {
    val changes = message.diffChanges.orEmpty()
    var expanded by remember(message.id) { mutableStateOf(false) }
    val totalAdditions = changes.sumOf { it.additions.toInt() }
    val totalDeletions = changes.sumOf { it.deletions.toInt() }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.diff_summary_title, changes.size),
                        fontWeight = FontWeight.SemiBold
                    )
                    com.opencode.android.ui.components.DiffStatSummary(totalAdditions, totalDeletions)
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    changes.forEach { change ->
                        Column {
                            Text(
                                text = change.displayPath.ifBlank { "変更ファイル" },
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (!change.patch.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                DiffView(change.patch, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolOrCommandCard(message: ChatMessage) {
    var expanded by remember(message.id, message.detail) {
        mutableStateOf(message.expandedByDefault || message.kind == ChatItemKind.COMMAND)
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (message.kind == ChatItemKind.COMMAND) {
                        Icons.Default.Terminal
                    } else {
                        Icons.Default.Security
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (message.kind) {
                            ChatItemKind.COMMAND -> stringResource(R.string.command_card)
                            ChatItemKind.REASONING -> stringResource(R.string.reasoning_card)
                            else -> stringResource(R.string.tool_card)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = message.toolName ?: message.text,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }
            if (expanded && !message.detail.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message.detail,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (message.isStreaming) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.processing),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private fun formatTokenCount(count: Int): String =
    if (count >= 1000) "%.1fK".format(count / 1000.0) else count.toString()

private fun formatMetadataValue(value: com.google.gson.JsonElement): String {
    val raw = if (value.isJsonPrimitive) value.asString else value.toString()
    return raw.trim().take(2000)
}

@Composable
private fun PermissionCard(
    permission: PermissionRequest,
    onPermission: (String, PermissionResponse, Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OpenCodeWarning.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = OpenCodeWarning)
                Spacer(Modifier.padding(horizontal = 5.dp))
                Text(stringResource(R.string.permission_required), fontWeight = FontWeight.SemiBold)
            }
            Text(
                text = permission.permission,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )
            permission.patterns.forEach { pattern ->
                Text(
                    text = pattern,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (permission.metadata.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    permission.metadata.entries.forEach { (key, value) ->
                        val text = formatMetadataValue(value)
                        if (text.isNotBlank()) {
                            Text(
                                text = "$key: $text",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onPermission(permission.id, PermissionResponse.REJECT, false) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.reject))
                }
                FilledTonalButton(
                    onClick = { onPermission(permission.id, PermissionResponse.ONCE, false) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.allow_once))
                }
                Button(
                    onClick = { onPermission(permission.id, PermissionResponse.ALWAYS, true) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.always_allow))
                }
            }
        }
    }
}
