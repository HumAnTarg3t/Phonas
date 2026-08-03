package com.phonas.backup.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.phonas.backup.data.prefs.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression guard for the double-backup bug: scheduling must produce exactly ONE trigger
 * (the periodic work) and no separate immediate/alarm-driven work, and re-scheduling must not
 * stack duplicates.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WorkSchedulerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @Test
    fun `schedule enqueues exactly one periodic work and no immediate work`() {
        WorkScheduler.schedule(context, AppSettings(scheduleIntervalMinutes = 60))

        val wm = WorkManager.getInstance(context)
        val periodic = wm.getWorkInfosForUniqueWork(WorkScheduler.WORK_NAME_PERIODIC).get()
        val immediate = wm.getWorkInfosForUniqueWork(WorkScheduler.WORK_NAME_IMMEDIATE).get()

        assertEquals(1, periodic.size)
        assertTrue(immediate.isEmpty())
    }

    @Test
    fun `scheduling twice does not stack duplicate periodic work`() {
        val settings = AppSettings(scheduleIntervalMinutes = 60)
        WorkScheduler.schedule(context, settings)
        WorkScheduler.schedule(context, settings)

        val periodic = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(WorkScheduler.WORK_NAME_PERIODIC).get()
        assertEquals(1, periodic.size)
    }
}
