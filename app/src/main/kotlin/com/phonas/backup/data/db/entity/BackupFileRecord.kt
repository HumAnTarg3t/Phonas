package com.phonas.backup.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class BackupStatus { PENDING, SUCCESS, FAILED }

@Entity(
    tableName = "backup_files",
    // Composite index for the stable-identity fallback lookup (findByRelativePathAndName),
    // hit once per file in scan-all mode — without it that was a full table scan.
    //
    // (fileSize, localSha256) serves the content-duplicate gate: SQLite seeks the fileSize
    // prefix alone for the free "does any same-size candidate exist?" probe, and the full key
    // for the exact content match. One index rather than two, and localSha256 is deliberately
    // not the leading column — nothing ever looks up a hash without already knowing the size.
    indices = [
        Index("nasPath"),
        Index(value = ["relativePath", "filename"]),
        Index(value = ["fileSize", "localSha256"])
    ]
)
/**
 * One row per local file that has been considered for backup.
 *
 * [nasPath] is NOT unique. A file skipped because its exact content is already on the NAS under
 * a different path gets a row pointing at the twin's copy, so several rows can share one path.
 * Never add a UNIQUE constraint on it, or a delete-by-nasPath.
 *
 * Any future retention policy must key on "the local file no longer exists", never on
 * [backedUpAt] age: pruning a row that other rows rely on as their dedup twin forces a re-hash,
 * and if the twin's own file has since left the device, a re-upload of every duplicate that
 * pointed at it.
 */
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
