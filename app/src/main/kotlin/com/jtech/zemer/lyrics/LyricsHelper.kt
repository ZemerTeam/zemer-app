@file:Suppress("unused")

package com.jtech.zemer.lyrics

import android.content.Context
import android.util.LruCache
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.jtech.zemer.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.jtech.zemer.lyrics.model.LyricsUnavailableException
import com.jtech.zemer.constants.LyricsProviderOrderKey
import com.jtech.zemer.lyrics.zemer.ZemerLyricsClient
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.utils.NetworkConnectivityObserver
import com.jtech.zemer.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val networkConnectivity: NetworkConnectivityObserver,
) {
    init {
        MusixmatchLyricsProvider.init(context)
    }

    /**
     * The enabled providers in the user's chain order, from ONE DataStore snapshot per walk (the order key
     * plus every provider's enable switch). The chain used to do a blocking read per provider per walk.
     */
    private suspend fun enabledProviders(): List<LyricsProvider> = enabledProviders(context.dataStore.data.first())

    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)

    // Free crowd-scrape: every non-Zemer provider body fetched for a played song is POSTed to the Zemer server
    // (which folds the synced timings into its own witness/gate pipeline). Fire-and-forget on a scope that outlives
    // the walk so it never blocks or is cancelled with the walk; failures are silent. See ZemerLyricsClient.submitScraped.
    private val scrapeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // A RANDOM anonymous per-install id (a stored UUID, not derived from any hardware/account identifier), so the
    // server can report how many installs are contributing scrapes. It identifies nothing about the device or user.
    @Volatile private var cachedScrapeId: String? = null
    private suspend fun scrapeInstallId(): String {
        cachedScrapeId?.let { return it }
        val key = androidx.datastore.preferences.core.stringPreferencesKey("crowd_scrape_install_id")
        val existing = runCatching { context.dataStore.data.first()[key] }.getOrNull()
        val id = if (!existing.isNullOrBlank()) existing else {
            val fresh = java.util.UUID.randomUUID().toString()
            runCatching { context.dataStore.edit { it[key] = fresh } }
            fresh
        }
        cachedScrapeId = id
        return id
    }

    /** Lyrics body plus the provider label to persist/show ("Zemer · jkaraoke", "SimpMusic", …) and the resolver's per-line extras, if any. */
    data class Fetched(val lyrics: String, val provider: String?, val lineExtras: ZemerLyricsClient.LineExtras? = null)

    suspend fun getLyrics(mediaMetadata: MediaMetadata): Fetched {
        // The resolver and SimpMusic are keyed by the YouTube videoId. setVideoId is the playlist-entry
        // token of the queue item, not a video identifier, so it must never be used as the key.
        val videoId = mediaMetadata.id

        val cached = cache.get(mediaMetadata.id)?.firstOrNull()
        if (cached != null) {
            return Fetched(cached.lyrics, cached.providerName)
        }

        // Check network connectivity before making network requests
        // Use synchronous check as fallback if flow doesn't emit
        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (_: Exception) {
            // If network check fails, try to proceed anyway
            true
        }
        
        if (!isNetworkAvailable) {
            // Still proceed but return not found to avoid hanging
            return Fetched(LYRICS_NOT_FOUND, null)
        }

        val providers = enabledProviders()
        val scope = CoroutineScope(SupervisorJob())
        val deferred = scope.async {
            // The pick rule (synced-first among trusted providers, low-trust YouTube only as a last resort) is
            // the pure SyncedFirstPicker; the schedule (primary alone, then the rest concurrently, low-trust
            // deferred) is the pure LyricsChainWalk. Both are tested without a network.
            LyricsChainWalk.run(providers) { provider ->
                val startedAt = System.currentTimeMillis()
                try {
                    val result = provider.getLabeledLyrics(
                        videoId,
                        mediaMetadata.title,
                        mediaMetadata.artists.joinToString { it.name },
                        mediaMetadata.duration,
                        mediaMetadata.album?.title,
                    )
                    Timber.d("Lyrics %s %s in %d ms", provider.name, if (result.isSuccess) "answered" else "no answer", System.currentTimeMillis() - startedAt)
                    val labeled = result.onFailure {
                        // Not found here is normal — keep looking. Report only unexpected exceptions.
                        if (it !is LyricsUnavailableException &&
                            !(it is IllegalStateException && it.message?.contains("Lyrics") == true)) {
                            reportException(it)
                        }
                    }.getOrNull()
                    // Free crowd-scrape: hand every non-Zemer provider body to our server (fire-and-forget). We do not
                    // echo the Zemer provider's own answers back, nor empty/not-found bodies.
                    labeled?.let { lab ->
                        if (!lab.label.startsWith("Zemer") && lab.lyrics.isNotBlank() && lab.lyrics != LYRICS_NOT_FOUND) {
                            scrapeScope.launch {
                                runCatching {
                                    ZemerLyricsClient.submitScraped(
                                        videoId, lab.label, lab.lyrics,
                                        mediaMetadata.title, mediaMetadata.artists.joinToString { it.name }, mediaMetadata.duration,
                                        device = scrapeInstallId(),
                                    )
                                }
                            }
                        }
                    }
                    labeled
                } catch (e: Exception) {
                    // Catch network-related exceptions like UnresolvedAddressException
                    Timber.d("Lyrics %s threw in %d ms", provider.name, System.currentTimeMillis() - startedAt)
                    reportException(e)
                    null
                }
            }
        }

        val lyrics = deferred.await()
        scope.cancel()
        return lyrics
    }

    companion object {
        private const val MAX_CACHE_SIZE = 3

        /** Pure: the user's ordered chain filtered to the providers enabled in [prefs] (blank order = default). */
        fun enabledProviders(prefs: Preferences): List<LyricsProvider> =
            LyricsProviderRegistry.getOrderedProviders(prefs[LyricsProviderOrderKey].orEmpty()).filter { it.isEnabled(prefs) }
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)
