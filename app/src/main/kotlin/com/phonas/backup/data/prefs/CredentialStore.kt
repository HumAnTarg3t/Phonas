package com.phonas.backup.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class CredentialStore(context: Context) {

    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context,
            "nas_credentials",
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
            .putString(KEY_HOST, host)
            .putString(KEY_SHARE, share)
            .putString(KEY_USERNAME, user)
            .putString(KEY_PASSWORD, pass)
            .apply()
    }

    companion object {
        private const val KEY_HOST = "nas_host"
        private const val KEY_SHARE = "nas_share"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
    }
}
