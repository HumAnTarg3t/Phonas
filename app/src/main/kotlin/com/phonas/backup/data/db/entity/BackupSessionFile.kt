package com.phonas.backup.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SessionFileStatus { COPIED, SKIPPED, FAILED }

@Entity(
    tableName = "backup_session_files",
    foreignKeys = [ForeignKey(
        entity = BackupLogEntry::class,
        parentColumns = ["id"],
        childColumns = ["logId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("logId")]
)
data class BackupSessionFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val logId: Long,
    val filename: String,
    val nasPath: String,
    val actionStatus: SessionFileStatus,
    val fileSize: Long,
    val errorMessage: String? = null,
    val localUri: String? = null
)
