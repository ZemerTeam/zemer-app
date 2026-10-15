package com.jtech.zemer.appgate

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.jtech.zemer.constants.AppConfigKey
import com.jtech.zemer.utils.ZemerContentClient
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.getSuspend
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Process-wide holder of the last good [AppConfig]: restored from DataStore at startup, then refreshed
 * from `content.zemer.io/app-config` on cold start and every [REFRESH_MS] while the process lives. A
 * failed fetch (offline, non-2xx, unparseable) keeps the last good config, or none - never a new gate.
 */
object AppConfigStore {
    private const val REFRESH_MS = 24 * 60 * 60 * 1000L

    private val _config = MutableStateFlow<AppConfig?>(null)
    val config: StateFlow<AppConfig?> = _config.asStateFlow()

    /** Restore the persisted config, then fetch now and daily. Runs for the app's lifetime (App scope). */
    suspend fun run(context: Context) {
        parseAppConfig(context.dataStore.getSuspend(AppConfigKey, ""))?.let { _config.value = it }
        while (true) {
            refresh(context)
            delay(REFRESH_MS)
        }
    }

    private suspend fun refresh(context: Context) {
        if (!ZemerContentClient.enabled) return
        runCatching {
            val raw = ZemerContentClient.appConfigRaw()
            val parsed = parseAppConfig(raw) ?: return
            _config.value = parsed
            context.dataStore.edit { it[AppConfigKey] = raw }
        }.onFailure { Timber.d("app-config unavailable (no gate change): %s", it.message) }
    }
}
