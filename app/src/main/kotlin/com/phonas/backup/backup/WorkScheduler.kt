package com.phonas.backup.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.phonas.backup.data.prefs.AppSettings
import java.util.concurrent.TimeUnit

/**
 * WorkManager is the single source of truth for scheduling. The periodic request
 * reschedules itself and survives reboots, so no AlarmManager is needed. Keeping two
 * independent schedulers (as a previous version did) is exactly what caused backups to
 * run twice per cycle.
 */

object WorkScheduler {

    const val WORK_NAME_PERIODIC = "nas_backup_periodic"
    const val WORK_NAME_IMMEDIATE = "nas_backup_immediate"

    fun schedule(context: Context, settings: AppSettings) {
        val constraints = buildConstraints(settings)

        val intervalMinutes = settings.scheduleIntervalMinutes.toLong()
        val flexMinutes = 5L
        val request = PeriodicWorkRequestBuilder<BackupWorker>(
            intervalMinutes, TimeUnit.MINUTES,
            flexMinutes, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        // UPDATE (not CANCEL_AND_REENQUEUE) so pressing Save applies a changed interval without
        // restarting the running cadence — re-enqueuing on every Save would spawn an extra run.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun runNow(context: Context, settings: AppSettings) {
        val constraints = buildConstraints(settings)

        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME_IMMEDIATE,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
    }

    fun cancelImmediate(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_IMMEDIATE)
    }

    private fun buildConstraints(settings: AppSettings): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .apply { if (settings.requireCharging) setRequiresCharging(true) }
            .build()
    }
}
