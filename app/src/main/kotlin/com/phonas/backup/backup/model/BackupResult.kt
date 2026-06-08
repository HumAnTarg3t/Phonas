package com.phonas.backup.backup.model

sealed class BackupResult {
    data class Success(
        val filesCopied: Int,
        val filesSkipped: Int,
        val filesFailed: Int,
        val bytesTransferred: Long
    ) : BackupResult()

    data class Failure(val message: String?) : BackupResult()
}
