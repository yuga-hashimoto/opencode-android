package com.opencode.android.feature.workspace

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencode.android.runtime.LocalRuntimeStatus
import com.opencode.android.runtime.local.LocalRuntimeDiagnostics
import com.opencode.android.ui.components.SectionCard
import com.opencode.android.ui.components.StatusChip
import java.util.Locale

@Composable
fun LocalRuntimeManagementScreen(
    state: LocalRuntimeManagementUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRepair: () -> Unit,
    onRequestDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ローカルランタイム") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.isLoading && !state.isDeleting) {
                        Icon(Icons.Default.Refresh, contentDescription = "診断を更新")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            state.diagnostics?.let { diagnostics ->
                RuntimeSummaryCard(diagnostics)
                RuntimeStorageCard(diagnostics)
                RuntimeToolsCard(diagnostics)
                RuntimeLogsCard(diagnostics.logTail)

                if (diagnostics.status.isInstalled()) {
                    SectionCard {
                        Text("管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = onRepair,
                            enabled = !state.isDeleting,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null)
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Text("修復して再セットアップ")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onRequestDelete,
                            enabled = !state.isDeleting,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.isDeleting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.DeleteForever,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Text(
                                if (state.isDeleting) "削除しています" else "ランタイムを完全削除",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            "Linux環境、ダウンロードキャッシュ、ログ、ローカル作業領域をすべて削除します。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            state.error?.let { error ->
                SectionCard {
                    Text(
                        "診断に失敗しました",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(error, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (state.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text("ローカルランタイムを削除しますか？") },
            text = {
                Text(
                    "OpenCode、Linux環境、キャッシュ、ログ、アプリ内のローカル作業領域が削除されます。PC接続設定は削除されません。"
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) {
                    Text("完全削除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) { Text("キャンセル") }
            }
        )
    }
}

@Composable
private fun RuntimeSummaryCard(diagnostics: LocalRuntimeDiagnostics) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("状態", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            StatusChip(
                text = diagnostics.status.displayName(),
                active = diagnostics.status is LocalRuntimeStatus.Ready
            )
        }
        Spacer(Modifier.height(10.dp))
        MetricRow("OpenCode", diagnostics.version ?: "—")
        MetricRow("ABI", diagnostics.abi.ifBlank { "—" })
        MetricRow("ポート", diagnostics.port?.toString() ?: "—")
        diagnostics.process?.let { process ->
            MetricRow("PID", process.pid?.toString() ?: "取得不可")
            MetricRow("メモリ", process.rssBytes?.let(::formatBytes) ?: "取得不可")
            MetricRow("稼働時間", formatDuration(process.uptimeMillis))
        }
    }
}

@Composable
private fun RuntimeStorageCard(diagnostics: LocalRuntimeDiagnostics) {
    SectionCard {
        Text("保存容量", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        MetricRow("ランタイム使用量", formatBytes(diagnostics.runtimeBytes))
        MetricRow("端末の空き容量", formatBytes(diagnostics.freeBytes))
    }
}

@Composable
private fun RuntimeToolsCard(diagnostics: LocalRuntimeDiagnostics) {
    SectionCard {
        Text("必須ツール", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        diagnostics.tools.forEach { tool ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (tool.available) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (tool.available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(tool.label, fontWeight = FontWeight.Medium)
                    Text(
                        tool.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3
                    )
                }
            }
        }
    }
}

@Composable
private fun RuntimeLogsCard(logTail: String) {
    SectionCard {
        Text("最新ログ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (logTail.isBlank()) {
            Text("ログはありません", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                Text(
                    text = logTail,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

private fun LocalRuntimeStatus.displayName(): String = when (this) {
    LocalRuntimeStatus.NotInstalled -> "未インストール"
    is LocalRuntimeStatus.Installing -> "セットアップ中"
    is LocalRuntimeStatus.Starting -> "起動中"
    is LocalRuntimeStatus.Updating -> "更新中"
    is LocalRuntimeStatus.Stopped -> "停止中"
    is LocalRuntimeStatus.Ready -> "稼働中"
    is LocalRuntimeStatus.Broken -> "問題あり"
    is LocalRuntimeStatus.UnsupportedAbi -> "未対応"
}

private fun LocalRuntimeStatus.isInstalled(): Boolean =
    this !is LocalRuntimeStatus.NotInstalled && this !is LocalRuntimeStatus.UnsupportedAbi

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unit])
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0 -> "${hours}時間${minutes}分"
        minutes > 0 -> "${minutes}分${seconds}秒"
        else -> "${seconds}秒"
    }
}
