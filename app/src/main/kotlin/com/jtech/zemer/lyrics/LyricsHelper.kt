@file:Suppress("unused")

package com.jtech.zemer.lyrics

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import com.jtech.zemer.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.jtech.zemer.lyrics.model.LyricsUnavailableException
import com.jtech.zemer.constants.LyricsProviderOrderKey
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.utils.NetworkConnectivityObserver
import com.jtech.zemer.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
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

    /** Lyrics body plus the provider label to persist/show ("Zemer · jkaraoke", "SimpMusic", …). */
    data class Fetched(val lyrics: String, val provider: String?)

    suspend fun getLyrics(mediaMetadata: MediaMetadata): Fetched {
        // The resolver and SimpMusic are keyed by the YouTube videoId. setVideoId is the playlist-entry
        // token of the queue item, not a video identifier, so it must never be used as the key.
        val videoId = mediaMetadata.id

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
        // The pick rule (synced-first among trusted providers, low-trust YouTube only as a last resort) is
        // the pure SyncedFirstPicker; the schedule (primary alone, then the rest concurrently, low-trust
        // deferred) is the pure LyricsChainWalk. Both are tested without a network. The walk runs
        // STRUCTURED under the caller: it used to run on a parentless scope whose cancel() sat after the
        // await, so a cancelled caller (the track-start prefetch skipping to the next song) left every
        // in-flight provider fetch running to completion with its body - heap churn during background
        // playback on low-RAM devices. Cancelling the caller now cancels the fetches (LyricsChainWalkTest).
        return LyricsChainWalk.run(providers) { provider ->
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
                result.onFailure {
                    // Not found here is normal — keep looking. Report only unexpected exceptions.
                    if (it !is LyricsUnavailableException &&
                        !(it is IllegalStateException && it.message?.contains("Lyrics") == true)) {
                        reportException(it)
                    }
                }.getOrNull()
            } catch (e: CancellationException) {
                throw e // the caller was cancelled (skipped track): propagate, never report or swallow
            } catch (e: Exception) {
                // Catch network-related exceptions like UnresolvedAddressException
                Timber.d("Lyrics %s threw in %d ms", provider.name, System.currentTimeMillis() - startedAt)
                reportException(e)
                null
            }
        }
    }

    companion object {
        /** Pure: the user's ordered chain filtered to the providers enabled in [prefs] (blank order = default). */
        fun enabledProviders(prefs: Preferences): List<LyricsProvider> =
            LyricsProviderRegistry.getOrderedProviders(prefs[LyricsProviderOrderKey].orEmpty()).filter { it.isEnabled(prefs) }
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)
