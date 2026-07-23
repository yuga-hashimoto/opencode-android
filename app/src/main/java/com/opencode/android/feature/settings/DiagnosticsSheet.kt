package com.opencode.android.feature.settings

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencode.android.BuildConfig
import com.opencode.android.ui.components.LabelValueRow
import com.opencode.android.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsSheet(
    onDismiss: () -> Unit,
    appVersion: String,
    connectionStatus: String,
    runtimeStatus: String
) {
    val clipboard = LocalClipboardManager.current
    val buildType = BuildConfig.BUILD_TYPE

    val runtime = Runtime.getRuntime()
    val totalMemoryMb = runtime.totalMemory() / (1024L * 1024L)
    val freeMemoryMb = runtime.freeMemory() / (1024L * 1024L)
    val maxMemoryMb = runtime.maxMemory() / (1024L * 1024L)

    val statFs = remember { StatFs(Environment.getDataDirectory().path) }
    val availableBytes = statFs.availableBytes
    val totalBytes = statFs.totalBytes

    val markdown = buildString {
        appendLine("# OpenCode Android Diagnostics")
        appendLine()
        appendLine("## App")
        appendLine("- Version: $appVersion")
        appendLine("- Build type: $buildType")
        appendLine()
        appendLine("## Connection")
        appendLine("- Status: $connectionStatus")
        appendLine("- Runtime: $runtimeStatus")
        appendLine()
        appendLine("## Memory")
        appendLine("- Total: ${totalMemoryMb}MB")
        appendLine("- Free: ${freeMemoryMb}MB")
        appendLine("- Max: ${maxMemoryMb}MB")
        appendLine()
        appendLine("## Storage")
        appendLine("- Available: ${formatBytes(availableBytes)}")
        appendLine("- Total: ${formatBytes(totalBytes)}")
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Diagnostics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DiagnosticsSectionHeader("App")
                    LabelValueRow(label = "Version", value = appVersion)
                    LabelValueRow(label = "Build type", value = buildType)
                }
            }

            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DiagnosticsSectionHeader("Connection")
                    LabelValueRow(label = "Status", value = connectionStatus)
                    LabelValueRow(label = "Runtime", value = runtimeStatus)
                }
            }

            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DiagnosticsSectionHeader("Memory")
                    LabelValueRow(label = "Total", value = "${totalMemoryMb}MB")
                    LabelValueRow(label = "Free", value = "${freeMemoryMb}MB")
                    LabelValueRow(label = "Max", value = "${maxMemoryMb}MB")
                }
            }

            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DiagnosticsSectionHeader("Storage")
                    LabelValueRow(label = "Available", value = formatBytes(availableBytes))
                    LabelValueRow(label = "Total", value = formatBytes(totalBytes))
                }
            }

            Button(
                onClick = {
                    clipboard.setText(AnnotatedString(markdown))
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Copy Diagnostics")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DiagnosticsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun formatBytes(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1.0) {
        "%.1f GB".format(gb)
    } else {
        "%.0f MB".format(bytes / (1024.0 * 1024.0))
    }
}
