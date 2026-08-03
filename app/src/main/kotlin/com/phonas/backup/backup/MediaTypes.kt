package com.phonas.backup.backup

/**
 * Single source of truth for what counts as backup-worthy media, shared by every scanner.
 * Kept broad on purpose: the goal is to capture everything the phone and its apps receive,
 * including messenger formats (WhatsApp/Telegram/Signal) and camera/action-cam variants.
 */
object MediaTypes {

    val imageExtensions = setOf(
        "jpg", "jpeg", "jpe", "jfif", "png", "webp", "gif", "bmp", "heic", "heif",
        "avif", "dng", "raw", "cr2", "cr3", "nef", "arw", "orf", "rw2", "tiff", "tif",
        "insp"
    )

    val videoExtensions = setOf(
        "mp4", "m4v", "mov", "avi", "mkv", "webm", "3gp", "3gpp", "3g2", "mpg", "mpeg",
        "mts", "m2ts", "ts", "wmv", "flv", "insv"
    )

    private val allExtensions = imageExtensions + videoExtensions

    /** True if the filename's extension is a supported image or video type. */
    fun isSupported(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in allExtensions

    /** True if a MIME type (e.g. from ContentResolver.getType / DocumentFile.getType) is media. */
    fun isMediaMime(mime: String?): Boolean =
        mime != null && (mime.startsWith("image/") || mime.startsWith("video/"))
}
