@file:Suppress("unused")

package com.jtech.zemer.lyrics

import android.content.Context
import android.util.LruCache
import com.jtech.zemer.constants.LyricsProviderOrderKey
import com.jtech.zemer.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.utils.NetworkConnectivityObserver
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val networkConnectivity: NetworkConnectivityObserver,
) {
    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)
    private var currentLyricsJob: Job? = null

    suspend fun getLyrics(mediaMetadata: MediaMetadata): String {
        currentLyricsJob?.cancel()

        val videoId = mediaMetadata.setVideoId ?: mediaMetadata.id

        val cached = cache.get(mediaMetadata.id)?.firstOrNull()
        if (cached != null) {
            return cached.lyrics
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
            return LYRICS_NOT_FOUND
        }

        val orderedProviders = resolveLyricsProviders()
        val cleanedTitle = LyricsUtils.cleanTitleForSearch(mediaMetadata.title)
        return withTimeoutOrNull(MAX_LYRICS_FETCH_MS) {
            for (provider in orderedProviders.filter { it.isEnabled(context) }) {
                val result = try {
                    withTimeoutOrNull(PER_PROVIDER_TIMEOUT_MS) {
                        provider.getLyrics(
                            videoId,
                            cleanedTitle,
                            mediaMetadata.artists.joinToString { it.name },
                            mediaMetadata.duration,
                            mediaMetadata.album?.title,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    reportException(e)
                    null
                }

                if (result != null && result.isSuccess) {
                    return@withTimeoutOrNull LyricsUtils.filterLyricsCreditLines(result.getOrThrow())
                }
            }
            LYRICS_NOT_FOUND
        } ?: LYRICS_NOT_FOUND
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        album: String? = null,
        callback: (LyricsResult) -> Unit,
    ) {
        currentLyricsJob?.cancel()

        val cacheKey = "$songArtists-$songTitle".replace(" ", "")
        cache.get(cacheKey)?.let { results ->
            results.forEach {
                callback(it)
            }
            return
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
            // Still try to proceed in case of false negative
            return
        }

        val allResult = mutableListOf<LyricsResult>()
        currentLyricsJob = CoroutineScope(SupervisorJob()).launch {
            val cleanedTitle = LyricsUtils.cleanTitleForSearch(songTitle)
            resolveLyricsProviders().filter { it.isEnabled(context) }.forEach { provider ->
                try {
                    provider.getAllLyrics(mediaId, cleanedTitle, songArtists, duration, album) { lyrics ->
                        val result = LyricsResult(provider.name, LyricsUtils.filterLyricsCreditLines(lyrics))
                        allResult += result
                        callback(result)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Catch network-related exceptions like UnresolvedAddressException
                    reportException(e)
                }
            }
            cache.put(cacheKey, allResult)
        }

        currentLyricsJob?.join()
    }

    private suspend fun resolveLyricsProviders(): List<LyricsProvider> {
        val providerOrder = context.dataStore.data
            .map { preferences -> preferences[LyricsProviderOrderKey].orEmpty() }
            .first()

        return if (providerOrder.isBlank()) {
            LyricsProviderRegistry.getDefaultProviderOrder().mapNotNull { LyricsProviderRegistry.getProviderByName(it) }
        } else {
            LyricsProviderRegistry.getOrderedProviders(providerOrder)
        }
    }

    fun cancelCurrentLyricsJob() {
        currentLyricsJob?.cancel()
        currentLyricsJob = null
    }

    companion object {
        private const val MAX_CACHE_SIZE = 3
        private const val MAX_LYRICS_FETCH_MS = 25000L
        private const val PER_PROVIDER_TIMEOUT_MS = 8000L
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)
