package com.jtech.zemer.playback

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import com.jtech.zemer.constants.AudioQuality
import com.jtech.zemer.constants.AudioQualityKey
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.utils.MediaStoreHelper
import com.jtech.zemer.utils.UrlValidator
import com.jtech.zemer.utils.YTPlayerUtils
import com.jtech.zemer.utils.enumPreference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow
import okhttp3.OkHttpClient
import okhttp3.Request
import com.metrolist.innertube.utils.ResilientDns
import com.metrolist.innertube.YouTube

/**
 * Download manager that uses MediaStore to save music files to the public Music/Zemer folder.
 *
 * Features:
 * - Downloads audio streams from YouTube via InnerTube API
 * - Saves files using MediaStore for Android 10+ compatibility
 * - Supports concurrent downloads (max 3 simultaneous)
 * - Retry logic with exponential backoff
 * - Progress tracking with StateFlow
 * - Download queue management
 * - Automatic cleanup on failure
 */
@Singleton
class MediaStoreDownloadManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val databaseLazy: dagger.Lazy<MusicDatabase>,
) {
    private val database: MusicDatabase
        get() = databaseLazy.get()
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val mediaStoreHelper = MediaStoreHelper(context)
    private val connectivityManager = context.getSystemService<ConnectivityManager>()
        ?: throw IllegalStateException("ConnectivityManager not available on this device")
    private val audioQuality by enumPreference(context, AudioQualityKey, AudioQuality.AUTO)
    private val httpClient = OkHttpClient.Builder()
        .dns(ResilientDns())
        .proxy(YouTube.proxy)
        .proxyAuthenticator { _, response ->
            YouTube.proxyAuth?.let { auth ->
                response.request.newBuilder()
                    .header("Proxy-Authorization", auth)
                    .build()
            } ?: response.request
        }
        .build()

    // Concurrent download limiter (max 3 simultaneous downloads)
    private val downloadSemaphore = Semaphore(MAX_CONCURRENT_DOWNLOADS)

    // Download state tracking
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    // Download queue
    private val downloadQueue = mutableListOf<Song>()
    private val activeDownloads = mutableMapOf<String, Job>()

    companion object {
        private const val MAX_CONCURRENT_DOWNLOADS = 3
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        private const val RETRY_BACKOFF_MULTIPLIER = 2.0
        private const val DEFAULT_AUDIO_FORMAT = "opus"
        // Throttle progress updates to avoid notification rate limiting (Android allows ~5/sec)
        private const val PROGRESS_UPDATE_INTERVAL_MS = 250L
        private const val PROGRESS_UPDATE_THRESHOLD = 0.02f // 2% change
    }

    /**
     * Download state for a song
     */
    data class DownloadState(
        val songId: String,
        val status: Status,
        val progress: Float = 0f,
        val bytesDownloaded: Long = 0,
        val totalBytes: Long = 0,
        val error: String? = null,
        val retryAttempt: Int = 0,
    ) {
        enum class Status {
            QUEUED,
            DOWNLOADING,
            COMPLETED,
            FAILED,
            CANCELLED
        }
    }

    /**
     * Start downloading a song
     *
     * @param song The song to download
     */
    fun downloadSong(song: Song) {
        scope.launch {
            // Start notification service
            MediaStoreDownloadService.start(context)
            // Check if already downloading or completed
            val currentState = _downloadStates.value[song.id]
            if (currentState?.status == DownloadState.Status.DOWNLOADING ||
                currentState?.status == DownloadState.Status.COMPLETED
            ) {
                return@launch
            }

            // Check if already exists in MediaStore
            val existingFile = mediaStoreHelper.findExistingFile(
                title = song.song.title,
                artist = song.artists.firstOrNull()?.name ?: "Unknown"
            )
            if (existingFile != null) {
                updateDownloadState(
                    song.id,
                    DownloadState(
                        songId = song.id,
                        status = DownloadState.Status.COMPLETED,
                        progress = 1f
                    )
                )
                markSongAsDownloaded(song.id, existingFile.toString())
                return@launch
            }

            // Add to queue
            synchronized(downloadQueue) {
                if (!downloadQueue.any { it.id == song.id }) {
                    downloadQueue.add(song)
                    updateDownloadState(
                        song.id,
                        DownloadState(
                            songId = song.id,
                            status = DownloadState.Status.QUEUED
                        )
                    )
                }
            }

            // Start download
            processQueue()
        }
    }

    /**
     * Cancel a download
     *
     * @param songId The ID of the song to cancel
     */
    fun cancelDownload(songId: String) {
        scope.launch {
            // Cancel active download
            activeDownloads[songId]?.cancel()
            activeDownloads.remove(songId)

            // Remove from queue
            synchronized(downloadQueue) {
                downloadQueue.removeAll { it.id == songId }
            }

            // Update state
            updateDownloadState(
                songId,
                DownloadState(
                    songId = songId,
                    status = DownloadState.Status.CANCELLED
                )
            )
        }
    }

    /**
     * Retry a failed download
     *
     * @param songId The ID of the song to retry
     */
    fun retryDownload(songId: String) {
        scope.launch {
            val song = database.song(songId).first() ?: return@launch

            // Reset download state
            updateDownloadState(
                songId,
                DownloadState(
                    songId = songId,
                    status = DownloadState.Status.QUEUED
                )
            )

            downloadSong(song)
        }
    }

    /**
     * Process the download queue
     */
    private suspend fun processQueue() {
        val song = synchronized(downloadQueue) {
            downloadQueue.firstOrNull()
        } ?: return

        // Try to acquire semaphore (limit concurrent downloads)
        if (downloadSemaphore.tryAcquire()) {
            val job = scope.launch {
                try {
                    performDownload(song)
                } finally {
                    downloadSemaphore.release()
                    synchronized(downloadQueue) {
                        downloadQueue.remove(song)
                    }
                    activeDownloads.remove(song.id)

                    // Process next item in queue
                    processQueue()
                }
            }
            activeDownloads[song.id] = job
        }
    }

    /**
     * Perform the actual download with retry logic
     */
    private suspend fun performDownload(song: Song, retryAttempt: Int = 0): Unit = withContext(Dispatchers.IO) {
        try {
            updateDownloadState(
                song.id,
                DownloadState(
                    songId = song.id,
                    status = DownloadState.Status.DOWNLOADING,
                    retryAttempt = retryAttempt
                )
            )

            // Get playback URL from YouTube using YTPlayerUtils
            val playbackData = YTPlayerUtils.playerResponseForPlayback(
                videoId = song.id,
                audioQuality = audioQuality,
                connectivityManager = connectivityManager
            ).getOrThrow()

            val format = playbackData.format
            val downloadUrl = playbackData.streamUrl

            // Create temporary file for download
            val tempFile = File(context.cacheDir, "temp_${song.id}.${format.mimeType.substringAfter("/")}")

            try {
                // Download to temp file
                downloadFile(downloadUrl, tempFile, song.id)

                if (!tempFile.exists() || tempFile.length() == 0L) {
                    throw Exception("Download failed - temp file not created or empty")
                }

                // Get audio metadata
                val title = song.song.title
                val artist = song.artists.firstOrNull()?.name ?: "Unknown Artist"
                val album = song.album?.title
                val duration = song.song.duration?.times(1000L) // Convert to milliseconds
                // Force MP3 extension for MediaStore compatibility (Android doesn't support audio/webm)
                val extension = "mp3"
                val mimeType = mediaStoreHelper.getMimeType(extension)

                // Save to MediaStore
                val fileName = "$artist - $title.$extension"
                val uri = mediaStoreHelper.saveFileToMediaStore(
                    tempFile = tempFile,
                    fileName = fileName,
                    mimeType = mimeType,
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = duration
                )

                if (uri != null) {
                    // Mark as completed
                    updateDownloadState(
                        song.id,
                        DownloadState(
                            songId = song.id,
                            status = DownloadState.Status.COMPLETED,
                            progress = 1f
                        )
                    )

                    // Update database with MediaStore URI
                    markSongAsDownloaded(song.id, uri.toString())
                } else {
                    throw Exception("Failed to save file to MediaStore")
                }
            } finally {
                // Clean up temp file
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }

        } catch (e: Exception) {
            // Retry logic with exponential backoff
            if (retryAttempt < MAX_RETRY_ATTEMPTS) {
                val delayMs: Long = (INITIAL_RETRY_DELAY_MS * RETRY_BACKOFF_MULTIPLIER.pow(retryAttempt)).toLong()

                updateDownloadState(
                    song.id,
                    DownloadState(
                        songId = song.id,
                        status = DownloadState.Status.DOWNLOADING,
                        error = "Retrying... (${retryAttempt + 1}/$MAX_RETRY_ATTEMPTS)",
                        retryAttempt = retryAttempt + 1
                    )
                )

                delay(delayMs)
                performDownload(song, retryAttempt + 1)
            } else {
                // Max retries reached
                updateDownloadState(
                    song.id,
                    DownloadState(
                        songId = song.id,
                        status = DownloadState.Status.FAILED,
                        error = e.message ?: "Unknown error",
                        retryAttempt = retryAttempt
                    )
                )
            }
        }
    }

    /**
     * Download a file from a URL to a temp file with progress tracking
     */
    private suspend fun downloadFile(url: String, outputFile: File, songId: String) = withContext(Dispatchers.IO) {
        // Validate URL before attempting to build request
        val validatedUrl = UrlValidator.validateAndParseUrl(url)
            ?: throw Exception("Invalid download URL: $url")

        val request = Request.Builder()
            .url(validatedUrl)
            .get()
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept", "*/*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Range", "bytes=0-")
            .build()

        val response = httpClient.newCall(request).execute()
        val responseCode = response.code

        if (!response.isSuccessful) {
            response.close()
            throw Exception("HTTP error $responseCode: ${response.message}")
        }

        val body = response.body ?: throw Exception("Empty response body")
        val contentLength = body.contentLength().coerceAtLeast(0)
        var totalBytesRead = 0L
        var lastProgressUpdate = 0L
        var lastReportedProgress = 0f

        body.byteStream().use { input ->
            outputFile.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    // Throttle progress updates to avoid notification rate limiting
                    // Update at most every 250ms OR when progress changes by 2%+
                    if (contentLength > 0) {
                        val progress = totalBytesRead.toFloat() / contentLength.toFloat()
                        val currentTime = System.currentTimeMillis()
                        val timeSinceLastUpdate = currentTime - lastProgressUpdate
                        val progressDelta = progress - lastReportedProgress

                        if (timeSinceLastUpdate >= PROGRESS_UPDATE_INTERVAL_MS || progressDelta >= PROGRESS_UPDATE_THRESHOLD) {
                            lastProgressUpdate = currentTime
                            lastReportedProgress = progress
                            updateDownloadState(
                                songId,
                                DownloadState(
                                    songId = songId,
                                    status = DownloadState.Status.DOWNLOADING,
                                    progress = progress,
                                    bytesDownloaded = totalBytesRead,
                                    totalBytes = contentLength.toLong()
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Update the download state for a song
     */
    private fun updateDownloadState(songId: String, state: DownloadState) {
        _downloadStates.value = _downloadStates.value + (songId to state)
    }

    /**
     * Mark a song as downloaded in the database with MediaStore URI
     */
    private suspend fun markSongAsDownloaded(songId: String, mediaStoreUri: String) {
        val song = database.song(songId).first()
        if (song != null) {
            database.query {
                database.upsert(
                    song.song.copy(
                        isDownloaded = true,
                        dateDownload = LocalDateTime.now(),
                        mediaStoreUri = mediaStoreUri
                    )
                )
            }
        }
    }

    /**
     * Get the download state for a song
     */
    fun getDownloadState(songId: String): DownloadState? {
        return _downloadStates.value[songId]
    }

    /**
     * Check if a song is downloaded
     */
    suspend fun isDownloaded(songId: String): Boolean {
        return database.song(songId).first()?.song?.isDownloaded == true
    }

    /**
     * Clear completed downloads from state
     */
    fun clearCompletedDownloads() {
        _downloadStates.value = _downloadStates.value.filterValues {
            it.status != DownloadState.Status.COMPLETED
        }
    }
}
