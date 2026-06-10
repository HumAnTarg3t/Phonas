package com.phonas.backup.backup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.phonas.backup.BackupApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BackupAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = (context.applicationContext as BackupApplication).container
                val settings = container.settingsStore.settings.first()
                WorkScheduler.runNow(context, settings)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
