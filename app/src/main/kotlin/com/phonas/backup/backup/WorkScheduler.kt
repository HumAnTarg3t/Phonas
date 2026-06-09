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

object WorkScheduler {

    const val WORK_NAME_PERIODIC = "nas_backup_periodic"
    const val WORK_NAME_IMMEDIATE = "nas_backup_immediate"

    fun schedule(context: Context, settings: AppSettings) {
        val constraints = buildConstraints(settings)

        val request = PeriodicWorkRequestBuilder<BackupWorker>(
            settings.scheduleIntervalHours.toLong(), TimeUnit.HOURS,
            15L, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

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
