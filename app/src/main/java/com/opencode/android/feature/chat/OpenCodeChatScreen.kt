package com.opencode.android.feature.chat

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.opencode.android.R
import com.opencode.android.core.api.OpenCodeAgent
import com.opencode.android.core.api.OpenCodeModel
import com.opencode.android.core.api.OpenCodeProvider
import com.opencode.android.core.api.PermissionRequest
import com.opencode.android.runtime.PermissionResponse
import com.opencode.android.ui.components.SectionCard
import com.opencode.android.ui.components.StatusChip
import com.opencode.android.ui.theme.OpenCodeWarning

@Composable
fun OpenCodeChatScreen(
    state: ChatUiState,
    providers: List<OpenCodeProvider>,
    agents: List<OpenCodeAgent>,
    selectedProviderId: String?,
    selectedModelId: String?,
    selectedAgentId: String?,
    onSelectModel: (String, String) -> Unit,
    onSelectAgent: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onPermission: (String, PermissionResponse, Boolean) -> Unit,
    onAbort: () -> Unit,
    onMic: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.permissions.size) {
        val totalItems = state.messages.size + state.permissions.size
        if (totalItems > 0) listState.animateScrollToItem(totalItems - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ChatHeader(
            state = state,
            providers = providers,
            agents = agents,
            selectedProviderId = selectedProviderId,
            selectedModelId = selectedModelId,
            selectedAgentId = selectedAgentId,
            onSelectModel = onSelectModel,
            onSelectAgent = onSelectAgent
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

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
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
                        Text(
                            text = error,
                            modifier = Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
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
                        if (input.isNotBlank()) {
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
                        if (input.isNotBlank()) {
                            onSendMessage(input)
                            input = ""
                        }
                    },
                    enabled = input.isNotBlank()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send_description))
                }
            }
        }
    }
}

@Composable
private fun ChatHeader(
    state: ChatUiState,
    providers: List<OpenCodeProvider>,
    agents: List<OpenCodeAgent>,
    selectedProviderId: String?,
    selectedModelId: String?,
    selectedAgentId: String?,
    onSelectModel: (String, String) -> Unit,
    onSelectAgent: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.sessionTitle.ifBlank { stringResource(R.string.new_chat) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = state.backendName,
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModelSelector(
                providers = providers,
                providerId = selectedProviderId,
                modelId = selectedModelId,
                onSelect = onSelectModel,
                modifier = Modifier.weight(1f)
            )
            AgentSelector(
                agents = agents,
                selectedAgentId = selectedAgentId,
                onSelect = onSelectAgent,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ModelSelector(
    providers: List<OpenCodeProvider>,
    providerId: String?,
    modelId: String?,
    onSelect: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val provider = providers.firstOrNull { it.id == providerId }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(stringResource(R.string.model), style = MaterialTheme.typography.labelSmall)
                Text(modelId ?: "Default", maxLines = 1, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            providers.forEach { itemProvider ->
                val models = itemProvider.models.values
                    .filter { it.status == null || it.status == "active" }
                    .sortedBy { it.name.lowercase() }
                    .take(60)
                if (models.isNotEmpty()) {
                    Text(
                        text = itemProvider.name,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    models.forEach { model: OpenCodeModel ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(model.name, maxLines = 1)
                                    Text(
                                        model.id,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            },
                            onClick = {
                                onSelect(itemProvider.id, model.id)
                                expanded = false
                            }
                        )
                    }
                }
            }
            if (provider == null && providers.isEmpty()) {
                DropdownMenuItem(text = { Text("No connected providers") }, onClick = { expanded = false })
            }
        }
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

@Composable
private fun MessageBubble(message: ChatMessage) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 340.dp),
            shape = if (message.isUser) {
                RoundedCornerShape(20.dp, 20.dp, 5.dp, 20.dp)
            } else {
                RoundedCornerShape(20.dp, 20.dp, 20.dp, 5.dp)
            },
            color = if (message.isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (message.isUser) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(text = message.text)
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
