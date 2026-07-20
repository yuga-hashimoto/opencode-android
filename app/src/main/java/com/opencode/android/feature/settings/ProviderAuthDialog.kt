package com.opencode.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.opencode.android.R

@Composable
fun ProviderAuthDialog(
    state: ProviderAuthDialogState,
    onSelectMethod: (Int) -> Unit,
    onInputChange: (String, String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCompleteCode: (String) -> Unit,
    onLaunchBrowser: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val authorization = state.authorization
    var code by remember(authorization?.url) { mutableStateOf("") }

    LaunchedEffect(authorization?.url) {
        authorization?.url?.takeIf(String::isNotBlank)?.let(onLaunchBrowser)
    }

    AlertDialog(
        onDismissRequest = { if (!state.isSubmitting) onDismiss() },
        title = { Text(stringResource(R.string.provider_connect_title, state.providerName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    authorization != null -> {
                        Text(authorization.instructions)
                        confirmationCode(authorization.instructions)?.let { confirmation ->
                            Text(
                                stringResource(R.string.provider_confirmation_code),
                                style = MaterialTheme.typography.labelMedium
                            )
                            SelectionContainer {
                                Text(confirmation, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        if (authorization.method == "code") {
                            OutlinedTextField(
                                value = code,
                                onValueChange = { code = it },
                                label = { Text(stringResource(R.string.provider_confirmation_code)) },
                                singleLine = true,
                                enabled = !state.isSubmitting,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                stringResource(R.string.provider_waiting_for_browser),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    state.methodIndex == null -> {
                        Text(stringResource(R.string.provider_select_method))
                        state.methods.forEachIndexed { index, method ->
                            OutlinedButton(
                                onClick = { onSelectMethod(index) },
                                enabled = !state.isSubmitting,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(method.label)
                            }
                        }
                    }
                    else -> {
                        val method = state.selectedMethod
                        state.visiblePrompts.forEach { prompt ->
                            when (prompt.type) {
                                "select" -> {
                                    Text(prompt.message, style = MaterialTheme.typography.labelLarge)
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        prompt.options.forEach { option ->
                                            FilterChip(
                                                selected = state.inputs[prompt.key] == option.value,
                                                onClick = { onInputChange(prompt.key, option.value) },
                                                label = {
                                                    Column {
                                                        Text(option.label)
                                                        option.hint?.takeIf(String::isNotBlank)?.let { hint ->
                                                            Text(hint, style = MaterialTheme.typography.labelSmall)
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                                else -> OutlinedTextField(
                                    value = state.inputs[prompt.key].orEmpty(),
                                    onValueChange = { onInputChange(prompt.key, it) },
                                    label = { Text(prompt.message) },
                                    placeholder = prompt.placeholder?.let { value -> { Text(value) } },
                                    singleLine = true,
                                    enabled = !state.isSubmitting,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        if (method?.type == "api") {
                            OutlinedTextField(
                                value = state.apiKey,
                                onValueChange = onApiKeyChange,
                                label = { Text(stringResource(R.string.api_key_hint)) },
                                singleLine = true,
                                enabled = !state.isSubmitting,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                if (state.failed) {
                    Text(
                        state.error ?: stringResource(R.string.provider_auth_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (state.isSubmitting) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                        Text(stringResource(R.string.provider_auth_in_progress))
                    }
                }
            }
        },
        confirmButton = {
            when {
                authorization?.method == "code" -> Button(
                    enabled = code.isNotBlank() && !state.isSubmitting,
                    onClick = { onCompleteCode(code) }
                ) {
                    Text(stringResource(R.string.continue_label))
                }
                authorization != null -> Unit
                state.methodIndex != null -> Button(
                    enabled = state.promptsComplete &&
                        (state.selectedMethod?.type != "api" || state.apiKey.isNotBlank()) &&
                        !state.isSubmitting,
                    onClick = onSubmit
                ) {
                    Text(stringResource(R.string.continue_label))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isSubmitting) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

internal fun confirmationCode(instructions: String): String? {
    val marker = instructions.substringAfterLast(':', missingDelimiterValue = "").trim()
    return marker.takeIf { candidate ->
        candidate.isNotBlank() && candidate.length <= 32 && candidate.none(Char::isWhitespace)
    }
}
