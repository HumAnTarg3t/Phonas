package com.phonas.backup.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.phonas.backup.data.db.entity.BackupFileRecord

@Dao
interface BackupFileDao {

    @Query("SELECT * FROM backup_files WHERE localUri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): BackupFileRecord?

    @Query("SELECT * FROM backup_files WHERE nasPath = :path LIMIT 1")
    suspend fun findByNasPath(path: String): BackupFileRecord?

    @Query("SELECT * FROM backup_files WHERE relativePath = :path AND filename = :filename AND status = 'SUCCESS' LIMIT 1")
    suspend fun findByRelativePathAndName(path: String, filename: String): BackupFileRecord?

    @Query("SELECT * FROM backup_files WHERE status = 'FAILED'")
    suspend fun findAllFailed(): List<BackupFileRecord>

    /**
     * Free first stage of the content-duplicate gate: has any already-backed-up file exactly this
     * size and a known content hash? Answered from the (fileSize, localSha256) index, so a file
     * with no same-size twin costs one seek and is never read from disk.
     *
     * status = 'SUCCESS' is load-bearing: markFailed does not clear localSha256, so a FAILED row
     * can still carry a hash whose nasPath may hold nothing.
     */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM backup_files
            WHERE fileSize = :size AND localSha256 IS NOT NULL
              AND status = 'SUCCESS' AND localUri != :excludeUri
        )
        """
    )
    suspend fun hasHashedFileOfSize(size: Long, excludeUri: String): Boolean

    /**
     * The already-backed-up file with identical bytes — same size AND same SHA-256 — under any
     * path. [excludeUri] stops a file matching its own record. Oldest wins: the longest-established
     * NAS copy is the one most likely to still be there.
     */
    @Query(
        """
        SELECT * FROM backup_files
        WHERE localSha256 = :sha256 AND fileSize = :size
          AND status = 'SUCCESS' AND localUri != :excludeUri
        ORDER BY backedUpAt ASC LIMIT 1
        """
    )
    suspend fun findContentDuplicate(
        sha256: String,
        size: Long,
        excludeUri: String
    ): BackupFileRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: BackupFileRecord)

    /** Move a record to a new localUri (the primary key) in place — no orphan row left behind. */
    @Query("UPDATE backup_files SET localUri = :newUri WHERE localUri = :oldUri")
    suspend fun updateLocalUri(oldUri: String, newUri: String)

    /** Remove any prior record(s) for the same physical file before writing a fresh one. */
    @Query("DELETE FROM backup_files WHERE relativePath = :path AND filename = :filename")
    suspend fun deleteByRelativePathAndName(path: String, filename: String)

    @Query(
        """
        UPDATE backup_files
        SET status = 'SUCCESS', nasPath = :nasPath, localSha256 = :sha256,
            backedUpAt = :backedUpAt, errorMessage = NULL
        WHERE localUri = :uri
        """
    )
    suspend fun markSuccess(uri: String, nasPath: String, sha256: String?, backedUpAt: Long)

    @Query(
        """
        UPDATE backup_files
        SET status = 'FAILED', errorMessage = :error, backedUpAt = :timestamp
        WHERE localUri = :uri
        """
    )
    suspend fun markFailed(
        uri: String,
        error: String?,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM backup_files")
    suspend fun deleteAll()
}
