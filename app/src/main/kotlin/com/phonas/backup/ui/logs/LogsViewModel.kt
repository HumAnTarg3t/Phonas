package com.phonas.backup.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.phonas.backup.AppContainer
import com.phonas.backup.data.db.entity.BackupLogEntry
import com.phonas.backup.data.db.entity.BackupSessionFile
import com.phonas.backup.data.db.entity.LogStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LogsViewModel(private val container: AppContainer) : ViewModel() {

    private val _filterStatus = MutableStateFlow<LogStatus?>(null)
    val filterStatus: StateFlow<LogStatus?> = _filterStatus.asStateFlow()

    val allLogs: StateFlow<List<BackupLogEntry>> = container.db.backupLogDao()
        .getAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Derive from allLogs so the underlying getAllLogs() flow is only collected once.
    val filteredLogs: StateFlow<List<BackupLogEntry>> = combine(
        allLogs,
        _filterStatus
    ) { logs, filter ->
        if (filter == null) logs else logs.filter { it.status == filter }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(status: LogStatus?) {
        _filterStatus.value = status
    }

    private val _selectedLogFiles = MutableStateFlow<List<BackupSessionFile>>(emptyList())
    val selectedLogFiles: StateFlow<List<BackupSessionFile>> = _selectedLogFiles.asStateFlow()

    private var detailJob: Job? = null

    fun loadLogDetail(logId: Long) {
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            container.db.backupSessionFileDao().getByLogId(logId).collect { files ->
                _selectedLogFiles.value = files
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return LogsViewModel(container) as T
        }
    }
}
