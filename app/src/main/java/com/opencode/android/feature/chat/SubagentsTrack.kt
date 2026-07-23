package com.opencode.android.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opencode.android.ui.components.ProviderIcon
import com.opencode.android.ui.components.SessionStatus
import com.opencode.android.ui.components.StatusDot

data class SubagentInfo(
    val id: String,
    val name: String,
    val status: String,
    val providerId: String
)

@Composable
fun SubagentsTrack(
    subagents: List<SubagentInfo>,
    onSubagentClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (subagents.isEmpty()) return

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(subagents, key = { it.id }) { subagent ->
            Surface(
                onClick = { onSubagentClick(subagent.id) },
                shape = RoundedCornerShape(100.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusDot(status = subagent.status.toSessionStatus())
                    Text(
                        text = subagent.name,
                        style = MaterialTheme.typography.labelLarge
                    )
                    ProviderIcon(providerId = subagent.providerId, size = 16)
                }
            }
        }
    }
}

private fun String.toSessionStatus(): SessionStatus = when (lowercase()) {
    "running" -> SessionStatus.RUNNING
    "waiting" -> SessionStatus.WAITING
    "error" -> SessionStatus.ERROR
    "permission" -> SessionStatus.PERMISSION
    else -> SessionStatus.IDLE
}
