package com.opencode.android.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.opencode.android.R
import com.opencode.android.runtime.LocalRuntimeStatus
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

/** Guided four-step setup. Only the current step is expanded. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidSetupScreen(
    runtimeStatus: LocalRuntimeStatus,
    onStartRuntimeSetup: () -> Unit,
    onSaveApiKey: (providerId: String, apiKey: String) -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    val runtimeReady = runtimeStatus is LocalRuntimeStatus.Ready || runtimeStatus is LocalRuntimeStatus.Stopped
    var currentStep by remember { mutableIntStateOf(if (runtimeReady) 2 else 1) }
    var selectedProviderId by remember { mutableStateOf(SETUP_PROVIDERS.first().id) }
    var apiKey by remember { mutableStateOf("") }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var testState by remember { mutableStateOf(LocalTestState.UNTESTED) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(runtimeReady) {
        if (runtimeReady && currentStep == 1) currentStep = 2
    }

    val primaryAction: SetupPrimaryAction? = when (currentStep) {
        1 -> when (runtimeStatus) {
            LocalRuntimeStatus.NotInstalled,
            is LocalRuntimeStatus.Broken -> SetupPrimaryAction(
                label = stringResource(R.string.setup_this_device_button),
                enabled = true,
                onClick = onStartRuntimeSetup
            )
            is LocalRuntimeStatus.UnsupportedAbi,
            is LocalRuntimeStatus.Installing,
            is LocalRuntimeStatus.Starting,
            is LocalRuntimeStatus.Updating -> null
            is LocalRuntimeStatus.Ready,
            is LocalRuntimeStatus.Stopped -> SetupPrimaryAction(
                label = stringResource(R.string.setup_next_action),
                enabled = true,
                onClick = { currentStep = 2 }
            )
        }
        2 -> SetupPrimaryAction(
            label = stringResource(R.string.setup_next_action),
            enabled = true,
            onClick = { currentStep = 3 }
        )
        3 -> SetupPrimaryAction(
            label = stringResource(R.string.setup_save_and_continue_action),
            enabled = apiKey.isNotBlank(),
            onClick = {
                onSaveApiKey(selectedProviderId, apiKey)
                testState = LocalTestState.UNTESTED
                currentStep = 4
            }
        )
        else -> if (testState == LocalTestState.SUCCESS) {
            SetupPrimaryAction(
                label = stringResource(R.string.setup_complete_button),
                enabled = true,
                onClick = onFinish
            )
        } else {
            SetupPrimaryAction(
                label = stringResource(R.string.run_connection_test_button),
                enabled = runtimeReady && apiKey.isNotBlank() && testState != LocalTestState.TESTING,
                onClick = {
                    scope.launch {
                        testState = LocalTestState.TESTING
                        delay(500)
                        testState = if (runtimeReady && apiKey.isNotBlank()) {
                            LocalTestState.SUCCESS
                        } else {
                            LocalTestState.FAILURE
                        }
                    }
                }
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.android_setup_title),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (currentStep > 1) currentStep -= 1 else onBack()
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            if (primaryAction != null) {
                SetupBottomBar(
                    currentStep = currentStep,
                    primaryAction = primaryAction,
                    onBackStep = { currentStep -= 1 }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SetupProgress(currentStep = currentStep)
            when (currentStep) {
                1 -> RuntimeDownloadStep(runtimeStatus)
                2 -> ProviderStep(
                    selectedProviderId = selectedProviderId,
                    onSelect = { selectedProviderId = it }
                )
                3 -> ApiKeyStep(
                    apiKey = apiKey,
                    apiKeyVisible = apiKeyVisible,
                    onApiKeyChange = {
                        apiKey = it
                        testState = LocalTestState.UNTESTED
                    },
                    onToggleVisibility = { apiKeyVisible = !apiKeyVisible }
                )
                else -> ConnectionTestStep(testState = testState)
            }
        }
    }
}

private data class SetupPrimaryAction(
    val label: String,
    val enabled: Boolean,
    val onClick: () -> Unit
)

@Composable
private fun SetupProgress(currentStep: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.setup_step_counter, currentStep, 4),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            (1..4).forEach { step ->
                val completed = step < currentStep
                val active = step == currentStep
                Surface(
                    modifier = Modifier.size(30.dp),
                    shape = CircleShape,
                    color = when {
                        active -> MaterialTheme.colorScheme.primary
                        completed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = when {
                        active -> MaterialTheme.colorScheme.onPrimary
                        completed -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (completed) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(17.dp))
                        } else {
                            Text(step.toString(), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                if (step < 4) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                            .height(1.dp)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = if (step < currentStep) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                            }
                        ) {}
                    }
                }
            }
        }
    }
}

@Composable
private fun StepHeader(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RuntimeDownloadStep(runtimeStatus: LocalRuntimeStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        StepHeader(
            title = stringResource(R.string.setup_step_download),
            description = stringResource(R.string.setup_download_description)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (runtimeStatus) {
                    LocalRuntimeStatus.NotInstalled -> {
                        Text(
                            stringResource(R.string.setup_runtime_not_installed),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is LocalRuntimeStatus.Installing -> {
                        Text(runtimeStatus.step, fontWeight = FontWeight.Medium)
                        if (runtimeStatus.progress != null) {
                            LinearProgressIndicator(
                                progress = { runtimeStatus.progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "${(runtimeStatus.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    is LocalRuntimeStatus.Starting -> {
                        Text(stringResource(R.string.starting_opencode_version, runtimeStatus.version))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    is LocalRuntimeStatus.Updating -> {
                        Text(runtimeStatus.step)
                        LinearProgressIndicator(
                            progress = { runtimeStatus.progress?.coerceIn(0f, 1f) ?: 0f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    is LocalRuntimeStatus.Ready -> ReadyRuntimeRow(runtimeStatus.version)
                    is LocalRuntimeStatus.Stopped -> ReadyRuntimeRow(runtimeStatus.version)
                    is LocalRuntimeStatus.Broken -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(runtimeStatus.reason, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    is LocalRuntimeStatus.UnsupportedAbi -> {
                        Text(
                            stringResource(R.string.unsupported_abi, runtimeStatus.abi),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadyRuntimeRow(version: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(stringResource(R.string.setup_runtime_ready), fontWeight = FontWeight.Medium)
            Text(
                text = "OpenCode $version",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProviderStep(selectedProviderId: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        StepHeader(
            title = stringResource(R.string.setup_step_provider),
            description = stringResource(R.string.setup_provider_description)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.height(190.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            userScrollEnabled = false
        ) {
            items(SETUP_PROVIDERS, key = { it.id }) { provider ->
                ProviderTile(
                    label = stringResource(provider.labelRes),
                    selected = provider.id == selectedProviderId,
                    onClick = { onSelect(provider.id) }
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
            .height(56.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
private fun ApiKeyStep(
    apiKey: String,
    apiKeyVisible: Boolean,
    onApiKeyChange: (String) -> Unit,
    onToggleVisibility: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        StepHeader(
            title = stringResource(R.string.setup_step_apikey),
            description = stringResource(R.string.setup_api_key_description)
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.api_key_hint)) },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = onToggleVisibility) {
                    Icon(
                        if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null
                    )
                }
            },
            visualTransformation = if (apiKeyVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(14.dp)
        )
        Text(
            stringResource(R.string.apikey_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConnectionTestStep(testState: LocalTestState) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        StepHeader(
            title = stringResource(R.string.setup_step_test),
            description = stringResource(R.string.setup_test_description)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (testState) {
                    LocalTestState.TESTING -> CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    LocalTestState.SUCCESS -> Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    LocalTestState.FAILURE -> Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    LocalTestState.UNTESTED -> Icon(
                        Icons.Default.NetworkCheck,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column {
                    Text(
                        text = when (testState) {
                            LocalTestState.UNTESTED -> stringResource(R.string.test_status_untested)
                            LocalTestState.TESTING -> stringResource(R.string.test_status_testing)
                            LocalTestState.SUCCESS -> stringResource(R.string.test_status_success)
                            LocalTestState.FAILURE -> stringResource(R.string.test_status_failure)
                        },
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.setup_test_caption),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupBottomBar(
    currentStep: Int,
    primaryAction: SetupPrimaryAction?,
    onBackStep: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (currentStep > 1) {
                OutlinedButton(
                    onClick = onBackStep,
                    modifier = Modifier.width(96.dp)
                ) {
                    Text(stringResource(R.string.setup_back_action))
                }
            }
            if (primaryAction != null) {
                Button(
                    onClick = primaryAction.onClick,
                    enabled = primaryAction.enabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(primaryAction.label, textAlign = TextAlign.Center)
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AndroidSetupScreenPreview() {
    OpenCodeAndroidTheme {
        AndroidSetupScreen(
            runtimeStatus = LocalRuntimeStatus.Installing(0.68f, "ランタイムをダウンロード中"),
            onStartRuntimeSetup = {},
            onSaveApiKey = { _, _ -> },
            onBack = {},
            onFinish = {}
        )
    }
}
