package com.phonas.backup

import android.app.Application
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

        // Clean up any log entries left as RUNNING from a previous crash or forced stop
        applicationScope.launch {
            container.db.backupLogDao().cancelStaleRunning(System.currentTimeMillis())
        }
    }
}
