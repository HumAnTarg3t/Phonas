package com.phonas.backup.ui.status

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.phonas.backup.backup.model.BackupProgress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phonas.backup.R
import com.phonas.backup.data.db.entity.BackupLogEntry
import com.phonas.backup.data.db.entity.LogStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun StatusScreen(
    viewModel: StatusViewModel,
    onNavigateToSetup: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val nowMs = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        viewModel.refreshConfigured()
        viewModel.refreshBatteryOptimization(context)
        while (true) {
            delay(30_000L)
            nowMs.longValue = System.currentTimeMillis()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshBatteryOptimization(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatusChip(status = state.status)

        if (state.status == BackupStatusDisplay.BACKING_UP && state.progress != null) {
            ProgressCard(progress = state.progress!!)
        }

        if (state.status == BackupStatusDisplay.BACKING_UP) {
            OutlinedButton(
                onClick = { viewModel.cancelBackupNow(context) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Text("Stop Backup")
            }
        }

        state.latestLog?.let { log ->
            LastBackupCard(log = log)
        }

        state.nextBackupMillis?.let { next ->
            val remaining = next - nowMs.longValue
            val label = when {
                remaining <= 0 -> "Next backup: soon"
                remaining < 60_000L -> "Next backup: in less than a minute"
                remaining < 3_600_000L -> "Next backup: in ${remaining / 60_000L} min"
                else -> "Next backup: ${formatNextBackup(next)}"
            }
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.isBatteryOptimized) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Battery optimization is on", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Android may delay or skip scheduled backups while the screen is off. Disable battery optimization for Phonas to keep backups running on schedule.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Disable battery optimization")
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (!state.isConfigured) {
            Text(
                text = stringResource(R.string.error_not_configured),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onNavigateToSetup, modifier = Modifier.fillMaxWidth()) {
                Text("Go to Setup")
            }
        } else {
            Button(
                onClick = { viewModel.startBackupNow(context) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.status != BackupStatusDisplay.BACKING_UP
            ) {
                Text(stringResource(R.string.btn_back_up_now))
            }
        }
    }
}

@Composable
private fun StatusChip(status: BackupStatusDisplay) {
    val label = when (status) {
        BackupStatusDisplay.IDLE -> stringResource(R.string.status_idle)
        BackupStatusDisplay.BACKING_UP -> stringResource(R.string.status_backing_up)
        BackupStatusDisplay.WAITING_WIFI -> stringResource(R.string.status_waiting_wifi)
        BackupStatusDisplay.WAITING_NAS -> stringResource(R.string.status_waiting_nas)
        BackupStatusDisplay.COMPLETED -> stringResource(R.string.status_completed)
        BackupStatusDisplay.ERROR -> stringResource(R.string.status_error)
    }
    SuggestionChip(onClick = {}, label = { Text(label, style = MaterialTheme.typography.titleMedium) })
}

@Composable
private fun ProgressCard(progress: BackupProgress) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = progress.currentFile,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (progress.filesTotal > 0) {
                LinearProgressIndicator(
                    progress = { progress.filesDone.toFloat() / progress.filesTotal },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${progress.filesDone} / ${progress.filesTotal} files",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        formatBytes(progress.bytesDone) + " / " + formatBytes(progress.bytesTotal),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun LastBackupCard(log: BackupLogEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val title = when (log.status) {
                LogStatus.COMPLETED -> "Last backup completed"
                LogStatus.FAILED -> "Last backup failed"
                LogStatus.RUNNING -> "Backup in progress"
                LogStatus.CANCELLED -> "Last backup cancelled"
            }
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                formatDateTime(log.startTime),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (log.status == LogStatus.COMPLETED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("${log.filesCopied} copied", style = MaterialTheme.typography.bodySmall)
                    Text("${log.filesSkipped} skipped", style = MaterialTheme.typography.bodySmall)
                    if (log.filesFailed > 0) {
                        Text(
                            "${log.filesFailed} failed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                log.endTime?.let { end ->
                    Text(
                        "Duration: ${formatDuration(end - log.startTime)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            log.errorMessage?.let { error ->
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatNextBackup(epochMillis: Long): String {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val cal = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val tomorrowStart = cal.timeInMillis + 86_400_000L
    val dayAfterStart = tomorrowStart + 86_400_000L
    return when {
        epochMillis < tomorrowStart -> "today at ${timeFormat.format(Date(epochMillis))}"
        epochMillis < dayAfterStart -> "tomorrow at ${timeFormat.format(Date(epochMillis))}"
        else -> SimpleDateFormat("EEE d MMM 'at' HH:mm", Locale.getDefault()).format(Date(epochMillis))
    }
}

private val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())

private fun formatDateTime(epochMillis: Long): String = dateFormat.format(Date(epochMillis))

private fun formatDuration(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    }
}
