package com.phonas.backup.data.prefs

data class AppSettings(
    val scheduleIntervalHours: Int = 24,
    val requireCharging: Boolean = false,
    val monitoredFolderUris: Set<String> = emptySet()
)
