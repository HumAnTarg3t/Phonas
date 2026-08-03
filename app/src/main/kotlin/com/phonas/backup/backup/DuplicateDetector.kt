package com.phonas.backup.backup

import com.phonas.backup.backup.model.MediaFile
import com.phonas.backup.data.db.AppDatabase
import com.phonas.backup.data.db.entity.BackupFileRecord
import com.phonas.backup.data.db.entity.BackupStatus
import com.phonas.backup.data.smb.SmbClient

class DuplicateDetector(
    private val db: AppDatabase,
    private val fileVerifier: FileVerifier
) {
    suspend fun shouldSkip(file: MediaFile, smbClient: SmbClient, remotePath: String): Boolean {
        var record = db.backupFileDao().findByUri(file.uri.toString())

        // MediaStore IDs can change on reindex; fall back to stable path+name lookup
        if (record == null) {
            record = db.backupFileDao().findByRelativePathAndName(file.relativePath, file.name)
            if (record != null) {
                // Self-heal: store the new URI for future fast-path hits
                db.backupFileDao().upsert(record.copy(localUri = file.uri.toString()))
            }
        }

        // Fast path: DB record matches current metadata exactly
        if (record != null
            && record.status == BackupStatus.SUCCESS
            && record.fileSize == file.size
            && record.lastModified == file.lastModified
        ) {
            return true
        }

        // Check NAS directly — handles reinstall or DB loss. A transient error here (not a clean
        // "absent") shouldn't fail the whole backup, so treat any failure as "not a duplicate"
        // and let the transfer path re-upload (harmless overwrite).
        val remoteInfo = runCatching { smbClient.getRemoteFileInfo(remotePath) }.getOrNull() ?: return false

        if (remoteInfo.size != file.size) return false

        val matched: Boolean
        val localHash: String?

        if (file.size <= BackupLimits.DEDUP_HASH_MAX_BYTES) {
            localHash = fileVerifier.computeLocalHash(file)
            val remoteHash = fileVerifier.computeRemoteHash(remotePath, smbClient)
            matched = (localHash == remoteHash)
        } else {
            // Large file: size match is sufficient to skip
            localHash = null
            matched = true
        }

        if (matched) {
            db.backupFileDao().upsert(
                record?.copy(
                    status = BackupStatus.SUCCESS,
                    fileSize = file.size,
                    lastModified = file.lastModified,
                    localSha256 = localHash ?: record.localSha256,
                    nasPath = remotePath,
                    backedUpAt = System.currentTimeMillis(),
                    errorMessage = null
                ) ?: BackupFileRecord(
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

        return matched
    }
}
