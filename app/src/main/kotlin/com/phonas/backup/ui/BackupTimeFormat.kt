package com.phonas.backup.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun formatNextBackupLabel(nextMs: Long, nowMs: Long): String {
    val remaining = nextMs - nowMs
    return when {
        remaining <= 0 -> "Backup overdue — will run when on Wi-Fi"
        remaining < 60_000L -> "Next backup: in less than a minute"
        remaining < 3_600_000L -> "Next backup: in ${remaining / 60_000L} min"
        else -> "Next backup: ${formatNextBackupTime(nextMs)}"
    }
}

fun formatNextBackupTime(epochMillis: Long): String {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val tomorrowStart = cal.timeInMillis + 86_400_000L
    val dayAfterStart = tomorrowStart + 86_400_000L
    return when {
        epochMillis < tomorrowStart -> "today at ${timeFormat.format(Date(epochMillis))}"
        epochMillis < dayAfterStart -> "tomorrow at ${timeFormat.format(Date(epochMillis))}"
        else -> SimpleDateFormat("EEE d MMM 'at' HH:mm", Locale.getDefault()).format(Date(epochMillis))
    }
}
