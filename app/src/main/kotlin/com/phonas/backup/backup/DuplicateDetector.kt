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
        val currentUri = file.uri.toString()
        var record = db.backupFileDao().findByUri(currentUri)

        // MediaStore IDs change on reindex and the same file has different URIs across sources
        // (content:// vs file://), so fall back to the stable path+name identity.
        if (record == null) {
            val healed = db.backupFileDao().findByRelativePathAndName(file.relativePath, file.name)
            if (healed != null) {
                // Self-heal in place (no orphan row) and reflect the new key in memory so the
                // upsert below updates this same row rather than resurrecting the old URI.
                db.backupFileDao().updateLocalUri(healed.localUri, currentUri)
                record = healed.copy(localUri = currentUri)
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
