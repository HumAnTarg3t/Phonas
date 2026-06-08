package com.phonas.backup.backup

import android.net.Uri
import com.phonas.backup.backup.model.MediaFile
import com.phonas.backup.data.db.AppDatabase
import com.phonas.backup.data.db.dao.BackupFileDao
import com.phonas.backup.data.db.entity.BackupFileRecord
import com.phonas.backup.data.db.entity.BackupStatus
import com.phonas.backup.data.smb.RemoteFileInfo
import com.phonas.backup.data.smb.SmbClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DuplicateDetectorTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: BackupFileDao
    private lateinit var fileVerifier: FileVerifier
    private lateinit var smb: SmbClient
    private lateinit var detector: DuplicateDetector

    private val remotePath = "Camera\\IMG_001.jpg"
    private val testFile = MediaFile(
        uri = Uri.parse("content://test/1"),
        name = "IMG_001.jpg",
        relativePath = "",
        size = 1024L,
        lastModified = 1_000_000L
    )

    @Before
    fun setup() {
        db = mock()
        dao = mock()
        fileVerifier = mock()
        smb = mock()
        whenever(db.backupFileDao()).thenReturn(dao)
        detector = DuplicateDetector(db, fileVerifier)
    }

    @Test
    fun `returns true when DB record matches file metadata`() = runTest {
        whenever(dao.findByUri(any())).thenReturn(
            successRecord(size = testFile.size, lastModified = testFile.lastModified)
        )

        assertTrue(detector.shouldSkip(testFile, smb, remotePath))
    }

    @Test
    fun `returns false when file size changed since last backup`() = runTest {
        whenever(dao.findByUri(any())).thenReturn(
            successRecord(size = 9999L, lastModified = testFile.lastModified)
        )
        whenever(smb.getRemoteFileInfo(any())).thenReturn(null)

        assertFalse(detector.shouldSkip(testFile, smb, remotePath))
    }

    @Test
    fun `returns false when not in DB and not on NAS`() = runTest {
        whenever(dao.findByUri(any())).thenReturn(null)
        whenever(smb.getRemoteFileInfo(any())).thenReturn(null)

        assertFalse(detector.shouldSkip(testFile, smb, remotePath))
    }

    @Test
    fun `returns false when NAS file has different size`() = runTest {
        whenever(dao.findByUri(any())).thenReturn(null)
        whenever(smb.getRemoteFileInfo(any())).thenReturn(RemoteFileInfo(size = 9999L, lastModified = 0))

        assertFalse(detector.shouldSkip(testFile, smb, remotePath))
    }

    @Test
    fun `returns true and updates DB when NAS file matches hash`() = runTest {
        whenever(dao.findByUri(any())).thenReturn(null)
        whenever(smb.getRemoteFileInfo(any())).thenReturn(
            RemoteFileInfo(size = testFile.size, lastModified = 0)
        )
        val hash = "abc123"
        whenever(fileVerifier.computeLocalHash(any())).thenReturn(hash)
        whenever(fileVerifier.computeRemoteHash(any(), any())).thenReturn(hash)

        assertTrue(detector.shouldSkip(testFile, smb, remotePath))
        verify(dao).upsert(any())
    }

    @Test
    fun `returns false when NAS file size matches but hashes differ`() = runTest {
        whenever(dao.findByUri(any())).thenReturn(null)
        whenever(smb.getRemoteFileInfo(any())).thenReturn(
            RemoteFileInfo(size = testFile.size, lastModified = 0)
        )
        whenever(fileVerifier.computeLocalHash(any())).thenReturn("localHash")
        whenever(fileVerifier.computeRemoteHash(any(), any())).thenReturn("remoteHash")

        assertFalse(detector.shouldSkip(testFile, smb, remotePath))
    }

    private fun successRecord(size: Long, lastModified: Long) = BackupFileRecord(
        localUri = testFile.uri.toString(),
        relativePath = "",
        filename = testFile.name,
        fileSize = size,
        lastModified = lastModified,
        localSha256 = null,
        nasPath = remotePath,
        backedUpAt = 0L,
        status = BackupStatus.SUCCESS,
        errorMessage = null
    )
}
