package com.jtech.zemer.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Returns true if the app is allowed to post notifications.
 * On Android 13+ we need both the runtime permission and notifications enabled.
 */
fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    val granted =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
}

/**
 * Safely start a foreground service; returns false if system rejects it.
 */
inline fun <reified T> Context.tryStartForegroundService(intent: Intent = Intent(this, T::class.java)): Boolean =
    try {
        ContextCompat.startForegroundService(this, intent)
        true
    } catch (e: ForegroundServiceStartNotAllowedException) {
        false
    } catch (_: SecurityException) {
        false
    }
