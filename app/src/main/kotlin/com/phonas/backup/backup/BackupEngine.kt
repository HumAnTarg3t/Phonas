package com.phonas.backup.backup

import android.content.Context
import android.net.Uri
import com.phonas.backup.backup.model.BackupProgress
import com.phonas.backup.backup.model.BackupResult
import com.phonas.backup.backup.model.MediaFile
import com.phonas.backup.data.db.AppDatabase
import com.phonas.backup.data.db.entity.BackupFileRecord
import com.phonas.backup.data.db.entity.BackupLogEntry
import com.phonas.backup.data.db.entity.BackupStatus
import com.phonas.backup.data.db.entity.LogStatus
import com.phonas.backup.data.prefs.AppSettings
import com.phonas.backup.data.prefs.FolderEntry
import com.phonas.backup.data.smb.SmbClient
import java.security.DigestInputStream
import java.security.MessageDigest

data class NasCredentials(
    val host: String,
    val share: String,
    val username: String,
    val password: String
)

class BackupEngine(
    private val context: Context,
    private val db: AppDatabase,
    private val smbClient: SmbClient,
    private val fileScanner: FileScanner,
    private val duplicateDetector: DuplicateDetector,
    private val fileVerifier: FileVerifier
) {
    var progressCallback: (suspend (BackupProgress) -> Unit)? = null

    suspend fun runBackup(settings: AppSettings, credentials: NasCredentials): BackupResult {
        val logId = db.backupLogDao().insert(
            BackupLogEntry(startTime = System.currentTimeMillis(), status = LogStatus.RUNNING)
        )

        var filesCopied = 0
        var filesSkipped = 0
        var filesFailed = 0
        var bytesTransferred = 0L

        return try {
            smbClient.connect(
                host = credentials.host,
                username = credentials.username,
                password = credentials.password,
                shareName = credentials.share
            )

            // Collect all files across all monitored folders
            data class IndexedFile(val file: MediaFile, val prefix: String)

            val allFiles = mutableListOf<IndexedFile>()
            for (entry in settings.monitoredFolders) {
                val folderUri = Uri.parse(entry.uri)
                fileScanner.scan(folderUri)
                    .filter { settings.sinceDateMillis == null || it.lastModified >= settings.sinceDateMillis }
                    .forEach { allFiles.add(IndexedFile(it, entry.prefix)) }
            }

            val bytesTotal = allFiles.sumOf { it.file.size }

            allFiles.forEachIndexed { index, (file, prefix) ->
                progressCallback?.invoke(
                    BackupProgress(
                        currentFile = file.name,
                        filesDone = index,
                        filesTotal = allFiles.size,
                        bytesDone = bytesTransferred,
                        bytesTotal = bytesTotal
                    )
                )

                val remotePath = buildRemotePath(prefix, file)
                if (duplicateDetector.shouldSkip(file, smbClient, remotePath)) {
                    filesSkipped++
                } else {
                    val (success, bytes) = transferAndVerify(file, remotePath)
                    if (success) {
                        filesCopied++
                        bytesTransferred += bytes
                    } else {
                        filesFailed++
                    }
                }
            }

            val endTime = System.currentTimeMillis()
            db.backupLogDao().updateCompleted(logId, endTime, filesCopied, filesSkipped, filesFailed, bytesTransferred)
            db.backupLogDao().deleteOldLogs(settings.maxLogEntries)
            BackupResult.Success(filesCopied, filesSkipped, filesFailed, bytesTransferred)
        } catch (e: Exception) {
            db.backupLogDao().updateFailed(logId, System.currentTimeMillis(), sanitizeError(e.message))
            db.backupLogDao().deleteOldLogs(settings.maxLogEntries)
            BackupResult.Failure(sanitizeError(e.message))
        } finally {
            smbClient.disconnect()
        }
    }

    private suspend fun transferAndVerify(file: MediaFile, remotePath: String): Pair<Boolean, Long> {
        return try {
            val parentPath = remotePath.substringBeforeLast("\\", "")
            if (parentPath.isNotEmpty()) smbClient.ensureDirectory(parentPath)

            val digest = MessageDigest.getInstance("SHA-256")
            val input = context.contentResolver.openInputStream(file.uri)
                ?: return markFailed(file, remotePath, "Cannot open source file")

            input.use { raw ->
                DigestInputStream(raw, digest).use { digestStream ->
                    smbClient.uploadFile(digestStream, remotePath)
                }
            }

            val localHash = digest.digest().toHexString()
            val verified = fileVerifier.verify(file, remotePath, localHash, smbClient)

            if (verified) {
                db.backupFileDao().markSuccess(
                    uri = file.uri.toString(),
                    nasPath = remotePath,
                    sha256 = localHash,
                    backedUpAt = System.currentTimeMillis()
                )
                // Ensure the record exists (markSuccess only updates if row already present)
                if (db.backupFileDao().findByUri(file.uri.toString()) == null) {
                    db.backupFileDao().upsert(
                        BackupFileRecord(
                            localUri = file.uri.toString(),
                            relativePath = file.relativePath,
                            filename = file.name,
                            fileSize = file.size,
                            lastModified = file.lastModified,
                            localSha256 = localHash,
                            nasPath = remotePath,
                            backedUpAt = System.currentTimeMillis(),
                            status = BackupStatus.SUCCESS,
                            errorMessage = null
                        )
                    )
                }
                Pair(true, file.size)
            } else {
                smbClient.deleteFile(remotePath)
                markFailed(file, remotePath, "Verification failed")
            }
        } catch (e: Exception) {
            runCatching { smbClient.deleteFile(remotePath) }
            markFailed(file, remotePath, sanitizeError(e.message))
        }
    }

    private suspend fun markFailed(file: MediaFile, remotePath: String, error: String): Pair<Boolean, Long> {
        val existing = db.backupFileDao().findByUri(file.uri.toString())
        if (existing == null) {
            db.backupFileDao().upsert(
                BackupFileRecord(
                    localUri = file.uri.toString(),
                    relativePath = file.relativePath,
                    filename = file.name,
                    fileSize = file.size,
                    lastModified = file.lastModified,
                    localSha256 = null,
                    nasPath = remotePath,
                    backedUpAt = System.currentTimeMillis(),
                    status = BackupStatus.FAILED,
                    errorMessage = error
                )
            )
        } else {
            db.backupFileDao().markFailed(file.uri.toString(), error)
        }
        return Pair(false, 0L)
    }

    private fun buildRemotePath(prefix: String, file: MediaFile): String {
        val parts = mutableListOf<String>()
        if (prefix.isNotBlank()) parts.add(prefix.trim())
        if (file.relativePath.isNotEmpty()) {
            parts.addAll(file.relativePath.split("/").filter { it.isNotEmpty() })
        }
        parts.add(file.name)
        return parts.joinToString("\\")
    }

    private fun sanitizeError(message: String?): String {
        return message?.let {
            if (it.contains("password", ignoreCase = true)
                || it.contains("credential", ignoreCase = true)
                || it.contains("auth", ignoreCase = true)
            ) "Authentication error" else it
        } ?: "Unknown error"
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}
