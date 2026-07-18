package com.opencode.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencode.android.R
import com.opencode.android.ui.AppUiState
import com.opencode.android.ui.components.LabelValueRow
import com.opencode.android.ui.components.SectionCard

@Composable
fun SettingsScreen(
    state: AppUiState,
    onOpenAssistantSettings: () -> Unit,
    onHotwordChange: (Boolean) -> Unit,
    onWakeWordChange: (String) -> Unit,
    onTtsChange: (Boolean) -> Unit,
    onContinuousChange: (Boolean) -> Unit
) {
    var wakeWordDraft by remember(state.wakeWord) { mutableStateOf(state.wakeWord) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.nav_settings),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            Text(
                text = stringResource(R.string.home_assistant),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(Icons.Default.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.set_default_assistant), fontWeight = FontWeight.Medium)
                        Text(
                            text = "ホームジェスチャーや電源ボタン長押しからOpenCodeを呼び出します。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onOpenAssistantSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.set_default_assistant))
                }
            }
        }

        item {
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.padding(horizontal = 7.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.wake_word_detection), fontWeight = FontWeight.Medium)
                        Text(
                            "マイクを使用するForeground Serviceとして動作します。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = state.hotwordEnabled, onCheckedChange = onHotwordChange)
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = wakeWordDraft,
                    onValueChange = { wakeWordDraft = it },
                    label = { Text(stringResource(R.string.wake_word_phrase)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("例: open code") }
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onWakeWordChange(wakeWordDraft) },
                    enabled = wakeWordDraft.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.voice),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            SectionCard {
                SettingSwitchRow(
                    icon = Icons.Default.RecordVoiceOver,
                    title = stringResource(R.string.voice_response),
                    description = "OpenCodeの最終回答をAndroidの音声で読み上げます。",
                    checked = state.ttsEnabled,
                    onCheckedChange = onTtsChange
                )
                Spacer(Modifier.height(16.dp))
                SettingSwitchRow(
                    icon = Icons.Default.Mic,
                    title = stringResource(R.string.continuous_conversation),
                    description = stringResource(R.string.auto_start_mic),
                    checked = state.continuousConversation,
                    onCheckedChange = onContinuousChange
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.model),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            SectionCard {
                LabelValueRow(
                    label = "Provider",
                    value = state.selectedProviderId ?: "Not selected"
                )
                Spacer(Modifier.height(12.dp))
                LabelValueRow(
                    label = stringResource(R.string.model),
                    value = state.selectedModelId ?: "Default"
                )
                Spacer(Modifier.height(12.dp))
                LabelValueRow(
                    label = stringResource(R.string.agent),
                    value = state.selectedAgentId ?: "build"
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "モデルとエージェントはチャット画面で選択できます。ホームアシストも同じ選択を使用します。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.local_runtime),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            SectionCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Android, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Column {
                        Text(stringResource(R.string.experimental_not_installed), fontWeight = FontWeight.Medium)
                        Text(
                            stringResource(R.string.local_runtime_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.about),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            SectionCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("OpenCode Android 0.1.0", fontWeight = FontWeight.Medium)
                        Text(
                            stringResource(R.string.unofficial_client),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "MIT License",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
