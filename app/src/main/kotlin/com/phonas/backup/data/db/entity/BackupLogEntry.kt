package com.phonas.backup.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class LogStatus { RUNNING, COMPLETED, FAILED, CANCELLED }

@Entity(tableName = "backup_logs")
data class BackupLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val filesCopied: Int = 0,
    val filesSkipped: Int = 0,
    val filesFailed: Int = 0,
    val totalBytesTransferred: Long = 0,
    val status: LogStatus,
    val errorMessage: String? = null
)
