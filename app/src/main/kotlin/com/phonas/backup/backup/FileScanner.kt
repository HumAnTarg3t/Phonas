package com.phonas.backup.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.phonas.backup.backup.model.MediaFile

class FileScanner(private val context: Context) {

    // Junk/derivative dirs to skip regardless of location.
    private val junkFolderNames = setOf(".thumbnails", ".trashed", ".trash", ".statuses")

    fun scan(folderUri: Uri): List<MediaFile> {
        val root = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
        return scanRecursive(root, "")
    }

    private fun scanRecursive(dir: DocumentFile, relativePath: String): List<MediaFile> {
        val results = mutableListOf<MediaFile>()

        for (child in dir.listFiles()) {
            val name = child.name ?: continue

            if (child.isDirectory) {
                val childPath = if (relativePath.isEmpty()) name else "$relativePath/$name"
                // Skip only sandboxed and junk subtrees; keep descending into Android/media so
                // app-received media (e.g. WhatsApp) is reachable when a high-level tree is granted.
                if (name.lowercase() in junkFolderNames) continue
                if (childPath.equals("Android/data", ignoreCase = true) ||
                    childPath.equals("Android/obb", ignoreCase = true)
                ) continue
                results.addAll(scanRecursive(child, childPath))
            } else if (child.isFile) {
                // Cheap extension check first; only fall back to the (per-file IPC) MIME lookup
                // for files whose extension isn't recognised.
                if (MediaTypes.isSupported(name) || MediaTypes.isMediaMime(child.type)) {
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
