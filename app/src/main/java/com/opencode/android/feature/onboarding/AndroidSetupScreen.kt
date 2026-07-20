package com.opencode.android.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.opencode.android.R
import com.opencode.android.runtime.LocalRuntimeStatus
import com.opencode.android.ui.components.SectionCard
import com.opencode.android.ui.theme.OpenCodeAndroidTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class SetupProvider(val id: String, val labelRes: Int)

private val SETUP_PROVIDERS = listOf(
    SetupProvider("openai", R.string.provider_openai),
    SetupProvider("google", R.string.provider_google),
    SetupProvider("anthropic", R.string.provider_anthropic),
    SetupProvider("ollama", R.string.provider_ollama),
    SetupProvider("other", R.string.provider_other)
)

private enum class LocalTestState { UNTESTED, TESTING, SUCCESS, FAILURE }

/**
 * Guided setup for running OpenCode directly on this Android device: download the
 * managed runtime, choose an AI provider, store its API key, then run a connection
 * test. Step 1 is wired to [onStartRuntimeSetup] (WorkspaceViewModel.setupLocalRuntime)
 * and reflects [runtimeStatus] (LocalRuntimeManager.state). Step 3 saves the key via
 * [onSaveApiKey] (SettingsViewModel draft + saveApiKey). Step 4's connection test is a
 * TODO placeholder — see comment below — since there is no dedicated "verify provider
 * credentials" backend call yet.
 */
@Composable
fun AndroidSetupScreen(
    runtimeStatus: LocalRuntimeStatus,
    onStartRuntimeSetup: () -> Unit,
    onSaveApiKey: (providerId: String, apiKey: String) -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    var selectedProviderId by remember { mutableStateOf(SETUP_PROVIDERS.first().id) }
    var apiKey by remember { mutableStateOf("") }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var testState by remember { mutableStateOf(LocalTestState.UNTESTED) }
    val scope = rememberCoroutineScope()

    val runtimeReady = runtimeStatus is LocalRuntimeStatus.Ready || runtimeStatus is LocalRuntimeStatus.Stopped
    val currentStep = when {
        !runtimeReady -> 1
        apiKey.isBlank() -> 3
        testState == LocalTestState.UNTESTED -> 4
        else -> 4
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.android_setup_title)) },
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
            StepIndicator(currentStep = currentStep)

            SectionCard {
                Text(
                    "1. " + stringResource(R.string.setup_step_download),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                when (val status = runtimeStatus) {
                    LocalRuntimeStatus.NotInstalled -> {
                        Button(onClick = onStartRuntimeSetup, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.setup_this_device_button))
                        }
                    }
                    is LocalRuntimeStatus.Installing -> {
                        Text(status.step, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        if (status.progress != null) {
                            LinearProgressIndicator(
                                progress = { status.progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    is LocalRuntimeStatus.Starting -> {
                        Text(stringResource(R.string.starting_opencode_version, status.version))
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    is LocalRuntimeStatus.Ready -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.ready_running, status.version))
                        }
                    }
                    is LocalRuntimeStatus.Stopped -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.installed_stopped, status.version))
                        }
                    }
                    is LocalRuntimeStatus.Broken -> {
                        Text(status.reason, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onStartRuntimeSetup, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.repair_and_resetup_button))
                        }
                    }
                    is LocalRuntimeStatus.UnsupportedAbi -> {
                        Text(stringResource(R.string.unsupported_abi, status.abi), color = MaterialTheme.colorScheme.error)
                    }
                    is LocalRuntimeStatus.Updating -> {
                        Text(status.step, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            SectionCard {
                Text(
                    "2. " + stringResource(R.string.setup_step_provider),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.height(160.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(SETUP_PROVIDERS, key = { it.id }) { provider ->
                        ProviderTile(
                            label = stringResource(provider.labelRes),
                            selected = provider.id == selectedProviderId,
                            onClick = { selectedProviderId = provider.id }
                        )
                    }
                }
            }

            SectionCard {
                Text(
                    "3. " + stringResource(R.string.setup_step_apikey),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        testState = LocalTestState.UNTESTED
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.api_key_hint)) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                            Icon(
                                if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.apikey_caption),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { onSaveApiKey(selectedProviderId, apiKey) },
                    enabled = apiKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.save_api_key))
                }
            }

            SectionCard {
                Text(
                    "4. " + stringResource(R.string.setup_step_test),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                TestStatusRow(testState)
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        // TODO: replace with a real provider connectivity check (e.g. a
                        // lightweight models-list call through the local runtime) once
                        // that endpoint is exposed. For now this simulates a check based
                        // on whether an API key has been entered.
                        scope.launch {
                            testState = LocalTestState.TESTING
                            delay(700)
                            testState = if (apiKey.isNotBlank()) LocalTestState.SUCCESS else LocalTestState.FAILURE
                        }
                    },
                    enabled = testState != LocalTestState.TESTING,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.run_connection_test_button))
                }
            }

            Button(
                onClick = onFinish,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(stringResource(R.string.setup_complete_button), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StepIndicator(currentStep: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        (1..4).forEach { step ->
            val active = step <= currentStep
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Text(
                    text = step.toString(),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ProviderTile(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun TestStatusRow(state: LocalTestState) {
    val (icon, label, color) = when (state) {
        LocalTestState.UNTESTED -> Triple(Icons.Default.NetworkCheck, stringResource(R.string.test_status_untested), MaterialTheme.colorScheme.onSurfaceVariant)
        LocalTestState.TESTING -> Triple(null, stringResource(R.string.test_status_testing), MaterialTheme.colorScheme.onSurfaceVariant)
        LocalTestState.SUCCESS -> Triple(Icons.Default.CheckCircle, stringResource(R.string.test_status_success), MaterialTheme.colorScheme.primary)
        LocalTestState.FAILURE -> Triple(Icons.Default.Error, stringResource(R.string.test_status_failure), MaterialTheme.colorScheme.error)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (state == LocalTestState.TESTING) {
            CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = color)
        }
        Spacer(Modifier.width(8.dp))
        Text(label, color = color)
    }
}

@Preview(showBackground = true)
@Composable
private fun AndroidSetupScreenPreview() {
    OpenCodeAndroidTheme {
        AndroidSetupScreen(
            runtimeStatus = LocalRuntimeStatus.NotInstalled,
            onStartRuntimeSetup = {},
            onSaveApiKey = { _, _ -> },
            onBack = {},
            onFinish = {}
        )
    }
}
