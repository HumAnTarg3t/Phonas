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

    @Query("SELECT * FROM backup_logs WHERE status = 'COMPLETED' ORDER BY startTime DESC LIMIT 1")
    fun getLastCompletedLogFlow(): Flow<BackupLogEntry?>

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

    /** Cancel a single specific run (used by the engine's own cancellation path). */
    @Query(
        "UPDATE backup_logs SET status = 'CANCELLED', endTime = :now WHERE id = :id AND status = 'RUNNING'"
    )
    suspend fun updateCancelled(id: Long, now: Long)

    /**
     * Startup cleanup: only reap RUNNING logs older than [cutoff] so a freshly-inserted
     * log from a worker that is starting concurrently is never flipped to CANCELLED.
     */
    @Query(
        "UPDATE backup_logs SET status = 'CANCELLED', endTime = :now WHERE status = 'RUNNING' AND startTime < :cutoff"
    )
    suspend fun cancelStaleRunning(cutoff: Long, now: Long)

    @Query(
        "DELETE FROM backup_logs WHERE id NOT IN (SELECT id FROM backup_logs ORDER BY startTime DESC LIMIT :keep)"
    )
    suspend fun deleteOldLogs(keep: Int = 100)

    @Query("DELETE FROM backup_logs")
    suspend fun deleteAll()
}
