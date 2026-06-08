package com.phonas.backup.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class BackupStatus { PENDING, SUCCESS, FAILED }

@Entity(
    tableName = "backup_files",
    indices = [Index("nasPath")]
)
data class BackupFileRecord(
    @PrimaryKey val localUri: String,
    val relativePath: String,
    val filename: String,
    val fileSize: Long,
    val lastModified: Long,
    val localSha256: String?,
    val nasPath: String,
    val backedUpAt: Long,
    val status: BackupStatus,
    val errorMessage: String?
)
