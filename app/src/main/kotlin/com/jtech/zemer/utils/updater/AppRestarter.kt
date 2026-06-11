package com.jtech.zemer.utils.updater

import android.content.Context

/**
 * Relaunches the app after a silent **root** self-update.
 *
 * The relaunch must run through the privileged (root) shell, not an activity start from our
 * own about-to-die process: a silent install replaces our package and the OS kills us, and
 * starting an activity from the background — e.g. via an AlarmManager PendingIntent — is
 * blocked on Android 10+, so the app would never come back. `am start` issued as root is
 * exempt. The root shell is an independent process, so `installRoot` chains this command
 * onto `pm install-commit` and it runs to completion after we are gone.
 *
 * Shizuku gets no auto-restart: its remote process is bound to ours and is reaped together
 * with us, so the launch can't be made to fire reliably — the user reopens manually.
 */
object AppRestarter {

    /**
     * `am start` command that relaunches our launcher activity, or null if it can't be
     * resolved. The caller adds any settle delay (root chains `&& sleep 1 && …` after commit).
     */
    fun relaunchCommand(context: Context): String? {
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName) ?: return null
        val component = launchIntent.component?.flattenToShortString() ?: return null
        return "am start -n $component"
    }
}
