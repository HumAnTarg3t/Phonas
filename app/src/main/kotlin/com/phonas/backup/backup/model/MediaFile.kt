package com.phonas.backup.backup.model

import android.net.Uri

data class MediaFile(
    val uri: Uri,
    val name: String,
    val relativePath: String,
    val size: Long,
    val lastModified: Long,
    /**
     * When the file first appeared on the device (MediaStore DATE_ADDED, millis). Unlike
     * [lastModified] this is set by the OS and cannot be backdated by the source app, so it is
     * the trustworthy signal for the "skip files older than" filter. 0 when unknown (file walk).
     */
    val dateAdded: Long = 0L
) {
    /**
     * Stable cross-source identity: the same physical file has the same key whether it was found
     * via MediaStore (content://) or the filesystem walk (file://), so the two sources dedupe and
     * a file backed up last cycle via one source is recognised when found via the other.
     */
    val identityKey: String
        get() = (if (relativePath.isEmpty()) name else "$relativePath/$name").lowercase()

    /** Effective age signal for the date filter — the later of when it was added or modified. */
    val effectiveDate: Long get() = maxOf(dateAdded, lastModified)
}
