package com.jtech.zemer.playback

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
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
import kotlinx.coroutines.DelicateCoroutinesApi
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
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadUtil
@Inject
constructor(
    @ApplicationContext private val appContext: Context,
    private val databaseLazy: dagger.Lazy<MusicDatabase>,
    val databaseProvider: DatabaseProvider,
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

    // Legacy ExoPlayer cache downloads. Still maintained for the download cache + removeDownload, but
    // the UI no longer reads this map — download/progress state is unified through MediaStore (see
    // DownloadStateResolver). Do not reintroduce UI reads of this (ui-audit rule R13).
    val downloads = MutableStateFlow<Map<String, Download>>(emptyMap())

    private val dataSourceFactory: ResolvingDataSource.Factory by lazy {
        ResolvingDataSource.Factory(
            CacheDataSource
                .Factory()
                .setCache(playerCache)
                .setUpstreamDataSourceFactory(
                    OkHttpDataSource.Factory(
                        OkHttpClient.Builder()
                            .dns(ResilientDns())
                            .proxy(YouTube.proxy)
                            .proxyAuthenticator { _, response ->
                                YouTube.proxyAuth?.let { auth ->
                                    response.request.newBuilder()
                                        .header("Proxy-Authorization", auth)
                                        .build()
                                } ?: response.request
                            }
                            .build(),
                    ),
                ),
        ) { dataSpec ->
            val mediaId = dataSpec.key ?: error("No media id")
            val length = if (dataSpec.length >= 0) dataSpec.length else 1

            if (playerCache.isCached(mediaId, dataSpec.position, length)) {
                return@Factory dataSpec
            }

            songUrlCache[mediaId]?.takeIf { it.second > System.currentTimeMillis() }?.let {
                return@Factory dataSpec.withUri(it.first.toUri())
            }

            // Use currently loaded audioQuality (initialized in init block)
            val currentQuality = audioQuality.takeIf { it != AudioQuality.AUTO } ?: AudioQuality.AUTO
            val playbackData = runBlocking(Dispatchers.IO) {
                YTPlayerUtils.playerResponseForPlayback(
                    mediaId,
                    audioQuality = currentQuality,
                    connectivityManager = connectivityManager,
                    forDownload = true,
                )
            }.getOrThrow()
            val format = playbackData.format

            val contentLength = format.contentLength ?: -1L
            val now = LocalDateTime.now()
            val existing = database.getSongByIdBlocking(mediaId)?.song

            val updatedSong =
                if (existing != null) {
                    // Preserve existing metadata and only stamp download time once
                    if (existing.dateDownload == null) existing.copy(dateDownload = now) else existing
                } else {
                    SongEntity(
                        id = mediaId,
                        title = playbackData.videoDetails?.title ?: "Unknown",
                        duration = playbackData.videoDetails?.lengthSeconds?.toIntOrNull() ?: 0,
                        thumbnailUrl = playbackData.videoDetails?.thumbnail?.thumbnails?.lastOrNull()?.url,
                        dateDownload = now,
                        isDownloaded = false
                    )
                }
            database.query {
                upsert(
                    FormatEntity(
                        id = mediaId,
                        itag = format.itag,
                        mimeType = format.mimeType.split(";")[0],
                        codecs = format.mimeType.split("codecs=")[1].removeSurrounding("\""),
                        bitrate = format.bitrate,
                        sampleRate = format.audioSampleRate,
                        contentLength = contentLength,
                        loudnessDb = playbackData.audioConfig?.loudnessDb,
                        playbackUrl = playbackData.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                    ),
                )
                upsert(updatedSong)
            }

            // Only update shared cache if there's no valid entry - don't overwrite player's URL
            // Different fetches return different URL signatures, overwriting breaks playback
            val baseStreamUrl = playbackData.streamUrl
            val existingEntry = songUrlCache[mediaId]
            if (existingEntry == null || existingEntry.second < System.currentTimeMillis()) {
                songUrlCache[mediaId] = baseStreamUrl to (System.currentTimeMillis() + playbackData.streamExpiresInSeconds * 1000L)
            }

            // Add range parameter only for this download request (full file)
            val downloadUrl = "${baseStreamUrl}&range=0-${format.contentLength ?: 10000000}"
            dataSpec.withUri(downloadUrl.toUri())
        }
    }

    val downloadNotificationHelper =
        DownloadNotificationHelper(appContext, ExoDownloadService.CHANNEL_ID)

    @OptIn(DelicateCoroutinesApi::class)
    val downloadManager: DownloadManager =
        DownloadManager(
            appContext,
            databaseProvider,
            downloadCache,
            dataSourceFactory,
            Executor(Runnable::run)
        ).apply {
            maxParallelDownloads = 3
            addListener(
                object : DownloadManager.Listener {
                    override fun onDownloadChanged(
                        downloadManager: DownloadManager,
                        download: Download,
                        finalException: Exception?,
                    ) {
                        downloads.update { map ->
                            map.toMutableMap().apply {
                                set(download.request.id, download)
                            }
                        }

                        scope.launch {
                            when (download.state) {
                                Download.STATE_COMPLETED -> {
                                    database.updateDownloadedInfo(download.request.id, true, LocalDateTime.now())
                                }
                                Download.STATE_FAILED,
                                Download.STATE_STOPPED,
                                Download.STATE_REMOVING -> {
                                    database.updateDownloadedInfo(download.request.id, false, null)
                                }
                                else -> {
                                }
                            }
                        }
                    }
                }
            )
        }

    init {
        // Initialize downloads snapshot
        val result = mutableMapOf<String, Download>()
        val cursor = downloadManager.downloadIndex.getDownloads()
        while (cursor.moveToNext()) {
            result[cursor.download.request.id] = cursor.download
        }
        downloads.value = result

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

    fun downloadToMediaStore(song: com.jtech.zemer.db.entities.Song) {
        mediaStoreDownloadManager.downloadSong(song)
    }

    /**
     * Download a video to MediaStore (Movies/Zemer folder)
     * This downloads the actual video file (mp4), not just audio.
     */
    fun downloadVideoToMediaStore(
        song: com.jtech.zemer.db.entities.Song,
        maxVideoBitrateKbps: Int? = null,
    ) {
        mediaStoreDownloadManager.downloadVideo(song, maxVideoBitrateKbps)
    }

    fun cancelMediaStoreDownload(songId: String) {
        mediaStoreDownloadManager.cancelDownload(songId)
    }

    fun retryMediaStoreDownload(songId: String) {
        mediaStoreDownloadManager.retryDownload(songId)
    }

    suspend fun isDownloadedInMediaStore(songId: String): Boolean {
        return database.song(songId).firstOrNull()?.song?.isDownloaded == true
    }

    /**
     * Remove a download and clean DB flags.
     */
    suspend fun removeDownload(songId: String) = withContext(Dispatchers.IO) {
        // Cancel queued/active MediaStore download and delete file/flags
        runCatching { mediaStoreDownloadManager.deleteDownloaded(songId) }

        // Remove legacy ExoPlayer cache download if present
        runCatching { downloadManager.removeDownload(songId) }

        downloads.update { it - songId }

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
