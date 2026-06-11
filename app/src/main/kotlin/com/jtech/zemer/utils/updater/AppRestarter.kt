package com.jtech.zemer.utils.updater

import android.content.Context
import com.jtech.zemer.utils.reportException
import rikka.shizuku.Shizuku

/**
 * Relaunches the app after a silent self-update (root / Shizuku).
 *
 * The relaunch must run through the **privileged shell**, not an activity start from our own
 * (about-to-die) process: a silent install replaces our package and the OS kills us, and
 * starting an activity from the background — e.g. via an AlarmManager PendingIntent — is
 * blocked on Android 10+, so the app would never actually come back. `am start` issued as
 * root or as the Shizuku shell user is exempt from that restriction.
 */
object AppRestarter {

    /**
     * `am start` command that relaunches our launcher activity, or null if it can't be
     * resolved. No `sleep`: the caller decides whether a settle is needed. Root chains this
     * onto `pm install-commit` (with its own settle); Shizuku runs it from
     * [relaunchViaShizuku] immediately.
     */
    fun relaunchCommand(context: Context): String? {
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName) ?: return null
        val component = launchIntent.component?.flattenToShortString() ?: return null
        return "am start -n $component"
    }

    /**
     * Relaunch through Shizuku's privileged shell. Called from [InstallReceiver] on
     * STATUS_SUCCESS — the package is already installed, so `am start` resolves at once and
     * hands the launch to system_server before our process dies. It must NOT sleep first:
     * the Shizuku remote process is bound to our (dying) process and would be reaped during
     * the wait, before the launch fires — which is exactly why root works here and a sleeping
     * Shizuku command did not. `newProcess` is hidden in the Shizuku API, hence reflection;
     * failure is non-fatal (the user just reopens manually).
     */
    fun relaunchViaShizuku(context: Context) {
        val command = relaunchCommand(context) ?: return
        runCatching {
            val newProcess = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            ).apply { isAccessible = true }
            val process = newProcess.invoke(null, arrayOf("sh", "-c", command), null, null)
            process?.javaClass?.getMethod("waitFor")?.invoke(process)
        }.onFailure {
            reportException(it, "Relaunch via Shizuku after update")
        }
    }
}
