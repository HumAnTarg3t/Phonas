package com.phonas.backup.ui.setup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.phonas.backup.AppContainer
import com.phonas.backup.backup.NasCredentials
import com.phonas.backup.backup.WorkScheduler
import com.phonas.backup.data.prefs.AppSettings
import com.phonas.backup.data.smb.SmbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SetupUiState(
    val nasHost: String = "",
    val nasShare: String = "",
    val username: String = "",
    val hasExistingPassword: Boolean = false,
    val scheduleIntervalHours: Int = 24,
    val requireCharging: Boolean = false,
    val monitoredFolderUris: Set<String> = emptySet(),
    val testConnectionResult: TestConnectionResult? = null,
    val isTesting: Boolean = false,
    val isSaved: Boolean = false
)

sealed class TestConnectionResult {
    data object Success : TestConnectionResult()
    data class Failure(val message: String) : TestConnectionResult()
}

class SetupViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = container.settingsStore.settings.first()
            _uiState.update {
                it.copy(
                    nasHost = container.credentialStore.nasHost,
                    nasShare = container.credentialStore.nasShare,
                    username = container.credentialStore.username,
                    hasExistingPassword = container.credentialStore.password.isNotBlank(),
                    scheduleIntervalHours = settings.scheduleIntervalHours,
                    requireCharging = settings.requireCharging,
                    monitoredFolderUris = settings.monitoredFolderUris
                )
            }
        }
    }

    fun addFolder(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        _uiState.update { it.copy(monitoredFolderUris = it.monitoredFolderUris + uri.toString()) }
    }

    fun removeFolder(uri: String) {
        _uiState.update { it.copy(monitoredFolderUris = it.monitoredFolderUris - uri) }
    }

    fun save(
        context: Context,
        host: String,
        share: String,
        username: String,
        password: String,
        scheduleHours: Int,
        requireCharging: Boolean
    ) {
        viewModelScope.launch {
            // If the user left the password field blank, keep the existing stored password
            val actualPassword = if (password.isBlank()) {
                container.credentialStore.password
            } else {
                password
            }
            container.credentialStore.save(host, share, username, actualPassword)
            val settings = AppSettings(
                scheduleIntervalHours = scheduleHours,
                requireCharging = requireCharging,
                monitoredFolderUris = _uiState.value.monitoredFolderUris
            )
            container.settingsStore.save(settings)
            WorkScheduler.schedule(context, settings)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun testConnection(host: String, share: String, username: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, testConnectionResult = null) }
            val result = withContext(Dispatchers.IO) {
                val smb = SmbClient()
                runCatching {
                    smb.connect(host, username, password, share)
                    smb.disconnect()
                    TestConnectionResult.Success
                }.getOrElse {
                    TestConnectionResult.Failure(sanitize(it.message))
                }
            }
            _uiState.update { it.copy(isTesting = false, testConnectionResult = result) }
        }
    }

    fun clearTestResult() {
        _uiState.update { it.copy(testConnectionResult = null) }
    }

    fun clearSaved() {
        _uiState.update { it.copy(isSaved = false) }
    }

    private fun sanitize(message: String?) = message?.let {
        if (it.contains("password", ignoreCase = true) || it.contains("auth", ignoreCase = true))
            "Authentication failed"
        else it
    } ?: "Connection failed"

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SetupViewModel(container) as T
        }
    }
}
