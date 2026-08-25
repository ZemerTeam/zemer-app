package com.jtech.zemer.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import androidx.core.content.getSystemService
import com.jtech.zemer.BuildConfig
import com.jtech.zemer.R
import com.jtech.zemer.constants.AudioQuality
import com.jtech.zemer.constants.AudioQualityKey
import com.jtech.zemer.constants.PlaybackMode
import com.jtech.zemer.constants.PlaybackModeKey
import com.jtech.zemer.extensions.toEnum
import com.jtech.zemer.playback.relay.RelayDeviceId
import com.jtech.zemer.playback.relay.RelayDownload
import com.jtech.zemer.playback.relay.RelayStream
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.tracking.Tracker
import com.jtech.zemer.tracking.TrackingActionKind
import com.jtech.zemer.db.entities.SongAlbumMap
import com.jtech.zemer.db.entities.SongArtistMap
import com.jtech.zemer.utils.CoverArtEmbedder
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.getSuspend
import com.jtech.zemer.utils.MediaStoreHelper
import com.jtech.zemer.utils.UrlValidator
import com.jtech.zemer.utils.VideoMuxer
import com.jtech.zemer.utils.YTPlayerUtils
import com.jtech.zemer.utils.enumPreference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
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
import android.media.MediaMetadataRetriever
import java.io.File
import java.util.concurrent.TimeUnit
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
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
    private val audioQuality by enumPreference(context, AudioQualityKey, AudioQuality.AUTO)
    private val httpClient = OkHttpClient.Builder()
        .dns(ResilientDns())
        .build()

    // RELAY downloads hit the whitelisted relay host directly and carry a read timeout: the relay buffers
    // the whole file server-side, and the
    // default client has NO read timeout, so a truly stalled pull would hang forever. The timeout turns a
    // dead connection into a retriable failure. It is the INTER-read gap — INCLUDING time-to-first-byte,
    // which scales with file size here (the server buffers before the first byte) — so it must comfortably
    // clear a LONG file's buffering: a 60-90 min podcast over the residential-proxy tail. 60s was too
    // aggressive and could kill a slow long download mid-buffer. Isolated from the DIRECT client.
    private val relayHttpClient = OkHttpClient.Builder()
        .dns(ResilientDns())
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.MINUTES)
        .build()

    // Concurrent download limiter (max 3 simultaneous downloads)
    private val downloadSemaphore = Semaphore(MAX_CONCURRENT_DOWNLOADS)

    // Download state tracking
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    // Per-download requested video bitrate (kbps), set by downloadVideo, consumed by performDownload.
    private val requestedVideoBitrate = ConcurrentHashMap<String, Int>()

    // The requested video-quality TARGET label ("1080p"/"2160p") per download — same lifecycle rules
    // as requestedVideoBitrate: set at enqueue, MUST survive a failed attempt (a retry must not
    // silently downgrade the user's explicit quality), cleared on success/cancel/delete only.
    private val requestedVideoQuality = ConcurrentHashMap<String, String>()

    // Download queue
    private val downloadQueue = mutableListOf<Song>()
    // Touched from several coroutines (processQueue, performDownload's finally, cancel/delete) — must be
    // concurrent or it can corrupt / leak an uncancellable orphan job.
    private val activeDownloads = ConcurrentHashMap<String, Job>()

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
    fun downloadVideo(
        song: Song,
        maxVideoBitrateKbps: Int? = null,
        fromUser: Boolean = false,
        requestedQuality: String? = null,
    ) {
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
            // Telemetry: AFTER the no-op check, so a re-tap that enqueues nothing reports nothing;
            // fromUser=false (retries, self-repair, auto-download-on-like) never reports.
            if (fromUser) Tracker.action(TrackingActionKind.DOWNLOAD, song.id)
            // Remember the requested video bitrate + quality target for this download (consumed in
            // performDownload).
            maxVideoBitrateKbps?.let { requestedVideoBitrate[song.id] = it }
            requestedQuality?.let { requestedVideoQuality[song.id] = it }

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

    /**
     * Start downloading a song
     *
     * @param song The song to download
     */
    fun downloadSong(song: Song, fromUser: Boolean = false) {
        Timber.d("downloadSong called: id=${song.id}, title=${song.song.title}, isVideo=${song.song.isVideo}")
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
            // Telemetry: AFTER the no-op check, so a re-tap that enqueues nothing reports nothing;
            // fromUser=false (retries, self-repair, auto-download-on-like) never reports.
            if (fromUser) Tracker.action(TrackingActionKind.DOWNLOAD, song.id)

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
            requestedVideoBitrate.remove(songId)
            requestedVideoQuality.remove(songId)

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

    /**
     * Delete a downloaded song (or cancel and clear pending state) and remove it from MediaStore/DB.
     */
    suspend fun deleteDownloaded(songId: String) {
        // Cancel active work and purge queue
        activeDownloads[songId]?.cancel()
        activeDownloads.remove(songId)
        requestedVideoBitrate.remove(songId)
        requestedVideoQuality.remove(songId)
        synchronized(downloadQueue) {
            downloadQueue.removeAll { it.id == songId }
        }

        val song = database.song(songId).first()
        val uriString = song?.song?.mediaStoreUri
        if (uriString != null) {
            runCatching { mediaStoreHelper.deleteFromMediaStore(Uri.parse(uriString)) }
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
        }

        // Clear state entry
        _downloadStates.value = _downloadStates.value - songId
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
                    // NOTE: do NOT drop the requested video bitrate here. A failed attempt ends in this
                    // finally too, and retryDownload() re-issues downloadVideo(song) with no bitrate — if
                    // we erased it on failure, the retry would silently fall back to best/default quality
                    // (a large file over a metered connection the user explicitly capped). It is cleared
                    // on success, cancel and delete instead.

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

            // RELAY mode: a kosher-filtered device can't reach googlevideo, so skip on-device /player
            // resolution entirely and pull the audio from the whitelisted relay (webm/opus, itag 251).
            // The relay serves audio only, so a relay download is always audio regardless of the requested
            // rendition (a filtered device could not fetch a muxed video file anyway).
            val relayMode =
                context.dataStore.getSuspend(PlaybackModeKey).toEnum(PlaybackMode.DIRECT) == PlaybackMode.RELAY
            val videoDownload = isVideoDownload && !relayMode

            // Get playback URL. DIRECT: resolve via YTPlayerUtils (video stream when preferVideo=true).
            Timber.d("Starting download for ${if (videoDownload) "video" else "song"} ${song.id}: ${song.song.title}, relay=$relayMode")
            val playbackData = if (relayMode) null else YTPlayerUtils.playerResponseForPlayback(
                videoId = song.id,
                audioQuality = audioQuality,
                connectivityManager = connectivityManager,
                preferVideo = videoDownload,
                // An explicit per-download cap survives retries (requestedVideoBitrate); without one,
                // the shared metered-aware default applies — a video download must never silently
                // fetch the largest available file on a metered connection.
                maxVideoBitrateKbps = if (videoDownload) {
                    requestedVideoBitrate[song.id]
                        ?: VideoRendition.defaultMaxBitrateKbps(connectivityManager?.isActiveNetworkMetered == true)
                } else {
                    null
                },
                forDownload = true,
                // The requested quality TARGET ("1080p"/"2160p") — resolves to the best remux-capable
                // rung at or below it; null/AUTO keeps the automatic progressive pick. An adaptive
                // (video-only) rung is downloaded as video+audio and remuxed on-device below.
                videoQualityTarget = if (videoDownload) requestedVideoQuality[song.id] else null,
            ).getOrThrow()

            // An adaptive rung has no audio track of its own: it needs the separately-downloaded
            // audio partner and the on-device remux (see downloadAdaptiveVideoAndMux).
            val adaptiveVideoDownload =
                videoDownload && playbackData != null && VideoQualityLogic.isVideoOnly(playbackData.format)

            // NEVER write the DIRECT URL into the shared playback URL cache: it is a forDownload format
            // (muxed MP4 for videos, and generally a different itag than what's streaming), and
            // downloads never read the cache themselves (this resolution is always fresh). Writing
            // it — even into an absent/expired slot — poisoned mid-play seeks: a download started
            // while a song was playing from cached spans left this URL as the seek's stream source,
            // and the already-initialized extractor read a foreign container ("No valid varint
            // length mask found" / Source error). (Relay does not use this cache either.)
            // Relay: the dedicated /download endpoint (NOT /stream) resolves + pulls the whole file
            // server-side and returns one clean response (accurate Content-Length, clean close), so a plain
            // one-shot GET -> save works reliably where a full pull of the range-based /stream did not.
            val downloadUrl = if (relayMode) RelayStream.downloadUrl(song.id) else playbackData!!.streamUrl
            Timber.d("Download URL length: ${downloadUrl.length}, relay=$relayMode")

            // Create temporary file for download
            val extension = if (relayMode) {
                "webm" // relay audio is webm/opus (itag 251)
            } else {
                val mimeTypeRaw = playbackData!!.format.mimeType.substringBefore(";").trim()
                if (videoDownload) {
                    // For video downloads, keep video extensions
                    when {
                        mimeTypeRaw.contains("webm") -> "webm"
                        mimeTypeRaw.contains("mp4") -> "mp4"
                        mimeTypeRaw.contains("3gp") -> "3gp"
                        else -> "mp4" // Default to mp4 for videos
                    }
                } else {
                    // For audio downloads, convert to audio extensions
                    when {
                        mimeTypeRaw.contains("webm") -> "webm"
                        mimeTypeRaw.contains("mp4") -> "m4a"
                        mimeTypeRaw.contains("ogg") -> "ogg"
                        mimeTypeRaw.contains("opus") -> "opus"
                        mimeTypeRaw.contains("mpeg") -> "mp3"
                        else -> mimeTypeRaw.substringAfterLast("/")
                    }
                }
            }
            val tempFile = File(context.cacheDir, "temp_${song.id}.$extension")

            try {
                // Download to temp file (an adaptive video rung downloads video+audio and remuxes).
                if (adaptiveVideoDownload) {
                    downloadAdaptiveVideoAndMux(song, playbackData!!, tempFile, extension)
                } else {
                    downloadFile(downloadUrl, tempFile, song.id)
                }

                if (!tempFile.exists() || tempFile.length() == 0L) {
                    throw Exception("Download failed - temp file not created or empty")
                }

                // The extension/MIME used for the MediaStore entry. The relay serves Opus in a WebM
                // container (or occasionally MP4); `.webm` is REJECTED by MediaStore.Audio (Android maps
                // .webm to video/webm, inconsistent with an audio entry -> insert returns null -> "Failed
                // to save file to MediaStore"). Sniff the real container and label WebM as .opus / MP4 as
                // .m4a — both MediaStore-accepted, and in-app playback sniffs the real container regardless.
                val saveExtension = if (relayMode) sniffAudioExtension(tempFile) else extension

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
                // Songs reached via an album/playlist page often carry no duration (0), which shows as
                // "0:00" in the Downloaded list — backfill it from the playback response so the saved
                // file's metadata AND the DB row get a real length. In relay mode there is no playback
                // response (playbackData is null), so read the length straight off the downloaded file.
                val effectiveDurationSec = song.song.duration.takeIf { it > 0 }
                    ?: playbackData?.videoDetails?.lengthSeconds?.toIntOrNull()
                    ?: (if (relayMode) durationSecFromFile(tempFile) else null)
                    ?: 0
                val duration = effectiveDurationSec.takeIf { it > 0 }?.times(1000L) // ms for MediaStore

                // Embed metadata if format supports it (audio only). Keyed on the SNIFFED saveExtension,
                // not the pre-download `extension` (which is the hardcoded "webm" in relay mode) — else a
                // relay m4a audio download would never get its title/artist/cover art embedded.
                if (!videoDownload && CoverArtEmbedder.supportsEmbedding(saveExtension)) {
                    CoverArtEmbedder.embedMetadataIntoFile(
                        context = context,
                        audioFile = tempFile,
                        thumbnailUrl = song.song.thumbnailUrl,
                        httpClient = httpClient,
                        title = title,
                        artist = artist,
                        album = album,
                        year = year
                    )
                }

                val fileName = "$title.$saveExtension"
                val uri: Uri?

                if (videoDownload) {
                    // Save video to Movies/Zemer folder
                    val mimeType = mediaStoreHelper.getVideoMimeType(saveExtension)
                    Timber.d("VIDEO DOWNLOAD PATH: Saving to Movies/Zemer: $fileName, mimeType: $mimeType, extension: $saveExtension, tempFile size: ${tempFile.length()}")
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
                    val mimeType = mediaStoreHelper.getMimeType(saveExtension)
                    Timber.d("AUDIO DOWNLOAD PATH: Saving to Music/Zemer: $fileName, mimeType: $mimeType, extension: $saveExtension, tempFile size: ${tempFile.length()}")
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

                if (uri != null) {
                    // Download succeeded — the requested bitrate has served its purpose.
                    requestedVideoBitrate.remove(song.id)
                    requestedVideoQuality.remove(song.id)
                    // Mark as completed
                    updateDownloadState(
                        song.id,
                        DownloadState(
                            songId = song.id,
                            status = DownloadState.Status.COMPLETED,
                            progress = 1f
                        )
                    )

                    // Update database with MediaStore URI (preserving isVideo flag), backfilling the
                    // duration AND thumbnail if the source had none so the Downloaded list shows a real
                    // time and artwork — a standalone video opened from the Video player is built with
                    // neither, and the old per-screen download set the thumbnail explicitly.
                    val effectiveThumbnailUrl = song.song.thumbnailUrl?.takeIf { it.isNotBlank() }
                        ?: playbackData?.videoDetails?.thumbnail?.thumbnails?.lastOrNull()?.url
                    val songWithMeta = song.copy(
                        song = song.song.copy(
                            duration = if (song.song.duration > 0) song.song.duration else effectiveDurationSec,
                            thumbnailUrl = effectiveThumbnailUrl,
                            // A relay download is always audio (relay serves no muxed video), so a
                            // video-song saves an audio-only file. Persist isVideo=false so the LOCAL video
                            // toggle is never offered over a file with no video track (black video) and the
                            // track is not hidden from the downloaded-music list.
                            isVideo = if (relayMode) false else song.song.isVideo,
                        ),
                    )
                    markSongAsDownloaded(songWithMeta, uri.toString())
                } else {
                    throw Exception("Failed to save file to MediaStore")
                }
            } finally {
                // Clean up temp file
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }

        } catch (e: CancellationException) {
            // The user cancelled this download (cancelDownload/deleteDownloaded cancelled the job).
            // Must rethrow — otherwise the retry branch below would swallow it, resurrect the download,
            // overwrite the CANCELLED state, and pin the foreground notification service open forever.
            throw e
        } catch (e: RelayUnavailableException) {
            // A relay 404: the track is genuinely unavailable. Fail immediately (no 3x retry) with the
            // same contracted message the playback path surfaces.
            Timber.w("Relay download unavailable (404) for ${song.id}")
            updateDownloadState(
                song.id,
                DownloadState(
                    songId = song.id,
                    status = DownloadState.Status.FAILED,
                    error = context.getString(R.string.relay_error_unavailable),
                    retryAttempt = retryAttempt,
                ),
            )
        } catch (e: Exception) {
            Timber.e(e, "Download failed for song ${song.id}: ${e.message}")
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

    /** A relay /download returned 404: the track is genuinely unavailable, so the download must fail fast
     *  (no retry) with the contracted message rather than the generic retry-then-fail path. */
    private class RelayUnavailableException : Exception("Relay: track unavailable (404)")

    /**
     * Best-effort media duration in seconds from a downloaded file, for RELAY downloads where there is no
     * /player response to read `lengthSeconds` from. Returns null on any failure (the caller falls back to
     * 0, i.e. unchanged behavior). Not on a UI thread (performDownload runs on the download coroutine).
     */
    private fun durationSecFromFile(file: File): Int? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(file.absolutePath)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.let { (it / 1000).toInt() }
                ?.takeIf { it > 0 }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { mmr.release() }
        }
    }

    /** Reads a downloaded file's container magic bytes and delegates the (pure, unit-tested) mapping to
     *  [RelayDownload.audioExtensionFromMagic]. See there for why `.webm` can't go straight to MediaStore. */
    private fun sniffAudioExtension(file: File): String =
        try {
            val head = ByteArray(12)
            var off = 0
            file.inputStream().use { input ->
                // A single read() may return fewer than 12 bytes; loop so ftyp (offset 4-7) is never missed.
                while (off < head.size) {
                    val r = input.read(head, off, head.size - off)
                    if (r < 0) break
                    off += r
                }
            }
            RelayDownload.audioExtensionFromMagic(head, off)
        } catch (e: Exception) {
            RelayDownload.DEFAULT_AUDIO_EXTENSION
        }

    /**
     * Download a file from a URL to a temp file with progress tracking
     */
    /**
     * The beyond-progressive video download: the chosen rung is VIDEO-ONLY (adaptive), so fetch the
     * video stream and the audio stream separately, verify each against its declared contentLength
     * (googlevideo has no relay-style clean-close guarantee — a truncated track must fail the attempt,
     * never mux), then remux both into [outputFile] with the framework muxer ([VideoMuxer]) — MP4 for
     * avc1 rungs, WebM for the vp9-only 1440p/2160p rungs. Progress is mapped so the notification
     * ring spans both fetches proportionally to their sizes. Intermediate part-files are always
     * deleted; failures throw into the ordinary retry pipeline (an explicit quality is never silently
     * downgraded — the user asked for THAT rung).
     */
    private suspend fun downloadAdaptiveVideoAndMux(
        song: Song,
        videoData: YTPlayerUtils.PlaybackData,
        outputFile: File,
        extension: String,
    ) = withContext(Dispatchers.IO) {
        // The audio partner comes from the SAME video resolution (container-matched, same client — no
        // second /player round-trip, no client-disagreement). MP4 output needs AAC, WebM needs Opus.
        // A defensive second resolution covers the (rare) case that response carried no usable audio.
        val webmOutput = extension == "webm"
        val audioUrl: String
        val audioMimeType: String
        val audioContentLength: Long?
        if (videoData.downloadAudioUrl != null && videoData.downloadAudioMimeType != null) {
            audioUrl = videoData.downloadAudioUrl
            audioMimeType = videoData.downloadAudioMimeType
            audioContentLength = videoData.downloadAudioContentLength
        } else {
            val audioData = YTPlayerUtils.playerResponseForPlayback(
                videoId = song.id,
                audioQuality = AudioQuality.HIGH,
                connectivityManager = connectivityManager,
                forDownload = !webmOutput,
            ).getOrThrow()
            audioUrl = audioData.streamUrl
            audioMimeType = audioData.format.mimeType
            audioContentLength = audioData.format.contentLength
        }
        // The framework muxer is container-strict: MP4 output needs AAC, WebM output needs Opus/Vorbis.
        // The selection above guarantees that in practice — verify anyway, and clear the quality
        // request first so the RETRY falls back to the automatic progressive pick (reported, bounded)
        // instead of re-failing on the identical incompatibility forever.
        val audioIsWebm = audioMimeType.startsWith("audio/webm")
        if (webmOutput != audioIsWebm) {
            requestedVideoQuality.remove(song.id)
            throw Exception("No mux-compatible audio for $extension output (got $audioMimeType)")
        }

        // Completeness is verified against each track's declared contentLength, so BOTH must declare
        // one — otherwise an early-closed connection could feed the muxer a truncated track that it
        // happily copies into a "successful" (silently corrupt / cut-short) download. Adaptive DASH
        // formats always carry contentLength; a missing one is anomalous. Fail the attempt — but clear
        // the quality request first (like the mux-INCOMPATIBLE branch below) so the retry degrades to
        // the automatic progressive pick instead of re-picking this same unverifiable rung to a hard
        // permanent FAILED with no fallback.
        if (videoData.format.contentLength == null || audioContentLength == null) {
            requestedVideoQuality.remove(song.id)
            throw Exception("Adaptive download missing contentLength (video=${videoData.format.contentLength}, audio=$audioContentLength) — cannot verify completeness")
        }

        val videoPart = File(context.cacheDir, "temp_${song.id}.video.part")
        val audioPart = File(context.cacheDir, "temp_${song.id}.audio.part")
        try {
            val videoBytes = videoData.format.contentLength ?: 0L
            val audioBytes = audioContentLength ?: 0L
            val total = (videoBytes + audioBytes).takeIf { it > 0 }
            val videoShare = if (total != null) videoBytes.toFloat() / total else 0.9f
            // The two fetches span the FULL 0..1 progress so the ring reaches 100% when the bytes are
            // down; the on-device mux + MediaStore save (which emit no byte progress) then run at 100%
            // as a brief finalization before COMPLETED — better than the ring visibly hanging partway
            // through, which reads as a stuck download (worst on a long 4K remux).
            downloadFile(
                videoData.streamUrl, videoPart, song.id,
                progressBase = 0f, progressSpan = videoShare,
                expectedBytes = videoData.format.contentLength,
            )
            downloadFile(
                audioUrl, audioPart, song.id,
                progressBase = videoShare, progressSpan = 1f - videoShare,
                expectedBytes = audioContentLength,
            )
            when (VideoMuxer.mux(videoPart, audioPart, outputFile, webm = webmOutput)) {
                VideoMuxer.Result.SUCCESS -> {}
                VideoMuxer.Result.INCOMPATIBLE -> {
                    // Deterministic for these inputs — clear the request so the retry falls back to
                    // the automatic progressive pick instead of re-failing forever.
                    requestedVideoQuality.remove(song.id)
                    throw Exception("Remux incompatible for ${song.id} (${videoData.format.qualityLabel})")
                }
                VideoMuxer.Result.TRANSIENT -> {
                    // I/O (disk full / read error) — PRESERVE the quality so the retry re-attempts the
                    // same rung (never silently downgrade what the user asked to save).
                    throw Exception("Remux failed (transient) for ${song.id}")
                }
            }
        } finally {
            videoPart.delete()
            audioPart.delete()
        }
    }

    private suspend fun downloadFile(
        url: String,
        outputFile: File,
        songId: String,
        // Maps this fetch's 0..1 progress into [progressBase, progressBase+progressSpan] so a
        // multi-part download (adaptive video + audio) renders ONE monotonic ring.
        progressBase: Float = 0f,
        progressSpan: Float = 1f,
        // When the format declared a contentLength, verify we read it all — googlevideo can close a
        // connection early, and committing a truncated track poisons the remux/file.
        expectedBytes: Long? = null,
    ) = withContext(Dispatchers.IO) {
        // Validate URL before attempting to build request
        val validatedUrl = UrlValidator.validateAndParseUrl(url)
            ?: throw Exception("Invalid download URL: $url")

        // Relay downloads use the direct, read-timeout client and hit the dedicated
        // /download endpoint, which resolves + buffers the whole file server-side and returns ONE clean
        // response (accurate Content-Length, clean close). So a plain one-shot GET -> save is reliable — no
        // client-side chunking needed (a full pull of the range-based /stream was what stalled at the tail).
        val isRelay = validatedUrl.toString().startsWith(RelayStream.BASE)
        val client = if (isRelay) relayHttpClient else httpClient
        // Relay-only device id (null in debug / DIRECT) so /download counts toward distinct relay users.
        val relayDeviceId = if (isRelay) RelayDeviceId.get(context) else null

        val request = try {
            Request.Builder()
                .url(validatedUrl)
                .get()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
                // /download returns the full file as a plain response; only the DIRECT googlevideo path
                // needs the open-ended range request.
                .apply {
                    if (!isRelay) header("Range", "bytes=0-")
                    // DEBUG marks the request so the relay serves but does not count it; release instead
                    // carries the relay-only device id so the relay can attribute the download to a filter.
                    else if (BuildConfig.DEBUG) header(RelayStream.DEBUG_HEADER, "1")
                    else relayDeviceId?.let { header(RelayDeviceId.HEADER, it) }
                }
                .build()
        } catch (e: Exception) {
            throw Exception("Failed to build download request for URL: $url", e)
        }

        val response = client.newCall(request).execute()
        val responseCode = response.code

        if (!response.isSuccessful) {
            response.close()
            when (RelayDownload.classifyHttpError(isRelay, responseCode)) {
                // Genuinely unavailable: fail fast (no 3x retry) with the contracted message.
                RelayDownload.HttpErrorAction.UNAVAILABLE -> throw RelayUnavailableException()
                // Transient relay/upstream error: retryable, but the message the user finally sees should be
                // the contracted one, not a raw "HTTP error 502" — it rides the exception to the FAILED state.
                RelayDownload.HttpErrorAction.TRANSIENT -> throw Exception(context.getString(R.string.relay_error_retry))
                RelayDownload.HttpErrorAction.GENERIC -> throw Exception("HTTP error $responseCode: ${response.message}")
            }
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

                    val rawProgress = if (contentLength > 0) {
                        totalBytesRead.toFloat() / contentLength.toFloat()
                    } else 0f
                    val progress = progressBase + rawProgress * progressSpan
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

        // Relay /download promises an accurate Content-Length and a clean close. When it's present, verify
        // completeness rather than commit a truncated file. When it's ABSENT (a chunked/gzip response makes
        // OkHttp report -1), accept the clean-closed stream instead of hard-failing EVERY relay download —
        // a rare truncation on a hypothetical future server config is far better than making relay downloads
        // impossible. Scoped to relay — the DIRECT range path is unchanged.
        if (isRelay && contentLength > 0 && totalBytesRead < contentLength) {
            throw Exception("Relay download incomplete: $totalBytesRead/$contentLength bytes")
        }
        // The adaptive two-stream path passes the format's declared size: a truncated track must fail
        // the attempt (the remux would otherwise bake a half-video into a "successful" download).
        if (expectedBytes != null && expectedBytes > 0 && totalBytesRead < expectedBytes) {
            throw Exception("Download incomplete: $totalBytesRead/$expectedBytes bytes")
        }
    }

    /**
     * Update the download state for a song. While a download is in progress, progress is clamped to
     * be monotonic (never decreases): a retry restarts the byte counter at 0 and the per-attempt
     * "Retrying…" state carries progress 0, which otherwise makes the UI ring jump backwards and
     * "bounce" up to 100%. Holding the last value until the new attempt catches up keeps it smooth.
     */
    private fun updateDownloadState(songId: String, state: DownloadState) {
        val prev = _downloadStates.value[songId]
        val adjusted =
            if (state.status == DownloadState.Status.DOWNLOADING &&
                prev?.status == DownloadState.Status.DOWNLOADING &&
                prev.progress > state.progress
            ) {
                state.copy(
                    progress = prev.progress,
                    bytesDownloaded = maxOf(prev.bytesDownloaded, state.bytesDownloaded),
                )
            } else {
                state
            }
        _downloadStates.value = _downloadStates.value + (songId to adjusted)
    }

    /**
     * Mark a song as downloaded in the database with its MediaStore URI.
     *
     * Persists the row (with isDownloaded = true), its artists and its album in ONE transaction.
     * This previously called [ensureSongPersisted] (which upserts the row with isDownloaded = false
     * for a brand-new song) and THEN a separate `query {}` upserting isDownloaded = true. `query {}`
     * runs on a fire-and-forget executor, so those two writes RACED: intermittently the "false" write
     * landed last, so a download whose file saved fine still ended up isDownloaded = 0 with no
     * mediaStoreUri — it vanished from the Downloaded list and played by streaming. A single
     * transaction whose authoritative write is isDownloaded = true removes the race.
     */
    private suspend fun markSongAsDownloaded(song: Song, mediaStoreUri: String) {
        // Base the persisted row on the EXISTING row (if any), not the caller's Song. The caller may
        // hand us a stale/partial Song (e.g. an album-page entity with liked = false, inLibrary = null);
        // a full-row @Upsert of that would silently un-like the song / drop it from the library. We only
        // overwrite the download-owned columns here (+ isVideo, + duration/thumbnail backfill when the
        // row lacks them) and preserve everything else the user set.
        val existing = database.song(song.id).first()?.song
        database.transaction {
            val base = existing ?: song.song
            upsert(
                base.copy(
                    isVideo = song.song.isVideo,
                    isDownloaded = true,
                    dateDownload = LocalDateTime.now(),
                    mediaStoreUri = mediaStoreUri,
                    duration = if (base.duration > 0) base.duration else song.song.duration,
                    thumbnailUrl = base.thumbnailUrl ?: song.song.thumbnailUrl,
                )
            )
            insertSongRelations(song)
        }
    }

    /** Insert a song's artists + album relations. Shared by [markSongAsDownloaded] and
     *  [ensureSongPersisted] so the relation wiring lives in one place. Call inside a query/transaction
     *  block (receiver is the [MusicDatabase]). */
    private fun MusicDatabase.insertSongRelations(song: Song) {
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
            insertSongRelations(song)
        }
    }

    /**
     * Get the download state for a song
     */
    fun getDownloadState(songId: String): DownloadState? {
        return _downloadStates.value[songId]
    }
}
