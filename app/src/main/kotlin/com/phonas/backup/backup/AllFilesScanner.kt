package com.phonas.backup.backup

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import com.phonas.backup.backup.model.MediaFile
import java.io.File
import java.util.ArrayDeque

/**
 * Filesystem walk that finds media MediaStore never indexes — specifically media in folders
 * containing a `.nomedia` file, which is where WhatsApp puts group-chat images when a chat's
 * "Media visibility" is off. This is the correctness backstop for scan-all mode.
 *
 * Requires All Files Access (MANAGE_EXTERNAL_STORAGE, Android 11+). When that is not granted, or
 * on Android 10, [scan] returns an empty list and the caller falls back to MediaStore only.
 * Android/data and Android/obb are unreadable by any non-root method and are skipped.
 */
class AllFilesScanner(private val context: Context) {

    /** True when the app can walk shared storage directly (grant survives reboot; re-check each run). */
    fun hasAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

    fun scan(): List<MediaFile> {
        if (!hasAccess()) return emptyList()

        val results = mutableListOf<MediaFile>()
        for (root in storageRoots()) {
            walk(root, results)
        }
        return results
    }

    private fun storageRoots(): List<File> {
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager ?: return emptyList()
        return runCatching {
            sm.storageVolumes.mapNotNull { vol ->
                // getDirectory() is API 30+ (same floor as isExternalStorageManager).
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) vol.directory else null
            }.filter { it.isDirectory }
        }.getOrDefault(emptyList())
    }

    /** Iterative DFS (no recursion depth limit) that skips unreadable/junk subtrees. */
    private fun walk(root: File, out: MutableList<MediaFile>) {
        val stack = ArrayDeque<File>()
        stack.push(root)
        while (stack.isNotEmpty()) {
            val dir = stack.pop()
            val children = dir.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    if (shouldSkipDir(root, child)) continue
                    stack.push(child)
                } else if (child.isFile) {
                    val name = child.name
                    val size = child.length()
                    if (size == 0L || !MediaTypes.isSupported(name)) continue
                    out.add(
                        MediaFile(
                            uri = Uri.fromFile(child),
                            name = name,
                            relativePath = relativePathOf(root, child.parentFile),
                            size = size,
                            lastModified = child.lastModified()
                        )
                    )
                }
            }
        }
    }

    private fun shouldSkipDir(root: File, dir: File): Boolean {
        val name = dir.name.lowercase()
        if (name in JUNK_DIR_NAMES) return true
        // Android/data and Android/obb are sandboxed and unreadable; never descend.
        val rel = relativePathOf(root, dir).lowercase()
        return rel == "android/data" || rel == "android/obb"
    }

    /** Path of [dir] relative to the volume [root], with '/' separators, matching MediaStore RELATIVE_PATH. */
    private fun relativePathOf(root: File, dir: File?): String {
        if (dir == null) return ""
        val rootPath = root.absolutePath
        val dirPath = dir.absolutePath
        return when {
            dirPath == rootPath -> ""
            dirPath.startsWith("$rootPath/") -> dirPath.substring(rootPath.length + 1).replace(File.separatorChar, '/')
            else -> dirPath.replace(File.separatorChar, '/')
        }
    }

    companion object {
        // Transient/derivative directories not worth backing up. ".statuses" holds WhatsApp
        // statuses that the app itself deletes within 24h.
        private val JUNK_DIR_NAMES = setOf(".thumbnails", ".trashed", ".trash", ".statuses")
    }
}
