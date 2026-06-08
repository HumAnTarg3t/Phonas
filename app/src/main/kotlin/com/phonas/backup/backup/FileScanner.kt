package com.phonas.backup.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.phonas.backup.backup.model.MediaFile

class FileScanner(private val context: Context) {

    private val supportedExtensions = setOf(
        "jpg", "jpeg", "heic", "heif", "png", "webp", "dng", "raw",
        "mp4", "mov", "avi", "mkv", "3gp", "3gpp"
    )

    private val ignoredFolderNames = setOf(
        "android", "obb", "data", ".thumbnails", "cache", ".cache", ".trash"
    )

    fun scan(folderUri: Uri): List<MediaFile> {
        val root = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
        return scanRecursive(root, "")
    }

    private fun scanRecursive(dir: DocumentFile, relativePath: String): List<MediaFile> {
        val results = mutableListOf<MediaFile>()

        for (child in dir.listFiles()) {
            val name = child.name ?: continue

            if (child.isDirectory) {
                if (name.startsWith('.') || name.lowercase() in ignoredFolderNames) continue
                val childPath = if (relativePath.isEmpty()) name else "$relativePath/$name"
                results.addAll(scanRecursive(child, childPath))
            } else if (child.isFile) {
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in supportedExtensions) {
                    results.add(
                        MediaFile(
                            uri = child.uri,
                            name = name,
                            relativePath = relativePath,
                            size = child.length(),
                            lastModified = child.lastModified()
                        )
                    )
                }
            }
        }

        return results
    }
}
