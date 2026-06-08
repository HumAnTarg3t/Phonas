package com.phonas.backup.ui.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.phonas.backup.R
import com.phonas.backup.data.db.entity.BackupLogEntry
import com.phonas.backup.data.db.entity.LogStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun LogsScreen(viewModel: LogsViewModel) {
    val logs by viewModel.logs.collectAsState()

    if (logs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.logs_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { }  // top spacing
            items(logs, key = { it.id }) { log ->
                LogEntryCard(log)
            }
            item { }  // bottom spacing
        }
    }
}

@Composable
private fun LogEntryCard(log: BackupLogEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDateTime(log.startTime),
                    style = MaterialTheme.typography.bodyMedium
                )
                val statusColor = when (log.status) {
                    LogStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                    LogStatus.FAILED -> MaterialTheme.colorScheme.error
                    LogStatus.RUNNING -> MaterialTheme.colorScheme.tertiary
                    LogStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = log.status.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
            }

            if (log.status == LogStatus.COMPLETED || log.status == LogStatus.FAILED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${log.filesCopied} copied",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "${log.filesSkipped} skipped",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (log.filesFailed > 0) {
                        Text(
                            text = "${log.filesFailed} failed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            log.endTime?.let { end ->
                Text(
                    text = "Duration: ${formatDuration(end - log.startTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            log.errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())

private fun formatDateTime(epochMillis: Long): String = dateFormat.format(Date(epochMillis))

private fun formatDuration(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}
