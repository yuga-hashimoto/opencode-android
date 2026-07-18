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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencode.android.R
import com.opencode.android.ui.AppUiState
import com.opencode.android.ui.components.OpenCodeBrand
import com.opencode.android.ui.components.SectionCard
import com.opencode.android.ui.components.StatusChip
import com.opencode.android.ui.theme.OpenCodeSuccess
import com.opencode.android.ui.theme.OpenCodeWarning

@Composable
fun HomeScreen(
    state: AppUiState,
    onNewChat: () -> Unit,
    onOpenConnections: () -> Unit,
    onOpenSessions: () -> Unit,
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

        item {
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = if (state.health?.healthy == true) Icons.Default.CheckCircle else Icons.Default.Computer,
                        contentDescription = null,
                        tint = if (state.health?.healthy == true) OpenCodeSuccess else MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.selectedConnection?.name ?: stringResource(R.string.connection_missing),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = when {
                                state.health?.healthy == true -> stringResource(
                                    R.string.connected_version,
                                    state.health.version
                                )
                                state.backend != null && state.isRefreshing -> stringResource(R.string.processing)
                                state.backend != null -> state.error ?: stringResource(R.string.connection_missing)
                                else -> stringResource(R.string.unofficial_client)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusChip(
                        text = if (state.health?.healthy == true) stringResource(R.string.active) else stringResource(R.string.not_set),
                        active = state.health?.healthy == true
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
                    enabled = state.backend != null
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(stringResource(R.string.new_chat))
                }
                FilledTonalButton(
                    onClick = onOpenConnections,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.AddLink, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(stringResource(R.string.nav_connections))
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.remote_runtime),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            SectionCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(Icons.Default.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.selectedConnection?.name ?: stringResource(R.string.connection_missing),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = state.selectedConnection?.baseUrl ?: stringResource(R.string.connection_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = OpenCodeWarning.copy(alpha = 0.08f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(Icons.Default.Android, contentDescription = null, tint = OpenCodeWarning)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.experimental_not_installed), fontWeight = FontWeight.Medium)
                        Text(
                            text = stringResource(R.string.local_runtime_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                    text = stringResource(R.string.recent_sessions),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                androidx.compose.material3.TextButton(onClick = onOpenSessions) {
                    Text(stringResource(R.string.view_all))
                }
            }
        }

        if (state.sessions.isEmpty()) {
            item {
                SectionCard {
                    Text(
                        text = stringResource(R.string.no_sessions),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(state.sessions.take(4), key = { it.id }) { session ->
                SectionCard(
                    modifier = Modifier.clickable {
                        onOpenSession(session.id, session.title)
                    }
                ) {
                    Text(
                        text = session.title.ifBlank { session.slug ?: session.id },
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = session.directory.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
