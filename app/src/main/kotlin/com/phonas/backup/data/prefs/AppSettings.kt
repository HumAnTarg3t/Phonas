package com.phonas.backup.data.prefs

data class FolderEntry(val uri: String, val prefix: String = "")

data class AppSettings(
    val scheduleIntervalHours: Int = 24,
    val requireCharging: Boolean = false,
    val monitoredFolders: List<FolderEntry> = emptyList(),
    val sinceDateMillis: Long? = null,
    val maxLogEntries: Int = 100,
    val scanAllMedia: Boolean = false
)
