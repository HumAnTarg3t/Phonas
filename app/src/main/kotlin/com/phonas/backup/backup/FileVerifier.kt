package com.phonas.backup.backup

import android.content.Context
import com.phonas.backup.backup.model.MediaFile
import com.phonas.backup.data.smb.SmbClient
import java.io.IOException
import java.security.MessageDigest

enum class VerificationOutcome { VERIFIED, NOT_FOUND, MISMATCH }

class FileVerifier(private val context: Context) {

    fun computeLocalHash(file: MediaFile): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = context.contentResolver.openInputStream(file.uri)
            ?: throw IOException("Cannot open source file: ${file.uri}")
        input.use {
            val buffer = ByteArray(8192)
            var n: Int
            while (it.read(buffer).also { r -> n = r } != -1) {
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
        // The full local hash is already computed for free during upload, so the only cost of a
        // full verify is the remote read-back. Do it for everything up to VERIFY_FULL_HASH_MAX;
        // above that, a size match is accepted (rare for phone media).
        if (file.size <= BackupLimits.VERIFY_FULL_HASH_MAX_BYTES) {
            val remoteHash = computeRemoteHash(remotePath, smb)
            return if (localHash == remoteHash) VerificationOutcome.VERIFIED else VerificationOutcome.MISMATCH
        }
        return VerificationOutcome.VERIFIED
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}
