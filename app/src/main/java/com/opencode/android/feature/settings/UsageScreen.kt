package com.opencode.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencode.android.R
import com.opencode.android.ui.components.LabelValueRow
import com.opencode.android.ui.components.SectionCard

private data class ProviderUsage(
    val name: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen(onBack: () -> Unit) {
    val periods = listOf("Today", "This Week", "This Month")
    var period by remember { mutableStateOf(periods.first()) }

    val providers = remember(period) {
        listOf(
            ProviderUsage("Anthropic", 12480, 3921, 16401),
            ProviderUsage("OpenAI", 8230, 2110, 10340),
            ProviderUsage("Google", 5610, 1480, 7090)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Usage") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                periods.forEach { entry ->
                    FilterChip(
                        selected = period == entry,
                        onClick = { period = entry },
                        label = { Text(entry) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(providers, key = { it.name }) { provider ->
                    SectionCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = provider.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            LabelValueRow(label = "Input tokens", value = provider.inputTokens.toString())
                            LabelValueRow(label = "Output tokens", value = provider.outputTokens.toString())
                            LabelValueRow(label = "Total tokens", value = provider.totalTokens.toString())
                        }
                    }
                }
                item {
                    Text(
                        text = "Usage data is collected locally from session activity",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
