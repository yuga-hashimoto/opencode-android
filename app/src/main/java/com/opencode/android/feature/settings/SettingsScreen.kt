package com.opencode.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.opencode.android.R
import com.opencode.android.core.api.OpenCodeAgent
import com.opencode.android.core.api.OpenCodeProvider
import com.opencode.android.ui.components.LabelValueRow
import com.opencode.android.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onOpenAssistantSettings: () -> Unit,
    onTtsChange: (Boolean) -> Unit,
    onContinuousChange: (Boolean) -> Unit,
    onAutoAllowReadOnlyChange: (Boolean) -> Unit = {},
    onThemeModeChange: (String?) -> Unit = {},
    onDynamicColorChange: (Boolean) -> Unit = {},
    onReplayOnboarding: () -> Unit = {},
    onDraftProviderId: (String) -> Unit = {},
    onDraftApiKey: (String) -> Unit = {},
    onSaveApiKey: () -> Unit = {},
    onClearApiKey: (String) -> Unit = {},
    onAssistantRuntime: (String?) -> Unit = {},
    onAssistantWorkspace: (String?) -> Unit = {},
    onAssistantModel: (String, String) -> Unit = { _, _ -> },
    onAssistantAgent: (String?) -> Unit = {},
    onUseChatDefaultsForAssistant: () -> Unit = {},
    onImportWorkspace: () -> Unit = {},
    onRequestNotifications: () -> Unit = {},
    wakeWordPackSummary: String = "",
    wakeWordInstalled: Boolean = false,
    wakeWordListeningEnabled: Boolean = false,
    wakeWordStatusMessage: String? = null,
    onInstallWakeWord: () -> Unit = {},
    onWakeWordListeningChange: (Boolean) -> Unit = {},
    onDeleteWakeWord: () -> Unit = {},
    onStartOAuth: (String, Int) -> Unit = { _, _ -> },
    onLaunchOAuthBrowser: (String) -> Unit = {},
    onSubmitOAuthCode: (String, Int, String?) -> Unit = { _, _, _ -> },
    onDismissOAuth: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.nav_settings),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            state.openCodeVersion?.let { version ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.capability_version, version),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.appearance),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            SectionCard {
                Text(
                    text = stringResource(R.string.theme),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeModeOption(
                        label = stringResource(R.string.theme_system),
                        selected = state.themeMode == null,
                        onClick = { onThemeModeChange(null) },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeModeOption(
                        label = stringResource(R.string.theme_light),
                        selected = state.themeMode == "light",
                        onClick = { onThemeModeChange("light") },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeModeOption(
                        label = stringResource(R.string.theme_dark),
                        selected = state.themeMode == "dark",
                        onClick = { onThemeModeChange("dark") },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    Spacer(Modifier.height(16.dp))
                    SettingSwitchRow(
                        icon = Icons.Default.Info,
                        title = stringResource(R.string.dynamic_color),
                        description = stringResource(R.string.dynamic_color_help),
                        checked = state.dynamicColorEnabled,
                        onCheckedChange = onDynamicColorChange
                    )
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.home_assistant),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(Icons.Default.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.set_default_assistant), fontWeight = FontWeight.Medium)
                        Text(
                            text = stringResource(R.string.assistant_role_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onOpenAssistantSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.set_default_assistant))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onRequestNotifications, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.request_notifications))
                }
                Spacer(Modifier.height(12.dp))
                var runtimeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = runtimeExpanded,
                    onExpandedChange = { runtimeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = state.runtimeOptions.firstOrNull { it.first == state.assistantRuntimeId }?.second
                            ?: state.assistantRuntimeId.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.assistant_runtime)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = runtimeExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = runtimeExpanded,
                        onDismissRequest = { runtimeExpanded = false }
                    ) {
                        state.runtimeOptions.forEach { (id, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    onAssistantRuntime(id)
                                    runtimeExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.assistantWorkspacePath.orEmpty(),
                    onValueChange = onAssistantWorkspace,
                    label = { Text(stringResource(R.string.assistant_workspace)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                AssistantModelSelector(
                    providers = state.assistantProviders,
                    providerId = state.assistantProviderId,
                    modelId = state.assistantModelId,
                    enabled = !state.isLoadingAssistantCatalog,
                    onSelect = onAssistantModel
                )
                Spacer(Modifier.height(8.dp))
                AssistantAgentSelector(
                    agents = state.assistantAgents,
                    agentId = state.assistantAgentId,
                    enabled = !state.isLoadingAssistantCatalog,
                    onSelect = onAssistantAgent
                )
                if (state.isLoadingAssistantCatalog) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                        Text(
                            stringResource(R.string.assistant_catalog_loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                state.assistantCatalogError?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onUseChatDefaultsForAssistant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.use_chat_defaults))
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.voice),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            SectionCard {
                SettingSwitchRow(
                    icon = Icons.Default.RecordVoiceOver,
                    title = stringResource(R.string.voice_response),
                    description = stringResource(R.string.voice_response_help),
                    checked = state.ttsEnabled,
                    onCheckedChange = onTtsChange
                )
                Spacer(Modifier.height(16.dp))
                SettingSwitchRow(
                    icon = Icons.Default.Mic,
                    title = stringResource(R.string.continuous_conversation),
                    description = stringResource(R.string.auto_start_mic),
                    checked = state.continuousConversation,
                    onCheckedChange = onContinuousChange
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.approvals),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            SectionCard {
                SettingSwitchRow(
                    icon = Icons.Default.Security,
                    title = stringResource(R.string.auto_allow_read_only),
                    description = stringResource(R.string.auto_allow_read_only_help),
                    checked = state.autoAllowReadOnlyTools,
                    onCheckedChange = onAutoAllowReadOnlyChange
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.provider_credentials),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            SectionCard {
                Text(
                    text = stringResource(R.string.provider_credentials_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                state.credentialStatuses.forEach { (providerId, saved) ->
                    LabelValueRow(
                        label = providerId,
                        value = if (saved) {
                            stringResource(R.string.key_saved)
                        } else {
                            stringResource(R.string.key_missing)
                        }
                    )
                    if (saved) {
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { onClearApiKey(providerId) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.clear_api_key))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                state.providerAuthMethods.toSortedMap().forEach { (providerId, methods) ->
                    Text(
                        text = providerId,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    methods.forEachIndexed { methodIndex, method ->
                        if (method.type == "oauth") {
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = { onStartOAuth(providerId, methodIndex) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(method.label)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = state.draftProviderId,
                    onValueChange = onDraftProviderId,
                    label = { Text(stringResource(R.string.provider_id_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.draftApiKey,
                    onValueChange = onDraftApiKey,
                    label = { Text(stringResource(R.string.api_key_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onSaveApiKey, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.save_api_key))
                }
                state.credentialMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                state.oauthMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.local_runtime),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            SectionCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Android, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.experimental_not_installed), fontWeight = FontWeight.Medium)
                        Text(
                            stringResource(R.string.local_runtime_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onImportWorkspace, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.import_workspace_folder))
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.model),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            SectionCard {
                LabelValueRow(
                    label = stringResource(R.string.model),
                    value = state.modelId ?: stringResource(R.string.not_set)
                )
                Spacer(Modifier.height(12.dp))
                LabelValueRow(
                    label = stringResource(R.string.agent),
                    value = state.agentId ?: "build"
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.about),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            SectionCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("OpenCode Android 0.1.0", fontWeight = FontWeight.Medium)
                        Text(
                            stringResource(R.string.unofficial_client),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "MIT License",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onReplayOnboarding, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.replay_onboarding))
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.wake_word_pack),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            SectionCard {
                Text(
                    text = stringResource(R.string.wake_word_pack_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = wakeWordPackSummary.ifBlank {
                        stringResource(R.string.wake_word_not_installed)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                wakeWordStatusMessage?.takeIf(String::isNotBlank)?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (wakeWordInstalled) {
                    Spacer(Modifier.height(12.dp))
                    SettingSwitchRow(
                        icon = Icons.Default.Mic,
                        title = stringResource(R.string.wake_word_listening),
                        description = stringResource(R.string.wake_word_listening_help),
                        checked = wakeWordListeningEnabled,
                        onCheckedChange = onWakeWordListeningChange
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onDeleteWakeWord,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.delete_wake_word))
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onInstallWakeWord,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.install_wake_word_pack))
                    }
                }
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }

    val authorization = state.oauthAuthorization
    val providerId = state.oauthProviderId
    if (authorization != null && providerId != null) {
        var code by remember(authorization.url) { mutableStateOf("") }
        LaunchedEffect(authorization.url) {
            onLaunchOAuthBrowser(authorization.url)
        }
        AlertDialog(
            onDismissRequest = onDismissOAuth,
            title = { Text(stringResource(R.string.oauth_authentication)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(authorization.instructions)
                    if (authorization.method == "code") {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it },
                            label = { Text(stringResource(R.string.oauth_code_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            stringResource(R.string.oauth_browser_opened),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = authorization.method != "code" || code.isNotBlank(),
                    onClick = {
                        val methodIndex = state.oauthMethodIndex ?: -1
                        if (methodIndex >= 0) {
                            onSubmitOAuthCode(providerId, methodIndex, code.takeIf(String::isNotBlank))
                        }
                    }
                ) {
                    Text(stringResource(R.string.oauth_complete))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissOAuth) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantModelSelector(
    providers: List<OpenCodeProvider>,
    providerId: String?,
    modelId: String?,
    enabled: Boolean,
    onSelect: (String, String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedProvider = providers.firstOrNull { it.id == providerId }
    val selectedModel = selectedProvider?.models?.get(modelId)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it }
    ) {
        OutlinedTextField(
            value = selectedModel?.name ?: modelId.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.assistant_model)) },
            supportingText = providerId?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            providers.forEach { provider ->
                val models = provider.models.values
                    .filter { it.status == null || it.status == "active" }
                    .sortedBy { it.name.lowercase() }
                if (models.isNotEmpty()) {
                    Text(
                        provider.name,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                models.forEach { model ->
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
                            onSelect(provider.id, model.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantAgentSelector(
    agents: List<OpenCodeAgent>,
    agentId: String?,
    enabled: Boolean,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it }
    ) {
        OutlinedTextField(
            value = agentId.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.assistant_agent)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.use_chat_defaults)) },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            agents.forEach { agent ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(agent.name)
                            agent.description?.takeIf(String::isNotBlank)?.let { description ->
                                Text(
                                    description,
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
private fun ThemeModeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        modifier = modifier
    )
}

@Composable
private fun SettingSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
