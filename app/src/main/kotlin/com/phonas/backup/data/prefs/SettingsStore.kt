package com.phonas.backup.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val SCHEDULE_HOURS = intPreferencesKey("schedule_hours")
        val REQUIRE_CHARGING = booleanPreferencesKey("require_charging")
        val FOLDER_URIS = stringPreferencesKey("folder_uris")      // legacy, read-only for migration
        val FOLDERS_JSON = stringPreferencesKey("folders_json")     // current format
        val SINCE_DATE = longPreferencesKey("since_date")
        val MAX_LOG_ENTRIES = intPreferencesKey("max_log_entries")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            scheduleIntervalHours = prefs[Keys.SCHEDULE_HOURS] ?: 24,
            requireCharging = prefs[Keys.REQUIRE_CHARGING] ?: false,
            monitoredFolders = when {
                prefs[Keys.FOLDERS_JSON] != null -> parseFolders(prefs[Keys.FOLDERS_JSON]!!)
                prefs[Keys.FOLDER_URIS] != null -> prefs[Keys.FOLDER_URIS]!!
                    .split("|").filter { it.isNotBlank() }
                    .map { FolderEntry(uri = it, prefix = "") }
                else -> emptyList()
            },
            sinceDateMillis = prefs[Keys.SINCE_DATE]?.takeIf { it > 0L },
            maxLogEntries = prefs[Keys.MAX_LOG_ENTRIES] ?: 100
        )
    }

    suspend fun save(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SCHEDULE_HOURS] = settings.scheduleIntervalHours
            prefs[Keys.REQUIRE_CHARGING] = settings.requireCharging
            prefs[Keys.FOLDERS_JSON] = serializeFolders(settings.monitoredFolders)
            prefs[Keys.SINCE_DATE] = settings.sinceDateMillis ?: 0L
            prefs[Keys.MAX_LOG_ENTRIES] = settings.maxLogEntries
        }
    }

    private fun parseFolders(json: String): List<FolderEntry> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            FolderEntry(uri = obj.getString("uri"), prefix = obj.optString("prefix", ""))
        }
    } catch (e: Exception) { emptyList() }

    private fun serializeFolders(folders: List<FolderEntry>): String {
        val arr = JSONArray()
        folders.forEach { f ->
            arr.put(JSONObject().apply { put("uri", f.uri); put("prefix", f.prefix) })
        }
        return arr.toString()
    }
}
