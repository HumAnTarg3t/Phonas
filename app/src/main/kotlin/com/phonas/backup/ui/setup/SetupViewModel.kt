package com.phonas.backup.ui.setup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.phonas.backup.AppContainer
import com.phonas.backup.backup.WorkScheduler
import com.phonas.backup.data.prefs.AppSettings
import com.phonas.backup.data.prefs.FolderEntry
import com.phonas.backup.data.smb.SmbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class SetupUiState(
    val nasHost: String = "",
    val nasShare: String = "",
    val username: String = "",
    val hasExistingPassword: Boolean = false,
    val scheduleIntervalHours: Int = 24,
    val requireCharging: Boolean = false,
    val monitoredFolders: List<FolderEntry> = emptyList(),
    val sinceDateMillis: Long? = null,
    val maxLogEntries: Int = 100,
    val testConnectionResult: TestConnectionResult? = null,
    val isTesting: Boolean = false,
    val isSaved: Boolean = false,
    val importExportMessage: String? = null
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
                    monitoredFolders = settings.monitoredFolders,
                    sinceDateMillis = settings.sinceDateMillis,
                    maxLogEntries = settings.maxLogEntries
                )
            }
        }
    }

    fun addFolder(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        _uiState.update { it.copy(monitoredFolders = it.monitoredFolders + FolderEntry(uri.toString())) }
    }

    fun removeFolder(uri: String) {
        _uiState.update { it.copy(monitoredFolders = it.monitoredFolders.filter { f -> f.uri != uri }) }
    }

    fun updateFolderPrefix(uri: String, prefix: String) {
        _uiState.update {
            it.copy(monitoredFolders = it.monitoredFolders.map { f ->
                if (f.uri == uri) f.copy(prefix = prefix) else f
            })
        }
    }

    fun setSinceDate(millis: Long?) {
        _uiState.update { it.copy(sinceDateMillis = millis) }
    }

    fun save(
        context: Context,
        host: String,
        share: String,
        username: String,
        password: String,
        scheduleHours: Int,
        requireCharging: Boolean,
        maxLogEntries: Int
    ) {
        viewModelScope.launch {
            runCatching {
                val actualPassword = if (password.isBlank()) container.credentialStore.password else password
                container.credentialStore.save(host, share, username, actualPassword)
                val settings = AppSettings(
                    scheduleIntervalHours = scheduleHours,
                    requireCharging = requireCharging,
                    monitoredFolders = _uiState.value.monitoredFolders,
                    sinceDateMillis = _uiState.value.sinceDateMillis,
                    maxLogEntries = maxLogEntries
                )
                container.settingsStore.save(settings)
                WorkScheduler.schedule(context, settings)
                _uiState.update {
                    it.copy(
                        isSaved = true,
                        hasExistingPassword = actualPassword.isNotBlank(),
                        importExportMessage = "Settings saved"
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(importExportMessage = "Failed to save settings: ${e.message}") }
            }
        }
    }

    fun resetDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                container.db.backupFileDao().deleteAll()
                container.db.backupLogDao().deleteAll()
                _uiState.update { it.copy(importExportMessage = "Database reset — all backup records cleared") }
            }.onFailure { e ->
                _uiState.update { it.copy(importExportMessage = "Reset failed: ${e.message}") }
            }
        }
    }

    fun exportConfig(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val settings = container.settingsStore.settings.first()
                val json = JSONObject().apply {
                    put("version", 1)
                    put("nasHost", container.credentialStore.nasHost)
                    put("nasShare", container.credentialStore.nasShare)
                    put("username", container.credentialStore.username)
                    put("password", "")
                    put("scheduleIntervalHours", settings.scheduleIntervalHours)
                    put("requireCharging", settings.requireCharging)
                    put("sinceDateMillis", settings.sinceDateMillis ?: JSONObject.NULL)
                    put("maxLogEntries", settings.maxLogEntries)
                    val foldersArr = JSONArray()
                    settings.monitoredFolders.forEach { f ->
                        foldersArr.put(JSONObject().apply { put("uri", f.uri); put("prefix", f.prefix) })
                    }
                    put("monitoredFolders", foldersArr)
                }
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(json.toString(2))
                }
                _uiState.update { it.copy(importExportMessage = "Configuration exported") }
            }.onFailure { e ->
                _uiState.update { it.copy(importExportMessage = "Export failed: ${e.message}") }
            }
        }
    }

    fun importConfig(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.readText() ?: error("Cannot read file")
                val json = JSONObject(text)

                val host = json.optString("nasHost", "")
                val share = json.optString("nasShare", "")
                val username = json.optString("username", "")
                val password = json.optString("password", "")
                val sinceDate = if (json.isNull("sinceDateMillis")) null
                                else json.optLong("sinceDateMillis").takeIf { it > 0L }

                val folderEntries = mutableListOf<FolderEntry>()
                // Support both new format (monitoredFolders) and legacy (monitoredFolderUris)
                val foldersArr = json.optJSONArray("monitoredFolders")
                val legacyArr = json.optJSONArray("monitoredFolderUris")
                val sourceArr = foldersArr ?: legacyArr
                if (sourceArr != null) {
                    for (i in 0 until sourceArr.length()) {
                        val (uriStr, prefix) = if (foldersArr != null) {
                            val obj = sourceArr.getJSONObject(i)
                            obj.getString("uri") to obj.optString("prefix", "")
                        } else {
                            sourceArr.getString(i) to ""
                        }
                        runCatching {
                            context.contentResolver.takePersistableUriPermission(
                                Uri.parse(uriStr),
                                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }
                        folderEntries.add(FolderEntry(uri = uriStr, prefix = prefix))
                    }
                }

                val settings = AppSettings(
                    scheduleIntervalHours = json.optInt("scheduleIntervalHours", 24),
                    requireCharging = json.optBoolean("requireCharging", false),
                    sinceDateMillis = sinceDate,
                    maxLogEntries = json.optInt("maxLogEntries", 100),
                    monitoredFolders = folderEntries
                )
                container.settingsStore.save(settings)

                val actualPassword = password.ifBlank { container.credentialStore.password }
                container.credentialStore.save(host, share, username, actualPassword)
                WorkScheduler.schedule(context, settings)

                _uiState.update {
                    it.copy(
                        nasHost = host,
                        nasShare = share,
                        username = username,
                        hasExistingPassword = actualPassword.isNotBlank(),
                        scheduleIntervalHours = settings.scheduleIntervalHours,
                        requireCharging = settings.requireCharging,
                        sinceDateMillis = settings.sinceDateMillis,
                        maxLogEntries = settings.maxLogEntries,
                        monitoredFolders = folderEntries,
                        importExportMessage = "Configuration imported"
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(importExportMessage = "Import failed: ${e.message}") }
            }
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

    fun clearImportExportMessage() {
        _uiState.update { it.copy(importExportMessage = null) }
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
