package com.phonas.backup.backup

import android.net.Uri
import com.phonas.backup.backup.model.MediaFile
import com.phonas.backup.data.db.AppDatabase
import com.phonas.backup.data.db.dao.BackupFileDao
import com.phonas.backup.data.db.entity.BackupFileRecord
import com.phonas.backup.data.db.entity.BackupStatus
import com.phonas.backup.data.smb.RemoteFileInfo
import com.phonas.backup.data.smb.SmbClient
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DuplicateDetectorTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: BackupFileDao
    private lateinit var fileVerifier: FileVerifier
    private lateinit var smb: SmbClient
    private lateinit var detector: DuplicateDetector

    private val remotePath = "Camera\\IMG_001.jpg"
    private val twinPath = "DCIM\\Camera\\IMG_original.jpg"
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
        // A suspend fun returning Boolean gives null from an unstubbed mock, and Kotlin's
        // non-null unboxing turns that into an NPE. Default the content gate to "no
        // candidate" so the tests that predate it exercise the same path as before.
        dao = mock {
            onBlocking { hasHashedFileOfSize(any(), any()) } doReturn false
        }
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

    // --- content dedup: same bytes already on the NAS under a different path ------

    @Test
    fun `never reads the file when no same-size candidate exists`() = runTest {
        whenever(dao.findByUri(any())).thenReturn(null)
        whenever(dao.hasHashedFileOfSize(any(), any())).thenReturn(false)
        whenever(smb.getRemoteFileInfo(any())).thenReturn(null)

        assertFalse(detector.shouldSkip(testFile, smb, remotePath))
        verify(fileVerifier, never()).computeLocalHash(any())
    }

    @Test
    fun `fast path short-circuits before the content gate`() = runTest {
        whenever(dao.findByUri(any())).thenReturn(
            successRecord(size = testFile.size, lastModified = testFile.lastModified)
        )

        assertTrue(detector.shouldSkip(testFile, smb, remotePath))
        verify(dao, never()).hasHashedFileOfSize(any(), any())
    }

    @Test
    fun `file above the hash cap never enters the content gate`() = runTest {
        val huge = testFile.copy(size = BackupLimits.DEDUP_HASH_MAX_BYTES + 1)
        whenever(dao.findByUri(any())).thenReturn(null)
        whenever(smb.getRemoteFileInfo(any())).thenReturn(null)

        assertFalse(detector.shouldSkip(huge, smb, remotePath))
        verify(dao, never()).hasHashedFileOfSize(any(), any())
        verify(fileVerifier, never()).computeLocalHash(any())
    }

    @Test
    fun `skips a file whose content is already on the NAS under another path`() = runTest {
        givenContentTwin()

        assertTrue(detector.shouldSkip(testFile, smb, remotePath))
    }

    @Test
    fun `records the twin nasPath and its own metadata when skipping`() = runTest {
        givenContentTwin()

        assertTrue(detector.shouldSkip(testFile, smb, remotePath))

        val captor = argumentCaptor<BackupFileRecord>()
        verify(dao).upsert(captor.capture())
        val saved = captor.firstValue
        // The bytes live at the twin's path, but the row describes THIS file, so the fast path
        // catches it on the next run without re-hashing.
        assertEquals(twinPath, saved.nasPath)
        assertEquals(CONTENT_HASH, saved.localSha256)
        assertEquals(testFile.size, saved.fileSize)
        assertEquals(testFile.lastModified, saved.lastModified)
        assertEquals(BackupStatus.SUCCESS, saved.status)
    }

    @Test
    fun `falls through when the same size holds different content`() = runTest {
        whenever(dao.findByUri(any())).thenReturn(null)
        whenever(dao.hasHashedFileOfSize(any(), any())).thenReturn(true)
        whenever(fileVerifier.computeLocalHash(any())).thenReturn(CONTENT_HASH)
        whenever(dao.findContentDuplicate(any(), any(), any())).thenReturn(null)
        whenever(smb.getRemoteFileInfo(any())).thenReturn(null)

        assertFalse(detector.shouldSkip(testFile, smb, remotePath))
    }

    @Test
    fun `falls through when the twin has vanished from the NAS`() = runTest {
        givenContentTwin(twinOnNas = null)

        assertFalse(detector.shouldSkip(testFile, smb, remotePath))
        verify(dao, never()).upsert(any())
    }

    @Test
    fun `falls through when the twin on the NAS changed size`() = runTest {
        givenContentTwin(twinOnNas = RemoteFileInfo(size = 9999L, lastModified = 0))

        assertFalse(detector.shouldSkip(testFile, smb, remotePath))
        verify(dao, never()).upsert(any())
    }

    @Test
    fun `a file never matches its own record`() = runTest {
        givenContentTwin()

        detector.shouldSkip(testFile, smb, remotePath)

        verify(dao).hasHashedFileOfSize(eq(testFile.size), eq(testFile.uri.toString()))
        verify(dao).findContentDuplicate(any(), any(), eq(testFile.uri.toString()))
    }

    @Test
    fun `an unreadable file falls through instead of throwing`() = runTest {
        whenever(dao.findByUri(any())).thenReturn(null)
        whenever(dao.hasHashedFileOfSize(any(), any())).thenReturn(true)
        whenever(fileVerifier.computeLocalHash(any())).thenAnswer { throw IOException("gone") }
        whenever(smb.getRemoteFileInfo(any())).thenReturn(null)

        assertFalse(detector.shouldSkip(testFile, smb, remotePath))
    }

    /** The happy path: a same-size candidate exists, hashes match, twin still on the NAS. */
    private suspend fun givenContentTwin(
        twinOnNas: RemoteFileInfo? = RemoteFileInfo(size = 1024L, lastModified = 0)
    ) {
        whenever(dao.findByUri(any())).thenReturn(null)
        whenever(dao.findByRelativePathAndName(any(), any())).thenReturn(null)
        whenever(dao.hasHashedFileOfSize(any(), any())).thenReturn(true)
        whenever(fileVerifier.computeLocalHash(any())).thenReturn(CONTENT_HASH)
        whenever(dao.findContentDuplicate(any(), any(), any())).thenReturn(
            successRecord(size = testFile.size, lastModified = 1L).copy(
                localUri = "content://test/original",
                localSha256 = CONTENT_HASH,
                nasPath = twinPath
            )
        )
        whenever(smb.getRemoteFileInfo(eq(twinPath))).thenReturn(twinOnNas)
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

    private companion object {
        const val CONTENT_HASH = "aaa111"
    }
}
