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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opencode.android.runtime.LocalRuntimeStatus
import com.opencode.android.runtime.local.LocalRuntimeDiagnostics
import com.opencode.android.runtime.local.LocalRuntimeOperationResult
import com.opencode.android.runtime.local.LocalRuntimeUpdateCheck
import com.opencode.android.ui.components.SectionCard
import com.opencode.android.ui.components.StatusChip
import java.util.Locale

@Composable
fun LocalRuntimeManagementScreen(
    state: LocalRuntimeManagementUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCheckForUpdate: () -> Unit,
    onRepair: () -> Unit,
    onRequestUpdate: () -> Unit,
    onDismissUpdate: () -> Unit,
    onConfirmUpdate: () -> Unit,
    onRequestRollback: () -> Unit,
    onDismissRollback: () -> Unit,
    onConfirmRollback: () -> Unit,
    onRequestDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    val busy = state.runtimeStatus.isBusy() || state.isDeleting
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ローカルOpenCode") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !state.isLoading && !state.isCheckingUpdate && !busy
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "診断と更新情報を再取得")
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

            (state.runtimeStatus as? LocalRuntimeStatus.Updating)?.let { status ->
                RuntimeUpdateProgressCard(status)
            }
            state.lastOperation?.let { operation ->
                RuntimeOperationResultCard(operation)
            }

            state.diagnostics?.let { diagnostics ->
                RuntimeSummaryCard(diagnostics)
                RuntimeStorageCard(diagnostics)
                RuntimeUpdateCard(
                    state = state,
                    freeBytes = diagnostics.freeBytes,
                    busy = busy,
                    onCheckForUpdate = onCheckForUpdate,
                    onRequestUpdate = onRequestUpdate,
                    onRequestRollback = onRequestRollback
                )
                RuntimeToolsCard(diagnostics)
                RuntimeLogsCard(diagnostics.logTail)

                if (diagnostics.status.isInstalled()) {
                    RuntimeManagementCard(
                        busy = busy,
                        isDeleting = state.isDeleting,
                        onRepair = onRepair,
                        onRequestDelete = onRequestDelete
                    )
                }
            }

            state.error?.let { error ->
                ErrorCard("操作に失敗しました", error)
            }
        }
    }

    val available = state.updateCheck as? LocalRuntimeUpdateCheck.Available
    if (state.showUpdateConfirmation && available != null) {
        AlertDialog(
            onDismissRequest = onDismissUpdate,
            title = { Text("OpenCode ${available.release.version}へ更新しますか？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("更新中はローカルOpenCodeを停止し、検証後に再起動します。")
                    Text("失敗した場合は現在のOpenCode ${available.currentVersion}へ自動復旧します。")
                    Text(
                        "必要な空き容量: ${formatBytes(available.release.asset.requiredFreeBytes)}",
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirmUpdate) { Text("更新する") }
            },
            dismissButton = {
                TextButton(onClick = onDismissUpdate) { Text("キャンセル") }
            }
        )
    }

    if (state.showRollbackConfirmation && !state.rollbackVersion.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = onDismissRollback,
            title = { Text("OpenCode ${state.rollbackVersion}へ戻しますか？") },
            text = {
                Text(
                    "現在のローカルOpenCodeを停止し、保存されている直前バージョンへ切り替えて再起動します。起動できない場合は現在のバージョンへ復旧します。"
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmRollback) { Text("ロールバック") }
            },
            dismissButton = {
                TextButton(onClick = onDismissRollback) { Text("キャンセル") }
            }
        )
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
private fun RuntimeUpdateProgressCard(status: LocalRuntimeStatus.Updating) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.SystemUpdate, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "OpenCode ${status.currentVersion} → ${status.targetVersion}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(status.step, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (status.progress == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(
                progress = { status.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "${(status.progress.coerceIn(0f, 1f) * 100).toInt()}%",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun RuntimeOperationResultCard(result: LocalRuntimeOperationResult) {
    val (icon, title, detail, isError) = when (result) {
        is LocalRuntimeOperationResult.UpdateSkipped -> OperationPresentation(
            Icons.Default.CheckCircle,
            "OpenCodeは最新です",
            "現在のバージョン: ${result.version}",
            false
        )
        is LocalRuntimeOperationResult.Updated -> OperationPresentation(
            Icons.Default.CheckCircle,
            "OpenCodeを更新しました",
            "${result.fromVersion} → ${result.toVersion}",
            false
        )
        is LocalRuntimeOperationResult.AutomaticRollback -> OperationPresentation(
            Icons.Default.Warning,
            "更新に失敗し、自動復旧しました",
            "${result.failedVersion}を起動できなかったため${result.restoredVersion}へ戻しました。${result.reason}",
            false
        )
        is LocalRuntimeOperationResult.RolledBack -> OperationPresentation(
            Icons.Default.History,
            "ロールバックしました",
            "${result.fromVersion} → ${result.toVersion}",
            false
        )
        is LocalRuntimeOperationResult.RollbackFailedRestored -> OperationPresentation(
            Icons.Default.Warning,
            "ロールバックを取り消しました",
            "${result.attemptedVersion}を起動できなかったため${result.restoredVersion}へ復旧しました。${result.reason}",
            false
        )
        is LocalRuntimeOperationResult.Failed -> OperationPresentation(
            Icons.Default.Error,
            "${result.operation}に失敗しました",
            result.message,
            true
        )
    }
    SectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
private fun RuntimeUpdateCard(
    state: LocalRuntimeManagementUiState,
    freeBytes: Long,
    busy: Boolean,
    onCheckForUpdate: () -> Unit,
    onRequestUpdate: () -> Unit,
    onRequestRollback: () -> Unit
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("OpenCodeの更新", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (state.isCheckingUpdate) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
        Spacer(Modifier.height(8.dp))

        when (val check = state.updateCheck) {
            null -> {
                Text("公式GitHub Releaseから最新版を確認します。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onCheckForUpdate,
                    enabled = !state.isCheckingUpdate && !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text("更新を確認")
                }
            }
            is LocalRuntimeUpdateCheck.UpToDate -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("OpenCode ${check.currentVersion}は最新です", fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onCheckForUpdate,
                    enabled = !state.isCheckingUpdate && !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("再確認")
                }
            }
            is LocalRuntimeUpdateCheck.Available -> {
                val requiredBytes = check.release.asset.requiredFreeBytes
                val enoughSpace = freeBytes >= requiredBytes
                Text(
                    "OpenCode ${check.release.version}を利用できます",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                MetricRow("現在", check.currentVersion)
                MetricRow("ダウンロード", formatBytes(check.release.asset.sizeBytes))
                MetricRow("必要な空き容量", formatBytes(requiredBytes))
                if (!enoughSpace) {
                    Text(
                        "空き容量が不足しています。現在: ${formatBytes(freeBytes)}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (check.release.releaseNotes.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text("リリースノート", fontWeight = FontWeight.Medium)
                    Text(
                        check.release.releaseNotes,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onRequestUpdate,
                    enabled = !busy && !state.isCheckingUpdate && enoughSpace,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text("OpenCode ${check.release.version}へ更新")
                }
            }
        }

        state.updateError?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        state.rollbackVersion?.let { rollbackVersion ->
            Spacer(Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(Modifier.height(14.dp))
            Text("直前のバージョン", fontWeight = FontWeight.Medium)
            Text(
                "OpenCode ${rollbackVersion}へ戻せます。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onRequestRollback,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.History, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text("${rollbackVersion}へロールバック")
            }
        }
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
private fun RuntimeManagementCard(
    busy: Boolean,
    isDeleting: Boolean,
    onRepair: () -> Unit,
    onRequestDelete: () -> Unit
) {
    SectionCard {
        Text("管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onRepair,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Build, contentDescription = null)
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text("修復して再セットアップ")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onRequestDelete,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isDeleting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text(
                if (isDeleting) "削除しています" else "ランタイムを完全削除",
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

@Composable
private fun ErrorCard(title: String, detail: String) {
    SectionCard {
        Text(title, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End
        )
    }
}

private data class OperationPresentation(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val detail: String,
    val isError: Boolean
)

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

private fun LocalRuntimeStatus.isBusy(): Boolean =
    this is LocalRuntimeStatus.Installing ||
        this is LocalRuntimeStatus.Starting ||
        this is LocalRuntimeStatus.Updating

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
