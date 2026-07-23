package com.opencode.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.opencode.android.R
import com.opencode.android.runtime.WorkspaceRef
import com.opencode.android.ui.components.SessionStatus
import com.opencode.android.ui.components.StatusDot
import com.opencode.android.ui.theme.OpenCodeAndroidTheme

data class DrawerRecentSession(
    val id: String,
    val title: String,
    val relativeTime: String,
    val directory: String? = null,
    val isActive: Boolean = false,
    val hasUnread: Boolean = false,
    val status: SessionStatus = SessionStatus.IDLE,
    val hasAttention: Boolean = false
)

private fun DrawerRecentSession.projectKey(): String =
    directory?.trimEnd('/')?.takeIf { it.isNotBlank() && it != "/root" && it != "/workspace" }.orEmpty()

private fun DrawerRecentSession.projectLabel(defaultLabel: String): String =
    projectKey().substringAfterLast('/').takeIf { it.isNotBlank() } ?: defaultLabel

@Composable
fun AppDrawerContent(
    recentSessions: List<DrawerRecentSession>,
    workspaces: List<WorkspaceRef>,
    selectedWorkspacePath: String?,
    onNewChat: () -> Unit,
    onSelectProject: (WorkspaceRef) -> Unit,
    onOpenSession: (String, String) -> Unit,
    onNavigate: (String) -> Unit,
    onDeleteSession: (String) -> Unit = {},
    onArchiveSession: (String) -> Unit = {},
    sidebarGrouping: String = "project",
    onGroupingChange: (String) -> Unit = {},
    collapsedSections: Set<String> = emptySet(),
    onToggleSection: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var sessionActionTarget by remember { mutableStateOf<DrawerRecentSession?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(288.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 6.dp)
            ) {
                DrawerHeader()
                NewChatRow(onClick = onNewChat)

                DrawerSectionHeader(stringResource(R.string.drawer_projects_title))
                DrawerProjectRow(
                    label = stringResource(R.string.drawer_project_default),
                    path = null,
                    selected = selectedWorkspacePath == null,
                    onClick = onNewChat
                )
                workspaces.forEach { workspace ->
                    DrawerProjectRow(
                        label = workspace.name,
                        path = workspace.path,
                        selected = workspace.path == selectedWorkspacePath,
                        onClick = { onSelectProject(workspace) }
                    )
                }
                DrawerAddProjectRow(onClick = { onNavigate("workspaces") })

                if (recentSessions.isNotEmpty()) {
                    val defaultLabel = stringResource(R.string.drawer_project_default)
                    DrawerSectionHeader(
                        text = stringResource(R.string.drawer_recent_chats),
                        collapsed = collapsedSections.contains("recent"),
                        onToggle = { onToggleSection("recent") }
                    )
                    AnimatedVisibility(visible = !collapsedSections.contains("recent")) {
                        Column {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = sidebarGrouping == "project",
                                    onClick = { onGroupingChange("project") },
                                    label = { Text("Project") }
                                )
                                FilterChip(
                                    selected = sidebarGrouping == "status",
                                    onClick = { onGroupingChange("status") },
                                    label = { Text("Status") }
                                )
                            }
                            if (sidebarGrouping == "status") {
                                val statusGroups = listOf(
                                    "Running" to SessionStatus.RUNNING,
                                    "Waiting" to SessionStatus.WAITING,
                                    "Done" to SessionStatus.IDLE,
                                    "Error" to SessionStatus.ERROR
                                )
                                statusGroups.forEach { (label, status) ->
                                    val sessions = recentSessions.filter { it.status == status }
                                    if (sessions.isNotEmpty()) {
                                        val sectionKey = "status_$label"
                                        DrawerRecentProjectHeader(
                                            label = label,
                                            collapsed = collapsedSections.contains(sectionKey),
                                            onToggle = { onToggleSection(sectionKey) }
                                        )
                                        AnimatedVisibility(visible = !collapsedSections.contains(sectionKey)) {
                                            Column {
                                                sessions.forEach { session ->
                                                    DrawerChatRow(
                                                        title = session.title.ifBlank { session.id },
                                                        status = session.status,
                                                        hasAttention = session.hasAttention,
                                                        isActive = session.isActive,
                                                        hasUnread = session.hasUnread,
                                                        onClick = { onOpenSession(session.id, session.title) },
                                                        onLongClick = { sessionActionTarget = session }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                val grouped = recentSessions
                                    .groupBy { it.projectKey() }
                                    .toList()
                                    .sortedByDescending { (_, sessions) ->
                                        sessions.firstOrNull()?.let { recentSessions.indexOf(it) } ?: Int.MAX_VALUE
                                    }
                                grouped.forEach { (key, sessions) ->
                                    val sectionKey = "project_$key"
                                    val sorted = sessions.sortedByDescending { it.hasAttention }
                                    DrawerRecentProjectHeader(
                                        label = sessions.first().projectLabel(defaultLabel),
                                        collapsed = collapsedSections.contains(sectionKey),
                                        onToggle = { onToggleSection(sectionKey) }
                                    )
                                    AnimatedVisibility(visible = !collapsedSections.contains(sectionKey)) {
                                        Column {
                                            sorted.forEach { session ->
                                                DrawerChatRow(
                                                    title = session.title.ifBlank { session.id },
                                                    status = session.status,
                                                    hasAttention = session.hasAttention,
                                                    isActive = session.isActive,
                                                    hasUnread = session.hasUnread,
                                                    onClick = { onOpenSession(session.id, session.title) },
                                                    onLongClick = { sessionActionTarget = session }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.42f))
            DrawerDestinationRow(
                icon = Icons.Default.Settings,
                label = stringResource(R.string.nav_settings),
                onClick = { onNavigate("settings") }
            )
            Spacer(Modifier.padding(bottom = 3.dp))
        }
    }

    sessionActionTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { sessionActionTarget = null },
            title = { Text(target.title.ifBlank { target.id }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            sessionActionTarget = null
                            onArchiveSession(target.id)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.drawer_archive_session))
                    }
                    TextButton(
                        onClick = {
                            sessionActionTarget = null
                            showDeleteConfirm = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.delete_session), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { sessionActionTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showDeleteConfirm) {
        val target = sessionActionTarget
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_session_title)) },
            text = { Text(stringResource(R.string.delete_session_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    target?.let { onDeleteSession(it.id) }
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun DrawerHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Terminal,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun NewChatRow(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(R.string.new_chat),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun DrawerSectionHeader(
    text: String,
    collapsed: Boolean = false,
    onToggle: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 16.dp, end = 16.dp, top = 17.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (collapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DrawerProjectRow(
    label: String,
    path: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            Color.Transparent
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (path != null) {
                    Text(
                        text = path,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerAddProjectRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = stringResource(R.string.drawer_add_project),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DrawerRecentProjectHeader(
    label: String,
    collapsed: Boolean = false,
    onToggle: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(
            imageVector = if (collapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerChatRow(
    title: String,
    status: SessionStatus = SessionStatus.IDLE,
    hasAttention: Boolean = false,
    isActive: Boolean = false,
    hasUnread: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatusDot(status = status)
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (hasAttention) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
            )
        }
    }
}

@Composable
private fun DrawerDestinationRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp)
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(showBackground = true)
@Composable
private fun AppDrawerContentPreview() {
    OpenCodeAndroidTheme {
        AppDrawerContent(
            recentSessions = listOf(
                DrawerRecentSession("1", "認証バグの調査", "3時間前", "/workspace/opencode-android"),
                DrawerRecentSession("2", "READMEの更新", "昨日", "/workspace/opencode-android"),
                DrawerRecentSession("3", "テスト失敗を修正", "2日前", "/workspace/api-server"),
                DrawerRecentSession("4", "APIレスポンスを整理", "4日前", "/workspace/api-server"),
                DrawerRecentSession("5", "依存関係を更新", "1週間前", null)
            ),
            workspaces = listOf(
                WorkspaceRef("/workspace/opencode-android", "opencode-android", "/workspace/opencode-android"),
                WorkspaceRef("/workspace/api-server", "api-server", "/workspace/api-server")
            ),
            selectedWorkspacePath = "/workspace/opencode-android",
            onNewChat = {},
            onSelectProject = {},
            onOpenSession = { _, _ -> },
            onNavigate = {}
        )
    }
}
