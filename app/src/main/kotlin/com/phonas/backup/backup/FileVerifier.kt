package com.phonas.backup.backup

import android.content.Context
import com.phonas.backup.backup.model.MediaFile
import com.phonas.backup.data.smb.SmbClient
import java.security.MessageDigest

class FileVerifier(private val context: Context) {

    private val hashThresholdBytes = 500L * 1024 * 1024  // 500 MB

    fun computeLocalHash(file: MediaFile): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(file.uri)?.use { input ->
            val buffer = ByteArray(8192)
            var n: Int
            while (input.read(buffer).also { n = it } != -1) {
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().toHexString()
    }

    fun computeRemoteHash(remotePath: String, smb: SmbClient): String {
        val digest = MessageDigest.getInstance("SHA-256")
        smb.openRemoteInputStream(remotePath).use { input ->
            val buffer = ByteArray(8192)
            var n: Int
            while (input.read(buffer).also { n = it } != -1) {
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().toHexString()
    }

    fun verify(file: MediaFile, remotePath: String, localHash: String, smb: SmbClient): Boolean {
        val remoteInfo = smb.getRemoteFileInfo(remotePath) ?: return false
        if (remoteInfo.size != file.size) return false
        if (file.size <= hashThresholdBytes) {
            val remoteHash = computeRemoteHash(remotePath, smb)
            return localHash == remoteHash
        }
        return true
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}
