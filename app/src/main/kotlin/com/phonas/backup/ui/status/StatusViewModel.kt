package com.phonas.backup.ui.status

import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.phonas.backup.AppContainer
import com.phonas.backup.backup.BackupWorker
import com.phonas.backup.backup.WorkScheduler
import com.phonas.backup.backup.model.BackupProgress
import com.phonas.backup.data.db.entity.BackupLogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BackupStatusDisplay {
    IDLE, BACKING_UP, WAITING_WIFI, WAITING_NAS, COMPLETED, ERROR
}

data class StatusUiState(
    val status: BackupStatusDisplay = BackupStatusDisplay.IDLE,
    val progress: BackupProgress? = null,
    val latestLog: BackupLogEntry? = null,
    val isConfigured: Boolean = false,
    val nextBackupMillis: Long? = null,
    val isBatteryOptimized: Boolean = false
)

class StatusViewModel(
    private val container: AppContainer,
    context: Context
) : ViewModel() {

    private val workManager = WorkManager.getInstance(context)
    private val _uiState = MutableStateFlow(StatusUiState())
    val uiState: StateFlow<StatusUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(isConfigured = container.credentialStore.isConfigured()) }

        // Observe latest log
        container.db.backupLogDao().getAllLogs()
            .onEach { logs ->
                _uiState.update { it.copy(latestLog = logs.firstOrNull()) }
            }
            .launchIn(viewModelScope)

        // Observe work state (periodic + immediate)
        val periodicFlow = workManager.getWorkInfosForUniqueWorkFlow(WorkScheduler.WORK_NAME_PERIODIC)
        val immediateFlow = workManager.getWorkInfosForUniqueWorkFlow(WorkScheduler.WORK_NAME_IMMEDIATE)

        combine(periodicFlow, immediateFlow) { periodic, immediate ->
            val immediateInfo = immediate.firstOrNull()
            val periodicInfo = periodic.firstOrNull()
            // Immediate work takes precedence; only it can show WAITING_WIFI
            if (immediateInfo != null && (immediateInfo.state == WorkInfo.State.RUNNING
                    || immediateInfo.state == WorkInfo.State.ENQUEUED
                    || immediateInfo.state == WorkInfo.State.BLOCKED)) {
                resolveStatus(immediateInfo, isImmediate = true)
            } else {
                resolveStatus(periodicInfo, isImmediate = false)
            }
        }.onEach { (status, progress) ->
            _uiState.update { it.copy(status = status, progress = progress) }
        }.launchIn(viewModelScope)

        combine(
            container.db.backupLogDao().getLastCompletedLogFlow(),
            container.settingsStore.settings
        ) { lastCompleted, settings ->
            val base = lastCompleted?.endTime ?: lastCompleted?.startTime ?: System.currentTimeMillis()
            base + settings.scheduleIntervalMinutes * 60_000L
        }.onEach { nextMillis ->
            _uiState.update { it.copy(nextBackupMillis = nextMillis) }
        }.launchIn(viewModelScope)
    }

    fun startBackupNow(context: Context) {
        viewModelScope.launch {
            val settings = container.settingsStore.settings.first()
            WorkScheduler.runNow(context, settings)
        }
    }

    fun cancelBackupNow(context: Context) {
        viewModelScope.launch { WorkScheduler.cancelImmediate(context) }
    }

    fun refreshConfigured() {
        _uiState.update { it.copy(isConfigured = container.credentialStore.isConfigured()) }
    }

    fun refreshBatteryOptimization(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        _uiState.update { it.copy(isBatteryOptimized = !pm.isIgnoringBatteryOptimizations(context.packageName)) }
    }

    private fun resolveStatus(workInfo: WorkInfo?, isImmediate: Boolean): Pair<BackupStatusDisplay, BackupProgress?> {
        if (workInfo == null) return Pair(BackupStatusDisplay.IDLE, null)

        return when (workInfo.state) {
            WorkInfo.State.RUNNING -> {
                val p = workInfo.progress
                val progress = BackupProgress(
                    currentFile = p.getString(BackupWorker.KEY_CURRENT_FILE) ?: "",
                    filesDone = p.getInt(BackupWorker.KEY_FILES_DONE, 0),
                    filesTotal = p.getInt(BackupWorker.KEY_FILES_TOTAL, 0),
                    bytesDone = p.getLong(BackupWorker.KEY_BYTES_DONE, 0L),
                    bytesTotal = p.getLong(BackupWorker.KEY_BYTES_TOTAL, 0L)
                )
                Pair(BackupStatusDisplay.BACKING_UP, progress)
            }
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
                if (isImmediate) Pair(BackupStatusDisplay.WAITING_WIFI, null)
                else Pair(BackupStatusDisplay.IDLE, null)
            WorkInfo.State.SUCCEEDED ->
                Pair(BackupStatusDisplay.COMPLETED, null)
            WorkInfo.State.FAILED ->
                Pair(BackupStatusDisplay.ERROR, null)
            WorkInfo.State.CANCELLED ->
                Pair(BackupStatusDisplay.IDLE, null)
        }
    }

    class Factory(private val container: AppContainer, private val context: Context) :
        ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return StatusViewModel(container, context) as T
        }
    }
}
