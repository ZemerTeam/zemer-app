package com.jtech.zemer.playback

import android.util.Log
import org.fcast.sender_sdk.*

class DevEventHandler(
    val device: CastingDevice,
    private val streamUrl: String?,
    private val contentType: String?,
    private val resumePosition: Double = 0.0
) : DeviceEventHandler {
    override fun connectionStateChanged(state: DeviceConnectionState) {
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
                        metadata = null,
                        requestHeaders = null
                    )
                )
            }
        }
    }
    override fun playbackStateChanged(state: PlaybackState) {
        Log.d("FCast", "Playback state: $state")
    }
    override fun timeChanged(time: Double) {}
    override fun volumeChanged(volume: Double) {}
    override fun durationChanged(duration: Double) {}
    override fun speedChanged(speed: Double) {}
    override fun sourceChanged(source: Source) {}
    override fun keyEvent(event: KeyEvent) {}
    override fun mediaEvent(event: MediaEvent) {}
    override fun playbackError(message: String) {
        Log.e("FCast", "Playback error: $message")
    }
}

class FCastDiscoveryHandler : DeviceDiscovererEventHandler {
    val castContext = CastContext()
    val discoveredDevices = mutableMapOf<String, DeviceInfo>()
    var connectedDevice: CastingDevice? = null

    fun connectTo(deviceInfo: DeviceInfo, streamUrl: String? = null, contentType: String? = null, resumePosition: Double = 0.0) {
        connectedDevice?.disconnect()
        val newDevice = castContext.createDeviceFromInfo(deviceInfo)
        newDevice.connect(null, DevEventHandler(newDevice, streamUrl, contentType, resumePosition), 1000u)
        connectedDevice = newDevice
    }

    fun disconnect() {
        connectedDevice?.disconnect()
        connectedDevice = null
    }

    override fun deviceAvailable(deviceInfo: DeviceInfo) {
        discoveredDevices[deviceInfo.name] = deviceInfo
    }

    override fun deviceChanged(deviceInfo: DeviceInfo) {
        discoveredDevices[deviceInfo.name] = deviceInfo
    }

    override fun deviceRemoved(deviceName: String) {
        discoveredDevices.remove(deviceName)
        if (connectedDevice == null) return
        connectedDevice = null
    }
}
