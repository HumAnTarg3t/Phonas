package com.phonas.backup.ui.logs

import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phonas.backup.data.db.entity.BackupSessionFile
import com.phonas.backup.data.db.entity.SessionFileStatus

@Composable
fun LogDetailScreen(logId: Long, viewModel: LogsViewModel, onBack: () -> Unit) {
    LaunchedEffect(logId) { viewModel.loadLogDetail(logId) }

    val files by viewModel.selectedLogFiles.collectAsState()
    val log = viewModel.allLogs.collectAsState().value.find { it.id == logId }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log?.let { formatDateTime(it.startTime) } ?: "Backup Session",
                    style = MaterialTheme.typography.titleMedium
                )
                log?.let {
                    Text(
                        text = "${it.filesCopied} copied · ${it.filesSkipped} skipped" +
                            (if (it.filesFailed > 0) " · ${it.filesFailed} failed" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        HorizontalDivider()

        if (files.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No file details available for this session",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item { }
                items(files, key = { it.id }) { file ->
                    SessionFileRow(
                        file = file,
                        onTap = if (file.localUri != null) {
                            {
                                val uri = Uri.parse(file.localUri)
                                val ext = file.filename.substringAfterLast('.', "").lowercase()
                                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, mime)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching { context.startActivity(intent) }
                            }
                        } else null
                    )
                }
                item { }
            }
        }
    }
}

@Composable
private fun SessionFileRow(file: BackupSessionFile, onTap: (() -> Unit)?) {
    val chipColor = when (file.actionStatus) {
        SessionFileStatus.COPIED -> MaterialTheme.colorScheme.primaryContainer
        SessionFileStatus.SKIPPED -> MaterialTheme.colorScheme.surfaceVariant
        SessionFileStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
    }
    val chipTextColor = when (file.actionStatus) {
        SessionFileStatus.COPIED -> MaterialTheme.colorScheme.onPrimaryContainer
        SessionFileStatus.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
        SessionFileStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SuggestionChip(
            onClick = {},
            label = { Text(file.actionStatus.name, style = MaterialTheme.typography.labelSmall) },
            colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = chipColor,
                labelColor = chipTextColor
            )
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.filename,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = file.nasPath,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            file.errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        Text(
            text = formatBytes(file.fileSize),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024 -> "%.0f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}
