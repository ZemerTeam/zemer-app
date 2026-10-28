package com.jtech.zemer.utils

import android.util.Log
import timber.log.Timber

/**
 * Forwards every Timber log (DEBUG and above) to the crash reporter as a breadcrumb,
 * so crash and non-fatal reports carry the recent log trail (stream resolution,
 * cipher, poToken, …). Throwables logged at ERROR are additionally recorded as
 * non-fatal issues.
 *
 * The reporter calls are injected so the tree stays unit-testable on the JVM.
 */
class CrashReportingTree(
    private val logBreadcrumb: (String) -> Unit,
    private val recordNonFatal: (Throwable) -> Unit,
) : Timber.Tree() {

    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.DEBUG

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val level = when (priority) {
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "A"
            else -> "V"
        }
        logBreadcrumb(if (tag != null) "$level/$tag: $message" else "$level: $message")
        if (t != null && priority >= Log.ERROR) {
            recordNonFatal(t)
        }
    }
}
