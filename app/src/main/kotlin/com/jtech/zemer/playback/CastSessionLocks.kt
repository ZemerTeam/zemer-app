package com.jtech.zemer.playback

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager

/**
 * Wi-Fi and CPU locks held while the phone relays a cast stream ([CastStreamRelay]). Casting is
 * exactly the screen-off, app-backgrounded state where Wi-Fi power-save and CPU idle would throttle
 * or stall the relay and starve the receiver mid-track.
 *
 * WIFI_MODE_FULL_HIGH_PERF is deprecated in favour of WIFI_MODE_FULL_LOW_LATENCY, but LOW_LATENCY is
 * documented to be effective only while the acquiring app is in the foreground with the screen ON —
 * the opposite of a cast session — so the deprecated high-perf mode is the correct choice here.
 */
class CastSessionLocks(context: Context) {
    private val appContext = context.applicationContext

    @Suppress("DEPRECATION")
    private val wifiLock by lazy {
        (appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "zemer:castRelay")
            .apply { setReferenceCounted(false) }
    }

    private val wakeLock by lazy {
        (appContext.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "zemer:castRelay")
            .apply { setReferenceCounted(false) }
    }

    /**
     * Idempotent. No timeout by design: a cast session has no fixed length, and every session end —
     * disconnect, device switch grace, service destroy — funnels through [release].
     */
    @SuppressLint("WakelockTimeout")
    fun acquire() {
        if (!wifiLock.isHeld) wifiLock.acquire()
        if (!wakeLock.isHeld) wakeLock.acquire()
    }

    /** Idempotent; safe when never acquired. */
    fun release() {
        if (wifiLock.isHeld) wifiLock.release()
        if (wakeLock.isHeld) wakeLock.release()
    }
}
