package com.phonas.backup.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.phonas.backup.AppContainer
import com.phonas.backup.data.db.entity.BackupLogEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class LogsViewModel(container: AppContainer) : ViewModel() {

    val logs: StateFlow<List<BackupLogEntry>> = container.db.backupLogDao()
        .getAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return LogsViewModel(container) as T
        }
    }
}
