package com.phonas.backup.data.smb

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import java.io.InputStream
import java.util.EnumSet

data class RemoteFileInfo(val size: Long, val lastModified: Long)

class SmbClient {

    private var smb: SMBClient? = null
    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null

    fun connect(host: String, username: String, password: String, shareName: String) {
        disconnect()
        smb = SMBClient()
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

    fun fileExists(remotePath: String): Boolean =
        runCatching { share!!.fileExists(remotePath) }.getOrDefault(false)

    fun folderExists(remotePath: String): Boolean =
        runCatching { share!!.folderExists(remotePath) }.getOrDefault(false)

    fun getRemoteFileInfo(remotePath: String): RemoteFileInfo? {
        return runCatching {
            val info = share!!.getFileInformation(remotePath)
            RemoteFileInfo(
                size = info.standardInformation.endOfFile,
                lastModified = info.basicInformation.lastWriteTime.toEpochMillis()
            )
        }.getOrNull()
    }

    fun ensureDirectory(remotePath: String) {
        if (remotePath.isBlank()) return
        val parts = remotePath.split("\\").filter { it.isNotEmpty() }
        var current = ""
        for (part in parts) {
            current = if (current.isEmpty()) part else "$current\\$part"
            if (!folderExists(current)) {
                runCatching { share!!.mkdir(current) }
            }
        }
    }

    fun deleteFile(remotePath: String) {
        runCatching { share!!.rm(remotePath) }
    }

    fun uploadFile(input: InputStream, remotePath: String) {
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
        // Wrap so closing the stream also closes the SMBJ file handle
        val stream = file.inputStream
        return object : InputStream() {
            override fun read(): Int = stream.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = stream.read(b, off, len)
            override fun close() {
                stream.close()
                file.close()
            }
        }
    }


}
