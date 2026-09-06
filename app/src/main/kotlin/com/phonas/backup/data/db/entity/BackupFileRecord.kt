package com.phonas.backup.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class BackupStatus { PENDING, SUCCESS, FAILED }

@Entity(
    tableName = "backup_files",
    // Composite index for the stable-identity fallback lookup (findByRelativePathAndName),
    // hit once per file in scan-all mode — without it that was a full table scan.
    indices = [Index("nasPath"), Index(value = ["relativePath", "filename"])]
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
