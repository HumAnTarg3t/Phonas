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
