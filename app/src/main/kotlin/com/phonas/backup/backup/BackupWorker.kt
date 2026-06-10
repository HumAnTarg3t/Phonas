package com.phonas.backup.backup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.phonas.backup.BackupApplication
import com.phonas.backup.R
import com.phonas.backup.backup.model.BackupResult
import kotlinx.coroutines.flow.first
import java.net.InetAddress

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val container get() = (applicationContext as BackupApplication).container

    override suspend fun doWork(): Result {
        setForeground(getForegroundInfo())

        val settings = container.settingsStore.settings.first()
        val credentials = container.credentialStore

        if (!credentials.isConfigured()) {
            return Result.failure()
        }

        if (!isOnWifi()) {
            AlarmScheduler.schedule(applicationContext, System.currentTimeMillis() + RETRY_INTERVAL_MS)
            return Result.retry()
        }

        if (!isNasReachable(credentials.nasHost)) {
            AlarmScheduler.schedule(applicationContext, System.currentTimeMillis() + RETRY_INTERVAL_MS)
            return Result.retry()
        }

        val nasCredentials = NasCredentials(
            host = credentials.nasHost,
            share = credentials.nasShare,
            username = credentials.username,
            password = credentials.password
        )

        container.backupEngine.progressCallback = { progress ->
            setProgress(
                workDataOf(
                    KEY_CURRENT_FILE to progress.currentFile,
                    KEY_FILES_DONE to progress.filesDone,
                    KEY_FILES_TOTAL to progress.filesTotal,
                    KEY_BYTES_DONE to progress.bytesDone,
                    KEY_BYTES_TOTAL to progress.bytesTotal
                )
            )
        }

        return when (container.backupEngine.runBackup(settings, nasCredentials)) {
            is BackupResult.Success -> Result.success()
            is BackupResult.Failure -> {
                AlarmScheduler.schedule(applicationContext, System.currentTimeMillis() + RETRY_INTERVAL_MS)
                Result.retry()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        ensureNotificationChannel()
        val notification = buildNotification(
            applicationContext.getString(R.string.notification_backing_up)
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun isOnWifi(): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            && !caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    private fun isNasReachable(host: String): Boolean {
        return runCatching {
            InetAddress.getByName(host).isReachable(3000)
        }.getOrDefault(false)
    }

    private fun ensureNotificationChannel() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "backup_progress"
        const val NOTIFICATION_ID = 1001

        private const val RETRY_INTERVAL_MS = 5 * 60 * 1000L

        const val KEY_CURRENT_FILE = "current_file"
        const val KEY_FILES_DONE = "files_done"
        const val KEY_FILES_TOTAL = "files_total"
        const val KEY_BYTES_DONE = "bytes_done"
        const val KEY_BYTES_TOTAL = "bytes_total"
    }
}
