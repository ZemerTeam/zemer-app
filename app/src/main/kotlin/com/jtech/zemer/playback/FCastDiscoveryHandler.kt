package com.jtech.zemer.playback

import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.utils.reportException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.fcast.sender_sdk.*

/**
 * Runs a throwing FCast SDK call (all CastingDevice mutators declare `throws CastingDeviceException`),
 * reporting rather than crashing if the receiver dropped mid-call.
 */
private inline fun castCall(block: () -> Unit) {
    runCatching { block() }.onFailure { reportException(it, "FCast SDK call") }
}

private fun urlLoadRequest(url: String, contentType: String, resumePosition: Double, metadata: Metadata?) =
    LoadRequest.Url(
        url = url,
        contentType = contentType,
        resumePosition = resumePosition,
        speed = null,
        volume = null,
        metadata = metadata,
        requestHeaders = null,
    )

/**
 * Builds the FCast [Metadata] sent to the receiver from the app's [MediaMetadata]. Single definition
 * so the cast title format ("Title - Artist, Artist") can't drift between the connect path and the
 * track-advance reload path.
 */
fun MediaMetadata.toCastMetadata(): Metadata =
    Metadata(
        title = "$title - ${artists.joinToString(", ") { it.name }}",
        thumbnailUrl = thumbnailUrl,
    )

class DevEventHandler(
    private val handler: FCastDiscoveryHandler,
    val device: CastingDevice,
    private val onTrackEnded: (() -> Unit)? = null,
    private val onPlaybackError: ((String) -> Unit)? = null,
) : DeviceEventHandler {
    private var wasConnected = false

    override fun connectionStateChanged(state: DeviceConnectionState) {
        // Ignore callbacks from a device we've already replaced (connectTo to a new device) or already
        // tore down (disconnect() ran onConnectionDisconnected synchronously): a stale async Disconnected
        // must not null out the new connection or fire onDisconnect twice, and a stale state publish must
        // not overwrite the new attempt's Connecting/Connected in remoteConnectionState — the picker
        // awaits that flow (CastConnect.awaitOutcome) to decide whether the connect succeeded.
        if (handler.connectedDevice !== device) return
        handler.remoteConnectionState.value = state
        if (state is DeviceConnectionState.Connected) {
            val isReconnect = wasConnected
            wasConnected = true
            handler.connectedDeviceFlow.value = device

            val url = handler.currentStreamUrl
            val type = handler.currentContentType
            if (url != null && type != null) {
                val pos = if (isReconnect && handler.remoteTime.value > 0) {
                    handler.remoteTime.value
                } else {
                    handler.initialResumePosition
                }
                castCall { device.load(urlLoadRequest(url, type, pos, handler.currentMetadata)) }
                // If the user intended to stay paused, enforce it immediately after loading.
                if (!handler.shouldPlay) castCall { device.pausePlayback() }
            }
        } else if (state is DeviceConnectionState.Disconnected) {
            handler.onConnectionDisconnected()
        }
    }

    override fun playbackStateChanged(state: PlaybackState) {
        // Same stale-device guard as connectionStateChanged: connectTo's stopPlayback on the outgoing
        // device solicits a final state report (e.g. PAUSED), which must not flip the new connection's
        // shouldPlay off — the switched-to device would load and immediately re-pause.
        if (handler.connectedDevice !== device) return
        handler.remotePlaybackState.value = state
        // Mirror the receiver's own state into our play intent so a pause/resume from the TV's remote
        // sticks. EXCEPT a PAUSED report at the very end of the track: some receivers auto-pause at
        // pos==duration to signal end-of-track (no IDLE, no END event). Treating that as a user pause would
        // flip the intent off and make the next auto-advanced track load paused — it's the track finishing,
        // not a pause. Pause-on-reconnect is enforced in connectionStateChanged, not here.
        val endOfTrackPause = state == PlaybackState.PAUSED &&
            CastAutoAdvance.nearEnd(handler.remoteDuration.value, handler.remoteTime.value, CastAutoAdvance.PAUSED_END_EPSILON_SEC)
        if (!endOfTrackPause) CastPlayback.playIntentForState(state)?.let { handler.shouldPlay = it }
    }

    override fun timeChanged(time: Double) {
        // Stale-device guard: the outgoing device's stop-solicited clock reset (Chromecast reports 0 on
        // stop) must not overwrite the new connection's resume position — a failed connect recovers the
        // local player from remoteTime, which would land at 0:00 instead of where the user left off.
        if (handler.connectedDevice !== device) return
        // Keep the last real progress report: a 0 here can be the receiver resetting its clock right
        // before the end-of-track IDLE (Chromecast does this), and the IDLE end detector needs the
        // position from just before that reset — see CastAutoAdvance.endEdgePositionSec.
        if (time > 0.0) handler.lastProgressSec = time
        handler.remoteTime.value = time
        handler.remoteTimeUpdatedAt = System.currentTimeMillis()
    }
    override fun volumeChanged(volume: Double) {
        // Same stale-device guard as connectionStateChanged: a late report from a device we've already
        // replaced must not overwrite the new connection's freshly-reset volume tracking.
        if (handler.connectedDevice !== device) return
        handler.volumeTracker.onReceiverReport(volume)
    }
    override fun durationChanged(duration: Double) {
        // Stale-device guard, as in timeChanged.
        if (handler.connectedDevice !== device) return
        handler.remoteDuration.value = duration
    }
    override fun speedChanged(speed: Double) {}
    override fun sourceChanged(source: Source) {}
    override fun keyEvent(event: KeyEvent) {}
    override fun mediaEvent(event: MediaEvent) {
        // Stale-device guard: a replaced device ending its (stopped) track must not auto-advance the queue.
        if (handler.connectedDevice !== device) return
        if (event.type == MediaItemEventType.END) onTrackEnded?.invoke()
    }
    override fun playbackError(message: String) {
        reportException(IllegalStateException("FCast playback error: $message"))
        // Same stale-device guard as the other callbacks: an error from a replaced device must not
        // trigger recovery against the new connection's load.
        if (handler.connectedDevice !== device) return
        onPlaybackError?.invoke(message)
    }
}

