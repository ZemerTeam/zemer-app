package com.jtech.zemer.utils.updater

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.jtech.zemer.utils.reportException

/**
 * Relaunches the app after a silent self-update (root / Shizuku).
 *
 * A silent install replaces our own package, so the OS kills this process as
 * part of the swap. We therefore schedule the relaunch through [AlarmManager]
 * with a [PendingIntent], which survives the kill, rather than trying to start
 * an activity from a process that is about to die.
 */
object AppRestarter {
    private const val RESTART_REQUEST_CODE = 0x2E57 // "ZE5T"
    private const val RESTART_DELAY_MS = 600L

    fun scheduleRestart(context: Context) {
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) }
            ?: return

        runCatching {
            val pendingIntent = PendingIntent.getActivity(
                context,
                RESTART_REQUEST_CODE,
                launchIntent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            // Inexact is fine — we only need the app back shortly after the swap.
            alarmManager.set(
                AlarmManager.RTC,
                System.currentTimeMillis() + RESTART_DELAY_MS,
                pendingIntent,
            )
        }.onFailure {
            reportException(it, "Schedule app restart after update")
        }
    }
}
