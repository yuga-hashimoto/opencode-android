package com.opencode.android.feature.workspace

import android.content.Context
import android.net.nsd.NsdManager
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.opencode.android.R
import com.opencode.android.core.api.OpenCodeHealth
import com.opencode.android.core.security.ConnectionQrPayload
import com.opencode.android.ui.components.SectionCard
import com.opencode.android.ui.theme.OpenCodeAndroidTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Dedicated "connect to PC/Mac" screen used both from first-run onboarding and from
 * the drawer's "リモート接続" entry. Reuses [ConnectionFormState] plus
 * WorkspaceViewModel.testConnection/saveConnection and the QR/LAN discovery helpers
 * already used by ConnectionDialog.kt.
 */
@Composable
fun RemoteConnectionScreen(
    onTestConnection: suspend (ConnectionFormState) -> Result<OpenCodeHealth>,
    onSaveConnection: (ConnectionFormState) -> Unit,
    onBack: () -> Unit,
    onConnected: () -> Unit
) {
    var form by remember { mutableStateOf(ConnectionFormState()) }
    var passwordVisible by remember { mutableStateOf(false) }
    var discoveryDialogOpen by remember { mutableStateOf(false) }
    var isDiscovering by remember { mutableStateOf(false) }
    var discoveredServers by remember { mutableStateOf<List<DiscoveredServer>>(emptyList()) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val qrScanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@rememberLauncherForActivityResult
        ConnectionQrPayload.parse(text)?.let { payload ->
            form = ConnectionFormState(
                name = payload.name.orEmpty(),
                baseUrl = payload.url.orEmpty(),
                username = payload.username?.takeIf { it.isNotBlank() } ?: "opencode",
                password = payload.password.orEmpty(),
                allowInsecureLan = payload.insecure
            )
        }
    }

    fun startLanDiscovery() {
        discoveryDialogOpen = true
        discoveredServers = emptyList()
        isDiscovering = true
        scope.launch {
            val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            withTimeoutOrNull(10_000) {
                LanDiscovery(nsdManager).discover().collect { server ->
                    discoveredServers = (discoveredServers + server).distinctBy { it.host to it.port }
                }
            }
            isDiscovering = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.remote_connection_title)) },
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
                StepRow(1, Icons.Default.PlayArrow, stringResource(R.string.remote_step1_title), stringResource(R.string.remote_step1_desc))
                Spacer(Modifier.height(12.dp))
                StepRow(2, Icons.Default.Link, stringResource(R.string.remote_step2_title), stringResource(R.string.remote_step2_desc))
                Spacer(Modifier.height(12.dp))
                StepRow(3, Icons.Default.CheckCircle, stringResource(R.string.remote_step3_title), stringResource(R.string.remote_step3_desc))
            }

            SectionCard {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { form = form.copy(name = it, testSucceeded = false, testMessage = null) },
                    label = { Text(stringResource(R.string.connection_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = form.baseUrl,
                    onValueChange = { form = form.copy(baseUrl = it, testSucceeded = false, testMessage = null) },
                    label = { Text(stringResource(R.string.server_url)) },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = form.baseUrl.isNotBlank() && form.normalizedUrl == null
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = form.username,
                    onValueChange = { form = form.copy(username = it) },
                    label = { Text(stringResource(R.string.username)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = form.password,
                    onValueChange = { form = form.copy(password = it, testSucceeded = false, testMessage = null) },
                    label = { Text(stringResource(R.string.password)) },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        qrScanLauncher.launch(ScanOptions().setBeepEnabled(false).setOrientationLocked(false))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.add_via_qr))
                }
                OutlinedButton(
                    onClick = { startLanDiscovery() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.WifiFind, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.discover_on_lan))
                }
            }

            SectionCard {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.remote_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (form.testMessage != null) {
                Text(
                    text = form.testMessage.orEmpty(),
                    color = if (form.testSucceeded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = {
                    scope.launch {
                        form = form.copy(isTesting = true, testMessage = null)
                        onTestConnection(form).fold(
                            onSuccess = { health ->
                                form = form.copy(
                                    isTesting = false,
                                    testSucceeded = health.healthy,
                                    testMessage = "OpenCode ${health.version}"
                                )
                            },
                            onFailure = { error ->
                                form = form.copy(
                                    isTesting = false,
                                    testSucceeded = false,
                                    testMessage = error.message ?: "Connection failed"
                                )
                            }
                        )
                    }
                },
                enabled = form.canSave && !form.isTesting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (form.isTesting) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.remote_test_connection_button), fontWeight = FontWeight.SemiBold)
            }

            if (form.testSucceeded) {
                Button(
                    onClick = {
                        onSaveConnection(form)
                        onConnected()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(stringResource(R.string.remote_save_connection_button), fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (discoveryDialogOpen) {
        AlertDialog(
            onDismissRequest = { discoveryDialogOpen = false },
            title = { Text(stringResource(R.string.discovered_servers_title)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isDiscovering) {
                        Text(
                            stringResource(R.string.discovering_servers),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (discoveredServers.isEmpty()) {
                        if (!isDiscovering) {
                            Text(
                                stringResource(R.string.no_servers_found),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        discoveredServers.forEach { server ->
                            SectionCard(
                                modifier = Modifier.clickable {
                                    form = ConnectionFormState(
                                        name = server.name,
                                        baseUrl = server.baseUrl,
                                        allowInsecureLan = true
                                    )
                                    discoveryDialogOpen = false
                                }
                            ) {
                                Text(server.name, fontWeight = FontWeight.Medium)
                                Text(
                                    server.baseUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { discoveryDialogOpen = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun StepRow(number: Int, icon: ImageVector, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(
                text = "$number. $title",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RemoteConnectionScreenPreview() {
    OpenCodeAndroidTheme {
        RemoteConnectionScreen(
            onTestConnection = { Result.success(OpenCodeHealth(true, "1.0.0")) },
            onSaveConnection = {},
            onBack = {},
            onConnected = {}
        )
    }
}
