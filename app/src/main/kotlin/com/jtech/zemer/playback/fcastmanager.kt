package com.jtech.zemer.playback

import android.content.Context
import android.util.Log
import org.fcast.sender_sdk.*

// Handles events from a connected casting device
class DevEventHandler(
    val device: CastingDevice
) : DeviceEventHandler {

    override fun connectionStateChanged(state: DeviceConnectionState) {
        Log.d("FCast", "Connection state: $state")
        if (state is DeviceConnectionState.Connected) {
            // Device is ready — you can now call device.load(...) here
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

// Handles discovery of FCast-compatible devices on the local network
class FCastDiscoveryHandler : DeviceDiscovererEventHandler {
    val castContext = CastContext()
    val discoveredDevices = mutableMapOf<String, DeviceInfo>()
    var device: CastingDevice? = null


    override fun deviceAvailable(deviceInfo: DeviceInfo) { discoveredDevices[deviceInfo.name] = deviceInfo }
    override fun deviceChanged(deviceInfo: DeviceInfo) { discoveredDevices[deviceInfo.name] = deviceInfo }
    override fun deviceRemoved(deviceName: String) {
        device = null
    }
}
