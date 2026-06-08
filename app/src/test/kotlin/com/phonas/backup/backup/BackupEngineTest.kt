package com.phonas.backup.backup

import android.content.Context
import android.net.Uri
import com.phonas.backup.backup.model.BackupResult
import com.phonas.backup.backup.model.MediaFile
import com.phonas.backup.data.db.AppDatabase
import com.phonas.backup.data.db.dao.BackupFileDao
import com.phonas.backup.data.db.dao.BackupLogDao
import com.phonas.backup.data.db.entity.BackupLogEntry
import com.phonas.backup.data.db.entity.LogStatus
import com.phonas.backup.data.prefs.AppSettings
import com.phonas.backup.data.smb.SmbClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BackupEngineTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var fileDao: BackupFileDao
    private lateinit var logDao: BackupLogDao
    private lateinit var smbClient: SmbClient
    private lateinit var fileScanner: FileScanner
    private lateinit var duplicateDetector: DuplicateDetector
    private lateinit var fileVerifier: FileVerifier
    private lateinit var engine: BackupEngine

    private val credentials = NasCredentials("nas", "share", "user", "pass")
    private val settings = AppSettings(monitoredFolderUris = setOf("content://test/folder"))

    @Before
    fun setup() {
        context = mock()
        db = mock()
        fileDao = mock()
        logDao = mock()
        smbClient = mock()
        fileScanner = mock()
        duplicateDetector = mock()
        fileVerifier = mock()

        whenever(db.backupFileDao()).thenReturn(fileDao)
        whenever(db.backupLogDao()).thenReturn(logDao)
        whenever(logDao.insert(any())).thenReturn(1L)

        engine = BackupEngine(context, db, smbClient, fileScanner, duplicateDetector, fileVerifier)
    }

    @Test
    fun `all files skipped returns Success with zero copied`() = runTest {
        val file = testMediaFile()
        whenever(fileScanner.scan(any())).thenReturn(listOf(file))
        whenever(duplicateDetector.shouldSkip(any(), any(), any())).thenReturn(true)

        val result = engine.runBackup(settings, credentials)

        assertTrue(result is BackupResult.Success)
        val success = result as BackupResult.Success
        assertTrue(success.filesCopied == 0)
        assertTrue(success.filesSkipped == 1)
        verify(logDao).updateCompleted(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `SMB connect failure returns Failure and logs error`() = runTest {
        whenever(fileScanner.scan(any())).thenReturn(emptyList())
        whenever(smbClient.connect(any(), any(), any(), any()))
            .thenThrow(RuntimeException("Connection refused"))

        val result = engine.runBackup(settings, credentials)

        assertTrue(result is BackupResult.Failure)
        verify(logDao).updateFailed(any(), any(), any())
    }

    @Test
    fun `empty folder set returns Success with zero files`() = runTest {
        val emptySettings = AppSettings(monitoredFolderUris = emptySet())

        val result = engine.runBackup(emptySettings, credentials)

        assertTrue(result is BackupResult.Success)
        val success = result as BackupResult.Success
        assertTrue(success.filesCopied == 0)
        assertTrue(success.filesSkipped == 0)
    }

    private fun testMediaFile() = MediaFile(
        uri = Uri.parse("content://test/img.jpg"),
        name = "img.jpg",
        relativePath = "",
        size = 1024L,
        lastModified = 0L
    )
}
