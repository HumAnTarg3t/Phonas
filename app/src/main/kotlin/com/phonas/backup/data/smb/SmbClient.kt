package com.phonas.backup.data.smb

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msdtyp.FileTime
import com.hierynomus.mserref.NtStatus
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileBasicInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import java.io.InputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit

data class RemoteFileInfo(val size: Long, val lastModified: Long)

class SmbClient {

    private var smb: SMBClient? = null
    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null

    fun connect(host: String, username: String, password: String, shareName: String) {
        disconnect()
        // Explicit timeouts so a sleeping/dropped NAS fails fast instead of blocking the worker
        // indefinitely (the default socket timeout is 0 = infinite).
        val config = SmbConfig.builder()
            .withSoTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .withTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        smb = SMBClient(config)
        connection = smb!!.connect(host)
        val auth = AuthenticationContext(username, password.toCharArray(), null)
        session = connection!!.authenticate(auth)
        share = session!!.connectShare(shareName) as DiskShare
    }

    fun disconnect() {
        runCatching { share?.close() }
        runCatching { session?.close() }
        runCatching { connection?.close() }
        runCatching { smb?.close() }
        share = null
        session = null
        connection = null
        smb = null
    }

    fun isConnected(): Boolean = share != null

    fun fileExists(remotePath: String): Boolean = existsOrThrow { share!!.fileExists(remotePath) }

    fun folderExists(remotePath: String): Boolean = existsOrThrow { share!!.folderExists(remotePath) }

    /**
     * Returns metadata, or null ONLY when the server reports the file genuinely does not exist.
     * Any other failure (connection dropped, access denied, IO) propagates so callers treat it as
     * an error rather than silently as "absent" — which previously let failed uploads be marked
     * successful.
     */
    fun getRemoteFileInfo(remotePath: String): RemoteFileInfo? {
        return try {
            val info = share!!.getFileInformation(remotePath)
            RemoteFileInfo(
                size = info.standardInformation.endOfFile,
                lastModified = info.basicInformation.lastWriteTime.toEpochMillis()
            )
        } catch (e: SMBApiException) {
            if (isNotFound(e)) null else throw e
        }
    }

    fun ensureDirectory(remotePath: String) {
        if (remotePath.isBlank()) return
        val parts = remotePath.split("\\").filter { it.isNotEmpty() }
        var current = ""
        for (part in parts) {
            current = if (current.isEmpty()) part else "$current\\$part"
            if (!folderExists(current)) {
                try {
                    share!!.mkdir(current)
                } catch (e: SMBApiException) {
                    // A racing create or an existing entry of the same name is benign; anything
                    // else (permission denied, path invalid) must surface as a real failure.
                    if (e.status != NtStatus.STATUS_OBJECT_NAME_COLLISION && !folderExists(current)) {
                        throw e
                    }
                }
            }
        }
    }

    fun deleteFile(remotePath: String) {
        runCatching { share!!.rm(remotePath) }
    }

    /** Atomically replaces [toPath] with [fromPath] (rename over the existing final file). */
    fun rename(fromPath: String, toPath: String, replaceIfExists: Boolean = true) {
        val file = share!!.openFile(
            fromPath,
            EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_READ, AccessMask.GENERIC_WRITE),
            EnumSet.noneOf(FileAttributes::class.java),
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            EnumSet.noneOf(SMB2CreateOptions::class.java)
        )
        file.use { it.rename(toPath, replaceIfExists) }
    }

    fun uploadFile(input: InputStream, remotePath: String, lastModifiedMillis: Long? = null) {
        val file = share!!.openFile(
            remotePath,
            EnumSet.of(AccessMask.GENERIC_WRITE),
            EnumSet.noneOf(FileAttributes::class.java),
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OVERWRITE_IF,
            EnumSet.noneOf(SMB2CreateOptions::class.java)
        )
        file.use { f ->
            f.outputStream.use { out ->
                input.copyTo(out, bufferSize = 65_536)
            }
            if (lastModifiedMillis != null) {
                // A rejected timestamp update must not fail an otherwise-good upload.
                runCatching {
                    val wft = FileTime.ofEpochMillis(lastModifiedMillis)
                    val noChange = FileBasicInformation.DONT_UPDATE
                    f.setFileInformation(FileBasicInformation(noChange, noChange, wft, wft, 0L))
                }
            }
        }
    }

    fun openRemoteInputStream(remotePath: String): InputStream {
        val file = share!!.openFile(
            remotePath,
            EnumSet.of(AccessMask.GENERIC_READ),
            EnumSet.noneOf(FileAttributes::class.java),
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            EnumSet.noneOf(SMB2CreateOptions::class.java)
        )
        // Obtain the stream carefully so a failure here doesn't leak the open SMBJ file handle.
        val stream = try {
            file.inputStream
        } catch (e: Exception) {
            runCatching { file.close() }
            throw e
        }
        // Wrap so closing the stream also closes the SMBJ file handle, at most once.
        return object : InputStream() {
            private var closed = false
            override fun read(): Int = stream.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = stream.read(b, off, len)
            override fun close() {
                if (closed) return
                closed = true
                runCatching { stream.close() }
                runCatching { file.close() }
            }
        }
    }

    private inline fun existsOrThrow(check: () -> Boolean): Boolean {
        return try {
            check()
        } catch (e: SMBApiException) {
            if (isNotFound(e)) false else throw e
        }
    }

    private fun isNotFound(e: SMBApiException): Boolean =
        e.status == NtStatus.STATUS_OBJECT_NAME_NOT_FOUND ||
            e.status == NtStatus.STATUS_OBJECT_PATH_NOT_FOUND

    companion object {
        private const val TIMEOUT_SECONDS = 30L
    }
}
