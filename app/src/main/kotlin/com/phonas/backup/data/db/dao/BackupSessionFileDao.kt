package com.phonas.backup.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.phonas.backup.data.db.entity.BackupSessionFile
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupSessionFileDao {

    @Insert
    suspend fun insert(file: BackupSessionFile)

    @Query("SELECT * FROM backup_session_files WHERE logId = :logId ORDER BY id ASC")
    fun getByLogId(logId: Long): Flow<List<BackupSessionFile>>

    @Query("DELETE FROM backup_session_files")
    suspend fun deleteAll()
}
