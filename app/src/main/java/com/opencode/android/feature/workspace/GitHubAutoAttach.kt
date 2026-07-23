package com.opencode.android.feature.workspace

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class GitHubReference(
    val type: String,
    val number: Int,
    val title: String,
    val url: String
)

@Composable
fun GitHubAutoAttachChips(
    references: List<GitHubReference>,
    onAttach: (GitHubReference) -> Unit,
    modifier: Modifier = Modifier
) {
    if (references.isEmpty()) return

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        references.forEach { reference ->
            SuggestionChip(
                onClick = { onAttach(reference) },
                label = {
                    Text(
                        "\uD83D\uDCCE ${reference.type} #${reference.number}: ${reference.title}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            )
        }
    }
}
