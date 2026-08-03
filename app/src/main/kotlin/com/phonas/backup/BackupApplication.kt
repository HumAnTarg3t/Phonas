package com.phonas.backup

import android.app.Application
import com.phonas.backup.backup.MediaChangeObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class BackupApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        // Android ships an incomplete BouncyCastle provider; SMBJ needs the full one.
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())

        container = AppContainer(this)

        // Clean up log entries left as RUNNING from a previous crash or forced stop. Only reap
        // rows older than the max plausible runtime so a worker starting concurrently with this
        // cold start (its RUNNING log freshly inserted) is never clobbered.
        applicationScope.launch {
            val now = System.currentTimeMillis()
            container.db.backupLogDao().cancelStaleRunning(cutoff = now - MAX_BACKUP_RUNTIME_MS, now = now)
        }

        // Low-latency trigger: back up shortly after new media is indexed (debounced).
        MediaChangeObserver(
            this, applicationScope, container.credentialStore, container.settingsStore
        ).register()
    }

    companion object {
        // dataSync foreground services are capped near 6h on modern Android; anything older
        // than this is definitely orphaned.
        private const val MAX_BACKUP_RUNTIME_MS = 6 * 60 * 60 * 1000L
    }
}
