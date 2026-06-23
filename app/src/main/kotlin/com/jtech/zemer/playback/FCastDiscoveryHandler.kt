package com.jtech.zemer.playback

import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.utils.reportException
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val onTrackEnded: (() -> Unit)? = null
) : DeviceEventHandler {
    private var wasConnected = false

    override fun connectionStateChanged(state: DeviceConnectionState) {
        handler.remoteConnectionState.value = state
        if (state is DeviceConnectionState.Connected) {
            val isReconnect = wasConnected
            wasConnected = true
            handler.connectedDevice = device
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
            // Ignore a disconnect from a device we've already replaced (connectTo to a new device) or
            // already tore down (disconnect() ran onConnectionDisconnected synchronously), so a stale
            // async callback can't null out the new connection or fire onDisconnect twice.
            if (handler.connectedDevice === device) {
                handler.onConnectionDisconnected()
            }
        }
    }

    override fun playbackStateChanged(state: PlaybackState) {
        handler.remotePlaybackState.value = state
        // Mirror the receiver's own state into our play intent instead of overriding it, so a
        // pause/resume from the TV's remote sticks. Pause-on-reconnect is enforced in
        // connectionStateChanged, not here.
        CastPlayback.playIntentForState(state)?.let { handler.shouldPlay = it }
    }

    override fun timeChanged(time: Double) { handler.remoteTime.value = time }
    override fun volumeChanged(volume: Double) {}
    override fun durationChanged(duration: Double) { handler.remoteDuration.value = duration }
    override fun speedChanged(speed: Double) {}
    override fun sourceChanged(source: Source) {}
    override fun keyEvent(event: KeyEvent) {}
    override fun mediaEvent(event: MediaEvent) {
        if (event.type == MediaItemEventType.END) onTrackEnded?.invoke()
    }
    override fun playbackError(message: String) {
        reportException(IllegalStateException("FCast playback error: $message"))
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

    // These are written from SDK callback threads (connectionStateChanged / deviceRemoved) and read on
    // the main thread (CastAwarePlayer, MusicService.onStartCommand), so they are @Volatile to publish
    // the write across threads — without it the main thread can read a stale connectedDevice and route
    // transport to a device that just disconnected.
    @Volatile var connectedDevice: CastingDevice? = null
    @Volatile var onDisconnect: ((Long) -> Unit)? = null

    // The single source of truth for "route transport to the receiver?" — true only once the device has
    // actually reported Connected, not merely from connectTo() assigning connectedDevice synchronously
    // (that early assignment exists only so the stale-disconnect guard can recognise the new device).
    // Every transport-routing site (CastAwarePlayer, MusicService.onStartCommand, PlayerConnection.isCasting)
    // gates on this so they can never disagree about whether a play/pause/seek goes local vs remote.
    val isConnected: Boolean get() = remoteConnectionState.value is DeviceConnectionState.Connected

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

    val discoveredDevicesFlow = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val connectedDeviceFlow = MutableStateFlow<CastingDevice?>(null)

    fun connectTo(
        deviceInfo: DeviceInfo,
        streamUrl: String? = null,
        contentType: String? = null,
        metadata: Metadata? = null,
        resumePosition: Double = 0.0,
        onTrackEnded: (() -> Unit)? = null
    ) {
        castCall { connectedDevice?.disconnect() }

        // Reset tracking state for the new connection.
        shouldPlay = true
        currentStreamUrl = streamUrl
        currentContentType = contentType
        currentMetadata = metadata
        initialResumePosition = resumePosition
        remoteTime.value = 0.0
        remoteDuration.value = 0.0

        // Publish the new device synchronously (before the old device's async Disconnected can land)
        // so the connectionStateChanged guard recognises it as current.
        val newDevice = castContext.createDeviceFromInfo(deviceInfo)
        connectedDevice = newDevice
        connectedDeviceFlow.value = newDevice
        castCall { newDevice.connect(null, DevEventHandler(this, newDevice, onTrackEnded), 1000u) }
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
        connectedDevice?.let { d ->
            castCall { d.load(urlLoadRequest(streamUrl, contentType, resumePosition, metadata)) }
            // The receiver auto-plays a freshly loaded item. Preserve the user's play intent instead of
            // forcing playback: an explicit play action (connect, tap a song, auto-advance while playing)
            // has already set shouldPlay=true, whereas a skip while the cast is paused leaves it false —
            // so honour it and re-pause, mirroring the pause-on-reconnect enforcement.
            if (!shouldPlay) castCall { d.pausePlayback() }
        }
    }

    fun onConnectionDisconnected() {
        val lastPos = CastPlayback.remoteSecondsToMs(remoteTime.value)
        connectedDevice = null
        connectedDeviceFlow.value = null
        remotePlaybackState.value = null
        remoteConnectionState.value = DeviceConnectionState.Disconnected
        onDisconnect?.invoke(lastPos)
    }

    fun disconnect() {
        connectedDevice?.let { d ->
            castCall { d.stopPlayback() }
            castCall { d.disconnect() }
        }
        onConnectionDisconnected()
    }

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

    override fun deviceAvailable(deviceInfo: DeviceInfo) {
        discoveredDevicesFlow.value = synchronized(devicesLock) {
            discoveredDevices[deviceInfo.name] = deviceInfo
            discoveredDevices.values.toList()
        }
    }

    override fun deviceChanged(deviceInfo: DeviceInfo) {
        discoveredDevicesFlow.value = synchronized(devicesLock) {
            discoveredDevices[deviceInfo.name] = deviceInfo
            discoveredDevices.values.toList()
        }
    }

    override fun deviceRemoved(deviceName: String) {
        discoveredDevicesFlow.value = synchronized(devicesLock) {
            discoveredDevices.remove(deviceName)
            discoveredDevices.values.toList()
        }
        if (connectedDevice?.name() == deviceName) {
            disconnect()
        }
    }
}
