package com.opencode.android.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.HorizontalDivider
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
import com.opencode.android.ui.theme.OpenCodeAndroidTheme

/** Compact settings landing screen backed only by real destinations and state. */
@Suppress("UNUSED_PARAMETER")
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
    var showAboutDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    stringResource(R.string.nav_settings),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            },
            navigationIcon = {
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu_description))
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                navigationIconContentColor = MaterialTheme.colorScheme.onBackground
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                SettingsSection(
                    title = stringResource(R.string.section_assistant_settings)
                ) {
                    SettingsRow(
                        icon = Icons.Default.Home,
                        title = stringResource(R.string.settings_home_assistant_row),
                        trailing = {
                            StatusPill(
                                text = if (assistantConfigured) {
                                    stringResource(R.string.assistant_enabled_pill)
                                } else {
                                    stringResource(R.string.assistant_disabled_pill)
                                },
                                active = assistantConfigured
                            )
                        },
                        onClick = onOpenAssistantSettings
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.Mic,
                        title = stringResource(R.string.settings_wake_word_row),
                        value = stringResource(R.string.settings_wake_word_value),
                        onClick = onOpenVoiceSettings
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
                SettingsSection(
                    title = stringResource(R.string.section_system_settings)
                ) {
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
                SettingsSection(
                    title = stringResource(R.string.section_app_settings)
                ) {
                    SettingsToggleRow(
                        icon = Icons.Default.Notifications,
                        title = stringResource(R.string.notifications_row),
                        checked = notificationsEnabled,
                        onCheckedChange = onToggleNotifications
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

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text(stringResource(R.string.app_info_row)) },
            text = {
                Text(
                    "${stringResource(R.string.app_name)} $appVersion\n" +
                        stringResource(R.string.unofficial_client)
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(stringResource(R.string.close_description))
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.75f))
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    value: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        value?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing?.invoke()
        Icon(
            Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
}

@Composable
private fun StatusPill(text: String, active: Boolean) {
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = RoundedCornerShape(100.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenV2Preview() {
    OpenCodeAndroidTheme {
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
