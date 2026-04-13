package com.jtech.zemer.playback

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import org.fcast.sender_sdk.*

class DevEventHandler(
    private val handler: FCastDiscoveryHandler,
    val device: CastingDevice,
    private val streamUrl: String?,
    private val contentType: String?,
    private val metadata: Metadata?,
    private val resumePosition: Double = 0.0,
    private val onTrackEnded: (() -> Unit)? = null
) : DeviceEventHandler {
    override fun connectionStateChanged(state: DeviceConnectionState) {
        handler.remoteConnectionState.value = state
        if (state is DeviceConnectionState.Connected) {
            if (streamUrl != null && contentType != null) {
                Log.d("FCast", "Attempting to load URL: $streamUrl with type: $contentType")
                device.load(
                    LoadRequest.Url(
                        url = streamUrl,
                        contentType = contentType,
                        resumePosition = resumePosition,
                        speed = null,
                        volume = null,
                        metadata = metadata,
                        requestHeaders = null
                    )
                )
            }
        } else if (state is DeviceConnectionState.Disconnected) {
            handler.onConnectionDisconnected()
        }
    }

    override fun playbackStateChanged(state: PlaybackState) {
        Log.d("FCast", "Playback state: $state")
        handler.remotePlaybackState.value = state
    }

    override fun timeChanged(time: Double) {
        handler.remoteTime.value = time
    }

    override fun volumeChanged(volume: Double) {
        handler.remoteVolume.value = volume
    }

    override fun durationChanged(duration: Double) {
        handler.remoteDuration.value = duration
    }

    override fun speedChanged(speed: Double) {}
    override fun sourceChanged(source: Source) {}
    override fun keyEvent(event: KeyEvent) {}
    override fun mediaEvent(event: MediaEvent) {
        if (event.type == MediaItemEventType.END) {
            onTrackEnded?.invoke()
        }
    }
    override fun playbackError(message: String) {
        Log.e("FCast", "Playback error: $message")
    }
}

class FCastDiscoveryHandler : DeviceDiscovererEventHandler {
    val castContext = CastContext()
    val discoveredDevices = mutableMapOf<String, DeviceInfo>()
    var connectedDevice: CastingDevice? = null
    var onDisconnect: ((Long) -> Unit)? = null

    // We initialize with a safe default if possible, or make it nullable
    val remotePlaybackState = MutableStateFlow<PlaybackState?>(null)
    val remoteTime = MutableStateFlow(0.0)
    val remoteDuration = MutableStateFlow(0.0)
    val remoteVolume = MutableStateFlow(1.0)
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
        connectedDevice?.disconnect()
        val newDevice = castContext.createDeviceFromInfo(deviceInfo)
        newDevice.connect(null, DevEventHandler(this, newDevice, streamUrl, contentType, metadata, resumePosition, onTrackEnded), 1000u)
        connectedDevice = newDevice
        connectedDeviceFlow.value = newDevice
    }

    fun load(streamUrl: String, contentType: String, metadata: Metadata? = null, resumePosition: Double = 0.0) {
        connectedDevice?.load(
            LoadRequest.Url(
                url = streamUrl,
                contentType = contentType,
                resumePosition = resumePosition,
                speed = null,
                volume = null,
                metadata = metadata,
                requestHeaders = null
            )
        )
    }

    fun onConnectionDisconnected() {
        val lastPos = (remoteTime.value * 1000).toLong()
        connectedDevice = null
        connectedDeviceFlow.value = null
        remotePlaybackState.value = null
        remoteConnectionState.value = DeviceConnectionState.Disconnected
        onDisconnect?.invoke(lastPos)
    }

    fun disconnect() {
        connectedDevice?.disconnect()
        onConnectionDisconnected()
    }

    fun play() {
        connectedDevice?.resumePlayback()
    }

    fun pause() {
        connectedDevice?.pausePlayback()
    }

    fun seek(position: Double) {
        connectedDevice?.seek(position)
    }

    fun stop() {
        connectedDevice?.stopPlayback()
    }

    fun setVolume(volume: Double) {
        connectedDevice?.changeVolume(volume)
    }

    override fun deviceAvailable(deviceInfo: DeviceInfo) {
        discoveredDevices[deviceInfo.name] = deviceInfo
        discoveredDevicesFlow.value = discoveredDevices.values.toList()
    }

    override fun deviceChanged(deviceInfo: DeviceInfo) {
        discoveredDevices[deviceInfo.name] = deviceInfo
        discoveredDevicesFlow.value = discoveredDevices.values.toList()
    }

    override fun deviceRemoved(deviceName: String) {
        discoveredDevices.remove(deviceName)
        discoveredDevicesFlow.value = discoveredDevices.values.toList()
        // Identification is usually done by name in fcast.
        if (connectedDevice?.name() == deviceName) {
            disconnect()
        }
    }
}
