package com.jtech.zemer.playback

import android.util.Log
import org.fcast.sender_sdk.*

class DevEventHandler(
    val device: CastingDevice
) : DeviceEventHandler {
    override fun connectionStateChanged(state: DeviceConnectionState) {
        Log.d("FCast", "Connection state: $state")
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

    fun connectTo(deviceInfo: DeviceInfo) {
        connectedDevice?.disconnect()
        val newDevice = castContext.createDeviceFromInfo(deviceInfo)
        newDevice.connect(null, DevEventHandler(newDevice), 1000u)
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