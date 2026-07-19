package com.opencode.android.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.opencode.android.R
import com.opencode.android.core.api.OpenCodeModel
import com.opencode.android.core.api.OpenCodeProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    providers: List<OpenCodeProvider>,
    recentModels: List<Pair<String, String>>,
    onSelect: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_models)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            if (query.isBlank() && recentModels.isNotEmpty()) {
                item(key = "recent-header") {
                    Text(
                        stringResource(R.string.recent_models),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }
                items(recentModels, key = { "recent-${it.first}-${it.second}" }) { (providerId, modelId) ->
                    val provider = providers.firstOrNull { it.id == providerId }
                    val model = provider?.models?.get(modelId)
                    if (provider != null && model != null) {
                        ModelRow(provider, model) {
                            onSelect(providerId, modelId)
                            onDismiss()
                        }
                    }
                }
            }
            providers.forEach { provider ->
                val models = provider.models.values
                    .filter { it.status == null || it.status == "active" }
                    .filter {
                        query.isBlank() ||
                            it.name.contains(query, ignoreCase = true) ||
                            it.id.contains(query, ignoreCase = true)
                    }
                    .sortedBy { it.name.lowercase() }
                if (models.isNotEmpty()) {
                    item(key = "header-${provider.id}") {
                        Text(
                            provider.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                        )
                    }
                    items(models, key = { "model-${provider.id}-${it.id}" }) { model ->
                        ModelRow(provider, model) {
                            onSelect(provider.id, model.id)
                            onDismiss()
                        }
                    }
                }
            }
            if (providers.all { provider ->
                    provider.models.values.none {
                        query.isBlank() || it.name.contains(query, ignoreCase = true) || it.id.contains(query, ignoreCase = true)
                    }
                }
            ) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.no_models_found),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelRow(provider: OpenCodeProvider, model: OpenCodeModel, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(model.name) },
        supportingContent = {
            Text("${provider.name} · ${model.id}", style = MaterialTheme.typography.labelSmall)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    )
}