class FCastDiscoveryHandler : DeviceDiscovererEventHandler {
    // Lazy so merely constructing the handler (a MusicService field) loads no native code — the FCast
    // lib isn't bundled; it's downloaded on demand. First touched in connectTo(), after the lib is ready.
    val castContext by lazy { CastContext() }

    // Discovery callbacks (deviceAvailable/Changed/Removed) arrive on the SDK's NSD threads, which are
    // not contractually serialised, so every mutate-then-snapshot of this map is guarded by [devicesLock].
    private val devicesLock = Any()
    val discoveredDevices = mutableMapOf<String, DeviceInfo>()

    // Written from SDK callback threads (connectionStateChanged / deviceRemoved), read on the main thread —
    // @Volatile to publish the write across threads (else the main thread routes transport to a stale device).
    @Volatile var connectedDevice: CastingDevice? = null
    @Volatile var onDisconnect: ((Long) -> Unit)? = null

    // The single source of truth for "route transport to the receiver?" — true only once the device has
    // actually reported Connected, not merely from connectTo() assigning connectedDevice synchronously
    // (that early assignment exists only so the stale-disconnect guard can recognise the new device).
    // Every transport-routing site (CastAwarePlayer, MusicService.onStartCommand, PlayerConnection.isCasting)
    // gates on this so they can never disagree about whether a play/pause/seek goes local vs remote.
    val isConnected: Boolean get() = remoteConnectionState.value is DeviceConnectionState.Connected

    // True while a full-screen local video screen is driving the phone's own audio (the receiver is
    // paused underneath it). Read by the hardware-volume-key routing so those keys adjust the local
    // video rather than the muted receiver — see CastVolumeKeys.decide. Written from the UI (main
    // thread), read on the key-dispatch (main) thread; @Volatile for safe publication regardless.
    @Volatile var videoPlaybackActive: Boolean = false

    // Tracking current playback intent and content for reconnections.
    @Volatile var shouldPlay: Boolean = true
    @Volatile var currentStreamUrl: String? = null
    @Volatile var currentContentType: String? = null
    @Volatile var currentMetadata: Metadata? = null
    @Volatile var initialResumePosition: Double = 0.0

    val remotePlaybackState = MutableStateFlow<PlaybackState?>(null)
    val remoteTime = MutableStateFlow(0.0)
    val remoteDuration = MutableStateFlow(0.0)
    val remoteConnectionState = MutableStateFlow<DeviceConnectionState>(DeviceConnectionState.Disconnected)

    // Receiver-volume tracking — see RemoteVolumeTracker for the unknown-until-reported stepping rule.
    val volumeTracker = RemoteVolumeTracker()
    val remoteVolume: StateFlow<Double> get() = volumeTracker.volume

    // Wall-clock ms when the receiver last reported its position (timeChanged). FCast receivers report the
    // clock coarsely (~1 Hz, and sometimes stop a few seconds before the end), so interpolatedRemoteTimeSec()
    // extrapolates between reports — smoothing the seek bar and letting the stall detector reach the end.
    @Volatile var remoteTimeUpdatedAt: Long = System.currentTimeMillis()

    // The receiver's last REAL (> 0) position report (sec). Chromecast-protocol receivers reset the
    // reported clock to 0 immediately before the end-of-track IDLE, so remoteTime already reads 0 on
    // the IDLE edge and the end detector falls back to this (CastAutoAdvance.endEdgePositionSec).
    // Every content (re)load resets it alongside remoteTime so it can never carry a previous track's
    // near-end position into a fresh one.
    @Volatile var lastProgressSec: Double = 0.0

