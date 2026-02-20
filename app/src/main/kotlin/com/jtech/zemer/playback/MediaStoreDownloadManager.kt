package com.jtech.zemer.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import androidx.core.content.getSystemService
import com.jtech.zemer.constants.AudioQuality
import com.jtech.zemer.constants.DownloadQuality
import com.jtech.zemer.constants.DownloadQualityKey
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.db.entities.SongAlbumMap
import com.jtech.zemer.db.entities.SongArtistMap
import com.jtech.zemer.utils.CoverArtEmbedder
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
import timber.log.Timber

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
    private val downloadQuality by enumPreference(context, DownloadQualityKey, DownloadQuality.HIGH)
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
     * Start downloading a video
     *
     * @param song The song/video to download as video file
     */
    fun downloadVideo(song: Song) {
        Timber.d("downloadVideo called: id=${song.id}, title=${song.song.title}, inputIsVideo=${song.song.isVideo}")
        scope.launch {
            // Start notification service
            MediaStoreDownloadService.start(context)
            // Check if already downloading or completed
            val currentState = _downloadStates.value[song.id]
            if (currentState?.status == DownloadState.Status.DOWNLOADING ||
                currentState?.status == DownloadState.Status.COMPLETED
            ) {
                Timber.d("downloadVideo skipped - already downloading or completed: status=${currentState?.status}")
                return@launch
            }

            // Mark as video FIRST, then persist
            val videoSong = song.copy(song = song.song.copy(isVideo = true))
            Timber.d("downloadVideo: videoSong.song.isVideo=${videoSong.song.isVideo}")

            // Make sure the song and its relations exist in the database WITH isVideo = true
            ensureSongPersisted(videoSong)

            // Add to queue - remove any existing entry first to ensure video flag is set
            synchronized(downloadQueue) {
                downloadQueue.removeAll { it.id == song.id }
                downloadQueue.add(videoSong)
                Timber.d("downloadVideo: added to queue with isVideo=${videoSong.song.isVideo}, queueSize=${downloadQueue.size}")
                updateDownloadState(
                    song.id,
                    DownloadState(
                        songId = song.id,
                        status = DownloadState.Status.QUEUED
                    )
                )
            }

            // Start download
            processQueue()
        }
    }

    // Target itag for downloads when user selects a specific format
    private val targetItagOverride = mutableMapOf<String, Int>()

    /**
     * Start downloading a song with a specific itag (exact format).
     * Bypasses cache and fetches the exact format user selected.
     * Can be used to re-download with different quality (overwrites existing file).
     *
     * @param song The song to download
     * @param itag The YouTube format itag
     * @param overwrite If true, deletes existing download first
     */
    fun downloadSongWithItag(song: Song, itag: Int, overwrite: Boolean = false) {
        Timber.tag("DownloadQuality").i("=== DOWNLOAD START: ${song.song.title} ===")
        Timber.tag("DownloadQuality").i("Target itag: $itag, overwrite=$overwrite")

        scope.launch {
            // If overwriting, delete existing download first
            if (overwrite) {
                Timber.tag("DownloadQuality").i("Deleting existing download for ${song.id}")
                deleteDownloaded(song.id)
            }

            // Store the target itag for this download
            targetItagOverride[song.id] = itag
            // Invalidate any cached URL to force fresh fetch
            DownloadUtil.invalidateUrl(song.id)
            Timber.tag("DownloadQuality").i("Cache invalidated, starting download...")
            // Start the download
            downloadSong(song)
        }
    }

    /**
     * Start downloading a song
     *
     * @param song The song to download
     */
    fun downloadSong(song: Song) {
        Timber.d("downloadSong called: id=${song.id}, title=${song.song.title}, isVideo=${song.song.isVideo}")
        scope.launch {
            // Start notification service
            MediaStoreDownloadService.start(context)
            // Check if already downloading or completed
            val currentState = _downloadStates.value[song.id]
            if (currentState?.status == DownloadState.Status.DOWNLOADING ||
                currentState?.status == DownloadState.Status.COMPLETED
            ) {
                Timber.tag("DownloadQuality").d("Skipping download - already ${currentState?.status}")
                return@launch
            }

            // For audio downloads, ensure isVideo is false (song may have been marked as video previously)
            val audioSong = song.copy(song = song.song.copy(isVideo = false))

            // Check if already exists in MediaStore
            // Make sure the song and its relations exist in the database so we can flag it
            // as downloaded later (needed for the Library > Downloaded view).
            ensureSongPersisted(audioSong)

            // Use album artist for consistency with download folder structure
            val checkArtist = if (audioSong.album != null) {
                val albumWithArtists = database.albumUnfiltered(audioSong.album.id).first()
                albumWithArtists?.artists?.firstOrNull()?.name
                    ?: audioSong.artists.firstOrNull()?.name
                    ?: "Unknown"
            } else {
                audioSong.artists.firstOrNull()?.name ?: "Unknown"
            }

            val existingFile = mediaStoreHelper.findExistingFile(
                title = audioSong.song.title,
                artist = checkArtist
            )
            if (existingFile != null) {
                Timber.tag("DownloadQuality").i("Existing file found: $existingFile - marking as completed")
                updateDownloadState(
                    audioSong.id,
                    DownloadState(
                        songId = audioSong.id,
                        status = DownloadState.Status.COMPLETED,
                        progress = 1f
                    )
                )
                markSongAsDownloaded(audioSong, existingFile.toString())
                return@launch
            }

            // Add to queue
            synchronized(downloadQueue) {
                if (!downloadQueue.any { it.id == audioSong.id }) {
                    downloadQueue.add(audioSong)
                    updateDownloadState(
                        audioSong.id,
                        DownloadState(
                            songId = audioSong.id,
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

            // Use video download if the song is marked as video
            if (song.song.isVideo) {
                downloadVideo(song)
            } else {
                downloadSong(song)
            }
        }
    }

    fun markPermissionMissing(songId: String) {
        updateDownloadState(
            songId,
            DownloadState(
                songId = songId,
                status = DownloadState.Status.FAILED,
                error = "Storage permission required"
            )
        )
    }

    /**
     * Delete a downloaded song (or cancel and clear pending state) and remove it from MediaStore/DB.
     */
    suspend fun deleteDownloaded(songId: String) {
        Timber.tag("DownloadRemoval").i("=== deleteDownloaded START: $songId ===")
        // Cancel active work and purge queue
        val hadActiveJob = activeDownloads[songId] != null
        activeDownloads[songId]?.cancel()
        activeDownloads.remove(songId)
        Timber.tag("DownloadRemoval").d("[$songId] Cancelled active job: $hadActiveJob")

        val queueCountBefore = downloadQueue.size
        synchronized(downloadQueue) {
            downloadQueue.removeAll { it.id == songId }
        }
        val queueCountAfter = downloadQueue.size
        Timber.tag("DownloadRemoval").d("[$songId] Queue cleanup: $queueCountBefore -> $queueCountAfter")

        // If the download state shows COMPLETED but DB doesn't have URI yet, wait briefly
        // This handles race condition where UI shows completed but DB update is still pending
        var song = database.song(songId).first()
        val downloadState = _downloadStates.value[songId]
        if (downloadState?.status == DownloadState.Status.COMPLETED && song?.song?.mediaStoreUri == null) {
            Timber.tag("DownloadRemoval").d("[$songId] State=COMPLETED but no URI in DB - waiting for DB sync...")
            // Wait up to 500ms for DB to sync
            repeat(5) {
                delay(100)
                song = database.song(songId).first()
                if (song?.song?.mediaStoreUri != null) {
                    Timber.tag("DownloadRemoval").d("[$songId] DB synced after ${(it + 1) * 100}ms")
                    return@repeat
                }
            }
        }

        val uriString = song?.song?.mediaStoreUri
        Timber.tag("DownloadRemoval").d("[$songId] DB state: found=${song != null}, uri=$uriString, isDownloaded=${song?.song?.isDownloaded}")

        // Get song metadata for variant deletion
        // IMPORTANT: Use same artist resolution logic as download (album artist > song artist)
        // Otherwise the folder path won't match!
        val title = song?.song?.title
        val album = song?.album?.title
        val artists: String? = if (song?.album != null) {
            // Try to get album artist first (matches download folder structure)
            val albumWithArtists = database.albumUnfiltered(song.album.id).first()
            albumWithArtists?.artists?.firstOrNull()?.name
                ?: song.artists.firstOrNull()?.name
        } else {
            song?.artists?.firstOrNull()?.name
        }
        Timber.tag("DownloadRemoval").d("[$songId] Resolved artist: '$artists' (album=${song?.album?.title})")

        // First, try to delete by stored URI
        if (uriString != null) {
            Timber.tag("DownloadRemoval").d("[$songId] Deleting from MediaStore by URI: $uriString")
            val deleteResult = runCatching {
                mediaStoreHelper.deleteFromMediaStore(Uri.parse(uriString))
            }
            if (deleteResult.isSuccess) {
                Timber.tag("DownloadRemoval").i("[$songId] MediaStore URI delete SUCCESS: ${deleteResult.getOrNull()}")
            } else {
                Timber.tag("DownloadRemoval").e("[$songId] MediaStore URI delete FAILED: ${deleteResult.exceptionOrNull()?.message}")
            }
        } else {
            Timber.tag("DownloadRemoval").d("[$songId] No MediaStore URI stored")
        }

        // Also delete ALL variants by title (handles files with different extensions)
        // This cleans up old downloads that may have used different formats (e.g., .opus vs .m4a)
        if (title != null && artists != null) {
            Timber.tag("DownloadRemoval").d("[$songId] Searching for file variants: '$title' by '$artists'")
            val variantsDeleted = runCatching {
                mediaStoreHelper.deleteAllVariantsByTitle(title, artists, album)
            }.getOrElse { e ->
                Timber.tag("DownloadRemoval").e("[$songId] Variant deletion error: ${e.message}")
                0
            }
            Timber.tag("DownloadRemoval").i("[$songId] Variant cleanup: $variantsDeleted files deleted")
        } else {
            Timber.tag("DownloadRemoval").w("[$songId] Cannot search variants: title=$title, artist=$artists")
        }

        song?.let {
            database.query {
                upsert(
                    it.song.copy(
                        isDownloaded = false,
                        dateDownload = null,
                        mediaStoreUri = null
                    )
                )
            }
            Timber.tag("DownloadRemoval").d("[$songId] Database flags cleared")
        }

        // Clear state entry
        val hadState = _downloadStates.value.containsKey(songId)
        _downloadStates.value = _downloadStates.value - songId
        Timber.tag("DownloadRemoval").i("=== deleteDownloaded END: $songId (hadState=$hadState) ===")
    }

    /**
     * Delete a downloaded video and remove it from MediaStore/DB.
     * This only removes the video download, not the audio download.
     */
    suspend fun deleteDownloadedVideo(songId: String) {
        Timber.tag("DownloadRemoval").i("=== deleteDownloadedVideo START: $songId ===")

        var song = database.song(songId).first()
        val videoUriString = song?.song?.videoMediaStoreUri
        Timber.tag("DownloadRemoval").d("[$songId] Video DB state: found=${song != null}, videoUri=$videoUriString")

        // Delete by stored video URI
        if (videoUriString != null) {
            Timber.tag("DownloadRemoval").d("[$songId] Deleting video from MediaStore by URI: $videoUriString")
            val deleteResult = runCatching {
                mediaStoreHelper.deleteFromMediaStore(Uri.parse(videoUriString))
            }
            if (deleteResult.isSuccess) {
                Timber.tag("DownloadRemoval").i("[$songId] Video MediaStore delete SUCCESS: ${deleteResult.getOrNull()}")
            } else {
                Timber.tag("DownloadRemoval").e("[$songId] Video MediaStore delete FAILED: ${deleteResult.exceptionOrNull()?.message}")
            }
        } else {
            Timber.tag("DownloadRemoval").d("[$songId] No video MediaStore URI stored")
        }

        // Clear only videoMediaStoreUri (keep audio download intact)
        song?.let {
            database.query {
                updateVideoMediaStoreUri(songId, null)
            }
            Timber.tag("DownloadRemoval").d("[$songId] Video URI cleared from database")
        }

        Timber.tag("DownloadRemoval").i("=== deleteDownloadedVideo END: $songId ===")
    }

    /**
     * Process the download queue
     */
    private suspend fun processQueue() {
        // Get and remove from queue atomically to prevent duplicate processing
        val song = synchronized(downloadQueue) {
            downloadQueue.firstOrNull()?.also { downloadQueue.remove(it) }
        } ?: return

        // Check if already downloading (race condition guard)
        if (activeDownloads.containsKey(song.id)) {
            processQueue() // Try next song
            return
        }

        // Try to acquire semaphore (limit concurrent downloads)
        if (downloadSemaphore.tryAcquire()) {
            val job = scope.launch {
                try {
                    performDownload(song)
                } finally {
                    downloadSemaphore.release()
                    activeDownloads.remove(song.id)

                    // Process next item in queue
                    processQueue()
                }
            }
            activeDownloads[song.id] = job
        } else {
            // Semaphore not available, put song back in queue for later
            synchronized(downloadQueue) {
                if (!downloadQueue.any { it.id == song.id }) {
                    downloadQueue.add(0, song) // Add back at front
                }
            }
        }
    }

    /**
     * Perform the actual download with retry logic
     */
    private suspend fun performDownload(song: Song, retryAttempt: Int = 0): Unit = withContext(Dispatchers.IO) {
        val isVideoDownload = song.song.isVideo
        Timber.d("performDownload: id=${song.id}, isVideo=${isVideoDownload}, title=${song.song.title}")
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
            // For videos, request video stream with preferVideo=true
            // Convert DownloadQuality to AudioQuality for the player utils
            val audioQualityForDownload = when (downloadQuality) {
                DownloadQuality.HIGH -> AudioQuality.HIGH
                DownloadQuality.LOW -> AudioQuality.LOW
            }
            // Check if user selected a specific itag (format)
            // Use get() instead of remove() - we'll remove after successful download
            // This prevents a race where a second download request loses the itag
            val targetItag = targetItagOverride[song.id] ?: 0
            Timber.tag("DownloadQuality").i("Fetching stream: videoId=${song.id}, targetItag=${if (targetItag > 0) targetItag else "auto (${downloadQuality})"}")
            val playbackData = YTPlayerUtils.playerResponseForPlayback(
                videoId = song.id,
                audioQuality = audioQualityForDownload,
                connectivityManager = connectivityManager,
                preferVideo = isVideoDownload,
                forDownload = true,
                targetItag = targetItag,
            ).getOrThrow()

            val format = playbackData.format
            val downloadUrl = playbackData.streamUrl

            Timber.tag("DownloadQuality").i("=== FORMAT SELECTED ===")
            Timber.tag("DownloadQuality").i("Requested itag: $targetItag")
            Timber.tag("DownloadQuality").i("Got itag: ${format.itag}")
            Timber.tag("DownloadQuality").i("Mime: ${format.mimeType}")
            Timber.tag("DownloadQuality").i("Bitrate: ${format.bitrate/1000}kbps")
            Timber.tag("DownloadQuality").i("Match: ${targetItag == format.itag || targetItag == 0}")

            // Only update shared cache if there's no valid entry - don't overwrite player's URL
            // Different fetches return different URL signatures, overwriting breaks playback
            val existingEntry = DownloadUtil.sharedUrlCache[song.id]
            if (existingEntry == null || existingEntry.second < System.currentTimeMillis()) {
                val expiresAt = System.currentTimeMillis() + playbackData.streamExpiresInSeconds * 1000L
                DownloadUtil.sharedUrlCache[song.id] = downloadUrl to expiresAt
                Timber.tag("StreamCache").i("[${song.id}] MediaStore download CACHED URL (expires in ${playbackData.streamExpiresInSeconds}s)")
            } else {
                val remainingSec = (existingEntry.second - System.currentTimeMillis()) / 1000
                Timber.tag("StreamCache").d("[${song.id}] MediaStore download NOT updating cache - valid entry exists (${remainingSec}s remaining)")
            }
            Timber.d("Got format: ${format.mimeType}, URL length: ${downloadUrl.length}")

            // Create temporary file for download
            val mimeTypeRaw = format.mimeType.substringBefore(";").trim()
            Timber.tag("DownloadQuality").i("MimeType raw: '$mimeTypeRaw'")
            val extension = if (isVideoDownload) {
                // For video downloads, keep video extensions
                when {
                    mimeTypeRaw.contains("webm") -> "webm"
                    mimeTypeRaw.contains("mp4") -> "mp4"
                    mimeTypeRaw.contains("3gp") -> "3gp"
                    else -> "mp4" // Default to mp4 for videos
                }
            } else {
                // For audio downloads, convert to audio extensions
                // Note: OPUS from YouTube comes in WebM container (audio/webm; codecs="opus")
                // WebM uses Matroska container - metadata embedding not supported
                when {
                    mimeTypeRaw.contains("webm") -> "webm"
                    mimeTypeRaw.contains("mp4") -> "m4a"
                    mimeTypeRaw.contains("ogg") -> "ogg"
                    mimeTypeRaw.contains("opus") -> "ogg"
                    mimeTypeRaw.contains("mpeg") -> "mp3"
                    else -> mimeTypeRaw.substringAfterLast("/")
                }
            }
            Timber.tag("DownloadQuality").i("Extension determined: '$extension'")
            val tempFile = File(context.cacheDir, "temp_${song.id}.$extension")

            try {
                // Download to temp file
                Timber.tag("DownloadQuality").i("Starting file download to: ${tempFile.name}")
                downloadFile(downloadUrl, tempFile, song.id)
                Timber.tag("DownloadQuality").i("File download complete, size: ${tempFile.length()} bytes")

                if (!tempFile.exists() || tempFile.length() == 0L) {
                    throw Exception("Download failed - temp file not created or empty")
                }

                // Get metadata for embedding and file naming
                val title = song.song.title
                val album = song.album?.title
                val year = song.song.year ?: song.album?.year

                // For folder structure: use album artist if available, otherwise song artist
                // This ensures all songs from an album go into the same folder
                val artist = if (song.album != null) {
                    val albumWithArtists = database.albumUnfiltered(song.album.id).first()
                    albumWithArtists?.artists?.firstOrNull()?.name
                        ?: song.artists.firstOrNull()?.name
                        ?: "Unknown Artist"
                } else {
                    song.artists.firstOrNull()?.name ?: "Unknown Artist"
                }
                val duration = song.song.duration.takeIf { it > 0 }?.times(1000L) // Convert to milliseconds

                // Embed metadata if format supports it (audio only)
                val supportsEmbed = CoverArtEmbedder.supportsEmbedding(extension)
                Timber.tag("CoverArt").d("Extension: $extension, supportsEmbedding: $supportsEmbed, isVideo: $isVideoDownload")
                if (!isVideoDownload && supportsEmbed) {
                    val embedSuccess = CoverArtEmbedder.embedMetadataIntoFile(
                        context = context,
                        audioFile = tempFile,
                        thumbnailUrl = song.song.thumbnailUrl,
                        httpClient = httpClient,
                        title = title,
                        artist = artist,
                        album = album,
                        year = year,
                        durationMs = duration
                    )
                    Timber.tag("CoverArt").d("Metadata embedding result: $embedSuccess")
                }

                val fileName = "$title.$extension"
                val uri: Uri?

                // For formats that don't support embedding (WebM/OPUS), save cover art as separate file
                val shouldSaveSeparateCover = !isVideoDownload && !supportsEmbed && !song.song.thumbnailUrl.isNullOrBlank()

                if (isVideoDownload) {
                    // Save video to Movies/Zemer folder
                    val mimeType = mediaStoreHelper.getVideoMimeType(extension)
                    Timber.d("VIDEO DOWNLOAD PATH: Saving to Movies/Zemer: $fileName, mimeType: $mimeType, extension: $extension, tempFile size: ${tempFile.length()}")
                    uri = mediaStoreHelper.saveVideoFileToMediaStore(
                        tempFile = tempFile,
                        fileName = fileName,
                        mimeType = mimeType,
                        title = title,
                        artist = artist,
                        durationMs = duration
                    )
                } else {
                    // Save audio to Music/Zemer folder
                    val mimeType = mediaStoreHelper.getMimeType(extension)
                    Timber.d("AUDIO DOWNLOAD PATH: Saving to Music/Zemer: $fileName, mimeType: $mimeType, extension: $extension, tempFile size: ${tempFile.length()}")
                    uri = mediaStoreHelper.saveFileToMediaStore(
                        tempFile = tempFile,
                        fileName = fileName,
                        mimeType = mimeType,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = duration
                    )
                }
                Timber.d("MediaStore save result: $uri")

                // Save cover art as separate file for formats that don't support embedding
                if (uri != null && shouldSaveSeparateCover) {
                    try {
                        saveCoverArt(song, title, artist, album)
                    } catch (e: Exception) {
                        Timber.tag("CoverArt").w(e, "Failed to save separate cover art")
                    }
                }

                if (uri != null) {
                    // IMPORTANT: Update database FIRST before marking as completed
                    // This prevents race condition where user taps "remove" before DB has the URI
                    markSongAsDownloaded(song, uri.toString())

                    Timber.tag("DownloadQuality").i("=== DOWNLOAD SUCCESS ===")
                    Timber.tag("DownloadQuality").i("Saved to: $uri")

                    // Mark as completed in state flow (after DB is updated)
                    updateDownloadState(
                        song.id,
                        DownloadState(
                            songId = song.id,
                            status = DownloadState.Status.COMPLETED,
                            progress = 1f
                        )
                    )

                    // Clear the itag override now that download is complete
                    targetItagOverride.remove(song.id)
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
            Timber.tag("DownloadQuality").e(e, "Download FAILED: ${e.message}")
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
                // Max retries reached - clear itag override
                targetItagOverride.remove(song.id)
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

        val request = try {
            Request.Builder()
                .url(validatedUrl)
                .get()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Range", "bytes=0-")
                .build()
        } catch (e: Exception) {
            throw Exception("Failed to build download request for URL: $url", e)
        }

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
                    val currentTime = System.currentTimeMillis()
                    val timeSinceLastUpdate = currentTime - lastProgressUpdate

                    val progress = if (contentLength > 0) {
                        totalBytesRead.toFloat() / contentLength.toFloat()
                    } else 0f
                    val progressDelta = progress - lastReportedProgress

                    if (timeSinceLastUpdate >= PROGRESS_UPDATE_INTERVAL_MS ||
                        (contentLength > 0 && progressDelta >= PROGRESS_UPDATE_THRESHOLD)
                    ) {
                        lastProgressUpdate = currentTime
                        lastReportedProgress = progress
                        updateDownloadState(
                            songId,
                            DownloadState(
                                songId = songId,
                                status = DownloadState.Status.DOWNLOADING,
                                progress = progress,
                                bytesDownloaded = totalBytesRead,
                                totalBytes = if (contentLength > 0) contentLength else totalBytesRead
                            )
                        )
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
     * Save cover art as a separate image file for formats that don't support embedding.
     * Saves to the same folder as the audio file.
     */
    private suspend fun saveCoverArt(
        song: Song,
        title: String,
        artist: String,
        album: String?
    ) {
        val thumbnailUrl = song.song.thumbnailUrl ?: return

        try {
            // Download cover art
            val request = okhttp3.Request.Builder()
                .url(thumbnailUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Timber.tag("CoverArt").w("Failed to download cover art: ${response.code}")
                return
            }

            val imageData = response.body?.bytes() ?: return
            if (imageData.size < 1000) {
                Timber.tag("CoverArt").w("Cover art too small: ${imageData.size} bytes")
                return
            }

            // Determine image format from data
            val isJpeg = imageData.size > 2 && imageData[0] == 0xFF.toByte() && imageData[1] == 0xD8.toByte()
            val extension = if (isJpeg) "jpg" else "png"
            val mimeType = if (isJpeg) "image/jpeg" else "image/png"

            // Save to temp file
            val tempFile = File(context.cacheDir, "cover_${song.id}.$extension")
            tempFile.writeBytes(imageData)

            // Save to MediaStore in same folder as audio
            val coverFileName = "$title.$extension"
            mediaStoreHelper.saveCoverArtToMediaStore(
                tempFile = tempFile,
                fileName = coverFileName,
                mimeType = mimeType,
                artist = artist,
                album = album
            )

            tempFile.delete()
            Timber.tag("CoverArt").d("Saved separate cover art: $coverFileName")
        } catch (e: Exception) {
            Timber.tag("CoverArt").w(e, "Failed to save cover art")
        }
    }

    /**
     * Mark a song as downloaded in the database with MediaStore URI.
     * For video downloads: Updates videoMediaStoreUri (separate from audio downloads).
     * For audio downloads: Updates isDownloaded, isVideo=false, and mediaStoreUri.
     * This allows dual downloads where a song can have both audio AND video files.
     */
    private suspend fun markSongAsDownloaded(song: Song, mediaStoreUri: String) {
        ensureSongPersisted(song)

        database.query {
            if (song.song.isVideo) {
                // Video download: Update videoMediaStoreUri (separate from audio's mediaStoreUri)
                // This allows a song to have both audio AND video downloads
                database.updateVideoMediaStoreUri(song.id, mediaStoreUri)
            } else {
                // Audio download: Full update with mediaStoreUri
                database.upsert(
                    song.song.copy(
                        isDownloaded = true,
                        dateDownload = LocalDateTime.now(),
                        mediaStoreUri = mediaStoreUri,
                        isVideo = false
                    )
                )
            }
        }
    }

    /**
     * Ensure the song and its basic relations are present in the database so download flags
     * can be stored and surfaced in the Library.
     */
    private suspend fun ensureSongPersisted(song: Song) {
        val existing = database.song(song.id).first()

        database.query {
            val mergedSong = song.song.copy(
                isDownloaded = existing?.song?.isDownloaded ?: song.song.isDownloaded,
                dateDownload = existing?.song?.dateDownload ?: song.song.dateDownload,
                mediaStoreUri = existing?.song?.mediaStoreUri ?: song.song.mediaStoreUri,
                // Use the incoming isVideo value - allows resetting a video back to song
                isVideo = song.song.isVideo,
            )

            upsert(mergedSong)

            song.artists.forEachIndexed { index, artist ->
                insert(artist)
                insert(
                    SongArtistMap(
                        songId = song.id,
                        artistId = artist.id,
                        position = index,
                    )
                )
            }

            song.album?.let { album ->
                insert(album)
                insert(
                    SongAlbumMap(
                        songId = song.id,
                        albumId = album.id,
                        index = 0,
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
