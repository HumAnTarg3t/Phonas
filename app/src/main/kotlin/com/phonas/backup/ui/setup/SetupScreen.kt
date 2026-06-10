package com.phonas.backup.ui.setup

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import com.phonas.backup.ui.formatNextBackupLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(viewModel: SetupViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var host by remember(state.nasHost) { mutableStateOf(state.nasHost) }
    var share by remember(state.nasShare) { mutableStateOf(state.nasShare) }
    var username by remember(state.username) { mutableStateOf(state.username) }
    var password by remember { mutableStateOf("") }
    var scheduleMinutes by remember(state.scheduleIntervalMinutes) { mutableStateOf(state.scheduleIntervalMinutes) }
    var requireCharging by remember(state.requireCharging) { mutableStateOf(state.requireCharging) }
    var maxLogEntries by remember(state.maxLogEntries) { mutableStateOf(state.maxLogEntries) }
    var scheduleDropdownExpanded by remember { mutableStateOf(false) }
    var logEntriesDropdownExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? -> uri?.let { viewModel.addFolder(context, it) } }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? -> uri?.let { viewModel.exportConfig(context, it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { viewModel.importConfig(context, it) } }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) viewModel.setScanAllMedia(true)
        else viewModel.onMediaPermissionDenied()
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) viewModel.clearSaved()
    }

    LaunchedEffect(state.importExportMessage) {
        if (state.importExportMessage != null) {
            delay(3000)
            viewModel.clearImportExportMessage()
        }
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
        // ── NAS Settings ──────────────────────────────────────────────
        Text("NAS Settings", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = host, onValueChange = { host = it },
            label = { Text(stringResource(R.string.label_nas_host)) },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = share, onValueChange = { share = it },
            label = { Text(stringResource(R.string.label_nas_share)) },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = username, onValueChange = { username = it },
            label = { Text(stringResource(R.string.label_username)) },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text(stringResource(R.string.label_password)) },
            placeholder = { if (state.hasExistingPassword) Text("Leave blank to keep current password") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
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

        // ── Folders ───────────────────────────────────────────────────
        Text(stringResource(R.string.label_monitored_folders), style = MaterialTheme.typography.titleMedium)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Scan all device media", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Backs up all photos and videos on the device, including WhatsApp, Screenshots, Downloads etc. Folder selection below is ignored.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.scanAllMedia,
                onCheckedChange = { enabled ->
                    if (!enabled) {
                        viewModel.setScanAllMedia(false)
                        return@Switch
                    }
                    val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
                    } else {
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                    val allGranted = perms.all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }
                    if (allGranted) viewModel.setScanAllMedia(true)
                    else mediaPermissionLauncher.launch(perms)
                }
            )
        }

        if (!state.scanAllMedia) {
            if (state.monitoredFolders.isEmpty()) {
                Text(
                    stringResource(R.string.label_no_folders),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.monitoredFolders.forEach { entry ->
                    val uri = Uri.parse(entry.uri)
                    val displayName = DocumentFile.fromTreeUri(context, uri)?.name ?: entry.uri
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(displayName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                IconButton(onClick = { viewModel.removeFolder(entry.uri) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove folder")
                                }
                            }
                            OutlinedTextField(
                                value = entry.prefix,
                                onValueChange = { viewModel.updateFolderPrefix(entry.uri, it) },
                                label = { Text("NAS prefix (optional)") },
                                placeholder = { Text("e.g. camera") },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                singleLine = true
                            )
                        }
                    }
                }
            }

            OutlinedButton(onClick = { folderPickerLauncher.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.btn_add_folder))
            }
        }

        HorizontalDivider()

        // ── Schedule ──────────────────────────────────────────────────
        Text(stringResource(R.string.label_schedule), style = MaterialTheme.typography.titleMedium)

        val scheduleOptions = listOf(
            15 to "Every 15 minutes",
            60 to "Every hour",
            360 to "Every 6 hours",
            720 to "Every 12 hours",
            1440 to "Every day"
        )
        val scheduleLabel = scheduleOptions.firstOrNull { it.first == scheduleMinutes }?.second ?: "Every $scheduleMinutes minutes"

        ExposedDropdownMenuBox(expanded = scheduleDropdownExpanded, onExpandedChange = { scheduleDropdownExpanded = it }) {
            OutlinedTextField(
                value = scheduleLabel, onValueChange = {}, readOnly = true,
                label = { Text("Interval") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = scheduleDropdownExpanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = scheduleDropdownExpanded, onDismissRequest = { scheduleDropdownExpanded = false }) {
                scheduleOptions.forEach { (minutes, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { scheduleMinutes = minutes; scheduleDropdownExpanded = false })
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.label_require_charging))
            Switch(checked = requireCharging, onCheckedChange = { requireCharging = it })
        }

        state.lastCompletedBackupMillis?.let { base ->
            val next = base + scheduleMinutes * 60_000L
            Text(
                formatNextBackupLabel(next, System.currentTimeMillis()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val logOptions = listOf(25, 50, 100, 200, 500)
        val logLabel = "$maxLogEntries sessions"

        ExposedDropdownMenuBox(expanded = logEntriesDropdownExpanded, onExpandedChange = { logEntriesDropdownExpanded = it }) {
            OutlinedTextField(
                value = logLabel, onValueChange = {}, readOnly = true,
                label = { Text("Keep backup logs") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = logEntriesDropdownExpanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = logEntriesDropdownExpanded, onDismissRequest = { logEntriesDropdownExpanded = false }) {
                logOptions.forEach { count ->
                    DropdownMenuItem(text = { Text("$count sessions") }, onClick = { maxLogEntries = count; logEntriesDropdownExpanded = false })
                }
            }
        }

        HorizontalDivider()

        // ── Date filter ───────────────────────────────────────────────
        Text("Skip Files Older Than", style = MaterialTheme.typography.titleMedium)

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                val sinceDate = state.sinceDateMillis
                if (sinceDate != null) {
                    Text(formatDate(sinceDate), style = MaterialTheme.typography.bodyMedium)
                    Text("Files before this date will not be backed up", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("All files (no date limit)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

        HorizontalDivider()

        // ── Export / Import ───────────────────────────────────────────
        Text("Export / Import Config", style = MaterialTheme.typography.titleMedium)

        Text(
            "The exported file contains all settings and credentials. Keep it secure.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        state.importExportMessage?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = if (msg.contains("failed", ignoreCase = true))
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { exportLauncher.launch("phonas_config.json") },
                modifier = Modifier.weight(1f)
            ) { Text("Export") }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                modifier = Modifier.weight(1f)
            ) { Text("Import") }
        }

        HorizontalDivider()

        // ── Reset Database ────────────────────────────────────────────
        OutlinedButton(
            onClick = { showResetDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        ) {
            Text("Reset backup database")
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Reset Database") },
                text = { Text("This deletes all local backup records and log history. Files already on the NAS are not affected. The next backup will re-scan all files.") },
                confirmButton = {
                    TextButton(onClick = { viewModel.resetDatabase(); showResetDialog = false }) {
                        Text("Reset", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
                }
            )
        }

        Spacer(Modifier.height(8.dp))

        // ── Save ──────────────────────────────────────────────────────
        Button(
            onClick = {
                viewModel.save(context, host, share, username, password, scheduleMinutes, requireCharging, maxLogEntries)
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

