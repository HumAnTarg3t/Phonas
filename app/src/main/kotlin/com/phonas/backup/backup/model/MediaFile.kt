package com.phonas.backup.backup.model

import android.net.Uri

data class MediaFile(
    val uri: Uri,
    val name: String,
    val relativePath: String,
    val size: Long,
    val lastModified: Long
)
