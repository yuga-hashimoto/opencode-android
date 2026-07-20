package com.opencode.android.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.opencode.android.R
import com.opencode.android.ui.components.SectionCard
import com.opencode.android.ui.theme.OpenCodeAndroidTheme

/**
 * Redesigned settings landing screen: grouped rounded cards under アシスタント設定 /
 * システム設定 / アプリ設定. Real sub-flows (voice, provider credentials) navigate to
 * dedicated screens; wake word / privacy / app info are lightweight stub dialogs.
 */
@Composable
fun SettingsScreenV2(
    assistantConfigured: Boolean,
    notificationsEnabled: Boolean,
    onToggleNotifications: (Boolean) -> Unit,
    appVersion: String,
    onOpenDrawer: () -> Unit,
    onOpenAssistantSettings: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    onOpenProviderSettings: () -> Unit,
    onOpenLocalRuntime: () -> Unit,
    onOpenRemoteConnection: () -> Unit,
    onOpenWorkspaces: () -> Unit,
    onOpenDiagnostics: () -> Unit
) {
    var showWakeWordDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.nav_settings), fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu_description))
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.section_assistant_settings),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            item {
                SectionCard {
                    SettingsRow(
                        icon = Icons.Default.Home,
                        title = stringResource(R.string.settings_home_assistant_row),
                        trailing = {
                            StatusPill(
                                text = if (assistantConfigured) stringResource(R.string.assistant_enabled_pill) else stringResource(R.string.assistant_disabled_pill),
                                active = assistantConfigured
                            )
                        },
                        onClick = onOpenAssistantSettings
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.Mic,
                        title = stringResource(R.string.wake_word_row_title),
                        trailingText = stringResource(R.string.wake_word_value),
                        onClick = { showWakeWordDialog = true }
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.RecordVoiceOver,
                        title = stringResource(R.string.voice_settings_row),
                        onClick = onOpenVoiceSettings
                    )
                }
            }

            item {
                Text(
                    stringResource(R.string.section_system_settings),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            item {
                SectionCard {
                    SettingsRow(
                        icon = Icons.Default.Key,
                        title = stringResource(R.string.provider_settings_row),
                        onClick = onOpenProviderSettings
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.Terminal,
                        title = stringResource(R.string.settings_local_runtime_row),
                        onClick = onOpenLocalRuntime
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.Router,
                        title = stringResource(R.string.remote_connection_row),
                        onClick = onOpenRemoteConnection
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.Folder,
                        title = stringResource(R.string.settings_workspace_row),
                        onClick = onOpenWorkspaces
                    )
                }
            }

            item {
                Text(
                    stringResource(R.string.section_app_settings),
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
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            stringResource(R.string.notifications_row),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(checked = notificationsEnabled, onCheckedChange = onToggleNotifications)
                    }
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.PrivacyTip,
                        title = stringResource(R.string.privacy_row),
                        onClick = { showPrivacyDialog = true }
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.History,
                        title = stringResource(R.string.diagnostics_row),
                        onClick = onOpenDiagnostics
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.Info,
                        title = stringResource(R.string.app_info_row),
                        onClick = { showAboutDialog = true }
                    )
                }
            }
        }
    }

    if (showWakeWordDialog) {
        StubDialog(
            title = stringResource(R.string.wake_word_row_title),
            body = stringResource(R.string.wake_word_stub_body),
            onDismiss = { showWakeWordDialog = false }
        )
    }
    if (showPrivacyDialog) {
        StubDialog(
            title = stringResource(R.string.privacy_row),
            body = stringResource(R.string.privacy_stub_body),
            onDismiss = { showPrivacyDialog = false }
        )
    }
    if (showAboutDialog) {
        StubDialog(
            title = stringResource(R.string.app_info_row),
            body = "${stringResource(R.string.app_name)} $appVersion\n${stringResource(R.string.unofficial_client)}",
            onDismiss = { showAboutDialog = false }
        )
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    trailingText: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        trailingText?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
        Icon(
            Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsDivider() {
    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
}

@Composable
private fun StatusPill(text: String, active: Boolean) {
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(100.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StubDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close_description)) }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenV2Preview() {
    OpenCodeAndroidTheme {
        SettingsScreenV2(
            assistantConfigured = true,
            notificationsEnabled = true,
            onToggleNotifications = {},
            appVersion = "0.1.0",
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
