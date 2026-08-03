package com.phonas.backup.backup

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.phonas.backup.data.prefs.CredentialStore
import com.phonas.backup.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Low-latency trigger: when new photos/videos are indexed, enqueue a backup shortly after so the
 * user doesn't wait for the next scheduled cycle. Debounced so a burst of new media causes one run.
 *
 * This only covers media MediaStore indexes (never .nomedia-hidden folders — those still rely on
 * the periodic full scan), and stops when the process dies; the scheduled backup is the backstop.
 */
class MediaChangeObserver(
    private val context: Context,
    private val scope: CoroutineScope,
    private val credentialStore: CredentialStore,
    private val settingsStore: SettingsStore
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private var debounceJob: Job? = null

    fun register() {
        val resolver = context.contentResolver
        resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, this)
        resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, this)
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        if (!credentialStore.isConfigured()) return
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_MS)
            if (!credentialStore.isConfigured()) return@launch
            val settings = settingsStore.settings.first()
            WorkScheduler.runNow(context, settings)
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 90_000L
    }
}
