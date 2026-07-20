package com.opencode.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.opencode.android.R
import com.opencode.android.ui.theme.OpenCodeAndroidTheme

/** A recent chat entry rendered in the drawer's "最近のチャット" section. */
data class DrawerRecentSession(
    val id: String,
    val title: String,
    val relativeTime: String
)

/**
 * ChatGPT-style left navigation drawer content: brand header, new-chat action,
 * recent chats, a static "pinned" section (placeholder), a features list, and a
 * footer account card.
 */
@Composable
fun AppDrawerContent(
    recentSessions: List<DrawerRecentSession>,
    onNewChat: () -> Unit,
    onOpenSession: (String, String) -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(300.dp)
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                // TODO: wire up search-in-chats once a search index exists.
                IconButton(onClick = {}) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.drawer_search_description))
                }
            }

            DrawerActionRow(
                icon = Icons.AutoMirrored.Filled.Chat,
                label = stringResource(R.string.new_chat),
                emphasized = true,
                onClick = onNewChat
            )

            Spacer(Modifier.height(8.dp))
            DrawerSectionHeader(stringResource(R.string.drawer_recent_chats))
            if (recentSessions.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_sessions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            } else {
                recentSessions.forEach { session ->
                    DrawerChatRow(
                        title = session.title.ifBlank { session.id },
                        subtitle = session.relativeTime,
                        onClick = { onOpenSession(session.id, session.title) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            DrawerSectionHeader(stringResource(R.string.drawer_pinned))
            // Static placeholder pinned items — TODO: back with real pinning once supported.
            DrawerChatRow(
                title = stringResource(R.string.drawer_pinned_sample_1),
                subtitle = null,
                onClick = {}
            )
            DrawerChatRow(
                title = stringResource(R.string.drawer_pinned_sample_2),
                subtitle = null,
                onClick = {}
            )

            Spacer(Modifier.height(8.dp))
            DrawerSectionHeader(stringResource(R.string.drawer_features))
            DrawerFeatureRow(Icons.Default.Schedule, stringResource(R.string.schedule_title)) { onNavigate("schedule") }
            DrawerFeatureRow(Icons.Default.Computer, stringResource(R.string.settings_local_runtime_row)) { onNavigate("local-runtime-management") }
            DrawerFeatureRow(Icons.Default.Router, stringResource(R.string.remote_connection_row)) { onNavigate("remote-connection") }
            DrawerFeatureRow(Icons.Default.Folder, stringResource(R.string.settings_workspace_row)) { onNavigate("workspaces") }
            DrawerFeatureRow(Icons.Default.Settings, stringResource(R.string.nav_settings)) { onNavigate("settings") }
            DrawerFeatureRow(Icons.Default.History, stringResource(R.string.diagnostics_row)) { onNavigate("activity") }
            Spacer(Modifier.height(8.dp))
        }

        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.height(32.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                // Static placeholder identity — TODO: replace with real account info once auth exists.
                Text(
                    text = stringResource(R.string.drawer_user_email_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.drawer_plan_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DrawerSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
private fun DrawerActionRow(
    icon: ImageVector,
    label: String,
    emphasized: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun DrawerChatRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Default.PushPin,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.height(16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DrawerFeatureRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Icon(
            Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AppDrawerContentPreview() {
    OpenCodeAndroidTheme {
        AppDrawerContent(
            recentSessions = listOf(
                DrawerRecentSession("1", "認証バグの調査", "3時間前"),
                DrawerRecentSession("2", "READMEの更新", "昨日")
            ),
            onNewChat = {},
            onOpenSession = { _, _ -> },
            onNavigate = {}
        )
    }
}
