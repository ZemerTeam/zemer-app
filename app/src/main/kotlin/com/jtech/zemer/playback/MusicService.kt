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
import androidx.media3.extractor.mp4.Mp4Extractor
import timber.log.Timber
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.session.SessionToken
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.utils.ResilientDns
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.jtech.zemer.MainActivity
import com.jtech.zemer.R
import com.jtech.zemer.constants.AndroidAutoTargetPlaylistKey
import com.jtech.zemer.constants.AudioNormalizationKey
import com.jtech.zemer.constants.PlaybackMode
import com.jtech.zemer.constants.PlaybackModeKey
import com.jtech.zemer.playback.relay.RelayDataSourceFactory
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
import com.jtech.zemer.constants.StopMusicOnTaskClearKey
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
import com.jtech.zemer.extensions.toast
import com.jtech.zemer.lyrics.LyricsHelper
import com.jtech.zemer.models.PersistPlayerState
import com.jtech.zemer.models.PersistQueue
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.playback.queues.EmptyQueue
import com.jtech.zemer.playback.queues.Queue
import com.jtech.zemer.playback.queues.StationQueue
import com.jtech.zemer.search.STATION_MAX_DRIFT_MS
import com.jtech.zemer.playback.queues.YouTubeQueue
import com.jtech.zemer.playback.queues.ZemerRadioQueue
import com.jtech.zemer.playback.queues.continuationItemsToAppend
import com.jtech.zemer.playback.queues.filterExplicit
import com.jtech.zemer.tracking.Tracker
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
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.flow.drop
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
import org.fcast.sender_sdk.*

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

    private var deviceDiscoverer: NsdDeviceDiscoverer? = null
    val discoveryHandler = FCastDiscoveryHandler()

    // The FCast native lib is downloaded on demand (not bundled). Lazy so it is built after the service's
    // base context is attached; its init applies the libraryOverride if a verified copy is already cached.
    val castLibLoader by lazy { CastNativeLibLoader(this) }

    // The cast control plane (auto-advance detectors + receiver reload + disconnect recovery), owned by
    // the (process-scoped) service so casting keeps advancing through its queue even after the UI Activity
    // is destroyed. Lazily built on first cast use; its init wires the detectors and the onDisconnect hook.
    val castController by lazy { CastController(this, scope) }

    // Orchestration for a user-initiated connect from the picker: stream resolve + click-time NSD
    // address re-resolve + awaiting the receiver's Connected/Disconnected outcome (see CastConnector).
    val castConnector by lazy { CastConnector(this) }

    // On-demand rebuild of the picker's device list (discovery burst + re-resolve + prune) — the SDK's
    // own discoverer never re-checks a device once found (see CastDeviceRefresher).
    val castDeviceRefresher by lazy { CastDeviceRefresher(this, discoveryHandler) }

    // The audio↔video rendition swap for the current item (the in-player Song/Video toggle). Service-
    // scoped like the cast control plane so its cast/block auto-revert works even with no UI bound.
    val videoModeController by lazy { VideoModeController(this, scope) }

    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var lastAudioFocusState = AudioManager.AUDIOFOCUS_NONE
    private var wasPlayingBeforeAudioFocusLoss = false
    private var hasAudioFocus = false

    // Service-lifetime scope. Also used by the cast picker to launch connects: an Activity-bound scope
    // would be cancelled by onStop mid-connect, stranding the picker's spinner and skipping the
    // timeout abort (CastConnector's TIMED_OUT handler.disconnect()).
    val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var startRadioJob: Job? = null
    private val binder = MusicBinder()

    private lateinit var connectivityManager: ConnectivityManager
    lateinit var connectivityObserver: NetworkConnectivityObserver
    val waitingForNetworkConnection = MutableStateFlow(false)
    // Non-private so VideoModeController (same package) can gate the streaming Song/Video toggle on it
    // (a SELF/COUNTERPART rendition streams — never offer it offline; a downloaded muxed LOCAL file is
    // the only offline video path). Updated by the connectivityObserver collector above.
    val isNetworkConnected = MutableStateFlow(false)

    private val audioQualityFlow = enumPreferenceFlow(
        this,
        AudioQualityKey,
        com.jtech.zemer.constants.AudioQuality.AUTO
    )
    private var audioQuality = com.jtech.zemer.constants.AudioQuality.AUTO

    private var currentQueue: Queue = EmptyQueue
        set(value) {
            field = value
            // Broadcast semantics ride on the queue TYPE: the session player mask strips the
            // skip/seek commands for every controller surface (notification, Auto, Bluetooth), and
            // the flag drives the in-app transport gating via PlayerConnection.
            val station = value is StationQueue
            isStationBroadcast.value = station
            if (::sessionPlayer.isInitialized && sessionPlayer.maskTransportForStation != station) {
                sessionPlayer.maskTransportForStation = station
                // Push the changed command set to every connected controller - media3 caches them.
                sessionPlayer.notifyStationMaskChanged()
            }
        }

    /** True while a Zemer Station broadcast is the active queue (see [StationQueue]). */
    val isStationBroadcast = MutableStateFlow(false)
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
    private lateinit var sessionPlayer: CastAwarePlayer

    private var isAudioEffectSessionOpened = false
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private var lastPlaybackSpeed = 1.0f

    val automixItems = MutableStateFlow<List<MediaItem>>(emptyList())

    // MIME of the resolved stream per mediaId, populated by resolveStreamUrl — the cast receiver needs
    // the real container (webm/opus vs mp4), not the local decoder's (often-null) output format.
    private val songMimeCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun streamContentType(mediaId: String): String = songMimeCache[mediaId] ?: "audio/mp4"

    /**
     * Stage 2 of the cast-403 fix: the receiver fetches googlevideo *through the phone*, so the
     * fetching network identity equals the minting one by construction (googlevideo binds stream URLs
     * to the minter's address and 403s other identities past the first free MiB — receivers behind
     * CGNAT IPv4 or on a different v6 prefix can never fetch our URLs directly). The resolver runs on
     * relay worker threads, never the main thread, so blocking on the resolve is fine.
     */
    val castStreamRelay = CastStreamRelay { mediaId, forceRefresh ->
        if (forceRefresh) invalidateStreamCache(mediaId)
        runBlocking { resolveStreamUrl(mediaId) }?.let { RelayUpstream(it, streamContentType(mediaId)) }
    }

    // lazy: a Service has no Context until attachBaseContext, and stopping the relay on a session that
    // never acquired must not eagerly construct the system-service locks just to no-op release them.
    private val castSessionLocks by lazy { CastSessionLocks(this) }

    /**
     * The URL the cast receiver should be handed for [mediaId]: the relay URL when the relay can
     * serve, else [rawUrl] (direct googlevideo — Stage 1's error-recovery ladder still backs that up).
     * Runs the relay's socket bind + route probe on IO.
     */
    suspend fun relayedStreamUrl(mediaId: String, rawUrl: String): String = withContext(Dispatchers.IO) {
        val relayed = runCatching { castStreamRelay.urlFor(mediaId) }
            .onFailure { reportException(it, "Cast relay URL") }
            .getOrNull()
        if (relayed != null) {
            castSessionLocks.acquire()
            relayed
        } else {
            Timber.tag("CastRelay").w("Relay unavailable — handing the receiver the direct URL for %s", mediaId)
            rawUrl
        }
    }

    /** Tears down the relay + its Wi-Fi/CPU locks; called when a cast session is truly over. */
    fun stopCastRelay() {
        castStreamRelay.stop()
        castSessionLocks.release()
    }

    /** Cast-lib state for the picker UI (downloading / failed / ready); the native lib isn't bundled. */
    val castLibState get() = castLibLoader.state

    /**
     * Start NSD discovery if the FCast native lib is already present — never downloads it (that needs
     * explicit consent via [downloadCastLib]; the lib is ~5 MB and not bundled). No-op until ready, and
     * idempotent: sender-sdk 0.4.0's NsdDeviceDiscoverer has no stop API, so discovery then runs until
     * the process dies.
     */
    fun startDiscovery() {
        if (deviceDiscoverer == null && castLibLoader.isReady) {
            deviceDiscoverer = NsdDeviceDiscoverer(this, discoveryHandler)
        }
    }

    /**
     * User-consented one-time download of the FCast native lib (not bundled, to save ~5 MB). Download
     * only — discovery is started separately by [startDiscovery] when the picker is open and ready, so
     * consenting from Settings doesn't kick off background NSD discovery. Progress/failure via
     * [castLibState]; safe to call repeatedly (no-op when ready or already downloading).
     */
    fun downloadCastLib() {
        if (castLibLoader.isReady || castLibState.value is CastLibState.Downloading) return
        scope.launch { withContext(Dispatchers.IO) { castLibLoader.ensure() } }
    }

    val currentStreamUrl: String?
        get() = player.currentMediaItem?.mediaId?.let { id ->
            songUrlCache[id]?.takeIf { it.second > System.currentTimeMillis() }?.first
        }

    // The cast receiver needs the real container MIME (populated by resolveStreamUrl). Do NOT fall back
    // to player.audioFormat.sampleMimeType — that is the decoder's codec MIME (e.g. audio/mp4a-latm,
    // audio/opus), the wrong granularity for a container, which makes the receiver reject the stream.
    // Delegates to streamContentType so the cache lookup + default container MIME live in one place.
    val currentContentType: String?
        get() = player.currentMediaItem?.mediaId?.let { streamContentType(it) }

    private var consecutivePlaybackErr = 0

    // Use shared URL cache from DownloadUtil for consistency between playback and downloads
    private val songUrlCache get() = DownloadUtil.sharedUrlCache

    override fun onCreate() {
        super.onCreate()
        // Cast discovery is started lazily by startDiscovery() the first time the user opens the cast
        // picker — not here — so we don't run NSD discovery on every launch.
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
                    sleepTimer = SleepTimer(scope, this@MusicService)
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
        sessionPlayer = CastAwarePlayer(player, discoveryHandler, scope)
        mediaSession =
            MediaLibrarySession
                .Builder(this, sessionPlayer, mediaLibrarySessionCallback)
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

        // Mirror the RELAY playback-mode flag into a synchronous field read by the data-source dispatcher.
        // DIRECT (every normal user) is the seed and the default, so the relay path is inert until a user
        // explicitly opts in; this collector only ever flips the one boolean.
        scope.launch {
            enumPreferenceFlow(this@MusicService, PlaybackModeKey, PlaybackMode.DIRECT).collect {
                relayModeNow = it == PlaybackMode.RELAY
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

        // The widget otherwise repaints only from local-player callbacks, which are silent while casting
        // (the local player stays paused): repaint on remote connect/play/pause edges so its icon always
        // matches what a tap will do, and run the seek-bar ticker off the remote clock while it plays.
        combine(
            discoveryHandler.remoteConnectionState,
            discoveryHandler.remotePlaybackState,
        ) { _, state -> discoveryHandler.isConnected && CastPlayback.isPlaying(state) }
            .distinctUntilChanged()
            // Skip the initial not-casting emission: repainting at service start would flash an empty
            // widget before the restored queue's metadata lands. Cast edges all come later.
            .drop(1)
            .collect(scope) { remotePlaying ->
                if (remotePlaying) startWidgetTicker() else updateWidget()
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
                    // Keep the cast-MIME cache in lockstep with the URL cache it's populated alongside.
                    songMimeCache.clear()

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

    /** The playing state the widget should render — the receiver's while casting, else the local player's. */
    private fun widgetIsPlaying(): Boolean =
        if (discoveryHandler.isConnected) discoveryHandler.isRemotePlaying() else player.isPlaying

    private fun updateWidget() {
        scope.launch {
            // While casting the local player is deliberately paused and its clock frozen, so render the
            // remote state/clock instead — otherwise the widget's icon contradicts what a tap does
            // (its ACTION_PLAY_PAUSE routes to the receiver).
            val casting = discoveryHandler.isConnected
            val metadata = currentMediaMetadata.value
            val isPlaying = widgetIsPlaying()
            val title = metadata?.title ?: getString(R.string.app_name)
            val artist = metadata?.artists?.joinToString(", ") { it.name } ?: ""
            val albumArtUrl = metadata?.thumbnailUrl
            val positionMs =
                (if (casting) CastPlayback.remoteSecondsToMs(discoveryHandler.interpolatedRemoteTimeSec()) else player.currentPosition)
                    .coerceAtLeast(0L)
            val durationMs =
                (if (casting) CastPlayback.remoteSecondsToMs(discoveryHandler.remoteDuration.value) else player.duration)
                    .takeIf { it > 0L } ?: 0L

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
            while (isActive && widgetIsPlaying()) {
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
                    // A broadcast has no repeat/shuffle/personal-radio: the buttons disable while a
                    // station plays (updateNotification re-runs on every queue/track change).
                    .setEnabled(currentQueue !is StationQueue)
                    .build(),
                CommandButton
                    .Builder()
                    .setDisplayName(getString(if (player.shuffleModeEnabled) R.string.action_shuffle_off else R.string.action_shuffle_on))
                    .setIconResId(if (player.shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle)
                    .setSessionCommand(CommandToggleShuffle)
                    .setEnabled(currentQueue !is StationQueue)
                    .build(),
                CommandButton.Builder()
                    .setDisplayName(getString(R.string.start_radio))
                    .setIconResId(R.drawable.radio)
                    .setSessionCommand(CommandToggleStartRadio)
                    .setEnabled(currentSong.value != null && currentQueue !is StationQueue)
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
            val nextResult = YouTube.next(WatchEndpoint(videoId = mediaId)).getOrNull()
            // Video mode: passively fold in any song→video counterpart the response carries (free — this
            // next() already runs for related songs). Empty for the common wrapper-less response.
            nextResult?.counterparts?.let { videoModeController.recordCounterparts(it) }
            val relatedEndpoint = nextResult?.relatedEndpoint ?: return
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
        val previousQueue = currentQueue
        stationWaitJob?.cancel()
        currentQueue = queue
        queueTitle = null
        player.shuffleModeEnabled = false
        // A broadcast has exactly one order and no looping: a persisted REPEAT_ONE would otherwise
        // trap the station on a single slot (the repeat transition reason skips the runway top-up).
        if (queue is StationQueue) player.repeatMode = REPEAT_MODE_OFF
        // Tracking: the tapped/preloaded item is always user-chosen context for this queue.
        Tracker.playSources.onQueueStarted(queue.playSource, listOfNotNull(queue.preloadItem?.id))
        queue.preloadItem?.let { preloadItem ->
            player.setMediaItem(preloadItem.toMediaItem())
            player.prepare()
            // While casting, the receiver is the one that plays: onMediaItemTransition() (via
            // CastController) pauses local and triggers the remote load. Never let local playback
            // start on top of it.
            player.playWhenReady = CastPlayback.shouldStartLocalPlayback(playWhenReady, discoveryHandler.isConnected)
        }
        scope.launch(SilentHandler) {
            val initialStatus =
                try {
                    withContext(Dispatchers.IO) {
                        queue.getInitialStatus().filterExplicit(dataStore.get(HideExplicitKey, false))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A failed fetch must never be silent. Without a preload nothing plays: tell the
                    // user and hand auto-continuation back to the queue that was playing (its player
                    // items are untouched — only the pointer was swapped). With a preload the tapped
                    // song IS playing but the radio fill failed: say so (a one-song queue reads as
                    // broken) and KEEP this queue — its nextPage retries the seed page on a later
                    // transition, so the radio can still start once the network recovers.
                    reportException(e)
                    if (currentQueue === queue) {
                        if (queue.preloadItem == null) currentQueue = previousQueue
                        onStartRadioFailed()
                    }
                    return@launch
                }
            // Tracking: initial items keep the queue's source when they are the chosen context
            // (album/playlist tracks); a radio queue's fill beyond the tapped song reports "radio".
            // Guarded: a slow-loading queue the user already replaced must not register its items
            // over the newer queue's registrations.
            if (currentQueue === queue) {
                initialStatus.items.map { it.mediaId }.let { ids ->
                    if (queue.initialItemsAreContext) {
                        Tracker.playSources.registerContext(queue.playSource, ids)
                    } else {
                        Tracker.playSources.registerRadio(ids)
                    }
                }
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
                // Same cast guard as the preload branch above.
                player.playWhenReady = CastPlayback.shouldStartLocalPlayback(playWhenReady, discoveryHandler.isConnected)
            }
        }
    }

    fun startRadioSeamlessly() {
        // A live station IS radio: swapping the shared broadcast for a personal /radio queue from
        // the same button would be a confusing silent exit - the affordance is hidden/disabled on
        // every surface, and this chokepoint guard covers any stale controller.
        if (currentQueue is StationQueue) return
        // Ignore re-taps while a radio fetch is in flight — two concurrent runs would both
        // append their radio items, duplicating the queue (#89).
        if (startRadioJob?.isActive == true) return
        val currentMediaMetadata = player.currentMetadata ?: return

        // The queue swap itself is invisible on the Now Playing screen (Android Auto included),
        // so surface a transient session message as immediate feedback (#89). INFO_CANCELLED is
        // media3's non-fatal informational code — controllers show the message and move on.
        mediaSession.sendError(
            SessionError(SessionError.INFO_CANCELLED, getString(R.string.starting_radio)),
        )

        startRadioJob = scope.launch(SilentHandler) {
            val radioQueue = ZemerRadioQueue(
                kind = "song",
                seed = currentMediaMetadata.id,
                context = this@MusicService,
            )
            val initialStatus = try {
                radioQueue.getInitialStatus()
            } catch (e: Exception) {
                reportException(e)
                onStartRadioFailed()
                return@launch
            }
            // Exclude the seed (currently-playing) song by id so it isn't queued twice — the Zemer radio
            // may or may not lead with the seed, unlike the YouTube watch playlist that always did.
            val radioItems = initialStatus.items.filterNot { it.mediaId == currentMediaMetadata.id }
            if (radioItems.isEmpty()) {
                // Fetch came back empty (e.g. everything whitelist-filtered) — leave the
                // existing queue alone instead of having wiped it for nothing.
                onStartRadioFailed()
                return@launch
            }
            // The user may have skipped to another song during the fetch — don't replace
            // their queue with a radio seeded from the previous song.
            if (player.currentMetadata?.id != currentMediaMetadata.id) return@launch

            // Only now, with radio items in hand, drop the rest of the old queue. Doing this
            // before the fetch stranded the user on a 1-song queue whenever it failed (#89).
            if (player.currentMediaItemIndex > 0) {
                player.removeMediaItems(0, player.currentMediaItemIndex)
            }
            if (player.currentMediaItemIndex < player.mediaItemCount - 1) {
                player.removeMediaItems(player.currentMediaItemIndex + 1, player.mediaItemCount)
            }

            if (initialStatus.title != null) {
                queueTitle = initialStatus.title
            }
            player.addMediaItems(radioItems)
            // Tracking: only the ADDED items are autoplay — the currently-playing song must
            // keep whatever source it already had (registerRadio would flip an unregistered
            // current song to "radio" mid-listen).
            Tracker.playSources.registerRadio(radioItems.map { it.mediaId })
            currentQueue = radioQueue
        }
    }

    private fun onStartRadioFailed() {
        // Car screens don't show toasts — the session error is what Android Auto displays.
        mediaSession.sendError(
            SessionError(SessionError.ERROR_IO, getString(R.string.radio_start_failed)),
        )
        this.toast(getString(R.string.radio_start_failed))
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
        exitStationOnQueueMutation()
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
        exitStationOnQueueMutation()
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
                        downloadUtil.downloadVideoToMediaStore(it, fromUser = false)
                    } else {
                        downloadUtil.downloadToMediaStore(it, fromUser = false)
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
                this@MusicService.toast(getString(R.string.android_auto_target_playlist_not_set))
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

    /**
     * Resolves a playable stream URL (and caches its MIME) for the cast path. Reuses the validated
     * YTPlayerUtils.playerResponseForPlayback() — the same cipher/poToken resolution the local player
     * goes through — so streaming correctness is shared, not a second implementation. It deliberately
     * skips the local-only FormatEntity persistence and recoverSong backfill that createDataSourceFactory
     * does: the receiver only needs a URL + container, not the song-details metadata.
     */
    suspend fun resolveStreamUrl(mediaId: String): String? {
        // Trust the cached URL only when its container MIME is cached too. songUrlCache is the shared
        // cache also populated by local playback, which may not have recorded the MIME; returning a URL
        // whose MIME then defaults to "audio/mp4" makes the receiver reject an opus/webm stream.
        songUrlCache[mediaId]?.takeIf { it.second > System.currentTimeMillis() }?.let { cached ->
            if (songMimeCache.containsKey(mediaId)) return cached.first
        }

        return withContext(Dispatchers.IO) {
            val playbackData = YTPlayerUtils.playerResponseForPlayback(
                mediaId,
                audioQuality = audioQuality,
                connectivityManager = connectivityManager,
            ).getOrNull()

            val streamUrl = playbackData?.streamUrl
            if (streamUrl != null) {
                songUrlCache[mediaId] =
                    streamUrl to System.currentTimeMillis() + (playbackData.streamExpiresInSeconds * 1000L)
                songMimeCache[mediaId] = playbackData.format.mimeType.split(";")[0]
            }
            streamUrl
        }
    }

    /**
     * Drops the cached stream URL + MIME for [mediaId] so the next [resolveStreamUrl] fetches a fresh
     * one — used by the cast error recovery when the receiver repeatedly fails to fetch the cached URL.
     */
    fun invalidateStreamCache(mediaId: String) {
        songUrlCache.remove(mediaId)
        songMimeCache.remove(mediaId)
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        // Video mode: our own rendition swap surfaces here as a same-item transition — skip ALL the
        // real-transition side effects (cast reload, auto-load-more, save-queue) and keep video mode. A
        // real track change instead reverts video to audio (I2) inside the controller, then falls through
        // to the normal handling below. onEvents still updates currentMediaMetadata either way.
        if (videoModeController.onMediaItemTransition(mediaItem, reason)) return

        lastPlaybackSpeed = -1.0f // force update song

        setupLoudnessEnhancer()
        updateWidget()

        // The cast receiver reload on a track change is owned by the (process-scoped) CastController, so it
        // runs whether or not a PlayerConnection is currently bound — auto-advance survives the UI Activity
        // being destroyed mid-cast. It is the single owner (PlayerConnection no longer reloads), so the
        // receiver is loaded exactly once per transition.
        castController.onMediaItemTransition(mediaItem, reason)

        // Station boundary sync (handoff par. 4): the ONLY place broadcast drift is corrected -
        // bidirectional (seek forward when behind, wait when ahead, re-tune when nothing queued is
        // on-air), never mid-track.
        (currentQueue as? StationQueue)?.let { resyncStationPlayback(it) }

        // Auto load more songs. A station's runway top-up is NOT optional: it ignores the user's
        // Auto-load-more preference (a broadcast that silently ends after six slots is broken, not
        // configured) and the repeat-reason guard (repeat is forced off for stations anyway).
        if ((dataStore.get(AutoLoadMoreKey, true) || currentQueue is StationQueue) &&
            reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
            player.mediaItemCount - player.currentMediaItemIndex <= 5 &&
            currentQueue.hasNextPage() &&
            !(dataStore.get(DisableLoadMoreWhenRepeatAllKey, false) && player.repeatMode == REPEAT_MODE_ALL)
        ) {
            scope.launch(SilentHandler) {
                val page =
                    currentQueue.nextPage().filterExplicit(dataStore.get(HideExplicitKey, false))
                // Append only what isn't queued yet: YouTube-style pages lead with the already-queued
                // current item, Zemer /radio pages are pure fresh tracks — the old blanket drop(1)
                // silently discarded the first (top-ranked) track of every Zemer page.
                val queuedIds = (0 until player.mediaItemCount)
                    .mapTo(mutableSetOf()) { player.getMediaItemAt(it).mediaId }
                val mediaItems = continuationItemsToAppend(queuedIds, page)
                // Tracking: a chosen playlist's later pages are still the chosen context (spec:
                // tracks continuing from an originally-chosen context KEEP its source); only a
                // radio queue's pages are autoplay.
                mediaItems.map { it.mediaId }.let { ids ->
                    if (currentQueue.continuationIsContext) {
                        Tracker.playSources.registerContext(currentQueue.playSource, ids)
                    } else {
                        Tracker.playSources.registerRadio(ids)
                    }
                }
                if (player.playbackState != STATE_IDLE) {
                    player.addMediaItems(mediaItems)
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
        // A broadcast never ends: reaching STATE_ENDED means the runway ran out (top-up lost a
        // race) - re-tune to the live schedule instead of parking in silence.
        if (playbackState == Player.STATE_ENDED) {
            (currentQueue as? StationQueue)?.let { resyncStationPlayback(it) }
        }
        // Save state when playback state changes
        if (dataStore.get(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }
        updateWidget()
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (playWhenReady) {
            setupLoudnessEnhancer()
            resyncStationOnResume()
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
            // Same-stack availability republish: the Song/Video pill state must change atomically with
            // the current item — the async combine's dispatch hops flashed it in mid player-open.
            videoModeController.recomputeNow()
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

        // Video mode (I8): a video-rendition failure reverts to audio at the captured position and surfaces
        // a snackbar. Must run BEFORE the audio 403-refresh path — that operates on the real id and would
        // invalidate the wrong (audio) cache entry and loop.
        if (videoModeController.onPlayerError(error)) return

        // Check for expired URL (403 error) - needs immediate URL refresh
        if (isExpiredUrlError(error)) {
            Timber.d("Expired URL detected (403), refreshing stream URL")
            handleExpiredUrlError()
            return
        }

        // A STREAMING item whose downloaded file exists hands playback over to the file instead of
        // failing — most importantly when the device went offline after a mid-play download (the
        // sticky source keeps streaming until the item restarts; without this the app would wait for
        // network with a perfectly good file on disk). Safe because seekTo+prepare re-initializes the
        // extractor, so the file is read from a fresh state, never under a stream-fed extractor (the
        // container-mix corruption class); the sticky flip + purge make every later open serve ONLY
        // file bytes. Stations keep their own slot recovery below.
        //
        // Async, not runBlocking: onPlayerError is a Player.Listener callback dispatched on the
        // application/main thread (no custom playback looper is set), so a blocking DB read + file
        // I/O here would stall the UI on every playback error — exactly what "never runBlocking on a
        // UI path" forbids. The fallback error handling below is shared via [handleUnrecoverablePlayerError]
        // so both the synchronous "can't possibly recover" path and the async "checked, no file" path
        // run the identical sequence.
        val mediaId = player.currentMediaItem?.mediaId
        if (currentQueue !is StationQueue && mediaId != null && playbackSourceIsLocal[mediaId] != true) {
            scope.launch {
                val mediaStoreUri = withContext(Dispatchers.IO) {
                    database.song(mediaId).first()?.song?.mediaStoreUri
                }
                if (mediaStoreUri != null && downloadedFileOpens(mediaStoreUri)) {
                    Timber.w("Player error while a downloaded file exists for $mediaId; handing over to the local file")
                    playbackSourceIsLocal[mediaId] = true
                    runCatching { playerCache.removeResource(mediaId) }
                    // The player may have moved on (skip, another error already handled) while this
                    // check was in flight — only apply the recovery to the item it was checked for.
                    if (player.currentMediaItem?.mediaId == mediaId) {
                        player.seekTo(player.currentMediaItemIndex, player.currentPosition)
                        player.prepare()
                        player.playWhenReady = true
                    }
                } else {
                    handleUnrecoverablePlayerError(error)
                }
            }
            return
        }

        handleUnrecoverablePlayerError(error)
    }

    /** The shared tail of [onPlayerError] once no local-file recovery is possible or found. */
    private fun handleUnrecoverablePlayerError(error: PlaybackException) {
        // RELAY: surface the relay's own HTTP errors with the contracted user messages (server doc §4).
        // A video-URL 404 for an audio-only id is handled earlier by videoModeController.onPlayerError
        // (revert to audio), so it never reaches here. Runs on the main thread (see onPlayerError).
        if (relayModeNow == true) {
            when (getHttpResponseCode(error)) {
                404 -> toast(R.string.relay_error_unavailable)
                502, 503 -> toast(R.string.relay_error_retry)
            }
        }

        val isConnectionError = (error.cause?.cause is PlaybackException) &&
                (error.cause?.cause as PlaybackException).errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED

        // Don't treat 403 as network error - it needs URL refresh, not network wait
        if (!isNetworkConnected.value || isConnectionError) {
            waitOnNetworkError()
            return
        }

        // A broadcast never stops for one bad slot: an unstreamable scheduled track (CDN 403 past
        // the wall, region block) is marked unplayable (so the resync can never seek back into it),
        // skipped, and the resync rejoins the wall clock - waiting out the gap if the next slot
        // hasn't started, so the listener is never left permanently ahead. Handoff-settled; the
        // zero-play-time guard already keeps the failed slot out of the play stats.
        (currentQueue as? StationQueue)?.let { station ->
            player.currentMediaItem?.mediaId?.let(station::markUnplayable)
            skipOnError()
            resyncStationPlayback(station)
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

    /** Whether [mediaId] is currently playing from its downloaded local file (drives the LOCAL video rendition). */
    fun playbackSourceIsLocalFile(mediaId: String): Boolean = playbackSourceIsLocal[mediaId] == true

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

    /**
     * The shared "should this open play the downloaded LOCAL file?" decision, used by BOTH the DIRECT and
     * RELAY resolvers so they behave identically. Returns the local MediaStore uri to play, or null to
     * stream. Decides the source ONCE at position 0 and honors it on later opens (no mid-track switch to a
     * just-completed download — the corruption commit 1f48d89 fixed), purges the id's cached span, nudges
     * video-mode availability, backfills via recoverSong, and self-repairs a stale/missing downloaded file
     * by re-enqueueing its download while streaming this play. Blocking read: ResolvingDataSource requires
     * synchronous code and this runs on ExoPlayer's loading thread, not the main thread.
     */
    private fun resolveDownloadedFileUri(mediaId: String, position: Long): String? {
        val song = runBlocking(Dispatchers.IO) { database.song(mediaId).first() }
        val mediaStoreUri = song?.song?.mediaStoreUri ?: return null
        val fileOpens = downloadedFileOpens(mediaStoreUri)
        if (position == 0L) {
            playbackSourceIsLocal[mediaId] = fileOpens
            videoModeController.onPlaybackSourceResolved()
            if (fileOpens) {
                runCatching { playerCache.removeResource(mediaId) }
                scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                return mediaStoreUri
            }
            // Stale "downloaded" row (file gone): stream this play and self-repair, unless a re-download
            // already FAILED this session (else a permanently-dead source re-downloads on every play).
            val liveStatus = downloadUtil.mediaStoreDownloadState(mediaId)?.status
            if (liveStatus == MediaStoreDownloadManager.DownloadState.Status.FAILED) {
                Timber.w("Downloaded file missing for $mediaId but its re-download already FAILED this session; streaming without re-enqueueing")
            } else {
                Timber.w("Downloaded file missing for $mediaId; re-downloading to self-repair and streaming this play")
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        if (song.song.isVideo) downloadUtil.downloadVideoToMediaStore(song, fromUser = false)
                        else downloadUtil.downloadToMediaStore(song, fromUser = false)
                    }
                }
            }
            return null
        } else if (fileOpens && playbackSourceIsLocal[mediaId] == true) {
            // Later open (seek / cache-span re-open) of an item that STARTED local -> keep the file.
            scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
            return mediaStoreUri
        }
        return null
    }

    private fun createDataSourceFactory(): DataSource.Factory {
        return ResolvingDataSource.Factory(createCacheDataSource()) { dataSpec ->
            val mediaId = dataSpec.key ?: error("No media id")

            // Video-mode rendition: a `video:<id>` key resolves a PROGRESSIVE MUXED stream via the same
            // YTPlayerUtils path as audio (preferVideo=true), bypassing all the audio-only machinery —
            // the local-file/downloadCache branch, the FormatEntity upsert, recoverSong, and the
            // Tracker.onStreamResolved record (a transient rendition must never pollute the formats table
            // or the listen's stream record). Its own namespaced cache entries (songUrlCache[videoKey] +
            // playerCache keyed on the video: key) keep video bytes isolated from the audio cache.
            if (VideoRendition.isVideoKey(mediaId)) {
                val renditionId = VideoRendition.renditionId(mediaId)
                if (playerCache.isCached(mediaId, dataSpec.position, CHUNK_LENGTH)) {
                    return@Factory dataSpec
                }
                songUrlCache[mediaId]?.takeIf { it.second > System.currentTimeMillis() }?.let {
                    return@Factory dataSpec.withUri(it.first.toUri())
                }
                // The shared metered-aware cap (one policy with muxed downloads — VideoRendition).
                val maxVideoBitrateKbps = VideoRendition.defaultMaxBitrateKbps(connectivityManager.isActiveNetworkMetered)
                val videoPlayback = runBlocking(Dispatchers.IO) {
                    YTPlayerUtils.playerResponseForPlayback(
                        renditionId,
                        audioQuality = com.jtech.zemer.constants.AudioQuality.HIGH,
                        connectivityManager = connectivityManager,
                        preferVideo = true,
                        maxVideoBitrateKbps = maxVideoBitrateKbps,
                    )
                }.getOrElse { throwable ->
                    when (throwable) {
                        is PlaybackException -> throw throwable
                        else -> throw PlaybackException(
                            getString(R.string.error_unknown), throwable, PlaybackException.ERROR_CODE_REMOTE_ERROR
                        )
                    }
                }
                val nonNullVideo = requireNotNull(videoPlayback) { getString(R.string.error_unknown) }
                val videoUrl = nonNullVideo.streamUrl
                songUrlCache[mediaId] =
                    videoUrl to System.currentTimeMillis() + (nonNullVideo.streamExpiresInSeconds * 1000L)
                return@Factory dataSpec.withUri(videoUrl.toUri()).subrange(dataSpec.uriPositionOffset, CHUNK_LENGTH)
            }

            // Downloaded local file? Decide once at position 0 and honor it later; self-repair a stale
            // file. Shared with the RELAY resolver via [resolveDownloadedFileUri].
            resolveDownloadedFileUri(mediaId, dataSpec.position)?.let {
                return@Factory dataSpec.withUri(it.toUri())
            }

            if (downloadCache.isCached(
                    mediaId,
                    dataSpec.position,
                    if (dataSpec.length >= 0) dataSpec.length else 1
                ) ||
                playerCache.isCached(mediaId, dataSpec.position, CHUNK_LENGTH)
            ) {
                // Cached playback skips the stream resolution that records musicVideoType, so kick the
                // on-demand probe NOW (deduped, one light call per unknown id per session) — the
                // Song/Video toggle is then already decided by the time the player is expanded,
                // instead of appearing a beat after (the probe's old expand-time trigger remains as
                // the fallback for items that start mid-span).
                videoModeController.requestVideoAvailability(mediaId)
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
                // Telemetry: remember which client (and, for deciphered web clients, which player
                // hash) served this stream, so the listen's `play` event can carry it.
                Tracker.onStreamResolved(
                    mediaId,
                    nonNullPlayback.streamClient,
                    playerHash = if (nonNullPlayback.streamClient in WEB_STREAM_CLIENTS) {
                        CipherDeobfuscator.lastUsedPlayerHash
                    } else {
                        null
                    },
                )
                // Video mode: remember this item's music-video type (ATV song vs OMV/UGC video) so the
                // Song/Video toggle knows whether a SELF video rendition exists — free, from the audio
                // resolution the item already needed.
                videoModeController.recordMusicVideoType(mediaId, nonNullPlayback.videoDetails?.musicVideoType)
                scope.launch(Dispatchers.IO) { recoverSong(mediaId, nonNullPlayback) }

                val streamUrl = nonNullPlayback.streamUrl

                songUrlCache[mediaId] =
                    streamUrl to System.currentTimeMillis() + (nonNullPlayback.streamExpiresInSeconds * 1000L)
                // Keep the cast-MIME cache coherent with the URL cache it's written alongside, so a song
                // played locally first then cast carries its real container (not the "audio/mp4" default).
                songMimeCache[mediaId] = format.mimeType.split(";")[0]
                return@Factory dataSpec.withUri(streamUrl.toUri()).subrange(dataSpec.uriPositionOffset, CHUNK_LENGTH)
            }
        }
    }

    private val dataSourceFactory: DataSource.Factory by lazy {
        createDataSourceFactory()
    }

    // ---- RELAY playback mode (opt-in; see RelayDataSourceFactory / the handoff doc). Isolated: none of
    // this runs when RELAY is off, and the DIRECT factory is used verbatim.
    // Mirror of PlaybackModeKey; null = not yet observed (the dispatcher resolves that below).
    @Volatile
    private var relayModeNow: Boolean? = null

    private val relayDataSourceFactory: DataSource.Factory by lazy {
        RelayDataSourceFactory.create(this) { mediaId, position -> resolveDownloadedFileUri(mediaId, position) }
    }

    // Per-open selector: RELAY -> the isolated relay factory, everyone else -> the DIRECT factory verbatim.
    // Reading the flag per open gives the "takes effect on the next track" toggle behavior.
    private val playbackDataSourceFactory = DataSource.Factory {
        // Resolve null (mirror not yet emitted) with a one-time synchronous read so a relay user's first
        // open is never mis-routed to DIRECT. createDataSource() runs on ExoPlayer's loading thread, so the
        // blocking read is off the main thread (same as the DIRECT resolver's runBlocking).
        val relay = relayModeNow ?: run {
            (dataStore.get(PlaybackModeKey, PlaybackMode.DIRECT.name) == PlaybackMode.RELAY.name)
                .also { relayModeNow = it }
        }
        if (relay) relayDataSourceFactory.createDataSource() else dataSourceFactory.createDataSource()
    }

    private fun createMediaSourceFactory() =
        DefaultMediaSourceFactory(
            playbackDataSourceFactory,
            ExtractorsFactory {
                // Mp4Extractor added for the video-mode progressive muxed MP4 (plain-moov container the
                // fragmented/mkv extractors can't parse); purely additive (sniffing tries in order), and
                // it also lets a downloaded muxed video file play inside the music queue.
                // MatroskaExtractor also decodes the relay's webm/opus (itag 251) audio.
                arrayOf(MatroskaExtractor(), FragmentedMp4Extractor(), Mp4Extractor())
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

        // Video mode (I4): a rendition swap ends this PlaybackStats session mid-listen, firing this
        // callback. The accumulator SUPPRESSES a swap-ended session (stashing its play time) and EMITS
        // the accumulated total once at the real end — so an audio↔video toggle never double-fires the
        // `play` event, the history insert, or the YT playback registration for one listen.
        val listen = videoModeController.onStatsReady(mediaItem.mediaId, playbackStats.totalPlayTimeMs)
        if (listen is ListenAccumulator.Result.Suppress) return
        val totalPlayTimeMs = (listen as ListenAccumulator.Result.Emit).totalMs

        // Anonymous telemetry (docs/tracking/README.md): one event per listen, when it ends —
        // EVERY listen however short (a 5-second skip is the negative signal the algorithm needs;
        // the server applies any qualification gate at analysis time). totalPlayTimeMs is the
        // accumulated actual play time: pauses excluded, seek-backs not double-counted. A session
        // with ZERO play time is not a listen — a restored persisted queue opens a stats session
        // for the current item without the user ever pressing play; those phantoms must not count.
        if (totalPlayTimeMs > 0) {
            Tracker.play(
                videoId = mediaItem.mediaId,
                secs = (totalPlayTimeMs / 1000L).toInt(),
                dur = mediaItem.metadata?.duration?.takeIf { it > 0 },
                source = Tracker.playSources.sourceFor(mediaItem.mediaId),
            )
        }

        if (totalPlayTimeMs >= (
                    dataStore[HistoryDuration]?.times(1000f)
                        ?: 10000f
                    ) &&
            !dataStore.get(PauseListenHistoryKey, false)
        ) {
            database.query {
                incrementTotalPlayTime(songId = mediaItem.mediaId, playTime = totalPlayTimeMs)
                try {
                    insert(
                        Event(
                            songId = mediaItem.mediaId,
                            timestamp = LocalDateTime.now(),
                            playTime = totalPlayTimeMs,
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

    /**
     * Broadcast pause = stop: on resume, rejoin the LIVE position (handoff par. 5) - a station that
     * resumes where it paused is a playlist.
     */
    private fun resyncStationOnResume() {
        (currentQueue as? StationQueue)?.let { resyncStationPlayback(it) }
    }

    /**
     * THE bidirectional broadcast resync (every station drift path funnels here - boundary
     * transitions, pause-resume, error skips, STATE_ENDED):
     *  - finds the queued slot that is ON-AIR by wall clock (never scanning backward past the
     *    current index into already-played slots) and seeks to its live offset when off by more
     *    than the drift tolerance;
     *  - if the landing slot has NOT started yet (we ran ahead: an error/blocked skip started it
     *    early), it WAITS - pauses and resumes exactly at the slot's startMs (the addendum's
     *    sanctioned handling), so one bad slot can never leave the listener permanently ahead;
     *  - if NOTHING queued is on-air (paused past the runway, stale schedule), it re-tunes from
     *    scratch - the exact flow a card tap runs, which also recovers STATE_ENDED.
     */
    private fun resyncStationPlayback(station: StationQueue) {
        val nowMs = System.currentTimeMillis()
        val startIndex = player.currentMediaItemIndex.coerceAtLeast(0)
        for (index in startIndex until player.mediaItemCount) {
            val mediaId = player.getMediaItemAt(index).mediaId
            val live = station.onAirOffsetMs(mediaId, nowMs) ?: continue
            val positionInSlot = if (index == player.currentMediaItemIndex) player.currentPosition else Long.MIN_VALUE
            if (index != player.currentMediaItemIndex || kotlin.math.abs(positionInSlot - live) > STATION_MAX_DRIFT_MS) {
                player.seekTo(index, live)
            }
            return
        }
        // Nothing from here on is on-air. If the CURRENT slot merely hasn't started (we're ahead
        // after an error/blocked skip), hold playback until its startMs instead of drifting ahead.
        val currentId = player.currentMediaItem?.mediaId
        val untilStart = currentId?.let { station.msUntilSlotStarts(it, nowMs) }
        if (untilStart != null && untilStart > 0) {
            if (untilStart > STATION_MAX_DRIFT_MS) {
                player.pause()
                player.seekTo(0)
                stationWaitJob?.cancel()
                stationWaitJob = scope.launch(SilentHandler) {
                    delay(untilStart)
                    if (currentQueue === station && player.currentMediaItem?.mediaId == currentId) {
                        player.play()
                    }
                }
            }
            return
        }
        // Past the whole runway (long pause / stale schedule): re-tune exactly like a card tap.
        playQueue(StationQueue(station.stationId, this))
    }

    private var stationWaitJob: Job? = null

    /**
     * A queue mutation (Play next / Add to queue) is incompatible with a broadcast - it turns the
     * player content into an ordinary list, so broadcast mode must END here: without this,
     * currentQueue latched as the station forever (LIVE bar stuck, skips disabled everywhere, and
     * the saveQueueToDisk guard silently disabling queue persistence for the rest of the process).
     */
    private fun exitStationOnQueueMutation() {
        if (currentQueue is StationQueue) {
            stationWaitJob?.cancel()
            currentQueue = EmptyQueue
        }
    }

    private fun saveQueueToDisk() {
        if (player.mediaItemCount == 0) {
            return
        }
        // A broadcast is never persisted: restoring a station paused at a stale position is the
        // exact "playlist, not a station" failure the contract forbids (pause = stop; resume = live).
        if (currentQueue is StationQueue) {
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
        // Tear down any active cast session so the receiver doesn't keep playing an orphaned stream
        // after the service dies. Clear onDisconnect first so the async Disconnected callback can't
        // seek/prepare the player we're about to release. (sender-sdk 0.4.0's NsdDeviceDiscoverer has
        // no stop API, so the NSD discovery itself can't be halted here.)
        discoveryHandler.onDisconnect = null
        discoveryHandler.connectedDevice?.let { device ->
            runCatching { device.stopPlayback() }
            runCatching { device.disconnect() }
        }
        // After the receiver is told to stop: nothing will fetch through the relay anymore.
        stopCastRelay()
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
                if (discoveryHandler.isConnected) {
                    // isRemotePlaying falls back to the play intent before the receiver's first state
                    // report, matching the widget's rendered icon (widgetIsPlaying).
                    if (discoveryHandler.isRemotePlaying()) {
                        discoveryHandler.pause()
                    } else {
                        discoveryHandler.play()
                    }
                } else if (player.isPlaying) {
                    player.pause()
                } else {
                    player.play()
                }
            }
            // The local skip advances the queue, whose media-item transition reloads the receiver while
            // casting. PREV uses seekToPreviousMediaItem when casting: the local clock is meaningless
            // remotely, so seekToPrevious's "restart current track if >3s in" would misfire.
            // A broadcast has no transport: the widget's skip taps reach the raw player and would
            // bypass the session command mask, so they are dropped while a station plays.
            MusicWidget.ACTION_NEXT -> if (currentQueue !is StationQueue) player.seekToNext()
            MusicWidget.ACTION_PREV ->
                if (currentQueue !is StationQueue) {
                    if (discoveryHandler.isConnected) player.seekToPreviousMediaItem() else player.seekToPrevious()
                }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?) = super.onBind(intent) ?: binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Issue #109: when "stop music on task clear" is enabled, swiping the app away from recents
        // must stop playback and dismiss the media notification. A foreground media service keeps
        // both alive while playing, so the notification otherwise lingered until force-stop. This is
        // the canonical hook for a recents-swipe (more reliable than the Activity's onDestroy).
        // onDestroy() still persists the queue first, so resume state is kept for next launch.
        if (dataStore.get(StopMusicOnTaskClearKey, false)) {
            // While casting, a bare local pause() leaves the receiver playing (its own socket + the
            // relay keep the stream alive), so end the cast session too — otherwise "stop on task
            // clear" wouldn't actually stop the music. disconnect() stops receiver playback and drops
            // the session; the local player is recovered paused via onDisconnect before it's torn down.
            if (CastPlayback.shouldEndCastOnTaskClear(true, discoveryHandler.isConnected)) {
                discoveryHandler.disconnect()
            }
            player.pause()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
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
        // Clients whose streams run the cipher — only these have a meaningful player hash to report
        // on the telemetry `play` event (mirrors ShowMediaInfo's isWebStream set).
        val WEB_STREAM_CLIENTS = setOf("WEB_REMIX", "WEB_CREATOR", "TVHTML5", "WEB")
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
