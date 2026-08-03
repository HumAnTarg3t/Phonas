package com.phonas.backup

import android.content.Context
import com.phonas.backup.backup.BackupEngine
import com.phonas.backup.backup.DuplicateDetector
import com.phonas.backup.backup.FileScanner
import com.phonas.backup.backup.FileVerifier
import com.phonas.backup.backup.MediaStoreScanner
import com.phonas.backup.data.db.AppDatabase
import com.phonas.backup.data.prefs.CredentialStore
import com.phonas.backup.data.prefs.SettingsStore

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val db = AppDatabase.create(context)
    val credentialStore = CredentialStore(context)
    val settingsStore = SettingsStore(context)
    val fileVerifier = FileVerifier(context)
    val fileScanner = FileScanner(context)
    val mediaStoreScanner = MediaStoreScanner(context)
    val duplicateDetector = DuplicateDetector(db, fileVerifier)
    // BackupEngine constructs a fresh SmbClient per run via its default factory.
    val backupEngine = BackupEngine(context, db, fileScanner, mediaStoreScanner, duplicateDetector, fileVerifier)
}
