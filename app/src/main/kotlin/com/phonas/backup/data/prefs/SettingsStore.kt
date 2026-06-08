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

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val SCHEDULE_HOURS = intPreferencesKey("schedule_hours")
        val REQUIRE_CHARGING = booleanPreferencesKey("require_charging")
        // Folder URIs stored as pipe-separated string; DataStore has no native Set type
        val FOLDER_URIS = stringPreferencesKey("folder_uris")
        // 0L = no filter (back up all files regardless of date)
        val SINCE_DATE = longPreferencesKey("since_date")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            scheduleIntervalHours = prefs[Keys.SCHEDULE_HOURS] ?: 24,
            requireCharging = prefs[Keys.REQUIRE_CHARGING] ?: false,
            monitoredFolderUris = prefs[Keys.FOLDER_URIS]
                ?.split("|")
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: emptySet(),
            sinceDateMillis = prefs[Keys.SINCE_DATE]?.takeIf { it > 0L }
        )
    }

    suspend fun save(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SCHEDULE_HOURS] = settings.scheduleIntervalHours
            prefs[Keys.REQUIRE_CHARGING] = settings.requireCharging
            prefs[Keys.FOLDER_URIS] = settings.monitoredFolderUris.joinToString("|")
            prefs[Keys.SINCE_DATE] = settings.sinceDateMillis ?: 0L
        }
    }
}
