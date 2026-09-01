package com.jtech.zemer.playback

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.media3.datasource.cache.SimpleCache
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.utils.ResilientDns
import com.jtech.zemer.constants.AudioQuality
import com.jtech.zemer.constants.AudioQualityKey
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.FormatEntity
import com.jtech.zemer.db.entities.SongEntity
import com.jtech.zemer.di.DownloadCache
import com.jtech.zemer.di.PlayerCache
import com.jtech.zemer.utils.YTPlayerUtils
import com.jtech.zemer.utils.enumPreferenceFlow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadUtil
@Inject
constructor(
    @ApplicationContext private val appContext: Context,
    private val databaseLazy: dagger.Lazy<MusicDatabase>,
    @DownloadCache val downloadCache: SimpleCache,
    @PlayerCache val playerCache: SimpleCache,
    val mediaStoreDownloadManager: MediaStoreDownloadManager,
) {
    val database: MusicDatabase
        get() = databaseLazy.get()
    private val connectivityManager = appContext.getSystemService<ConnectivityManager>()
        ?: throw IllegalStateException("ConnectivityManager not available on this device")
    private val audioQualityFlow = enumPreferenceFlow(appContext, AudioQualityKey, AudioQuality.AUTO)
    private var audioQuality = AudioQuality.AUTO

    companion object {
        /**
         * Shared URL cache between MusicService and DownloadUtil.
         * Stores stream URLs and their expiry timestamps.
         * Using ConcurrentHashMap for thread-safety.
         */
        val sharedUrlCache = ConcurrentHashMap<String, Pair<String, Long>>()

        /**
         * Clears the cached URL for a specific media ID.
         * Call this when a stream URL is known to be expired or invalid.
         */
        fun invalidateUrl(mediaId: String) {
            sharedUrlCache.remove(mediaId)
        }
    }

    // Use shared cache
    private val songUrlCache get() = sharedUrlCache

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Initialize audioQuality from preference
        scope.launch {
            audioQualityFlow.collect { quality ->
                audioQuality = quality
            }
        }
    }


    // MediaStore download methods
    fun getMediaStoreDownload(songId: String): Flow<MediaStoreDownloadManager.DownloadState?> =
        mediaStoreDownloadManager.downloadStates.map { it[songId] }

    fun getAllMediaStoreDownloads(): StateFlow<Map<String, MediaStoreDownloadManager.DownloadState>> =
        mediaStoreDownloadManager.downloadStates

    /** Synchronous snapshot of a song's live download state (null if none this session). */
    fun mediaStoreDownloadState(songId: String): MediaStoreDownloadManager.DownloadState? =
        mediaStoreDownloadManager.getDownloadState(songId)

    /**
     * [fromUser] = false for machine-initiated enqueues (auto-download-on-like, the missing-file
     * self-repair) so the telemetry `download` action stays a pure user-intent signal. The event
     * itself fires inside the manager, AFTER its already-downloading/completed no-op check — a
     * collection re-tap that enqueues nothing must not report downloads.
     */
    fun downloadToMediaStore(song: com.jtech.zemer.db.entities.Song, fromUser: Boolean = true) {
        mediaStoreDownloadManager.downloadSong(song, fromUser)
    }

    /**
     * Download a video to MediaStore (Movies/Zemer folder)
     * This downloads the actual video file (mp4), not just audio.
     */
    fun downloadVideoToMediaStore(
        song: com.jtech.zemer.db.entities.Song,
        maxVideoBitrateKbps: Int? = null,
        fromUser: Boolean = true,
        requestedQuality: String? = null,
    ) {
        mediaStoreDownloadManager.downloadVideo(song, maxVideoBitrateKbps, fromUser, requestedQuality)
    }

    fun cancelMediaStoreDownload(songId: String) {
        mediaStoreDownloadManager.cancelDownload(songId)
    }

    fun retryMediaStoreDownload(songId: String) {
        mediaStoreDownloadManager.retryDownload(songId)
    }

    /**
     * Remove a download and clean DB flags.
     */
    suspend fun removeDownload(songId: String) = withContext(Dispatchers.IO) {
        // Cancel queued/active MediaStore download and delete file/flags
        runCatching { mediaStoreDownloadManager.deleteDownloaded(songId) }

        // Purge the id's playerCache resources: local-file plays cached FILE bytes under this key,
        // which are a different container than a future STREAM of the same id — stale spans would
        // corrupt the extractor mid-track. The video: rendition namespace is purged too (a deleted
        // muxed file's cached video spans must not survive it) — including every itag-suffixed
        // quality-rung key and the merge-audio partner (VideoRendition.allRenditionKeys).
        runCatching { playerCache.removeResource(songId) }
        // Per-key runCatching: one throwing resource must not skip purging the rest of the family.
        runCatching { VideoRendition.allRenditionKeys(songId, playerCache.keys) }
            .getOrDefault(emptyList())
            .forEach { runCatching { playerCache.removeResource(it) } }

        runCatching {
            database.song(songId).firstOrNull()?.let { song ->
                database.query {
                    upsert(
                        song.song.copy(
                            isDownloaded = false,
                            dateDownload = null,
                            mediaStoreUri = null
                        )
                    )
                }
            }
        }
    }

    fun release() {
        scope.cancel()
    }
}
