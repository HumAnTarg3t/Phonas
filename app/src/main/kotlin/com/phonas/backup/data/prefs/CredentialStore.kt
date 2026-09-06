package com.phonas.backup.data.prefs

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore

class CredentialStore(context: Context) {

    private val appContext = context.applicationContext

    // Built lazily so the disk + Keystore work happens off the app's UI startup path (first
    // access is from a worker or a ViewModel's IO scope), not eagerly in Application.onCreate.
    private val prefs: SharedPreferences by lazy { buildPrefs() }

    private fun buildPrefs(): SharedPreferences {
        return try {
            createEncryptedPrefs()
        } catch (e: Exception) {
            // A corrupted keyset/master key would otherwise crash the app on every launch. Wipe
            // the encrypted store (and master key) and recreate — the user re-enters credentials,
            // which beats a permanent crash loop.
            Log.w(TAG, "EncryptedSharedPreferences unusable; resetting credential store", e)
            runCatching { appContext.deleteSharedPreferences(PREFS_NAME) }
            runCatching {
                val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                ks.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            }
            createEncryptedPrefs()
        }
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var nasHost: String
        get() = prefs.getString(KEY_HOST, "") ?: ""
        set(value) = prefs.edit().putString(KEY_HOST, value).apply()

    var nasShare: String
        get() = prefs.getString(KEY_SHARE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SHARE, value).apply()

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    fun isConfigured(): Boolean =
        nasHost.isNotBlank() && nasShare.isNotBlank()
            && username.isNotBlank() && password.isNotBlank()

    fun save(host: String, share: String, user: String, pass: String) {
        prefs.edit()
            .putString(KEY_HOST, normalizeHost(host))
            .putString(KEY_SHARE, normalizeShare(share))
            .putString(KEY_USERNAME, user.trim())
            .putString(KEY_PASSWORD, pass)  // never trim the password
            .apply()
    }

    private fun normalizeHost(raw: String): String =
        raw.trim().removePrefix("smb://").trimStart('\\', '/').trimEnd('\\', '/')

    private fun normalizeShare(raw: String): String =
        raw.trim().trim('\\', '/')

    companion object {
        private const val TAG = "CredentialStore"
        private const val PREFS_NAME = "nas_credentials"
        private const val KEY_HOST = "nas_host"
        private const val KEY_SHARE = "nas_share"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
    }
}
