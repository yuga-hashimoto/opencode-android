package com.opencode.android.feature.home

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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencode.android.R
import com.opencode.android.runtime.RuntimeState
import com.opencode.android.runtime.RuntimeType
import com.opencode.android.ui.components.OpenCodeBrand
import com.opencode.android.ui.components.SectionCard
import com.opencode.android.ui.components.StatusChip
import com.opencode.android.ui.theme.OpenCodeSuccess
import com.opencode.android.ui.theme.OpenCodeWarning

@Composable
fun HomeScreen(
    state: HomeUiState,
    onNewChat: () -> Unit,
    onOpenWorkspaces: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenSession: (String, String) -> Unit,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OpenCodeBrand(modifier = Modifier.weight(1f))
                IconButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                }
            }
        }

        if (state.connected && (state.runningCount > 0 || state.pendingApprovalCount > 0)) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.pendingApprovalCount > 0) {
                        LiveOpsStatCard(
                            count = state.pendingApprovalCount,
                            label = stringResource(R.string.pending_approvals),
                            icon = Icons.Default.Security,
                            tint = OpenCodeWarning,
                            onClick = onOpenActivity,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (state.runningCount > 0) {
                        LiveOpsStatCard(
                            count = state.runningCount,
                            label = stringResource(R.string.running_now),
                            icon = Icons.Default.Terminal,
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onOpenActivity,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = when (state.runtimeType) {
                            RuntimeType.LOCAL -> Icons.Default.Android
                            RuntimeType.REMOTE -> Icons.Default.Computer
                            null -> Icons.Default.Computer
                        },
                        contentDescription = null,
                        tint = if (state.connected) OpenCodeSuccess else MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.runtimeName.ifBlank { "実行先を選択してください" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = runtimeDescription(state),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusChip(
                        text = if (state.connected) "接続済み" else "未接続",
                        active = state.connected
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onNewChat,
                    modifier = Modifier.weight(1f),
                    enabled = state.runtimeId != null
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(stringResource(R.string.new_chat))
                }
                FilledTonalButton(
                    onClick = onOpenWorkspaces,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(stringResource(R.string.nav_workspaces))
                }
            }
        }

        item {
            Text("現在の構成", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        item {
            SectionCard {
                ConfigurationRow("作業フォルダ", state.workspace?.path ?: "未選択")
                Spacer(Modifier.height(12.dp))
                ConfigurationRow("モデル", state.modelId ?: "OpenCodeの既定値")
                Spacer(Modifier.height(12.dp))
                ConfigurationRow("エージェント", state.agentId ?: "build")
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.padding(horizontal = 5.dp))
                    Text(
                        text = state.providerId ?: "AIサービス未選択",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        state.error?.let { error ->
            item {
                SectionCard {
                    Text("接続または取得に失敗しました", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(6.dp))
                    Text(error, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.recent_sessions),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                androidx.compose.material3.TextButton(onClick = onOpenActivity) {
                    Text(stringResource(R.string.view_all))
                }
            }
        }

        if (state.sessions.isEmpty()) {
            item {
                SectionCard {
                    Text(stringResource(R.string.no_sessions), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(state.sessions.take(4), key = { it.id }) { session ->
                SectionCard(
                    modifier = Modifier.clickable { onOpenSession(session.id, session.title) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(session.title.ifBlank { session.slug ?: session.id }, fontWeight = FontWeight.Medium)
                            Text(
                                session.directory.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveOpsStatCard(
    count: Int,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint)
            Column {
                Text(count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tint)
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ConfigurationRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

private fun runtimeDescription(state: HomeUiState): String = when {
    state.connected -> "OpenCode ${state.version}"
    state.isRefreshing -> "接続と状態を確認しています"
    state.runtimeState is RuntimeState.Unavailable -> state.runtimeState.reason
    state.runtimeState is RuntimeState.Failed -> state.runtimeState.message
    state.runtimeId == null -> "AndroidローカルまたはPCを選択できます"
    else -> "OpenCodeへ未接続"
}
