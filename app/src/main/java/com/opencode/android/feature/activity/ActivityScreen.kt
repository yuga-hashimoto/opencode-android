package com.opencode.android.feature.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencode.android.core.api.OpenCodeSession
import com.opencode.android.ui.components.SectionCard
import com.opencode.android.ui.components.StatusChip

@Composable
fun ActivityScreen(
    state: ActivityUiState,
    onRefresh: () -> Unit,
    onInspectSession: (OpenCodeSession) -> Unit,
    onOpenSession: (String, String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("実行中", "承認", "セッション", "ログ")

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("アクティビティ", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("OpenCodeの実行状況と履歴", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                Icon(Icons.Default.Refresh, contentDescription = "更新")
            }
        }

        PrimaryTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTab) {
            0 -> RunningTab(state, onInspectSession, onOpenSession)
            1 -> ApprovalTab(state)
            2 -> SessionsTab(state, onInspectSession, onOpenSession)
            else -> LogsTab(state)
        }
    }
}

@Composable
private fun RunningTab(
    state: ActivityUiState,
    onInspectSession: (OpenCodeSession) -> Unit,
    onOpenSession: (String, String) -> Unit
) {
    val activeSessions = state.sessions.filter { it.id in state.activeSessionIds }
    ActivityList(emptyText = "実行中のタスクはありません", isEmpty = activeSessions.isEmpty()) {
        items(activeSessions, key = { it.id }) { session ->
            SectionCard(modifier = Modifier.clickable { onInspectSession(session) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(session.title.ifBlank { session.id }, fontWeight = FontWeight.SemiBold)
                        Text(session.directory.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusChip("実行中", active = true)
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { onOpenSession(session.id, session.title) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("チャットを開く")
                }
            }
        }
    }
}

@Composable
private fun ApprovalTab(state: ActivityUiState) {
    ActivityList(emptyText = "承認待ちはありません", isEmpty = state.permissions.isEmpty()) {
        items(state.permissions, key = { it.id }) { permission ->
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(permission.permission, fontWeight = FontWeight.SemiBold)
                        permission.patterns.forEach { pattern ->
                            Text(
                                pattern,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("チャット画面で許可または拒否できます。", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionsTab(
    state: ActivityUiState,
    onInspectSession: (OpenCodeSession) -> Unit,
    onOpenSession: (String, String) -> Unit
) {
    ActivityList(emptyText = "セッションはまだありません", isEmpty = state.sessions.isEmpty()) {
        items(state.sessions, key = { it.id }) { session ->
            SectionCard(modifier = Modifier.clickable { onInspectSession(session) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(session.title.ifBlank { session.slug ?: session.id }, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(session.directory.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { onOpenSession(session.id, session.title) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("チャットを続ける")
                }
            }
        }
    }
}

@Composable
private fun LogsTab(state: ActivityUiState) {
    ActivityList(emptyText = "イベントログはまだありません", isEmpty = state.logs.isEmpty()) {
        items(state.logs, key = { "${it.timestamp}-${it.title}-${it.sessionId}" }) { log ->
            SectionCard {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(log.title, fontWeight = FontWeight.Medium)
                        log.detail?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        log.sessionId?.let {
                            Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityList(
    emptyText: String,
    isEmpty: Boolean,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isEmpty) {
            item {
                SectionCard {
                    Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            content()
        }
        item {
            Spacer(Modifier.height(72.dp))
        }
    }
}
