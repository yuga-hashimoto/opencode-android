package com.opencode.android.feature.workspace

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.opencode.android.R
import com.opencode.android.core.api.OpenCodeHealth
import com.opencode.android.data.connection.ConnectionProfile
import com.opencode.android.ui.components.SectionCard
import com.opencode.android.ui.components.StatusChip
import kotlinx.coroutines.launch

@Composable
fun ConnectionsScreen(
    connections: List<ConnectionProfile>,
    selectedConnectionId: String?,
    onSelect: (String) -> Unit,
    onSave: (ConnectionFormState) -> Unit,
    onDelete: (String) -> Unit,
    onTest: suspend (ConnectionFormState) -> Result<OpenCodeHealth>
) {
    var editing by remember { mutableStateOf<ConnectionFormState?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = ConnectionFormState() }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_connection))
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.nav_connections),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.connection_help),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (connections.isEmpty()) {
                item {
                    SectionCard {
                        Icon(Icons.Default.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.connection_missing), fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.connection_help),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = { editing = ConnectionFormState() }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Text(stringResource(R.string.add_connection))
                        }
                    }
                }
            }

            items(connections, key = { it.id }) { connection ->
                SectionCard(
                    modifier = Modifier.clickable { onSelect(connection.id) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(connection.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                connection.baseUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (connection.id == selectedConnectionId) {
                            StatusChip(stringResource(R.string.active), active = true)
                        } else {
                            TextButton(onClick = { onSelect(connection.id) }) {
                                Text(stringResource(R.string.select))
                            }
                        }
                        IconButton(onClick = { editing = ConnectionFormState.from(connection) }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_connection))
                        }
                    }
                }
            }
        }
    }

    editing?.let { initial ->
        ConnectionDialog(
            initial = initial,
            onDismiss = { editing = null },
            onSave = {
                onSave(it)
                editing = null
            },
            onDelete = if (connections.any { it.id == initial.id }) {
                {
                    onDelete(initial.id)
                    editing = null
                }
            } else null,
            onTest = onTest
        )
    }
}

@Composable
private fun ConnectionDialog(
    initial: ConnectionFormState,
    onDismiss: () -> Unit,
    onSave: (ConnectionFormState) -> Unit,
    onDelete: (() -> Unit)?,
    onTest: suspend (ConnectionFormState) -> Result<OpenCodeHealth>
) {
    var form by remember(initial.id) { mutableStateOf(initial) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (onDelete == null) stringResource(R.string.add_connection)
                else stringResource(R.string.edit_connection)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { form = form.copy(name = it, testSucceeded = false, testMessage = null) },
                    label = { Text(stringResource(R.string.connection_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = form.baseUrl,
                    onValueChange = { form = form.copy(baseUrl = it, testSucceeded = false, testMessage = null) },
                    label = { Text(stringResource(R.string.server_url)) },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                    placeholder = { Text("192.168.1.10:4096") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = form.baseUrl.isNotBlank() && form.normalizedUrl == null
                )
                OutlinedTextField(
                    value = form.username,
                    onValueChange = { form = form.copy(username = it) },
                    label = { Text(stringResource(R.string.username)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = form.password,
                    onValueChange = { form = form.copy(password = it, testSucceeded = false, testMessage = null) },
                    label = { Text(stringResource(R.string.password)) },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = form.allowInsecureLan,
                        onCheckedChange = { form = form.copy(allowInsecureLan = it) }
                    )
                    Text(
                        text = stringResource(R.string.allow_lan_http),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            form = form.copy(isTesting = true, testMessage = null)
                            onTest(form).fold(
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (form.isTesting) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (form.testSucceeded) Icons.Default.CheckCircle else Icons.Default.NetworkCheck,
                            contentDescription = null
                        )
                    }
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(form.testMessage ?: stringResource(R.string.test_connection))
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(form) }, enabled = form.canSave && !form.isTesting) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                onDelete?.let {
                    TextButton(onClick = it) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Text(stringResource(R.string.delete))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}
