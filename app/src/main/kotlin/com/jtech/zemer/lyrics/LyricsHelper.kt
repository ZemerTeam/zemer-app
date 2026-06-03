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
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val preferred = context.dataStore.data
        .map { preferences ->
            val providerOrder = preferences[LyricsProviderOrderKey].orEmpty()
            if (providerOrder.isNotBlank()) {
                LyricsProviderRegistry.getOrderedProviders(providerOrder)
            } else {
                LyricsProviderRegistry.getDefaultProviderOrder()
                    .mapNotNull { LyricsProviderRegistry.getProviderByName(it) }
            }
        }
        .distinctUntilChanged()

    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)
    private var currentLyricsJob: Job? = null

    suspend fun getLyrics(mediaMetadata: MediaMetadata): String = getLyricsWithProvider(mediaMetadata).lyrics

    suspend fun getLyricsWithProvider(mediaMetadata: MediaMetadata): LyricsWithProvider {
        currentLyricsJob?.cancel()

        val videoId = mediaMetadata.setVideoId ?: mediaMetadata.id

        val cached = cache.get(mediaMetadata.id)?.firstOrNull()
        if (cached != null) {
            return LyricsWithProvider(cached.lyrics, cached.providerName)
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
            return LyricsWithProvider(LYRICS_NOT_FOUND, PROVIDER_NONE)
        }

        val orderedProviders = resolveLyricsProviders()
        val cleanedTitle = LyricsUtils.cleanTitleForSearch(mediaMetadata.title)
        return withTimeoutOrNull(MAX_LYRICS_FETCH_MS) {
            for (provider in orderedProviders.filter { it.isEnabled(context) }) {
                val result = try {
                    withTimeoutOrNull(PER_PROVIDER_TIMEOUT_MS) {
                        provider.getLyrics(
                            context,
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
                    return@withTimeoutOrNull LyricsWithProvider(
                        LyricsUtils.filterLyricsCreditLines(result.getOrThrow()),
                        provider.name,
                    )
                }
            }
            LyricsWithProvider(LYRICS_NOT_FOUND, PROVIDER_NONE)
        } ?: LyricsWithProvider(LYRICS_NOT_FOUND, PROVIDER_NONE)
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
            val enabledProviders = resolveLyricsProviders().filter { it.isEnabled(context) }
            val otherProviders = enabledProviders.filter { it.name != "LyricsPlus" }
            val lyricsPlusProvider = enabledProviders.find { it.name == "LyricsPlus" }
            val callbackMutex = Any()

            val otherJobs = otherProviders.map { provider ->
                launch {
                    try {
                        provider.getAllLyrics(context, mediaId, cleanedTitle, songArtists, duration, album) { lyrics ->
                            val result = LyricsResult(provider.name, LyricsUtils.filterLyricsCreditLines(lyrics))
                            synchronized(callbackMutex) {
                                allResult += result
                                callback(result)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }
            }
            otherJobs.forEach { it.join() }

            val otherLyricsCount = allResult.count { it.providerName != "LyricsPlus" }
            if (lyricsPlusProvider != null && otherLyricsCount <= 2) {
                launch {
                    try {
                        lyricsPlusProvider.getAllLyrics(context, mediaId, cleanedTitle, songArtists, duration, album) { lyrics ->
                            val result = LyricsResult(lyricsPlusProvider.name, LyricsUtils.filterLyricsCreditLines(lyrics))
                            synchronized(callbackMutex) {
                                allResult += result
                                callback(result)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }.join()
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
        private const val PROVIDER_NONE = ""
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)

data class LyricsWithProvider(
    val lyrics: String,
    val provider: String,
)