    val discoveredDevicesFlow = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val connectedDeviceFlow = MutableStateFlow<CastingDevice?>(null)

    /**
     * The remote position (seconds) smoothed between the receiver's periodic reports: while it is PLAYING,
     * extrapolate the last report by the elapsed wall-clock (capped at the duration); otherwise return the
     * last reported value. Used by the seek bar and the stall-based end detector so a coarse or briefly
     * stalled remote clock reads neither choppy nor stuck short of the end.
     */
    fun interpolatedRemoteTimeSec(): Double {
        val base = remoteTime.value
        val dur = remoteDuration.value
        // No extrapolation without a known duration (a just-loaded track) or while not playing — return the
        // last reported value so the bar doesn't creep up from 0 before the new track's clock has arrived.
        if (dur <= 0.0 || !CastPlayback.isPlaying(remotePlaybackState.value)) return base
        val elapsedSec = (System.currentTimeMillis() - remoteTimeUpdatedAt).coerceAtLeast(0L) / 1000.0
        return (base + elapsedSec).coerceAtMost(dur)
    }

    /**
     * Issues a connect to [deviceInfo]. Returns false when the attempt could not even be started
     * (device creation or the connect call threw — e.g. the SDK's MissingAddresses); the async outcome
     * of a started attempt — Connected or Disconnected — lands on [remoteConnectionState], which is
     * preset to Connecting here so callers can await the transition (CastConnect.awaitOutcome) without
     * mistaking the previous session's Disconnected for this attempt failing.
     */
    fun connectTo(
        deviceInfo: DeviceInfo,
        streamUrl: String? = null,
        contentType: String? = null,
        metadata: Metadata? = null,
        resumePosition: Double = 0.0,
        onTrackEnded: (() -> Unit)? = null,
        onPlaybackError: ((String) -> Unit)? = null,
    ): Boolean {
        // Silence the outgoing receiver before dropping its socket (same order as disconnect()): a bare
        // disconnect leaves its loaded stream playing — the relay keeps serving it — so switching devices
        // would otherwise leave the old and new receivers playing simultaneously.
        connectedDevice?.let { d ->
            castCall { d.stopPlayback() }
            castCall { d.disconnect() }
        }

        // Reset tracking state for the new connection. The clock starts at the resume position (like
        // load()) so a failed attempt recovers the local player to where the user left off, not to 0.
        shouldPlay = true
        currentStreamUrl = streamUrl
        currentContentType = contentType
        currentMetadata = metadata
        initialResumePosition = resumePosition
        remoteTime.value = resumePosition
        remoteDuration.value = 0.0
        remoteTimeUpdatedAt = System.currentTimeMillis()
        lastProgressSec = resumePosition
        volumeTracker.reset()

        // Assign the @Volatile field synchronously (before the old device's async Disconnected lands) so
        // the connectionStateChanged guard recognises it; connectedDeviceFlow publishes only from
        // Connected — see isConnected — so the UI never shows "connected" while transport still routes local.
        val newDevice = runCatching { castContext.createDeviceFromInfo(deviceInfo) }
            .onFailure { reportException(it, "FCast createDeviceFromInfo") }
            .getOrNull()
        if (newDevice == null) {
            // Tear down through the single path so a previously connected device's UI state is cleared too.
            onConnectionDisconnected()
            return false
        }
        connectedDevice = newDevice
        remoteConnectionState.value = DeviceConnectionState.Connecting
        val issued = runCatching { newDevice.connect(null, DevEventHandler(this, newDevice, onTrackEnded, onPlaybackError), 1000u) }
            .onFailure { reportException(it, "FCast connect") }
            .isSuccess
        if (!issued) onConnectionDisconnected()
        return issued
    }

    fun load(streamUrl: String, contentType: String, metadata: Metadata? = null, resumePosition: Double = 0.0) {
        currentStreamUrl = streamUrl
        currentContentType = contentType
        currentMetadata = metadata
        initialResumePosition = resumePosition
        // Reset the remote clock for the new track so the seek bar / stall detector don't briefly read the
        // previous track's near-end position+duration until the receiver reports the new ones.
        remoteTime.value = resumePosition
        remoteDuration.value = 0.0
        remoteTimeUpdatedAt = System.currentTimeMillis()
        lastProgressSec = resumePosition
        connectedDevice?.let { d ->
            castCall { d.load(urlLoadRequest(streamUrl, contentType, resumePosition, metadata)) }
            // The receiver auto-plays a freshly loaded item; honour the user's play intent and re-pause when
            // shouldPlay is false (a skip while the cast was paused), mirroring pause-on-reconnect.
            if (!shouldPlay) castCall { d.pausePlayback() }
        }
    }

