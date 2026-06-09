package com.phonas.backup.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.phonas.backup.data.db.entity.BackupLogEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupLogDao {

    @Insert
    suspend fun insert(entry: BackupLogEntry): Long

    @Query("SELECT * FROM backup_logs ORDER BY startTime DESC")
    fun getAllLogs(): Flow<List<BackupLogEntry>>

    @Query("SELECT * FROM backup_logs ORDER BY startTime DESC LIMIT 1")
    suspend fun getLatestLog(): BackupLogEntry?

    @Query(
        """
        UPDATE backup_logs
        SET status = 'COMPLETED', endTime = :endTime, filesCopied = :copied,
            filesSkipped = :skipped, filesFailed = :failed, totalBytesTransferred = :bytes
        WHERE id = :id
        """
    )
    suspend fun updateCompleted(
        id: Long,
        endTime: Long,
        copied: Int,
        skipped: Int,
        failed: Int,
        bytes: Long
    )

    @Query(
        """
        UPDATE backup_logs
        SET status = 'FAILED', endTime = :endTime, errorMessage = :error
        WHERE id = :id
        """
    )
    suspend fun updateFailed(id: Long, endTime: Long, error: String?)

    @Query(
        "UPDATE backup_logs SET status = 'CANCELLED', endTime = :now WHERE status = 'RUNNING'"
    )
    suspend fun cancelStaleRunning(now: Long)

    @Query(
        "DELETE FROM backup_logs WHERE id NOT IN (SELECT id FROM backup_logs ORDER BY startTime DESC LIMIT :keep)"
    )
    suspend fun deleteOldLogs(keep: Int = 100)

    @Query("DELETE FROM backup_logs")
    suspend fun deleteAll()
}
