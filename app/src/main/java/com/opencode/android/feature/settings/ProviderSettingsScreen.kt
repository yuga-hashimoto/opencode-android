package com.opencode.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.opencode.android.R
import com.opencode.android.ui.components.LabelValueRow
import com.opencode.android.ui.components.SectionCard
import com.opencode.android.ui.theme.OpenCodeAndroidTheme

/**
 * Provider / API-key management detail screen. Mirrors the provider credential
 * section that used to live inline in the old SettingsScreen.kt, wired to the same
 * SettingsViewModel draft + save/clear API key actions.
 */
@Composable
fun ProviderSettingsScreen(
    credentialStatuses: Map<String, Boolean>,
    draftProviderId: String,
    draftApiKey: String,
    credentialMessage: String?,
    onDraftProviderId: (String) -> Unit,
    onDraftApiKey: (String) -> Unit,
    onSaveApiKey: () -> Unit,
    onClearApiKey: (String) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.provider_settings_row)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
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
                Spacer(Modifier.height(8.dp))
                credentialStatuses.forEach { (providerId, saved) ->
                    LabelValueRow(
                        label = providerId,
                        value = if (saved) stringResource(R.string.key_saved) else stringResource(R.string.key_missing)
                    )
                    if (saved) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onClearApiKey(providerId) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.clear_api_key))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = draftProviderId,
                    onValueChange = onDraftProviderId,
                    label = { Text(stringResource(R.string.provider_id_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = draftApiKey,
                    onValueChange = onDraftApiKey,
                    label = { Text(stringResource(R.string.api_key_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onSaveApiKey, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.save_api_key))
                }
                credentialMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProviderSettingsScreenPreview() {
    OpenCodeAndroidTheme {
        ProviderSettingsScreen(
            credentialStatuses = mapOf("openai" to true, "anthropic" to false),
            draftProviderId = "",
            draftApiKey = "",
            credentialMessage = null,
            onDraftProviderId = {},
            onDraftApiKey = {},
            onSaveApiKey = {},
            onClearApiKey = {},
            onBack = {}
        )
    }
}