    fun onConnectionDisconnected() {
        val lastPos = CastPlayback.remoteSecondsToMs(remoteTime.value)
        connectedDevice = null
        connectedDeviceFlow.value = null
        remotePlaybackState.value = null
        remoteConnectionState.value = DeviceConnectionState.Disconnected
        lastProgressSec = 0.0
        onDisconnect?.invoke(lastPos)
    }

    fun disconnect() {
        connectedDevice?.let { d ->
            castCall { d.stopPlayback() }
            castCall { d.disconnect() }
        }
        onConnectionDisconnected()
    }

    /**
     * Whether the receiver is playing, for toggle decisions — see [CastPlayback.isRemotePlaying]:
     * the reported state once known, else the play intent, matching what the UI's isPlaying displays.
     */
    fun isRemotePlaying(): Boolean =
        CastPlayback.isRemotePlaying(remotePlaybackState.value, shouldPlay)

    fun play() {
        shouldPlay = true
        castCall { connectedDevice?.resumePlayback() }
    }

    fun pause() {
        shouldPlay = false
        castCall { connectedDevice?.pausePlayback() }
    }

    fun seek(position: Double) {
        castCall { connectedDevice?.seek(position) }
    }

    /**
     * Sets the receiver's volume, clamped to [0.0, 1.0]. Updates the tracked volume immediately
     * (rather than waiting on the receiver's own volumeChanged echo) so the player-menu slider tracks
     * the drag without a round-trip lag.
     */
    fun setVolume(volume: Double) {
        val v = volumeTracker.setAbsolute(volume)
        castCall { connectedDevice?.changeVolume(v) }
    }

    /**
     * Steps the receiver's volume by one hardware-key press in [direction] (+1 up / -1 down). Inert
     * until the receiver's actual level is known (its first volumeChanged report, or a slider set) —
     * the caller still consumes the key, but the placeholder level is never stepped-and-sent.
     */
    fun adjustVolume(direction: Int) {
        val v = volumeTracker.step(direction) ?: return
        castCall { connectedDevice?.changeVolume(v) }
    }

    // deviceAvailable and deviceChanged both upsert the device and republish the list (under devicesLock —
    // these arrive on the SDK's not-contractually-serialised NSD threads).
    private fun upsertDevice(deviceInfo: DeviceInfo) {
        discoveredDevicesFlow.value = synchronized(devicesLock) {
            discoveredDevices[deviceInfo.name] = deviceInfo
            discoveredDevices.values.toList()
        }
    }

    override fun deviceAvailable(deviceInfo: DeviceInfo) = upsertDevice(deviceInfo)

    override fun deviceChanged(deviceInfo: DeviceInfo) = upsertDevice(deviceInfo)

    /**
     * Apply a refresh burst's findings ([CastDeviceRefresher]) over the discovery map: fresh
     * addresses win, entries absent from an authoritative burst are pruned, live entries keep their
     * instance. Merge semantics live in [CastDeviceCatalog.merge]; this only holds the lock.
     */
    fun applyRefreshedDevices(fresh: List<DeviceInfo>, authoritativeProtocols: Set<ProtocolType>) {
        discoveredDevicesFlow.value = synchronized(devicesLock) {
            val merged = CastDeviceCatalog.merge(discoveredDevices.toMap(), fresh, authoritativeProtocols)
            discoveredDevices.clear()
            discoveredDevices.putAll(merged)
            discoveredDevices.values.toList()
        }
    }

    /**
     * Drop a device the app just failed to connect to. A failed/timed-out connect is the definitive
     * "this entry is unreachable right now" signal — stronger than anything discovery offers: a
     * force-closed receiver sends no mDNS goodbye, so its advertisement lingers in caches, the refresh
     * burst still "finds" it, and its failed resolve deliberately blocks pruning (non-authoritative).
     * If the device is actually alive, the SDK's events or the next refresh re-add it within seconds.
     */
    fun pruneDevice(deviceName: String) = deviceRemoved(deviceName)

    override fun deviceRemoved(deviceName: String) {
        // Only drop it from the picker list. Do NOT disconnect an active session here: NSD "Service lost"
        // events flap transiently (a routine re-resolve drops then re-finds the service), and the cast
        // connection is a separate TCP socket with its own heartbeat — the SDK reports a genuine drop via
        // connectionStateChanged(Disconnected). Disconnecting on a transient discovery loss dropped active
        // casts mid-use (e.g. while skipping), leaving the phone paused on the next track.
        discoveredDevicesFlow.value = synchronized(devicesLock) {
            discoveredDevices.remove(deviceName)
            discoveredDevices.values.toList()
        }
    }
}
