@file:Suppress("DEPRECATION")

package com.jtech.zemer.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import android.net.ConnectivityManager
import android.os.Binder
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Player.EVENT_MEDIA_ITEM_TRANSITION
import androidx.media3.common.Player.EVENT_MEDIA_METADATA_CHANGED
import androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY
import androidx.media3.common.Player.EVENT_TIMELINE_CHANGED
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Timeline
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import java.sql.SQLException
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import timber.log.Timber
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.utils.ResilientDns
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.jtech.zemer.MainActivity
import com.jtech.zemer.R
import com.jtech.zemer.constants.AndroidAutoTargetPlaylistKey
import com.jtech.zemer.constants.AudioNormalizationKey
import com.jtech.zemer.constants.AudioOffload
import com.jtech.zemer.constants.AudioQualityKey
import com.jtech.zemer.constants.AutoDownloadOnLikeKey
import com.jtech.zemer.constants.AutoLoadMoreKey
import com.jtech.zemer.constants.AutoSkipNextOnErrorKey
import com.jtech.zemer.constants.DisableLoadMoreWhenRepeatAllKey
import com.jtech.zemer.constants.HideExplicitKey
import com.jtech.zemer.constants.HistoryDuration
import com.jtech.zemer.constants.MediaSessionConstants
import com.jtech.zemer.constants.MediaSessionConstants.CommandAddToTargetPlaylist
import com.jtech.zemer.constants.MediaSessionConstants.CommandToggleLike
import com.jtech.zemer.constants.MediaSessionConstants.CommandToggleRepeatMode
import com.jtech.zemer.constants.MediaSessionConstants.CommandToggleShuffle
import com.jtech.zemer.constants.MediaSessionConstants.CommandToggleStartRadio
import com.jtech.zemer.constants.PauseListenHistoryKey
import com.jtech.zemer.constants.PersistentQueueKey
import com.jtech.zemer.constants.PlayerVolumeKey
import com.jtech.zemer.constants.RepeatModeKey
import com.jtech.zemer.constants.ShowLyricsKey
import com.jtech.zemer.constants.SkipSilenceKey
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.Event
import com.jtech.zemer.db.entities.FormatEntity
import com.jtech.zemer.db.entities.LyricsEntity
import com.jtech.zemer.db.entities.RelatedSongMap
import com.jtech.zemer.di.DownloadCache
import com.jtech.zemer.di.PlayerCache
import com.jtech.zemer.extensions.SilentHandler
import com.jtech.zemer.extensions.collect
import com.jtech.zemer.extensions.collectLatest
import com.jtech.zemer.extensions.currentMetadata
import com.jtech.zemer.extensions.findNextMediaItemById
import com.jtech.zemer.extensions.mediaItems
import com.jtech.zemer.extensions.metadata
import com.jtech.zemer.extensions.setOffloadEnabled
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.extensions.toPersistQueue
import com.jtech.zemer.extensions.toQueue
import com.jtech.zemer.lyrics.LyricsHelper
import com.jtech.zemer.models.PersistPlayerState
import com.jtech.zemer.models.PersistQueue
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.playback.queues.EmptyQueue
import com.jtech.zemer.playback.queues.Queue
import com.jtech.zemer.playback.queues.YouTubeQueue
import com.jtech.zemer.playback.queues.filterExplicit
import com.jtech.zemer.utils.CoilBitmapLoader
import com.jtech.zemer.utils.filterWhitelisted
import com.jtech.zemer.utils.NetworkConnectivityObserver
import com.jtech.zemer.utils.SyncUtils
import com.jtech.zemer.utils.YTPlayerUtils
import com.zemer.cipher.CipherDeobfuscator
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.hasNotificationPermission
import com.jtech.zemer.widget.MusicWidget
import com.jtech.zemer.utils.enumPreference
import com.jtech.zemer.utils.enumPreferenceFlow
import com.jtech.zemer.utils.get
import com.jtech.zemer.utils.reportException
import com.metrolist.innertube.utils.parseCookieString
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.concurrent.Executor
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@AndroidEntryPoint
class MusicService :
    MediaLibraryService(),
    Player.Listener,
    PlaybackStatsListener.Callback {
    @Inject
    lateinit var databaseLazy: dagger.Lazy<MusicDatabase>
    val database: MusicDatabase
        get() = databaseLazy.get()

    @Inject
    lateinit var lyricsHelper: LyricsHelper

    @Inject
    lateinit var syncUtils: SyncUtils

    @Inject
    lateinit var downloadUtil: DownloadUtil

    @Inject
    lateinit var mediaLibrarySessionCallback: MediaLibrarySessionCallback

    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var lastAudioFocusState = AudioManager.AUDIOFOCUS_NONE
    private var wasPlayingBeforeAudioFocusLoss = false
    private var hasAudioFocus = false

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val binder = MusicBinder()

    private lateinit var connectivityManager: ConnectivityManager
    lateinit var connectivityObserver: NetworkConnectivityObserver
    val waitingForNetworkConnection = MutableStateFlow(false)
    private val isNetworkConnected = MutableStateFlow(false)

    private val audioQualityFlow = enumPreferenceFlow(
        this,
        AudioQualityKey,
        com.jtech.zemer.constants.AudioQuality.AUTO
    )
    private var audioQuality = com.jtech.zemer.constants.AudioQuality.AUTO

    private var currentQueue: Queue = EmptyQueue
    var queueTitle: String? = null

    val currentMediaMetadata = MutableStateFlow<com.jtech.zemer.models.MediaMetadata?>(null)
    private val currentSong =
        currentMediaMetadata
            .flatMapLatest { mediaMetadata ->
                database.song(mediaMetadata?.id)
            }.stateIn(scope, SharingStarted.Lazily, null)
    private val currentFormat =
        currentMediaMetadata.flatMapLatest { mediaMetadata ->
            database.format(mediaMetadata?.id)
        }

    val playerVolume = MutableStateFlow(dataStore.get(PlayerVolumeKey, 1f).coerceIn(0f, 1f))

    lateinit var sleepTimer: SleepTimer

    @Inject
    @PlayerCache
    lateinit var playerCache: SimpleCache

    @Inject
    @DownloadCache
    lateinit var downloadCache: SimpleCache

    lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession

    private var isAudioEffectSessionOpened = false
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private var lastPlaybackSpeed = 1.0f

    val automixItems = MutableStateFlow<List<MediaItem>>(emptyList())

    private var consecutivePlaybackErr = 0

    // Use shared URL cache from DownloadUtil for consistency between playback and downloads
    private val songUrlCache get() = DownloadUtil.sharedUrlCache

    override fun onCreate() {
        super.onCreate()
        // Media3's MediaLibraryService handles foreground notification automatically
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(
                this,
                { NOTIFICATION_ID },
                CHANNEL_ID,
                R.string.music_player
            )
                .apply {
                    setSmallIcon(R.drawable.small_icon)
                },
        )
        player =
            ExoPlayer
                .Builder(this)
                .setMediaSourceFactory(createMediaSourceFactory())
                .setRenderersFactory(createRenderersFactory())
                .setLoadControl(
                    // media3 1.8.0 defaults, except start playback once ~750ms is buffered (vs the
                    // 1000ms default) so the first audio is audible sooner. Min/max (50_000) and
                    // after-rebuffer (2_000) are left at the actual media3 1.8.0 defaults, so
                    // buffering/rebuffer recovery is unchanged (no stutter regression).
                    DefaultLoadControl
                        .Builder()
                        .setBufferDurationsMs(50_000, 50_000, 750, 2_000)
                        .build(),
                )
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    false,
                ).setSeekBackIncrementMs(5000)
                .setSeekForwardIncrementMs(5000)
                .build()
                .apply {
                    addListener(this@MusicService)
                    sleepTimer = SleepTimer(scope, this)
                    addListener(sleepTimer)
                    addAnalyticsListener(PlaybackStatsListener(false, this@MusicService))
                    setOffloadEnabled(dataStore.get(AudioOffload, false))
                }

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        setupAudioFocusRequest()

        mediaLibrarySessionCallback.apply {
            toggleLike = ::toggleLike
            toggleStartRadio = ::toggleStartRadio
            toggleLibrary = ::toggleLibrary
            addToTargetPlaylist = ::addToTargetPlaylist
        }
        mediaSession =
            MediaLibrarySession
                .Builder(this, player, mediaLibrarySessionCallback)
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).setBitmapLoader(CoilBitmapLoader(this, scope))
                .build()
        player.repeatMode = dataStore.get(RepeatModeKey, REPEAT_MODE_OFF)

        // Keep a connected controller so that notification works (deferred to avoid blocking)
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        scope.launch(Dispatchers.Default) {
            try {
                MediaController.Builder(this@MusicService, sessionToken).buildAsync().get()
            } catch (e: Exception) {
            }
        }

        connectivityManager = getSystemService<ConnectivityManager>()
            ?: throw IllegalStateException("ConnectivityManager not available on this device")
        connectivityObserver = NetworkConnectivityObserver(this)

        // Initialize audioQuality from preference
        scope.launch {
            audioQualityFlow.collect { quality ->
                audioQuality = quality
            }
        }

        // Keep YTPlayerUtils in sync with the stream source toggles
        scope.launch {
            dataStore.data.collect { prefs ->
                val disabled = mutableSetOf<String>()
                if (prefs[com.jtech.zemer.constants.StreamSourceWebRemixKey] == false) disabled += "WEB_REMIX"
                if (prefs[com.jtech.zemer.constants.StreamSourceTVHTML5Key]   == false) disabled += "TVHTML5"
                if (prefs[com.jtech.zemer.constants.StreamSourceAndroidVRKey] == false) {
                    disabled += "ANDROID_VR"
                }
                // IOS/IPADOS are spc-gated and ANDROID_CREATOR needs DroidGuard — proven unfixable,
                // so they default OFF (`!= true`: unset or false both disable; only explicit on enables).
                if (prefs[com.jtech.zemer.constants.StreamSourceIOSKey]       != true)  disabled += "IOS"
                if (prefs[com.jtech.zemer.constants.StreamSourceIPadOSKey]    != true)  disabled += "IOS" // IPADOS uses IOS clientName
                if (prefs[com.jtech.zemer.constants.StreamSourceVisionOSKey]  == false) disabled += "VISIONOS"
                if (prefs[com.jtech.zemer.constants.StreamSourceWebCreatorKey] == false) disabled += "WEB_CREATOR"
                if (prefs[com.jtech.zemer.constants.StreamSourceAndroidCreatorKey] != true)  disabled += "ANDROID_CREATOR"
                YTPlayerUtils.disabledStreamClients = disabled
            }
        }

        scope.launch {
            connectivityObserver.networkStatus.collect { isConnected ->
                isNetworkConnected.value = isConnected
                if (isConnected && waitingForNetworkConnection.value) {
                    // Simple auto-play logic like OuterTune
                    waitingForNetworkConnection.value = false
                    if (player.currentMediaItem != null && player.playWhenReady) {
                        player.prepare()
                        player.play()
                    }
                }
            }
        }

        playerVolume.collectLatest(scope) {
            player.volume = it
        }

        playerVolume.debounce(1000).collect(scope) { volume ->
            dataStore.edit { settings ->
                settings[PlayerVolumeKey] = volume
            }
        }

        currentSong.debounce(1000).collect(scope) { song ->
            updateNotification()
            updateWidget()
        }

        combine(
            currentMediaMetadata.distinctUntilChangedBy { it?.id },
            dataStore.data.map { it[ShowLyricsKey] ?: false }.distinctUntilChanged(),
        ) { mediaMetadata, showLyrics ->
            mediaMetadata to showLyrics
        }.collectLatest(scope) { (mediaMetadata, showLyrics) ->
            if (showLyrics && mediaMetadata != null && database.lyrics(mediaMetadata.id)
                    .first() == null
            ) {
                val lyrics = lyricsHelper.getLyrics(mediaMetadata)
                database.query {
                    upsert(
                        LyricsEntity(
                            id = mediaMetadata.id,
                            lyrics = lyrics,
                        ),
                    )
                }
            }
        }

        dataStore.data
            .map { it[SkipSilenceKey] ?: true }
            .distinctUntilChanged()
            .collectLatest(scope) {
                player.skipSilenceEnabled = it
            }

        combine(
            currentFormat,
            dataStore.data
                .map { it[AudioNormalizationKey] ?: true }
                .distinctUntilChanged(),
        ) { format, normalizeAudio ->
            format to normalizeAudio
        }.collectLatest(scope) { (format, normalizeAudio) -> setupLoudnessEnhancer()}

        // Observe authentication state changes to keep MusicService in sync
        scope.launch {
            dataStore.data
                .map { it[com.jtech.zemer.constants.InnerTubeCookieKey] }
                .distinctUntilChanged()
                .collect { cookie ->
                    // Update YouTube auth context in MusicService when it changes
                    YouTube.cookie = cookie

                    // Clear stream cache when auth changes to force fresh URLs with new auth
                    songUrlCache.clear()

                    // Log authentication state change for debugging
                    val isLoggedIn = cookie != null && "SAPISID" in parseCookieString(cookie ?: "")
                    android.util.Log.d("MusicService", "Auth state changed: isLoggedIn=$isLoggedIn")
                }
        }

        if (dataStore.get(PersistentQueueKey, true)) {
            runCatching {
                filesDir.resolve(PERSISTENT_QUEUE_FILE).inputStream().use { fis ->
                    ObjectInputStream(fis).use { oos ->
                        oos.readObject() as PersistQueue
                    }
                }
            }.onSuccess { queue ->
                // Convert back to proper queue type
                val restoredQueue = queue.toQueue()
                playQueue(
                    queue = restoredQueue,
                    playWhenReady = false,
                )
            }
            runCatching {
                filesDir.resolve(PERSISTENT_AUTOMIX_FILE).inputStream().use { fis ->
                    ObjectInputStream(fis).use { oos ->
                        oos.readObject() as PersistQueue
                    }
                }
            }.onSuccess { queue ->
                automixItems.value = queue.items.map { it.toMediaItem() }
            }

            // Restore player state
            runCatching {
                filesDir.resolve(PERSISTENT_PLAYER_STATE_FILE).inputStream().use { fis ->
                    ObjectInputStream(fis).use { oos ->
                        oos.readObject() as PersistPlayerState
                    }
                }
            }.onSuccess { playerState ->
                // Restore player settings after queue is loaded
                scope.launch {
                    delay(1000) // Wait for queue to be loaded
                    player.repeatMode = playerState.repeatMode
                    player.shuffleModeEnabled = playerState.shuffleModeEnabled
                    player.volume = playerState.volume

                    // Restore position if it's still valid
                    if (playerState.currentMediaItemIndex < player.mediaItemCount) {
                        player.seekTo(playerState.currentMediaItemIndex, playerState.currentPosition)
                    }
                }
            }
        }

        // Save queue periodically to prevent queue loss from crash or force kill
        scope.launch {
            while (isActive) {
                delay(30.seconds)
                if (dataStore.get(PersistentQueueKey, true)) {
                    saveQueueToDisk()
                }
            }
        }

        // Save queue more frequently when playing to ensure state is preserved
        scope.launch {
            while (isActive) {
                delay(10.seconds)
                if (dataStore.get(PersistentQueueKey, true) && player.isPlaying) {
                    saveQueueToDisk()
                }
            }
        }
    }

    private fun setupAudioFocusRequest() {
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener { focusChange ->
                handleAudioFocusChange(focusChange)
            }
            .setAcceptsDelayedFocusGain(true)
            .build()
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true

                if (wasPlayingBeforeAudioFocusLoss) {
                    player.play()
                    wasPlayingBeforeAudioFocusLoss = false
                }

                player.volume = playerVolume.value

                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                wasPlayingBeforeAudioFocusLoss = false

                if (player.isPlaying) {
                    player.pause()
                }

                abandonAudioFocus()

                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                wasPlayingBeforeAudioFocusLoss = player.isPlaying

                if (player.isPlaying) {
                    player.pause()
                }

                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {

                hasAudioFocus = false

                wasPlayingBeforeAudioFocusLoss = player.isPlaying

                if (player.isPlaying) {
                    player.volume = (playerVolume.value * 0.2f)
                }

                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> {

                hasAudioFocus = true

                if (wasPlayingBeforeAudioFocusLoss) {
                    player.play()
                    wasPlayingBeforeAudioFocusLoss = false
                }

                player.volume = playerVolume.value

                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> {
                hasAudioFocus = true

                player.volume = playerVolume.value

                lastAudioFocusState = focusChange
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true

        audioFocusRequest?.let { request ->
            val result = audioManager.requestAudioFocus(request)
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            return hasAudioFocus
        }
        return false
    }

    private fun abandonAudioFocus() {
        if (hasAudioFocus) {
            audioFocusRequest?.let { request ->
                audioManager.abandonAudioFocusRequest(request)
                hasAudioFocus = false
            }
        }
    }

    fun hasAudioFocusForPlayback(): Boolean {
        return hasAudioFocus
    }

    private fun waitOnNetworkError() {
        waitingForNetworkConnection.value = true
    }

    private fun skipOnError() {
        /**
         * Auto skip to the next media item on error.
         *
         * To prevent a "runaway diesel engine" scenario, force the user to take action after
         * too many errors come up too quickly. Pause to show player "stopped" state
         */
        consecutivePlaybackErr += 2
        val nextWindowIndex = player.nextMediaItemIndex

        if (consecutivePlaybackErr <= MAX_CONSECUTIVE_ERR && nextWindowIndex != C.INDEX_UNSET) {
            player.seekTo(nextWindowIndex, C.TIME_UNSET)
            player.prepare()
            player.play()
            return
        }

        player.pause()
        consecutivePlaybackErr = 0
    }

    private fun stopOnError() {
        player.pause()
    }

    private var widgetTickerJob: Job? = null

    private fun updateWidget() {
        scope.launch {
            val metadata = currentMediaMetadata.value
            val isPlaying = player.isPlaying
            val title = metadata?.title ?: getString(R.string.app_name)
            val artist = metadata?.artists?.joinToString(", ") { it.name } ?: ""
            val albumArtUrl = metadata?.thumbnailUrl
            val positionMs = player.currentPosition.coerceAtLeast(0L)
            val durationMs = player.duration.takeIf { it > 0L } ?: 0L

            MusicWidget.updateWidget(
                context = this@MusicService,
                title = title,
                artist = artist,
                isPlaying = isPlaying,
                albumArtUrl = albumArtUrl,
                positionMs = positionMs,
                durationMs = durationMs,
            )
        }
    }

    /** While playing, refresh the widget's seek bar/time once a second. Self-stops when paused. */
    private fun startWidgetTicker() {
        if (widgetTickerJob?.isActive == true) return
        widgetTickerJob = scope.launch {
            // Only spin the per-second ticker when a widget is actually placed — checked once per
            // playback session rather than every tick, so users with no widget pay nothing.
            if (!MusicWidget.hasPlacedWidget(this@MusicService)) return@launch
            while (isActive && player.isPlaying) {
                updateWidget()
                delay(1000)
            }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) startWidgetTicker() else updateWidget()
    }

    private fun updateNotification() {
        mediaSession.setCustomLayout(
            listOf(
                CommandButton
                    .Builder()
                    .setDisplayName(
                        getString(
                            if (currentSong.value?.song?.liked ==
                                true
                            ) {
                                R.string.action_remove_like
                            } else {
                                R.string.action_like
                            },
                        ),
                    )
                    .setIconResId(if (currentSong.value?.song?.liked == true) R.drawable.favorite else R.drawable.favorite_border)
                    .setSessionCommand(CommandToggleLike)
                    .setEnabled(currentSong.value != null)
                    .build(),
                CommandButton
                    .Builder()
                    .setDisplayName(
                        getString(
                            when (player.repeatMode) {
                                REPEAT_MODE_OFF -> R.string.repeat_mode_off
                                REPEAT_MODE_ONE -> R.string.repeat_mode_one
                                REPEAT_MODE_ALL -> R.string.repeat_mode_all
                                else -> throw IllegalStateException()
                            },
                        ),
                    ).setIconResId(
                        when (player.repeatMode) {
                            REPEAT_MODE_OFF -> R.drawable.repeat
                            REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                            REPEAT_MODE_ALL -> R.drawable.repeat_on
                            else -> throw IllegalStateException()
                        },
                    ).setSessionCommand(CommandToggleRepeatMode)
                    .build(),
                CommandButton
                    .Builder()
                    .setDisplayName(getString(if (player.shuffleModeEnabled) R.string.action_shuffle_off else R.string.action_shuffle_on))
                    .setIconResId(if (player.shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle)
                    .setSessionCommand(CommandToggleShuffle)
                    .build(),
                CommandButton.Builder()
                    .setDisplayName(getString(R.string.start_radio))
                    .setIconResId(R.drawable.radio)
                    .setSessionCommand(CommandToggleStartRadio)
                    .setEnabled(currentSong.value != null)
                    .build(),
                CommandButton.Builder()
                    .setDisplayName(getString(R.string.android_auto_target_playlist))
                    .setIconResId(R.drawable.playlist_add)
                    .setSessionCommand(CommandAddToTargetPlaylist)
                    .setEnabled(currentSong.value != null)
                    .build(),
            ),
        )
    }

    private suspend fun recoverSong(
        mediaId: String,
        playbackData: YTPlayerUtils.PlaybackData? = null
    ) {
        val song = database.song(mediaId).first()
        val mediaMetadata = withContext(Dispatchers.Main) {
            player.findNextMediaItemById(mediaId)?.metadata
        } ?: return
        val duration = song?.song?.duration?.takeIf { it != -1 }
            ?: mediaMetadata.duration.takeIf { it != -1 }
            ?: (playbackData?.videoDetails ?: YTPlayerUtils.playerResponseForMetadata(mediaId)
                .getOrNull()?.videoDetails)?.lengthSeconds?.toInt()
            ?: -1
        database.query {
            if (song == null) insert(mediaMetadata.copy(duration = duration))
            else if (song.song.duration == -1) update(song.song.copy(duration = duration))
        }
        if (!database.hasRelatedSongs(mediaId)) {
            val relatedEndpoint =
                YouTube.next(WatchEndpoint(videoId = mediaId)).getOrNull()?.relatedEndpoint
                    ?: return
            val relatedPage = YouTube.related(relatedEndpoint).getOrNull() ?: return
            val filteredSongs = relatedPage.songs.filterWhitelisted(database).filterIsInstance<SongItem>()
            database.query {
                filteredSongs
                    .map(SongItem::toMediaMetadata)
                    .onEach(::insert)
                    .map {
                        RelatedSongMap(
                            songId = mediaId,
                            relatedSongId = it.id
                        )
                    }
                    .forEach(::insert)
            }
        }
    }

    fun playQueue(
        queue: Queue,
        playWhenReady: Boolean = true,
    ) {
        currentQueue = queue
        queueTitle = null
        player.shuffleModeEnabled = false
        queue.preloadItem?.let { preloadItem ->
            player.setMediaItem(preloadItem.toMediaItem())
            player.prepare()
            player.playWhenReady = playWhenReady
        }
        scope.launch(SilentHandler) {
            val initialStatus =
                withContext(Dispatchers.IO) {
                    queue.getInitialStatus().filterExplicit(dataStore.get(HideExplicitKey, false))
                }
            if (queue.preloadItem != null && player.playbackState == STATE_IDLE) return@launch
            if (initialStatus.title != null) {
                queueTitle = initialStatus.title
            }
            if (initialStatus.items.isEmpty()) return@launch
            if (queue.preloadItem != null) {
                player.addMediaItems(
                    0,
                    initialStatus.items.subList(0, initialStatus.mediaItemIndex)
                )
                player.addMediaItems(
                    initialStatus.items.subList(
                        initialStatus.mediaItemIndex + 1,
                        initialStatus.items.size
                    )
                )
            } else {
                player.setMediaItems(
                    initialStatus.items,
                    if (initialStatus.mediaItemIndex >
                        0
                    ) {
                        initialStatus.mediaItemIndex
                    } else {
                        0
                    },
                    initialStatus.position,
                )
                player.prepare()
                player.playWhenReady = playWhenReady
            }
        }
    }

    fun startRadioSeamlessly() {
        val currentMediaMetadata = player.currentMetadata ?: return

        // Save current song
        val currentSong = player.currentMediaItem

        // Remove other songs from queue
        if (player.currentMediaItemIndex > 0) {
            player.removeMediaItems(0, player.currentMediaItemIndex)
        }
        if (player.currentMediaItemIndex < player.mediaItemCount - 1) {
            player.removeMediaItems(player.currentMediaItemIndex + 1, player.mediaItemCount)
        }

        scope.launch(SilentHandler) {
            val radioQueue = YouTubeQueue(
                endpoint = WatchEndpoint(videoId = currentMediaMetadata.id),
                preloadItem = null,
                database = database
            )
            val initialStatus = radioQueue.getInitialStatus()

            if (initialStatus.title != null) {
                queueTitle = initialStatus.title
            }

            // Add radio songs after current song
            player.addMediaItems(initialStatus.items.drop(1))
            currentQueue = radioQueue
        }
    }

    fun getAutomixAlbum(albumId: String) {
        scope.launch(SilentHandler) {
            YouTube
                .album(albumId)
                .onSuccess {
                    getAutomix(it.album.playlistId)
                }
        }
    }

    fun getAutomix(playlistId: String) {
        // Automix/similar content feature disabled
    }

    fun addToQueueAutomix(
        item: MediaItem,
        position: Int,
    ) {
        automixItems.value =
            automixItems.value.toMutableList().apply {
                removeAt(position)
            }
        addToQueue(listOf(item))
    }

    fun playNextAutomix(
        item: MediaItem,
        position: Int,
    ) {
        automixItems.value =
            automixItems.value.toMutableList().apply {
                removeAt(position)
            }
        playNext(listOf(item))
    }

    fun clearAutomix() {
        automixItems.value = emptyList()
    }

    fun playNext(items: List<MediaItem>) {
        // If queue is empty or player is idle, play immediately instead
        if (player.mediaItemCount == 0 || player.playbackState == STATE_IDLE) {
            player.setMediaItems(items)
            player.prepare()
            player.play()
            return
        }

        val insertIndex = player.currentMediaItemIndex + 1
        val shuffleEnabled = player.shuffleModeEnabled

        // Insert items immediately after the current item in the window/index space
        player.addMediaItems(insertIndex, items)
        player.prepare()

        if (shuffleEnabled) {
            // Rebuild shuffle order so that newly inserted items are played next
            val timeline = player.currentTimeline
            if (!timeline.isEmpty) {
                val size = timeline.windowCount
                val currentIndex = player.currentMediaItemIndex

                // Newly inserted indices are a contiguous range [insertIndex, insertIndex + items.size)
                val newIndices = (insertIndex until (insertIndex + items.size)).toSet()

                // Collect existing shuffle traversal order excluding current index
                val orderAfter = mutableListOf<Int>()
                var idx = currentIndex
                while (true) {
                    idx = timeline.getNextWindowIndex(idx, Player.REPEAT_MODE_OFF, /*shuffleModeEnabled=*/true)
                    if (idx == C.INDEX_UNSET) break
                    if (idx != currentIndex) orderAfter.add(idx)
                }

                val prevList = mutableListOf<Int>()
                var pIdx = currentIndex
                while (true) {
                    pIdx = timeline.getPreviousWindowIndex(pIdx, Player.REPEAT_MODE_OFF, /*shuffleModeEnabled=*/true)
                    if (pIdx == C.INDEX_UNSET) break
                    if (pIdx != currentIndex) prevList.add(pIdx)
                }
                prevList.reverse() // preserve original forward order

                val existingOrder = (prevList + orderAfter).filter { it != currentIndex && it !in newIndices }

                // Build new shuffle order: current -> newly inserted (in insertion order) -> rest
                val nextBlock = (insertIndex until (insertIndex + items.size)).toList()
                val finalOrder = IntArray(size)
                var pos = 0
                finalOrder[pos++] = currentIndex
                nextBlock.forEach { if (it in 0 until size) finalOrder[pos++] = it }
                existingOrder.forEach { if (pos < size) finalOrder[pos++] = it }

                // Fill any missing indices (safety) to ensure a full permutation
                if (pos < size) {
                    for (i in 0 until size) {
                        if (!finalOrder.contains(i)) {
                            finalOrder[pos++] = i
                            if (pos == size) break
                        }
                    }
                }

                player.setShuffleOrder(DefaultShuffleOrder(finalOrder, System.currentTimeMillis()))
            }
        }
    }

    fun addToQueue(items: List<MediaItem>) {
        player.addMediaItems(items)
        player.prepare()
    }

    private fun toggleLibrary() {
        database.query {
            currentSong.value?.let {
                update(it.song.toggleLibrary())
            }
        }
    }

    fun toggleLike() {
        database.query {
            currentSong.value?.let {
                val song = it.song.toggleLike()
                update(song)
                syncUtils.likeSong(song)

                // Check if auto-download on like is enabled and the song is now liked
                if (dataStore.get(AutoDownloadOnLikeKey, false) && song.liked) {
                    // Trigger download for the liked song (use video download if isVideo)
                    if (it.song.isVideo) {
                        downloadUtil.downloadVideoToMediaStore(it)
                    } else {
                        downloadUtil.downloadToMediaStore(it)
                    }
                }
            }
        }
    }

    fun toggleStartRadio() {
        startRadioSeamlessly()
    }

    fun addToTargetPlaylist() {
        scope.launch {
            val current = currentSong.value ?: return@launch
            val targetPlaylistId = dataStore.get(
                AndroidAutoTargetPlaylistKey,
                MediaSessionConstants.TARGET_PLAYLIST_AUTO,
            )

            if (targetPlaylistId == MediaSessionConstants.TARGET_PLAYLIST_AUTO) {
                Toast.makeText(
                    this@MusicService,
                    getString(R.string.android_auto_target_playlist_not_set),
                    Toast.LENGTH_SHORT,
                ).show()
                return@launch
            }

            val targetPlaylist = withContext(Dispatchers.IO) {
                database.playlist(targetPlaylistId).first()
            } ?: return@launch

            database.query {
                addSongToPlaylist(targetPlaylist, listOf(current.id))
            }
        }
    }

    private fun setupLoudnessEnhancer() {
        val audioSessionId = player.audioSessionId

        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) {
            return
        }

        // Create or recreate enhancer if needed
        if (loudnessEnhancer == null) {
            try {
                loudnessEnhancer = LoudnessEnhancer(audioSessionId)
            } catch (e: Exception) {
                reportException(e)
                loudnessEnhancer = null
                return
            }
        }

        scope.launch {
            try {
                val currentMediaId = withContext(Dispatchers.Main) {
                    player.currentMediaItem?.mediaId
                }

                val normalizeAudio = withContext(Dispatchers.IO) {
                    dataStore.data.map { it[AudioNormalizationKey] ?: true }.first()
                }

                if (normalizeAudio && currentMediaId != null) {
                    val format = withContext(Dispatchers.IO) {
                        database.format(currentMediaId).first()
                    }

                    val loudnessDb = format?.loudnessDb

                    withContext(Dispatchers.Main) {
                        if (loudnessDb != null) {
                            val targetGain = (-loudnessDb * 100).toInt()
                            val clampedGain = targetGain.coerceIn(MIN_GAIN_MB, MAX_GAIN_MB)
                            try {
                                loudnessEnhancer?.setTargetGain(clampedGain)
                                loudnessEnhancer?.enabled = true
                            } catch (e: Exception) {
                                reportException(e)
                                releaseLoudnessEnhancer()
                            }
                        } else {
                            loudnessEnhancer?.enabled = false
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        loudnessEnhancer?.enabled = false
                    }
                }
            } catch (e: Exception) {
                reportException(e)
                releaseLoudnessEnhancer()
            }
        }
    }


    private fun releaseLoudnessEnhancer() {
        try {
            loudnessEnhancer?.release()
        } catch (e: Exception) {
            reportException(e)
        } finally {
            loudnessEnhancer = null
        }
    }

    private fun openAudioEffectSession() {
        if (isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = true
        setupLoudnessEnhancer()
        sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            },
        )
    }

    private fun closeAudioEffectSession() {
        if (!isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = false
        releaseLoudnessEnhancer()
        sendBroadcast(
            Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            },
        )
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        lastPlaybackSpeed = -1.0f // force update song

        setupLoudnessEnhancer()
        updateWidget()

        // Auto load more songs
        if (dataStore.get(AutoLoadMoreKey, true) &&
            reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
            player.mediaItemCount - player.currentMediaItemIndex <= 5 &&
            currentQueue.hasNextPage() &&
            !(dataStore.get(DisableLoadMoreWhenRepeatAllKey, false) && player.repeatMode == REPEAT_MODE_ALL)
        ) {
            scope.launch(SilentHandler) {
                val mediaItems =
                    currentQueue.nextPage().filterExplicit(dataStore.get(HideExplicitKey, false))
                if (player.playbackState != STATE_IDLE) {
                    player.addMediaItems(mediaItems.drop(1))
                }
            }
        }

        // Save state when media item changes
        if (dataStore.get(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }
    }

    override fun onPlaybackStateChanged(
        @Player.State playbackState: Int,
    ) {
        // Save state when playback state changes
        if (dataStore.get(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }
        updateWidget()
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (playWhenReady) {
            setupLoudnessEnhancer()
        }
        updateWidget()
    }

    override fun onEvents(
        player: Player,
        events: Player.Events,
    ) {
        if (events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED
            )
        ) {
            val isBufferingOrReady =
                player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY
            if (isBufferingOrReady && player.playWhenReady) {
                val focusGranted = requestAudioFocus()
                if (focusGranted) {
                    openAudioEffectSession()
                }
            } else {
                closeAudioEffectSession()
            }
        }
        if (
            events.containsAny(
                EVENT_MEDIA_METADATA_CHANGED,
                EVENT_MEDIA_ITEM_TRANSITION,
                EVENT_TIMELINE_CHANGED,
                EVENT_POSITION_DISCONTINUITY
            )
        ) {
            currentMediaMetadata.value = player.currentMetadata
        }
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        updateNotification()
        if (shuffleModeEnabled) {
            // If queue is empty, don't shuffle
            if (player.mediaItemCount == 0) return

            val shuffledIndices = IntArray(player.mediaItemCount) { it }
            shuffledIndices.shuffle()
            shuffledIndices[shuffledIndices.indexOf(player.currentMediaItemIndex)] =
                shuffledIndices[0]
            shuffledIndices[0] = player.currentMediaItemIndex
            player.setShuffleOrder(DefaultShuffleOrder(shuffledIndices, System.currentTimeMillis()))
        }

        // Save state when shuffle mode changes
        if (dataStore.get(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        updateNotification()
        scope.launch {
            dataStore.edit { settings ->
                settings[RepeatModeKey] = repeatMode
            }
        }

        // Save state when repeat mode changes
        if (dataStore.get(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        super.onPlaybackParametersChanged(playbackParameters)
        lastPlaybackSpeed = playbackParameters.speed
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
        Timber.w(error, "Player error occurred: ${error.message}")

        // Check for expired URL (403 error) - needs immediate URL refresh
        if (isExpiredUrlError(error)) {
            Timber.d("Expired URL detected (403), refreshing stream URL")
            handleExpiredUrlError()
            return
        }

        val isConnectionError = (error.cause?.cause is PlaybackException) &&
                (error.cause?.cause as PlaybackException).errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED

        // Don't treat 403 as network error - it needs URL refresh, not network wait
        if (!isNetworkConnected.value || isConnectionError) {
            waitOnNetworkError()
            return
        }

        if (dataStore.get(AutoSkipNextOnErrorKey, false)) {
            skipOnError()
        } else {
            stopOnError()
        }
    }

    /**
     * Extracts the HTTP response code from an error's cause chain.
     * Returns null if no HTTP response code is found.
     */
    private fun getHttpResponseCode(error: PlaybackException): Int? {
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                return cause.responseCode
            }
            cause = cause.cause
        }
        return null
    }

    /**
     * Checks if the error is caused by an expired/invalid URL.
     * HTTP 403 (Forbidden) and 410 (Gone) typically indicate expired YouTube stream URLs.
     */
    private fun isExpiredUrlError(error: PlaybackException): Boolean {
        val code = getHttpResponseCode(error)
        return code == 403 || code == 410
    }

    /**
     * Handles expired URL errors by clearing the cached URL and immediately retrying.
     */
    private fun handleExpiredUrlError() {
        val mediaId = player.currentMediaItem?.mediaId
        if (mediaId != null) {
            // If this was a WEB_REMIX stream that 403d on GET, mark it so the next
            // resolution skips WEB_REMIX and falls through to TVHTML5/ANDROID_VR.
            YTPlayerUtils.markWebRemixFailed(mediaId)
            // Clear the cached URL so it will be refreshed on next request
            DownloadUtil.invalidateUrl(mediaId)
            Timber.d("Cleared cached URL for $mediaId, marked WEB_REMIX as failed")
            // A 403 can also mean the cipher produced a wrong-but-non-throwing signature from a
            // stale/wrong player config. Ask the cipher to re-fetch its config (rate-limited); if
            // that corrects the table, the cipher rebuilds its WebView on the next decipher, so we
            // clear the WEB_REMIX failure set to let playback return to WEB_REMIX — no app restart.
            scope.launch {
                if (CipherDeobfuscator.onStreamRejected()) {
                    Timber.d("Player config changed after stream rejection — restoring WEB_REMIX")
                    YTPlayerUtils.clearWebRemixFailures()
                }
            }
        }

        // Seek to current position to force URL re-resolution
        val currentPosition = player.currentPosition
        player.seekTo(player.currentMediaItemIndex, currentPosition)
        player.prepare()
        // Let playWhenReady handle playback resume
    }

    private fun createCacheDataSource(): CacheDataSource.Factory =
        CacheDataSource
            .Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(
                CacheDataSource
                    .Factory()
                    .setCache(playerCache)
                    .setUpstreamDataSourceFactory(
                        DefaultDataSource.Factory(
                            this,
                            OkHttpDataSource.Factory(
                            OkHttpClient
                                .Builder()
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
                    ),
            ).setCacheWriteDataSinkFactory(null)
            .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)

    /** Per-media-item source decision, made at the position-0 open that starts playback: true = playing
     *  the local downloaded file, false/absent = streaming. Later opens (seeks, cache-span re-opens)
     *  honor this so a song that started streaming never switches to the local file mid-playback when its
     *  download finishes (the "source switching during download" bug, commit 1f48d89), while a song that
     *  was already downloaded when playback began uses the local file at every position so seeks work.
     *
     *  KNOWN LIMITATION (accepted, not a TODO bandaid to "fix in place"): this is a per-byte
     *  source decision inside a streaming `ResolvingDataSource`, so it cannot reconcile the fact that a
     *  MediaStore download is a DIFFERENT container (m4a/itag140) than the streamed audio (webm/opus).
     *  The one path it does NOT make perfect: if you DOWNLOAD a song WHILE actively listening to that
     *  same song, that playing instance stays on the stream (it won't switch to the local file until the
     *  song is re-selected), so offline it can only play as far as the stream cached. It does not crash;
     *  re-tapping the song plays it from the local file. Every other path (download then play later,
     *  seek a downloaded song, offline playback of a downloaded song, restart/resume) works.
     *  The complete fix is architectural — route a downloaded song as a LOCAL `MediaItem` (content://
     *  URI) at queue-build time so it never enters the stream pipeline (Media3-standard), instead of
     *  guessing per read here. Deliberately deferred; do not "fix" this map/probe further — replace it. */
    private val playbackSourceIsLocal = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /** Whether the downloaded file at [uriString] actually opens. Returns false on ANY failure to open
     *  (ENOENT / null descriptor / FileNotFound / a SecurityException or other resolver error) so that
     *  playback falls back to STREAMING rather than handing ExoPlayer a URI we just failed to open
     *  (which would only fail again). Worst case for a present-but-momentarily-unreadable file is one
     *  streamed play + a self-repair re-download — never a hard playback failure. We never delete the
     *  download here — the flag is the user's, not ours to silently drop. */
    private fun downloadedFileOpens(uriString: String): Boolean =
        try {
            contentResolver.openFileDescriptor(uriString.toUri(), "r")?.use { true }
                ?: run {
                    Timber.w("Downloaded file probe returned null descriptor for uri=$uriString; will stream")
                    false
                }
        } catch (e: java.io.FileNotFoundException) {
            Timber.w("Downloaded file MISSING (FileNotFound) for uri=$uriString; will stream")
            false
        } catch (e: Exception) {
            Timber.w(e, "Could not open downloaded file $uriString; streaming instead of handing over a dead URI")
            false
        }

    private fun createDataSourceFactory(): DataSource.Factory {
        return ResolvingDataSource.Factory(createCacheDataSource()) { dataSpec ->
            val mediaId = dataSpec.key ?: error("No media id")

            // Check for MediaStore URI first (local playback).
            // Use a blocking call here because ResolvingDataSource.Factory requires synchronous code.
            val song = runBlocking(Dispatchers.IO) {
                database.song(mediaId).first()
            }

            // Source selection for a downloaded song. We must satisfy BOTH:
            //  (a) seeking a downloaded song must use its LOCAL file (it's random-access; a googlevideo
            //      range request from a byte offset has no moov/init atom and the mp4 extractor dies —
            //      "Skipping atom with length > … unsupported" → Source error), AND
            //  (b) a song that STARTED streaming (not yet downloaded) must NOT switch to the local file
            //      mid-playback when its download finishes — that mid-stream source switch is the bug
            //      commit 1f48d89 fixed with a blanket `position == 0L` guard (which broke (a)).
            // So we decide the source ONCE, at the position-0 open that begins playback of this item,
            // remember it, and honor that decision for every later open (seek or cache-span re-open).
            val mediaStoreUri = song?.song?.mediaStoreUri
            if (mediaStoreUri != null) {
                val fileOpens = downloadedFileOpens(mediaStoreUri)
                if (dataSpec.position == 0L) {
                    // Start of playback for this item: pick the source and record it.
                    playbackSourceIsLocal[mediaId] = fileOpens
                    if (fileOpens) {
                        scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                        return@Factory dataSpec.withUri(mediaStoreUri.toUri())
                    }
                    // The file is gone (a stale "downloaded" row — interrupted download or an older
                    // build that deleted the file). Self-repair: re-download so it's local next time,
                    // and stream THIS play instead of crashing with ENOENT. We don't clear the flag
                    // (no vanishing); the re-download replaces the dead URI on success. Skip re-enqueue
                    // if a download for this id already FAILED this session (the manager only no-ops
                    // active/complete, not FAILED), else a permanently-unrecoverable source would
                    // re-download on every play; a new session (state cleared) gets one fresh attempt.
                    val liveStatus = downloadUtil.mediaStoreDownloadState(mediaId)?.status
                    if (liveStatus == MediaStoreDownloadManager.DownloadState.Status.FAILED) {
                        Timber.w("Downloaded file missing for $mediaId but its re-download already FAILED this session; streaming without re-enqueueing")
                    } else {
                        Timber.w("Downloaded file missing for $mediaId; re-downloading to self-repair and streaming this play")
                        song?.let { stale ->
                            scope.launch(Dispatchers.IO) {
                                runCatching {
                                    if (stale.song.isVideo) downloadUtil.downloadVideoToMediaStore(stale)
                                    else downloadUtil.downloadToMediaStore(stale)
                                }
                            }
                        }
                    }
                } else if (fileOpens && playbackSourceIsLocal[mediaId] == true) {
                    // A later open (a SEEK, or a cache-span re-open) for an item that STARTED on the
                    // local file → keep using the local file so seeks work. An item that started
                    // streaming (playbackSourceIsLocal == false/absent) deliberately falls through to
                    // the cache/stream path below — no mid-stream switch when its download completes.
                    scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                    return@Factory dataSpec.withUri(mediaStoreUri.toUri())
                }
            }

            if (downloadCache.isCached(
                    mediaId,
                    dataSpec.position,
                    if (dataSpec.length >= 0) dataSpec.length else 1
                ) ||
                playerCache.isCached(mediaId, dataSpec.position, CHUNK_LENGTH)
            ) {
                scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                return@Factory dataSpec
            }

            songUrlCache[mediaId]?.takeIf { it.second > System.currentTimeMillis() }?.let {
                scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                return@Factory dataSpec.withUri(it.first.toUri())
            }

            // Validate current authentication state before fetching stream
            val currentAuthCookie = YouTube.cookie
            val isLoggedIn = currentAuthCookie != null && "SAPISID" in parseCookieString(currentAuthCookie)

            val playbackData = runBlocking(Dispatchers.IO) {
                YTPlayerUtils.playerResponseForPlayback(
                    mediaId,
                    audioQuality = audioQuality,
                    connectivityManager = connectivityManager,
                )
            }.getOrElse { throwable ->
                when (throwable) {
                    is PlaybackException -> throw throwable

                    is java.net.ConnectException, is java.net.UnknownHostException -> {
                        throw PlaybackException(
                            getString(R.string.error_no_internet),
                            throwable,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                        )
                    }

                    is java.net.SocketTimeoutException -> {
                        throw PlaybackException(
                            getString(R.string.error_timeout),
                            throwable,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                        )
                    }

                    else -> throw PlaybackException(
                        getString(R.string.error_unknown),
                        throwable,
                        PlaybackException.ERROR_CODE_REMOTE_ERROR
                    )
                }
            }

            val nonNullPlayback = requireNotNull(playbackData) {
                getString(R.string.error_unknown)
            }
            run {
                val format = nonNullPlayback.format

                val contentLength = format.contentLength ?: -1L
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
                            loudnessDb = nonNullPlayback.audioConfig?.loudnessDb,
                            playbackUrl = nonNullPlayback.playbackTracking?.videostatsPlaybackUrl?.baseUrl,
                            streamClient = nonNullPlayback.streamClient,
                        )
                    )
                }
                scope.launch(Dispatchers.IO) { recoverSong(mediaId, nonNullPlayback) }

                val streamUrl = nonNullPlayback.streamUrl

                songUrlCache[mediaId] =
                    streamUrl to System.currentTimeMillis() + (nonNullPlayback.streamExpiresInSeconds * 1000L)
                return@Factory dataSpec.withUri(streamUrl.toUri()).subrange(dataSpec.uriPositionOffset, CHUNK_LENGTH)
            }
        }
    }

    private val dataSourceFactory: DataSource.Factory by lazy {
        createDataSourceFactory()
    }

    private fun createMediaSourceFactory() =
        DefaultMediaSourceFactory(
            dataSourceFactory,
            ExtractorsFactory {
                arrayOf(MatroskaExtractor(), FragmentedMp4Extractor())
            },
        )

    private fun createRenderersFactory() =
        object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ) = DefaultAudioSink
                .Builder(this@MusicService)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessorChain(
                    DefaultAudioSink.DefaultAudioProcessorChain(
                        emptyArray(),
                        SilenceSkippingAudioProcessor(2_000_000, 20_000, 256),
                        SonicAudioProcessor(),
                    ),
                ).build()
        }

    override fun onPlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats,
    ) {
        val mediaItem = eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem

        if (playbackStats.totalPlayTimeMs >= (
                    dataStore[HistoryDuration]?.times(1000f)
                        ?: 10000f
                    ) &&
            !dataStore.get(PauseListenHistoryKey, false)
        ) {
            database.query {
                incrementTotalPlayTime(mediaItem.mediaId, playbackStats.totalPlayTimeMs)
                try {
                    insert(
                        Event(
                            songId = mediaItem.mediaId,
                            timestamp = LocalDateTime.now(),
                            playTime = playbackStats.totalPlayTimeMs,
                        ),
                    )
                } catch (_: SQLException) {
                }
            }

            CoroutineScope(Dispatchers.IO).launch {
                val playbackUrl = YTPlayerUtils.playerResponseForMetadata(mediaItem.mediaId, null)
                    .getOrNull()?.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                playbackUrl?.let {
                    YouTube.registerPlayback(null, playbackUrl)
                        .onFailure {
                            reportException(it)
                        }
                }
            }
        }
    }

    private fun saveQueueToDisk() {
        if (player.mediaItemCount == 0) {
            return
        }

        // Save current queue with proper type information
        val persistQueue = currentQueue.toPersistQueue(
            title = queueTitle,
            items = player.mediaItems.mapNotNull { it.metadata },
            mediaItemIndex = player.currentMediaItemIndex,
            position = player.currentPosition
        )

        val persistAutomix =
            PersistQueue(
                title = "automix",
                items = automixItems.value.mapNotNull { it.metadata },
                mediaItemIndex = 0,
                position = 0,
            )

        // Save player state
        val persistPlayerState = PersistPlayerState(
            playWhenReady = player.playWhenReady,
            repeatMode = player.repeatMode,
            shuffleModeEnabled = player.shuffleModeEnabled,
            volume = player.volume,
            currentPosition = player.currentPosition,
            currentMediaItemIndex = player.currentMediaItemIndex,
            playbackState = player.playbackState
        )

        runCatching {
            filesDir.resolve(PERSISTENT_QUEUE_FILE).outputStream().use { fos ->
                ObjectOutputStream(fos).use { oos ->
                    oos.writeObject(persistQueue)
                }
            }
        }.onFailure {
            reportException(it)
        }
        runCatching {
            filesDir.resolve(PERSISTENT_AUTOMIX_FILE).outputStream().use { fos ->
                ObjectOutputStream(fos).use { oos ->
                    oos.writeObject(persistAutomix)
                }
            }
        }.onFailure {
            reportException(it)
        }
        runCatching {
            filesDir.resolve(PERSISTENT_PLAYER_STATE_FILE).outputStream().use { fos ->
                ObjectOutputStream(fos).use { oos ->
                    oos.writeObject(persistPlayerState)
                }
            }
        }.onFailure {
            reportException(it)
        }
    }

    override fun onDestroy() {
        if (dataStore.get(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }
        connectivityObserver.unregister()
        abandonAudioFocus()
        releaseLoudnessEnhancer()
        // Stop the widget ticker before releasing the player so a stray tick can't touch it.
        widgetTickerJob?.cancel()
        mediaSession.release()
        player.removeListener(this)
        player.removeListener(sleepTimer)
        player.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        isRunning = false
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            MusicWidget.ACTION_PLAY_PAUSE -> {
                if (player.isPlaying) player.pause() else player.play()
            }
            MusicWidget.ACTION_NEXT -> player.seekToNext()
            MusicWidget.ACTION_PREV -> player.seekToPrevious()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?) = super.onBind(intent) ?: binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    inner class MusicBinder : Binder() {
        val service: MusicService
            get() = this@MusicService
    }

    companion object {
        const val ROOT = "root"
        const val SONG = "song"
        const val ARTIST = "artist"
        const val ALBUM = "album"
        const val PLAYLIST = "playlist"
        const val YOUTUBE_PLAYLIST = "youtube_playlist"
        const val SEARCH = "search"
        const val SHUFFLE_ACTION = "__shuffle__"

        const val CHANNEL_ID = "music_channel_01"
        const val NOTIFICATION_ID = 888
        const val ERROR_CODE_NO_STREAM = 1000001
        const val CHUNK_LENGTH = 512 * 1024L
        const val PERSISTENT_QUEUE_FILE = "persistent_queue.data"
        const val PERSISTENT_AUTOMIX_FILE = "persistent_automix.data"
        const val PERSISTENT_PLAYER_STATE_FILE = "persistent_player_state.data"
        const val MAX_CONSECUTIVE_ERR = 5
        // Constants for audio normalization
        private const val MAX_GAIN_MB = 800 // Maximum gain in millibels (8 dB)
        private const val MIN_GAIN_MB = -800 // Minimum gain in millibels (-8 dB)

        private const val TAG = "MusicService"

        @Volatile
        var isRunning: Boolean = false
    }
}
