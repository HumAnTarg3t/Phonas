package com.phonas.backup.backup

import android.content.Context
import com.phonas.backup.backup.model.MediaFile
import com.phonas.backup.data.smb.SmbClient
import java.security.MessageDigest

enum class VerificationOutcome { VERIFIED, NOT_FOUND, MISMATCH }

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

    fun verify(file: MediaFile, remotePath: String, localHash: String, smb: SmbClient): VerificationOutcome {
        val remoteInfo = smb.getRemoteFileInfo(remotePath) ?: return VerificationOutcome.NOT_FOUND
        if (remoteInfo.size != file.size) return VerificationOutcome.MISMATCH
        if (file.size <= hashThresholdBytes) {
            val remoteHash = computeRemoteHash(remotePath, smb)
            return if (localHash == remoteHash) VerificationOutcome.VERIFIED else VerificationOutcome.MISMATCH
        }
        return VerificationOutcome.VERIFIED
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}
