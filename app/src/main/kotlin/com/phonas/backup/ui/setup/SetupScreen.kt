package com.phonas.backup.ui.setup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.phonas.backup.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(viewModel: SetupViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var host by remember(state.nasHost) { mutableStateOf(state.nasHost) }
    var share by remember(state.nasShare) { mutableStateOf(state.nasShare) }
    var username by remember(state.username) { mutableStateOf(state.username) }
    var password by remember { mutableStateOf("") }
    var scheduleHours by remember(state.scheduleIntervalHours) {
        mutableStateOf(state.scheduleIntervalHours)
    }
    var requireCharging by remember(state.requireCharging) { mutableStateOf(state.requireCharging) }
    var scheduleDropdownExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.addFolder(context, it) }
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) viewModel.clearSaved()
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.sinceDateMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setSinceDate(datePickerState.selectedDateMillis)
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    state.testConnectionResult?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.clearTestResult() },
            title = { Text("Connection Test") },
            text = {
                when (result) {
                    is TestConnectionResult.Success ->
                        Text(stringResource(R.string.connection_success))
                    is TestConnectionResult.Failure ->
                        Text(result.message, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearTestResult() }) { Text("OK") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("NAS Settings", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text(stringResource(R.string.label_nas_host)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = share,
            onValueChange = { share = it },
            label = { Text(stringResource(R.string.label_nas_share)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(stringResource(R.string.label_username)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.label_password)) },
            placeholder = {
                if (state.hasExistingPassword) Text("Leave blank to keep current password")
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        OutlinedButton(
            onClick = { viewModel.testConnection(host, share, username, password) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isTesting && host.isNotBlank() && share.isNotBlank()
                && username.isNotBlank() && password.isNotBlank()
        ) {
            Text(if (state.isTesting) "Testing…" else stringResource(R.string.btn_test_connection))
        }

        HorizontalDivider()

        Text(stringResource(R.string.label_monitored_folders), style = MaterialTheme.typography.titleMedium)

        if (state.monitoredFolderUris.isEmpty()) {
            Text(
                stringResource(R.string.label_no_folders),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            state.monitoredFolderUris.forEach { uriString ->
                val uri = Uri.parse(uriString)
                val displayName = DocumentFile.fromTreeUri(context, uri)?.name ?: uriString
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = displayName,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        IconButton(onClick = { viewModel.removeFolder(uriString) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove folder")
                        }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { folderPickerLauncher.launch(null) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.btn_add_folder))
        }

        HorizontalDivider()

        Text(stringResource(R.string.label_schedule), style = MaterialTheme.typography.titleMedium)

        val scheduleOptions = listOf(1 to "Every hour", 6 to "Every 6 hours",
            12 to "Every 12 hours", 24 to "Every day")
        val selectedLabel = scheduleOptions.firstOrNull { it.first == scheduleHours }?.second
            ?: "Every $scheduleHours hours"

        ExposedDropdownMenuBox(
            expanded = scheduleDropdownExpanded,
            onExpandedChange = { scheduleDropdownExpanded = it }
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Interval") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = scheduleDropdownExpanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = scheduleDropdownExpanded,
                onDismissRequest = { scheduleDropdownExpanded = false }
            ) {
                scheduleOptions.forEach { (hours, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            scheduleHours = hours
                            scheduleDropdownExpanded = false
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.label_require_charging))
            Switch(
                checked = requireCharging,
                onCheckedChange = { requireCharging = it }
            )
        }

        HorizontalDivider()

        Text("Skip Files Older Than", style = MaterialTheme.typography.titleMedium)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val sinceDate = state.sinceDateMillis
                if (sinceDate != null) {
                    Text(
                        formatDate(sinceDate),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Files before this date will not be backed up",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "All files (no date limit)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row {
                if (state.sinceDateMillis != null) {
                    TextButton(onClick = { viewModel.setSinceDate(null) }) { Text("Clear") }
                }
                OutlinedButton(onClick = { showDatePicker = true }) {
                    Text(if (state.sinceDateMillis != null) "Change" else "Set Date")
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                viewModel.save(context, host, share, username, password, scheduleHours, requireCharging)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = host.isNotBlank() && share.isNotBlank() && username.isNotBlank()
        ) {
            Text(stringResource(R.string.btn_save))
        }
    }
}

private val dateFormatter = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
private fun formatDate(epochMillis: Long): String = dateFormatter.format(Date(epochMillis))
