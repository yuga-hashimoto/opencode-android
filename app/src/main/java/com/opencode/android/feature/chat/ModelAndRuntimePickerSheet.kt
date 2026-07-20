package com.opencode.android.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencode.android.R
import com.opencode.android.core.api.OpenCodeModel
import com.opencode.android.core.api.OpenCodeProvider
import com.opencode.android.runtime.RuntimeTarget
import com.opencode.android.runtime.RuntimeType

/**
 * Bottom sheet opened from the chat screen's model chip. Lets the user pick both
 * the execution target (this Android device or a registered remote runtime) and
 * the model to use from the currently selected provider's catalog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelAndRuntimePickerSheet(
    sheetState: SheetState,
    runtimeTargets: List<RuntimeTarget>,
    selectedRuntimeId: String?,
    onSelectRuntime: (String) -> Unit,
    providers: List<OpenCodeProvider>,
    selectedProviderId: String?,
    selectedModelId: String?,
    onSelectModel: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Column {
                    Text(
                        text = stringResource(R.string.picker_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.picker_caption),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SheetSectionHeader(stringResource(R.string.section_runtime))
            }
            items(runtimeTargets, key = { "runtime-${it.id}" }) { target ->
                RuntimeRow(
                    target = target,
                    selected = target.id == selectedRuntimeId,
                    onClick = { onSelectRuntime(target.id) }
                )
            }

            item {
                Column {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    SheetSectionHeader(stringResource(R.string.section_model))
                }
            }

            if (providers.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.picker_no_providers),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                providers.forEach { provider ->
                    val models = provider.models.values
                        .filter { it.status == null || it.status == "active" }
                        .sortedBy { it.name.lowercase() }
                    if (models.isNotEmpty()) {
                        item {
                            Text(
                                text = provider.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(models, key = { "model-${provider.id}-${it.id}" }) { model ->
                            ModelRow(
                                model = model,
                                selected = provider.id == selectedProviderId && model.id == selectedModelId,
                                onClick = { onSelectModel(provider.id, model.id) }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SheetSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun RuntimeRow(
    target: RuntimeTarget,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = if (target.type == RuntimeType.LOCAL) Icons.Default.Android else Icons.Default.Computer,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = if (target.type == RuntimeType.LOCAL) {
                stringResource(R.string.this_android)
            } else {
                target.displayName
            },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        RadioButton(selected = selected, onClick = onClick)
    }
}

@Composable
private fun ModelRow(
    model: OpenCodeModel,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(model.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(
                model.id,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}
