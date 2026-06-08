package com.phonas.backup.backup

import android.content.Context
import android.net.Uri
import com.phonas.backup.backup.model.MediaFile
import com.phonas.backup.data.smb.RemoteFileInfo
import com.phonas.backup.data.smb.SmbClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.security.MessageDigest

class FileVerifierTest {

    private lateinit var context: Context
    private lateinit var smb: SmbClient
    private lateinit var verifier: FileVerifier

    private val testBytes = "Hello, NAS!".toByteArray()
    private val testHash = sha256(testBytes)

    @Before
    fun setup() {
        context = mock()
        smb = mock()
        verifier = FileVerifier(context)
    }

    @Test
    fun `verify returns false when remote file size differs`() {
        val file = mediaFile(size = 100L)
        whenever(smb.getRemoteFileInfo(any())).thenReturn(RemoteFileInfo(size = 999L, lastModified = 0))

        val result = verifier.verify(file, "remote\\path\\file.jpg", "anyHash", smb)

        assertFalse(result)
    }

    @Test
    fun `verify returns true on matching size for large file without hash`() {
        val largeSize = 600L * 1024 * 1024  // 600 MB — above hash threshold
        val file = mediaFile(size = largeSize)
        whenever(smb.getRemoteFileInfo(any())).thenReturn(RemoteFileInfo(size = largeSize, lastModified = 0))

        val result = verifier.verify(file, "remote\\path\\file.mp4", "ignoredHash", smb)

        assertTrue(result)
    }

    @Test
    fun `verify returns true when sizes and hashes match for small file`() {
        val file = mediaFile(size = testBytes.size.toLong())
        whenever(smb.getRemoteFileInfo(any())).thenReturn(
            RemoteFileInfo(size = testBytes.size.toLong(), lastModified = 0)
        )
        whenever(smb.openRemoteInputStream(any())).thenReturn(ByteArrayInputStream(testBytes))

        val result = verifier.verify(file, "remote\\path\\file.jpg", testHash, smb)

        assertTrue(result)
    }

    @Test
    fun `verify returns false when hashes differ`() {
        val file = mediaFile(size = testBytes.size.toLong())
        val differentBytes = "Different content".toByteArray()
        whenever(smb.getRemoteFileInfo(any())).thenReturn(
            RemoteFileInfo(size = testBytes.size.toLong(), lastModified = 0)
        )
        // Return different bytes from remote — remote hash won't match localHash
        whenever(smb.openRemoteInputStream(any())).thenReturn(ByteArrayInputStream(differentBytes))

        val result = verifier.verify(file, "remote\\path\\file.jpg", testHash, smb)

        assertFalse(result)
    }

    @Test
    fun `verify returns false when remote file info is null`() {
        val file = mediaFile(size = 100L)
        whenever(smb.getRemoteFileInfo(any())).thenReturn(null)

        val result = verifier.verify(file, "remote\\path\\file.jpg", "anyHash", smb)

        assertFalse(result)
    }

    private fun mediaFile(size: Long) = MediaFile(
        uri = Uri.EMPTY,
        name = "test.jpg",
        relativePath = "",
        size = size,
        lastModified = 0L
    )

    private fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }
}
