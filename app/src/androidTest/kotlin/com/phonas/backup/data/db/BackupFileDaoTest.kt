package com.phonas.backup.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.phonas.backup.data.db.dao.BackupFileDao
import com.phonas.backup.data.db.entity.BackupFileRecord
import com.phonas.backup.data.db.entity.BackupStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupFileDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: BackupFileDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.backupFileDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndFindByUri() = runTest {
        val record = testRecord("content://test/1", BackupStatus.PENDING)
        dao.upsert(record)

        val found = dao.findByUri("content://test/1")
        assertNotNull(found)
        assertEquals(BackupStatus.PENDING, found!!.status)
    }

    @Test
    fun markSuccess_updatesStatus() = runTest {
        dao.upsert(testRecord("content://test/2", BackupStatus.PENDING))
        dao.markSuccess("content://test/2", "NAS\\file.jpg", "abc123", System.currentTimeMillis())

        val found = dao.findByUri("content://test/2")
        assertEquals(BackupStatus.SUCCESS, found!!.status)
        assertEquals("abc123", found.localSha256)
    }

    @Test
    fun markFailed_updatesStatus() = runTest {
        dao.upsert(testRecord("content://test/3", BackupStatus.PENDING))
        dao.markFailed("content://test/3", "IO error")

        val found = dao.findByUri("content://test/3")
        assertEquals(BackupStatus.FAILED, found!!.status)
        assertEquals("IO error", found.errorMessage)
    }

    @Test
    fun findAllFailed_returnsOnlyFailed() = runTest {
        dao.upsert(testRecord("content://test/10", BackupStatus.SUCCESS))
        dao.upsert(testRecord("content://test/11", BackupStatus.FAILED))
        dao.upsert(testRecord("content://test/12", BackupStatus.FAILED))

        val failed = dao.findAllFailed()
        assertEquals(2, failed.size)
    }

    @Test
    fun upsert_replacesExistingRecord() = runTest {
        val original = testRecord("content://test/5", BackupStatus.PENDING)
        dao.upsert(original)
        dao.upsert(original.copy(status = BackupStatus.SUCCESS, localSha256 = "hash"))

        val found = dao.findByUri("content://test/5")
        assertEquals(BackupStatus.SUCCESS, found!!.status)
    }

    @Test
    fun findByUri_returnsNullWhenNotFound() = runTest {
        val found = dao.findByUri("content://test/nonexistent")
        assertNull(found)
    }

    private fun testRecord(uri: String, status: BackupStatus) = BackupFileRecord(
        localUri = uri,
        relativePath = "",
        filename = "test.jpg",
        fileSize = 1024L,
        lastModified = 0L,
        localSha256 = null,
        nasPath = "NAS\\test.jpg",
        backedUpAt = 0L,
        status = status,
        errorMessage = null
    )
}
