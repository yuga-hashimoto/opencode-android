package com.opencode.android.feature.workspace

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencode.android.core.api.OpenCodeHealth
import com.opencode.android.runtime.LocalRuntimeStatus
import com.opencode.android.runtime.RuntimeState
import com.opencode.android.runtime.RuntimeType
import com.opencode.android.runtime.WorkspaceRef
import com.opencode.android.ui.components.SectionCard
import com.opencode.android.ui.components.StatusChip

@Composable
fun WorkspacesScreen(
    state: WorkspaceUiState,
    onSelectRuntime: (String) -> Unit,
    onSaveConnection: (ConnectionFormState) -> Unit,
    onDeleteConnection: (String) -> Unit,
    onTestConnection: suspend (ConnectionFormState) -> Result<OpenCodeHealth>,
    onRefresh: () -> Unit,
    onOpenWorkspace: (WorkspaceRef) -> Unit,
    onSetupLocal: () -> Unit,
    onStartLocal: () -> Unit,
    onStopLocal: () -> Unit,
    onReinstallLocal: () -> Unit,
    onOpenLocalManagement: () -> Unit
) {
    var editing by remember { mutableStateOf<ConnectionFormState?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = ConnectionFormState() }) {
                Icon(Icons.Default.Add, contentDescription = "PC接続を追加")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ワークスペース",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "OpenCodeを動かす端末と作業フォルダを選びます。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                        Icon(Icons.Default.Refresh, contentDescription = "更新")
                    }
                }
            }

            item {
                Text("実行先", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            items(state.targets, key = { it.id }) { target ->
                val remoteProfile = state.connections.firstOrNull { it.id == target.id }
                SectionCard(
                    modifier = Modifier.clickable(enabled = target.type == RuntimeType.REMOTE || state.localStatus is LocalRuntimeStatus.Ready) {
                        onSelectRuntime(target.id)
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (target.type == RuntimeType.LOCAL) Icons.Default.Android else Icons.Default.Computer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(target.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = targetSubtitle(target, state.localStatus, remoteProfile?.baseUrl),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (target.selected) {
                            StatusChip("使用中", active = true)
                        } else if (target.type == RuntimeType.REMOTE) {
                            TextButton(onClick = { onSelectRuntime(target.id) }) { Text("選択") }
                        }
                        if (remoteProfile != null) {
                            IconButton(onClick = { editing = ConnectionFormState.from(remoteProfile) }) {
                                Icon(Icons.Default.Edit, contentDescription = "編集")
                            }
                        }
                    }
                    if (target.type == RuntimeType.LOCAL) {
                        Spacer(Modifier.height(12.dp))
                        when (val local = state.localStatus) {
                            LocalRuntimeStatus.NotInstalled -> {
                                Button(onClick = onSetupLocal, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Build, contentDescription = null)
                                    Spacer(Modifier.padding(horizontal = 4.dp))
                                    Text("この端末へセットアップ")
                                }
                            }
                            is LocalRuntimeStatus.Installing -> {
                                Text(local.step, style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(8.dp))
                                if (local.progress != null) {
                                    LinearProgressIndicator(
                                        progress = { local.progress.coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                            is LocalRuntimeStatus.Starting -> {
                                Text("OpenCode ${local.version}を起動しています")
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            is LocalRuntimeStatus.Updating -> {
                                Text(local.step, style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(8.dp))
                                if (local.progress != null) {
                                    LinearProgressIndicator(
                                        progress = { local.progress.coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                            is LocalRuntimeStatus.Stopped -> {
                                Button(onClick = onStartLocal, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.padding(horizontal = 4.dp))
                                    Text("OpenCodeを起動")
                                }
                            }
                            is LocalRuntimeStatus.Ready -> {
                                OutlinedButton(onClick = onStopLocal, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Stop, contentDescription = null)
                                    Spacer(Modifier.padding(horizontal = 4.dp))
                                    Text("ローカル実行を停止")
                                }
                            }
                            is LocalRuntimeStatus.Broken -> {
                                Button(onClick = onReinstallLocal, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Build, contentDescription = null)
                                    Spacer(Modifier.padding(horizontal = 4.dp))
                                    Text("修復して再セットアップ")
                                }
                            }
                            is LocalRuntimeStatus.UnsupportedAbi -> Unit
                        }
                        if (state.localStatus !is LocalRuntimeStatus.UnsupportedAbi) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onOpenLocalManagement,
                                enabled = state.localStatus !is LocalRuntimeStatus.Installing &&
                                    state.localStatus !is LocalRuntimeStatus.Starting &&
                                    state.localStatus !is LocalRuntimeStatus.Updating,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null)
                                Spacer(Modifier.padding(horizontal = 4.dp))
                                Text("診断と管理")
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "作業フォルダ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text("${state.workspaces.size}件", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (state.workspaces.isEmpty()) {
                item {
                    SectionCard {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(10.dp))
                        Text("作業フォルダはまだありません", fontWeight = FontWeight.Medium)
                        Text(
                            "セッションを開始すると、OpenCodeが利用したフォルダがここに表示されます。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                items(state.workspaces, key = { it.id }) { workspace ->
                    SectionCard(modifier = Modifier.clickable { onOpenWorkspace(workspace) }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(workspace.name, fontWeight = FontWeight.Medium)
                                Text(
                                    workspace.path,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }

            state.error?.let { error ->
                item {
                    SectionCard {
                        Text("状態の取得に失敗しました", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                        Text(error, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    editing?.let { initial ->
        ConnectionDialog(
            initial = initial,
            onDismiss = { editing = null },
            onSave = {
                onSaveConnection(it)
                editing = null
            },
            onDelete = if (state.connections.any { it.id == initial.id }) {
                {
                    onDeleteConnection(initial.id)
                    editing = null
                }
            } else null,
            onTest = onTestConnection
        )
    }
}

private fun targetSubtitle(
    target: RuntimeSummary,
    localStatus: LocalRuntimeStatus,
    remoteUrl: String?
): String = when (target.type) {
    RuntimeType.REMOTE -> when (val runtimeState = target.state) {
        is RuntimeState.Connected -> "OpenCode ${runtimeState.version} · ${remoteUrl.orEmpty()}"
        RuntimeState.Connecting -> "接続中 · ${remoteUrl.orEmpty()}"
        is RuntimeState.Failed -> compactRuntimeError(runtimeState.message)
        is RuntimeState.Unavailable -> runtimeState.reason
        RuntimeState.Disconnected -> remoteUrl.orEmpty()
    }
    RuntimeType.LOCAL -> when (localStatus) {
        LocalRuntimeStatus.NotInstalled -> "未インストール"
        is LocalRuntimeStatus.Installing -> "セットアップ中 · ${localStatus.step}"
        is LocalRuntimeStatus.Starting -> "OpenCode ${localStatus.version}を起動中"
        is LocalRuntimeStatus.Updating ->
            "OpenCode ${localStatus.currentVersion} → ${localStatus.targetVersion} · ${localStatus.step}"
        is LocalRuntimeStatus.Stopped -> "導入済み · OpenCode ${localStatus.version} · 停止中"
        is LocalRuntimeStatus.Ready -> "OpenCode ${localStatus.version} · 稼働中"
        is LocalRuntimeStatus.Broken -> compactRuntimeError(localStatus.reason)
        is LocalRuntimeStatus.UnsupportedAbi -> "未対応ABI: ${localStatus.abi}"
    }
}

private fun compactRuntimeError(message: String): String {
    val firstUsefulLine = message.lineSequence()
        .map(String::trim)
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
    val compact = firstUsefulLine.take(160)
    return when {
        compact.isBlank() -> "ローカルランタイムで問題が発生しました"
        compact.length < firstUsefulLine.length -> "$compact…"
        else -> compact
    }
}
