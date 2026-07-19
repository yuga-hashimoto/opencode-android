package com.opencode.android.feature.activity

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opencode.android.core.api.OpenCodeFileChange
import com.opencode.android.core.api.OpenCodeTodo
import com.opencode.android.ui.components.DiffStatSummary
import com.opencode.android.ui.components.DiffView
import com.opencode.android.ui.components.SectionCard
import com.opencode.android.ui.components.StatusChip

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    state: SessionDetailUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onContinueChat: () -> Unit,
    runtimeOptions: List<Pair<String, String>> = emptyList(),
    handoffMessage: String? = null,
    onHandoff: (String) -> Unit = {}
) {
    var handoffTarget by remember { mutableStateOf(runtimeOptions.firstOrNull()?.first.orEmpty()) }
    var handoffExpanded by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.session.title.ifBlank { state.session.slug ?: state.session.id },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        state.session.directory.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onRefresh, enabled = !state.isLoading) {
                    Icon(Icons.Default.Refresh, contentDescription = "更新")
                }
            }
        }

        item {
            SectionCard {
                Text("セッション情報", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                SessionInfoRow("ID", state.session.id)
                state.session.version?.let { SessionInfoRow("OpenCode", it) }
                state.session.projectId?.let { SessionInfoRow("プロジェクト", it) }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onContinueChat, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text("このセッションのチャットを続ける")
                }
                if (runtimeOptions.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    ExposedDropdownMenuBox(
                        expanded = handoffExpanded,
                        onExpandedChange = { handoffExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = runtimeOptions.firstOrNull { it.first == handoffTarget }?.second
                                ?: handoffTarget,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("引き継ぎ先") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(handoffExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = handoffExpanded,
                            onDismissRequest = { handoffExpanded = false }
                        ) {
                            runtimeOptions.forEach { (id, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        handoffTarget = id
                                        handoffExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onHandoff(handoffTarget) },
                        enabled = handoffTarget.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null)
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text("セッションを引き継ぐ")
                    }
                    handoffMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        if (state.isLoading) {
            item {
                SectionCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("セッション情報を取得しています")
                    }
                }
            }
        }

        item { SectionHeading("Todo", state.todos.size) }

        if (!state.isLoading && state.todos.isEmpty()) {
            item {
                SectionCard {
                    Text("このセッションにTodoはありません", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(state.todos, key = { "${it.status}-${it.priority}-${it.content}" }) { todo ->
                TodoCard(todo)
            }
        }

        item { SectionHeading("変更差分", state.diff.size) }

        if (!state.isLoading && state.diff.isEmpty()) {
            item {
                SectionCard {
                    Text("このセッションによるファイル変更はありません", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(state.diff, key = { it.displayPath.ifBlank { it.patch.orEmpty() } }) { change ->
                SessionChangeCard(change)
            }
        }

        state.error?.let { error ->
            item {
                SectionCard {
                    Text("取得に失敗しました", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                    Text(error, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun TodoCard(todo: OpenCodeTodo) {
    val completed = todo.status == "completed"
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = when (todo.status) {
                    "completed" -> Icons.Default.CheckCircle
                    "in_progress" -> Icons.Default.PendingActions
                    else -> Icons.Default.TaskAlt
                },
                contentDescription = null,
                tint = when (todo.status) {
                    "completed" -> MaterialTheme.colorScheme.secondary
                    "in_progress" -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(todo.content, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(todoStatusLabel(todo.status), active = completed || todo.status == "in_progress")
                    StatusChip(todoPriorityLabel(todo.priority), active = todo.priority == "high")
                }
            }
        }
    }
}

@Composable
private fun SessionChangeCard(change: OpenCodeFileChange) {
    var expanded by remember(change.displayPath, change.patch) { mutableStateOf(false) }
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Column(modifier = Modifier.weight(1f)) {
                Text(change.displayPath.ifBlank { "変更ファイル" }, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DiffStatSummary(change.additions.toInt(), change.deletions.toInt())
                    if (!change.status.isNullOrBlank()) {
                        Text(
                            "  ${change.status}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (!change.patch.isNullOrBlank()) {
                OutlinedButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "閉じる" else "差分")
                }
            }
        }
        if (expanded && !change.patch.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            DiffView(change.patch, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SectionHeading(title: String, count: Int) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text("${count}件", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SessionInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.35f))
        Text(value, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.65f))
    }
}

private fun todoStatusLabel(status: String): String = when (status) {
    "in_progress" -> "進行中"
    "completed" -> "完了"
    "cancelled" -> "取消"
    else -> "未着手"
}

private fun todoPriorityLabel(priority: String): String = when (priority) {
    "high" -> "高"
    "medium" -> "中"
    else -> "低"
}
