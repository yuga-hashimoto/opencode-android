package com.opencode.android.feature.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opencode.android.core.api.OpenCodeFileChange
import com.opencode.android.core.api.OpenCodeFileNode
import com.opencode.android.ui.components.SectionCard
import com.opencode.android.ui.components.StatusChip

@Composable
fun WorkspaceExplorerScreen(
    state: WorkspaceExplorerUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenNode: (OpenCodeFileNode) -> Unit,
    onCloseFile: () -> Unit,
    onNavigateUp: () -> Unit,
    onSearch: (String) -> Unit,
    onRefreshChanges: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("ファイル", "検索", "変更")

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    state.workspace.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    state.workspace.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = if (selectedTab == 2) onRefreshChanges else onRefresh,
                enabled = !state.isLoadingFiles && !state.isLoadingChanges
            ) {
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
            0 -> FilesTab(state, onOpenNode, onCloseFile, onNavigateUp)
            1 -> SearchTab(state, onSearch)
            else -> ChangesTab(state)
        }
    }
}

@Composable
private fun FilesTab(
    state: WorkspaceExplorerUiState,
    onOpenNode: (OpenCodeFileNode) -> Unit,
    onCloseFile: () -> Unit,
    onNavigateUp: () -> Unit
) {
    if (state.selectedFilePath != null) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onCloseFile) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ファイル一覧へ戻る")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            state.selectedFilePath,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        state.selectedFile?.mimeType?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (state.isLoadingFiles) {
                item { LoadingCard("ファイルを読み込んでいます") }
            } else {
                state.selectedFile?.let { file ->
                    item {
                        SectionCard {
                            if (file.type == "binary") {
                                Text("バイナリファイルはプレビューできません。")
                            } else {
                                SelectionContainer {
                                    Text(
                                        file.content,
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.horizontalScroll(rememberScrollState())
                                    )
                                }
                            }
                        }
                    }
                }
            }
            state.error?.let { item { ErrorCard(it) } }
            item { Spacer(Modifier.height(72.dp)) }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        state.currentPath,
                        modifier = Modifier.weight(1f),
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = onNavigateUp, enabled = state.currentPath != ".") {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "上のフォルダ")
                    }
                }
            }
        }

        if (state.isLoadingFiles) {
            item { LoadingCard("ファイル一覧を読み込んでいます") }
        } else if (state.files.isEmpty()) {
            item {
                SectionCard {
                    Text("このフォルダは空です", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(state.files, key = { it.absolute }) { node ->
                SectionCard(modifier = Modifier.clickable { onOpenNode(node) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            if (node.type == "directory") Icons.Default.Folder else Icons.Default.Description,
                            contentDescription = null,
                            tint = if (node.type == "directory") {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(node.name, fontWeight = FontWeight.Medium)
                            Text(
                                node.path,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (node.ignored) StatusChip("除外", active = false)
                    }
                }
            }
        }
        state.error?.let { item { ErrorCard(it) } }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun SearchTab(
    state: WorkspaceExplorerUiState,
    onSearch: (String) -> Unit
) {
    var query by rememberSaveable(state.workspace.id) { mutableStateOf(state.searchQuery) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("ファイル名または本文を検索") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { onSearch(query) },
                enabled = query.isNotBlank() && !state.isSearching,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("検索")
            }
        }

        if (state.isSearching) {
            item { LoadingCard("検索しています") }
        }

        if (!state.isSearching && state.searchQuery.isNotBlank() &&
            state.textMatches.isEmpty() && state.fileMatches.isEmpty()
        ) {
            item {
                SectionCard {
                    Text("一致する結果はありません", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (state.fileMatches.isNotEmpty()) {
            item { SectionTitle("ファイル名", state.fileMatches.size) }
            items(state.fileMatches, key = { "file-$it" }) { path ->
                SectionCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Text(path, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (state.textMatches.isNotEmpty()) {
            item { SectionTitle("本文", state.textMatches.size) }
            items(
                state.textMatches,
                key = { "${it.path.text}:${it.lineNumber}:${it.absoluteOffset}" }
            ) { match ->
                SectionCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${match.path.text}:${match.lineNumber}",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(6.dp))
                            SelectionContainer {
                                Text(
                                    match.lines.text.trimEnd(),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        state.error?.let { item { ErrorCard(it) } }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun ChangesTab(state: WorkspaceExplorerUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Source, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Git", fontWeight = FontWeight.SemiBold)
                        Text(
                            state.vcsInfo?.branch?.let { "ブランチ: $it" } ?: "Gitリポジトリ情報なし",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text("${state.changes.size}件")
                }
            }
        }

        if (state.isLoadingChanges) {
            item { LoadingCard("変更を取得しています") }
        } else if (state.changes.isEmpty()) {
            item {
                SectionCard {
                    Text("未コミットの変更はありません", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(state.changes, key = { it.displayPath }) { change ->
                val detailed = state.diff.firstOrNull { it.displayPath == change.displayPath } ?: change
                ChangeCard(detailed)
            }
        }
        state.error?.let { item { ErrorCard(it) } }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun ChangeCard(change: OpenCodeFileChange) {
    var expanded by remember(change.displayPath, change.patch) { mutableStateOf(false) }
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Column(modifier = Modifier.weight(1f)) {
                Text(change.displayPath, fontWeight = FontWeight.SemiBold)
                Text(
                    "+${change.additions.toInt()}  -${change.deletions.toInt()}  ${change.status.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!change.patch.isNullOrBlank()) {
                OutlinedButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "閉じる" else "差分")
                }
            }
        }
        if (expanded && !change.patch.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            SelectionContainer {
                Text(
                    change.patch,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, count: Int) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text("${count}件", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoadingCard(text: String) {
    SectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator()
            Text(text)
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    SectionCard {
        Text("取得に失敗しました", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
