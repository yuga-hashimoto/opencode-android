package com.opencode.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.opencode.android.R
import com.opencode.android.core.api.OpenCodeProvider
import com.opencode.android.core.api.ProviderAuthMethod
import com.opencode.android.ui.components.LabelValueRow
import com.opencode.android.ui.components.SectionCard
import com.opencode.android.ui.theme.OpenCodeAndroidTheme

/** Provider authentication driven entirely by the selected OpenCode runtime. */
@Composable
fun ProviderSettingsScreen(
    state: SettingsUiState,
    onOpenProviderAuth: (String) -> Unit,
    onSelectProviderAuthMethod: (Int) -> Unit,
    onProviderAuthInput: (String, String) -> Unit,
    onProviderApiKey: (String) -> Unit,
    onSubmitProviderAuth: () -> Unit,
    onCompleteProviderOAuth: (String) -> Unit,
    onDisconnectProvider: (String) -> Unit,
    onLaunchOAuthBrowser: (String) -> Unit,
    onDismissProviderAuth: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.provider_settings_row)) },
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard {
                Text(
                    text = stringResource(R.string.provider_credentials_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                if (state.providerAuthMethods.isEmpty()) {
                    Text(
                        stringResource(R.string.provider_auth_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                state.providerAuthMethods.toSortedMap().forEach { (providerId, methods) ->
                    val providerName = state.availableProviders
                        .firstOrNull { it.id == providerId }
                        ?.name
                        ?: providerId
                    val connected = providerId in state.connectedProviderIds

                    LabelValueRow(
                        label = providerName,
                        value = if (connected) {
                            stringResource(R.string.provider_connected)
                        } else {
                            stringResource(R.string.provider_not_connected)
                        }
                    )
                    Text(
                        text = methods.joinToString(" · ") { it.label },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onOpenProviderAuth(providerId) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (connected) {
                                stringResource(R.string.provider_change_connection)
                            } else {
                                stringResource(R.string.provider_connect)
                            }
                        )
                    }
                    if (connected) {
                        TextButton(
                            onClick = { onDisconnectProvider(providerId) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.provider_disconnect))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                state.providerAuthNotice?.let { notice ->
                    Text(
                        text = stringResource(
                            when (notice) {
                                ProviderAuthNotice.CONNECTED -> R.string.provider_connected_success
                                ProviderAuthNotice.DISCONNECTED -> R.string.provider_disconnected_success
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                state.oauthMessage?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    state.providerAuthDialog?.let { dialog ->
        ProviderAuthDialog(
            state = dialog,
            onSelectMethod = onSelectProviderAuthMethod,
            onInputChange = onProviderAuthInput,
            onApiKeyChange = onProviderApiKey,
            onSubmit = onSubmitProviderAuth,
            onCompleteCode = onCompleteProviderOAuth,
            onLaunchBrowser = onLaunchOAuthBrowser,
            onDismiss = onDismissProviderAuth
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProviderSettingsScreenPreview() {
    OpenCodeAndroidTheme {
        ProviderSettingsScreen(
            state = SettingsUiState(
                availableProviders = listOf(OpenCodeProvider(id = "openai", name = "OpenAI")),
                providerAuthMethods = mapOf(
                    "openai" to listOf(
                        ProviderAuthMethod(type = "oauth", label = "ChatGPT Plus/Pro"),
                        ProviderAuthMethod(type = "api", label = "API key")
                    )
                )
            ),
            onOpenProviderAuth = {},
            onSelectProviderAuthMethod = {},
            onProviderAuthInput = { _, _ -> },
            onProviderApiKey = {},
            onSubmitProviderAuth = {},
            onCompleteProviderOAuth = {},
            onDisconnectProvider = {},
            onLaunchOAuthBrowser = {},
            onDismissProviderAuth = {},
            onBack = {}
        )
    }
}
