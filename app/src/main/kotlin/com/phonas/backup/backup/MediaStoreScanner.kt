package com.phonas.backup.backup

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import com.phonas.backup.backup.model.MediaFile

/**
 * Fast path for scan-all mode: queries MediaStore across every external volume (primary + SD).
 *
 * NOTE: MediaStore is an index, not the filesystem — it never surfaces media in folders that
 * contain a `.nomedia` file (e.g. WhatsApp chats with "Media visibility" off). Those are covered
 * by [AllFilesScanner]; this scanner is the cheap, complete source for everything that IS indexed.
 */
class MediaStoreScanner(private val context: Context) {

    fun scanAll(): List<MediaFile> {
        val results = mutableListOf<MediaFile>()
        // getExternalVolumeNames returns "external_primary" plus any mounted SD/USB volumes.
        val volumes = runCatching { MediaStore.getExternalVolumeNames(context) }
            .getOrDefault(setOf(MediaStore.VOLUME_EXTERNAL))
        for (volume in volumes) {
            results.addAll(query(MediaStore.Images.Media.getContentUri(volume)))
            results.addAll(query(MediaStore.Video.Media.getContentUri(volume)))
        }
        return results
    }

    private fun query(collection: android.net.Uri): List<MediaFile> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATE_ADDED
        )

        val files = mutableListOf<MediaFile>()
        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                null, null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val modCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol) ?: continue
                    val size = cursor.getLong(sizeCol)
                    if (size == 0L) {
                        // Transient/being-written rows report 0; skip but make it observable.
                        Log.w(TAG, "Skipping zero-size MediaStore entry: $name")
                        continue
                    }

                    val id = cursor.getLong(idCol)
                    // RELATIVE_PATH includes a trailing slash, e.g. "DCIM/Camera/" — strip it.
                    val relativePath = cursor.getString(pathCol)?.trimEnd('/') ?: ""
                    // DATE_MODIFIED / DATE_ADDED are seconds since epoch; convert to millis.
                    val lastModified = cursor.getLong(modCol) * 1000L
                    val dateAdded = cursor.getLong(addedCol) * 1000L
                    val contentUri = ContentUris.withAppendedId(collection, id)

                    files.add(
                        MediaFile(
                            uri = contentUri,
                            name = name,
                            relativePath = relativePath,
                            size = size,
                            lastModified = lastModified,
                            dateAdded = dateAdded
                        )
                    )
                }
            }
        }.onFailure { Log.w(TAG, "MediaStore query failed for $collection: ${it.message}") }
        return files
    }

    companion object {
        private const val TAG = "MediaStoreScanner"
    }
}
