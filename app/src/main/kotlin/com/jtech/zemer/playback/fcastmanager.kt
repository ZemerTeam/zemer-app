package com.jtech.zemer.playback

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import org.fcast.sender_sdk.*

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
            val metadata = handler.currentMetadata
            
            if (url != null && type != null) {
                Log.d("FCast", "Attempting to load URL: $url (isReconnect: $isReconnect)")
                val pos = if (isReconnect && handler.remoteTime.value > 0) {
                    handler.remoteTime.value 
                } else {
                    handler.initialResumePosition
                }
                
                device.load(
                    LoadRequest.Url(
                        url = url,
                        contentType = type,
                        resumePosition = pos,
                        speed = null,
                        volume = null,
                        metadata = metadata,
                        requestHeaders = null
                    )
                )
                
                // If the user intended to stay paused, enforce it immediately after loading
                if (!handler.shouldPlay) {
                    device.pausePlayback()
                }
            }
        } else if (state is DeviceConnectionState.Disconnected) {
            handler.onConnectionDisconnected()
        }
    }

    override fun playbackStateChanged(state: PlaybackState) {
        Log.d("FCast", "Playback state: $state")
        handler.remotePlaybackState.value = state
        if (state == PlaybackState.PLAYING || state == PlaybackState.BUFFERING) {
            if (!handler.shouldPlay) {
                // If device auto-plays on reconnect but we should be paused, force pause
                device.pausePlayback()
            } else {
                handler.shouldPlay = true
            }
        } else if (state == PlaybackState.PAUSED) {
            if (handler.shouldPlay) {
                device.resumePlayback()
            } else {
                handler.shouldPlay = false
            }
        }
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
    
    // Tracking current playback intent and content for reconnections
    var shouldPlay: Boolean = true
    var currentStreamUrl: String? = null
    var currentContentType: String? = null
    var currentMetadata: Metadata? = null
    var initialResumePosition: Double = 0.0

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
        
        // Reset tracking state for new connection
        shouldPlay = true
        currentStreamUrl = streamUrl
        currentContentType = contentType
        currentMetadata = metadata
        initialResumePosition = resumePosition
        remoteTime.value = 0.0

        val newDevice = castContext.createDeviceFromInfo(deviceInfo)
        newDevice.connect(null, DevEventHandler(this, newDevice, onTrackEnded), 1000u)
        connectedDevice = newDevice
        connectedDeviceFlow.value = newDevice
    }

    fun load(streamUrl: String, contentType: String, metadata: Metadata? = null, resumePosition: Double = 0.0) {
        shouldPlay = true
        currentStreamUrl = streamUrl
        currentContentType = contentType
        currentMetadata = metadata
        initialResumePosition = resumePosition
        
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
        connectedDevice?.stopPlayback()
        connectedDevice?.disconnect()
        onConnectionDisconnected()
    }

    fun play() {
        shouldPlay = true
        connectedDevice?.resumePlayback()
    }

    fun pause() {
        shouldPlay = false
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
        if (connectedDevice?.name() == deviceName) {
            disconnect()
        }
    }
}
