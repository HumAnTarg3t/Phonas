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
    private val hashThresholdBytes = 500L * 1024 * 1024

    suspend fun shouldSkip(file: MediaFile, smbClient: SmbClient, remotePath: String): Boolean {
        val record = db.backupFileDao().findByUri(file.uri.toString())

        // Fast path: DB record matches current metadata exactly
        if (record != null
            && record.status == BackupStatus.SUCCESS
            && record.fileSize == file.size
            && record.lastModified == file.lastModified
        ) {
            return true
        }

        // Check NAS directly — handles reinstall or DB loss
        val remoteInfo = smbClient.getRemoteFileInfo(remotePath) ?: return false

        if (remoteInfo.size != file.size) return false

        val matched: Boolean
        val localHash: String?

        if (file.size <= hashThresholdBytes) {
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
