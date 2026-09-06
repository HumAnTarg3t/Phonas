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

        // Content dedup: these exact bytes may already be on the NAS under a different path —
        // the same photo in DCIM and in a WhatsApp folder, or copied into a second album. Two
        // stages, so this costs nothing in the common case: a same-size candidate must already
        // exist in the DB before the file is ever read from disk.
        //
        // Capped at DEDUP_HASH_MAX_BYTES with NO size-only fallback, unlike the tier below. There
        // a bare size match is weak evidence about a file at its own destination path; here it
        // would be no evidence at all, and skipping on it would silently fail to back up a
        // distinct file. Above the cap, fall through and upload.
        if (file.size <= BackupLimits.DEDUP_HASH_MAX_BYTES &&
            db.backupFileDao().hasHashedFileOfSize(file.size, currentUri)
        ) {
            // An unreadable file must fall through to the transfer path, which records a proper
            // FAILED row, rather than abort the whole run.
            val contentHash = runCatching { fileVerifier.computeLocalHash(file) }.getOrNull()
            val twin = contentHash?.let {
                db.backupFileDao().findContentDuplicate(it, file.size, currentUri)
            }
            if (twin != null && twinStillOnNas(twin, file.size, smbClient)) {
                recordContentDuplicate(file, record, twin, contentHash)
                return true
            }
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

    /**
     * The twin was uploaded from a *different* local file, so confirm its NAS copy is still there
     * at the right size before trusting it. One metadata round-trip, no data transfer — a rounding
     * error against the upload it avoids, and it stops a NAS-side deletion from silently stranding
     * this duplicate, which unlike the fast-path case was never uploaded to its own path at all.
     */
    private fun twinStillOnNas(twin: BackupFileRecord, size: Long, smbClient: SmbClient): Boolean =
        runCatching { smbClient.getRemoteFileInfo(twin.nasPath) }.getOrNull()?.size == size

    /**
     * Record the skip so it is stable across runs: the file's own size and lastModified, so the
     * fast path catches it next time and never re-hashes — but the twin's nasPath, because that is
     * where these bytes actually live.
     *
     * status must be SUCCESS. Both the fast path above and findByRelativePathAndName filter on it,
     * so any other value would re-hash this file on every single run.
     */
    private suspend fun recordContentDuplicate(
        file: MediaFile,
        existing: BackupFileRecord?,
        twin: BackupFileRecord,
        contentHash: String
    ) {
        db.backupFileDao().upsert(
            existing?.copy(
                status = BackupStatus.SUCCESS,
                fileSize = file.size,
                lastModified = file.lastModified,
                localSha256 = contentHash,
                nasPath = twin.nasPath,
                backedUpAt = System.currentTimeMillis(),
                errorMessage = null
            ) ?: BackupFileRecord(
                localUri = file.uri.toString(),
                relativePath = file.relativePath,
                filename = file.name,
                fileSize = file.size,
                lastModified = file.lastModified,
                localSha256 = contentHash,
                nasPath = twin.nasPath,
                backedUpAt = System.currentTimeMillis(),
                status = BackupStatus.SUCCESS,
                errorMessage = null
            )
        )
    }
}
