package com.phonas.backup.backup.model

data class BackupProgress(
    val currentFile: String,
    val filesDone: Int,
    val filesTotal: Int,
    val bytesDone: Long,
    val bytesTotal: Long
)
