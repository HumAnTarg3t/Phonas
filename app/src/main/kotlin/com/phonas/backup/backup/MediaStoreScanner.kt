package com.phonas.backup.backup

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.phonas.backup.backup.model.MediaFile

class MediaStoreScanner(private val context: Context) {

    fun scanAll(): List<MediaFile> {
        val results = mutableListOf<MediaFile>()
        results.addAll(query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI))
        results.addAll(query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI))
        return results
    }

    private fun query(collection: android.net.Uri): List<MediaFile> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )

        val files = mutableListOf<MediaFile>()
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
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol) ?: continue
                val size = cursor.getLong(sizeCol)
                if (size == 0L) continue

                val id = cursor.getLong(idCol)
                // RELATIVE_PATH includes trailing slash, e.g. "DCIM/Camera/" — strip it
                val relativePath = cursor.getString(pathCol)?.trimEnd('/') ?: ""
                // DATE_MODIFIED is seconds since epoch; convert to millis
                val lastModified = cursor.getLong(dateCol) * 1000L
                val contentUri = ContentUris.withAppendedId(collection, id)

                files.add(
                    MediaFile(
                        uri = contentUri,
                        name = name,
                        relativePath = relativePath,
                        size = size,
                        lastModified = lastModified
                    )
                )
            }
        }
        return files
    }
}
