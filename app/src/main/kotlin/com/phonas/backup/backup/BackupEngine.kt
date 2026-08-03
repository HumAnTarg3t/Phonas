package com.phonas.backup.backup

import android.content.Context
import android.net.Uri
import com.phonas.backup.backup.model.BackupProgress
import com.phonas.backup.backup.model.BackupResult
import com.phonas.backup.backup.model.MediaFile
import com.phonas.backup.data.db.AppDatabase
import com.phonas.backup.data.db.entity.BackupFileRecord
import com.phonas.backup.data.db.entity.BackupLogEntry
import com.phonas.backup.data.db.entity.BackupSessionFile
import com.phonas.backup.data.db.entity.BackupStatus
import com.phonas.backup.data.db.entity.LogStatus
import com.phonas.backup.data.db.entity.SessionFileStatus
import com.phonas.backup.data.prefs.AppSettings
import com.phonas.backup.data.smb.SmbClient

import androidx.room.withTransaction
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

data class NasCredentials(
    val host: String,
    val share: String,
    val username: String,
    val password: String
)

class BackupEngine(
    private val context: Context,
    private val db: AppDatabase,
    private val fileScanner: FileScanner,
    private val mediaStoreScanner: MediaStoreScanner,
    private val allFilesScanner: AllFilesScanner,
    private val duplicateDetector: DuplicateDetector,
    private val fileVerifier: FileVerifier,
    // A fresh SmbClient per run avoids two overlapping backups sharing (and tearing down)
    // one connection. Injectable so tests can supply a mock.
    private val smbClientFactory: () -> SmbClient = { SmbClient() }
) {
    // Serializes backups: a second trigger (e.g. "Back Up Now" during a scheduled run) is
    // skipped rather than run concurrently, which previously corrupted the shared connection.
    private val mutex = Mutex()

    // Credential substrings scrubbed from any raw error text that reaches logs/DB. Set per run;
    // safe as shared mutable state because runBackup is serialized by the mutex above.
    private var redactTerms: List<String> = emptyList()

    suspend fun runBackup(
        settings: AppSettings,
        credentials: NasCredentials,
        progressCallback: (suspend (BackupProgress) -> Unit)? = null
    ): BackupResult {
        if (!mutex.tryLock()) return BackupResult.AlreadyRunning
        try {
            return runBackupLocked(settings, credentials, progressCallback)
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun runBackupLocked(
        settings: AppSettings,
        credentials: NasCredentials,
        progressCallback: (suspend (BackupProgress) -> Unit)?
    ): BackupResult {
        val smbClient = smbClientFactory()
        redactTerms = listOf(credentials.host, credentials.share, credentials.username)
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

            data class IndexedFile(val file: MediaFile, val prefix: String)

            val allFiles = mutableListOf<IndexedFile>()
            val since = settings.sinceDateMillis
            if (settings.scanAllMedia) {
                // MediaStore first (content:// URIs are stable for tap-to-open), then the
                // filesystem walk that catches .nomedia-hidden media (WhatsApp group chats).
                // Dedupe on stable identity so a file found by both sources is uploaded once.
                val merged = LinkedHashMap<String, MediaFile>()
                for (f in mediaStoreScanner.scanAll()) merged.putIfAbsent(f.identityKey, f)
                for (f in allFilesScanner.scan()) merged.putIfAbsent(f.identityKey, f)
                merged.values
                    .filter { since == null || it.effectiveDate >= since }
                    .forEach { allFiles.add(IndexedFile(it, "")) }
            } else {
                for (entry in settings.monitoredFolders) {
                    val folderUri = Uri.parse(entry.uri)
                    fileScanner.scan(folderUri)
                        .filter { since == null || it.effectiveDate >= since }
                        .forEach { allFiles.add(IndexedFile(it, entry.prefix)) }
                }
            }

            val bytesTotal = allFiles.sumOf { it.file.size }

            allFiles.forEachIndexed { index, (file, prefix) ->
                currentCoroutineContext().ensureActive()
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
                    db.backupSessionFileDao().insert(
                        BackupSessionFile(
                            logId = logId, filename = file.name, nasPath = remotePath,
                            actionStatus = SessionFileStatus.SKIPPED, fileSize = file.size,
                            localUri = file.uri.toString()
                        )
                    )
                } else {
                    val (success, bytes) = transferAndVerify(smbClient, file, remotePath, logId)
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
            // No manual rescheduling: WorkManager's PeriodicWorkRequest repeats itself.
            BackupResult.Success(filesCopied, filesSkipped, filesFailed, bytesTransferred)
        } catch (e: kotlinx.coroutines.CancellationException) {
            withContext(NonCancellable) {
                db.backupLogDao().updateCancelled(logId, System.currentTimeMillis())
                db.backupLogDao().deleteOldLogs(settings.maxLogEntries)
            }
            throw e
        } catch (e: Exception) {
            val error = sanitizeError(e)
            db.backupLogDao().updateFailed(logId, System.currentTimeMillis(), error)
            db.backupLogDao().deleteOldLogs(settings.maxLogEntries)
            BackupResult.Failure(error)
        } finally {
            smbClient.disconnect()
        }
    }

    private suspend fun transferAndVerify(smbClient: SmbClient, file: MediaFile, remotePath: String, logId: Long): Pair<Boolean, Long> {
        // Upload to a temp path, verify it, then atomically promote it over the final name. This
        // keeps the last good backup intact if the transfer fails midway, and never leaves a
        // truncated file under the real filename.
        val tempPath = "$remotePath.part"
        return try {
            val parentPath = remotePath.substringBeforeLast("\\", "")
            if (parentPath.isNotEmpty()) smbClient.ensureDirectory(parentPath)

            val digest = MessageDigest.getInstance("SHA-256")
            val input = context.contentResolver.openInputStream(file.uri)
                ?: return markFailed(file, remotePath, "Cannot open source file", logId)

            input.use { raw ->
                DigestInputStream(raw, digest).use { digestStream ->
                    smbClient.uploadFile(digestStream, tempPath, file.lastModified)
                }
            }

            val localHash = digest.digest().toHexString()
            when (fileVerifier.verify(file, tempPath, localHash, smbClient)) {
                VerificationOutcome.VERIFIED -> {
                    smbClient.rename(tempPath, remotePath, replaceIfExists = true)
                    recordSuccess(file, remotePath, localHash, logId)
                    Pair(true, file.size)
                }
                VerificationOutcome.MISMATCH -> {
                    runCatching { smbClient.deleteFile(tempPath) }
                    markFailed(file, remotePath, "Verification failed: hash mismatch", logId)
                }
                VerificationOutcome.NOT_FOUND -> {
                    // The upload did not land (or was removed) — retry next run rather than
                    // recording a file that isn't verifiably on the NAS.
                    runCatching { smbClient.deleteFile(tempPath) }
                    markFailed(file, remotePath, "Verification failed: file missing after upload", logId)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runCatching { smbClient.deleteFile(tempPath) }
            markFailed(file, remotePath, sanitizeError(e), logId)
        }
    }

    private suspend fun recordSuccess(file: MediaFile, remotePath: String, localHash: String, logId: Long) {
        // Atomic: one fully-populated row per physical file (drop any prior record — possibly under
        // a different URI from a previous source — before inserting), plus the session detail row.
        db.withTransaction {
            db.backupFileDao().deleteByRelativePathAndName(file.relativePath, file.name)
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
            db.backupSessionFileDao().insert(
                BackupSessionFile(
                    logId = logId, filename = file.name, nasPath = remotePath,
                    actionStatus = SessionFileStatus.COPIED, fileSize = file.size,
                    localUri = file.uri.toString()
                )
            )
        }
    }

    private suspend fun markFailed(file: MediaFile, remotePath: String, error: String, logId: Long): Pair<Boolean, Long> {
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
        db.backupSessionFileDao().insert(
            BackupSessionFile(
                logId = logId, filename = file.name, nasPath = remotePath,
                actionStatus = SessionFileStatus.FAILED, fileSize = file.size, errorMessage = error,
                localUri = file.uri.toString()
            )
        )
        return Pair(false, 0L)
    }

    private fun buildRemotePath(prefix: String, file: MediaFile): String {
        val parts = mutableListOf<String>()
        if (prefix.isNotBlank()) parts.add(prefix.trim())
        if (file.relativePath.isNotEmpty()) {
            parts.addAll(file.relativePath.split("/").filter { it.isNotEmpty() })
        }
        parts.add(file.name)
        // ensureDirectory() derives directory segments from this same string, so sanitizing here
        // keeps file and directory paths consistent.
        return parts.joinToString("\\") { RemotePath.sanitizeComponent(it) }
    }

    /**
     * Map a failure to a fixed, user-facing category (never persisting raw provider strings that
     * could contain the host/username), scrubbing configured credentials as a backstop for the
     * uncategorised case. Walks the cause chain so a wrapped exception is still classified.
     */
    private fun sanitizeError(e: Throwable): String {
        val combined = generateSequence(e as Throwable?) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" | ")
            .lowercase()
        return when {
            combined.isBlank() -> "Unknown error"
            listOf("auth", "password", "credential", "logon", "access is denied", "access_denied")
                .any { it in combined } -> "Authentication or access denied"
            listOf("unknownhost", "no route", "unreachable", "timed out", "timeout", "connection", "socket")
                .any { it in combined } -> "Network error — NAS unreachable"
            "space" in combined || "quota" in combined || "disk full" in combined -> "NAS out of space"
            listOf("no such", "not found", "cannot find", "does not exist")
                .any { it in combined } -> "Path or share not found"
            else -> scrubCredentials(combined)
        }
    }

    private fun scrubCredentials(message: String): String {
        var scrubbed = message
        for (term in redactTerms) {
            if (term.isNotBlank()) scrubbed = scrubbed.replace(term.lowercase(), "***")
        }
        return scrubbed
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}
