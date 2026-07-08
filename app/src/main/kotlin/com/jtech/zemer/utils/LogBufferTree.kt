package com.jtech.zemer.utils

import android.util.Log
import timber.log.Timber
import java.util.LinkedList

/**
 * In-memory ring buffer Timber tree. Keeps the last [MAX_ENTRIES] log entries so the
 * Log viewer screen can show them without touching logcat. Planted in [com.jtech.zemer.App]
 * alongside the Crashlytics tree so every Timber call also lands here for live inspection.
 *
 * Thread-safe: the buffer is guarded by a synchronized lock.
 */
object LogBufferTree : Timber.Tree() {
    private const val MAX_ENTRIES = 500
    private val buffer = LinkedList<LogEntry>()

    data class LogEntry(
        val timestamp: Long,
        val priority: Int,
        val tag: String?,
        val message: String,
        val throwable: Throwable?,
    )

    val entries: List<LogEntry>
        get() = synchronized(buffer) { buffer.toList() }

    fun clear() {
        synchronized(buffer) { buffer.clear() }
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        synchronized(buffer) {
            buffer.addLast(LogEntry(System.currentTimeMillis(), priority, tag, message, t))
            while (buffer.size > MAX_ENTRIES) {
                buffer.removeFirst()
            }
        }
    }

    fun priorityName(priority: Int): String = when (priority) {
        Log.VERBOSE -> "V"
        Log.DEBUG -> "D"
        Log.INFO -> "I"
        Log.WARN -> "W"
        Log.ERROR -> "E"
        Log.ASSERT -> "A"
        else -> "?"
    }
}
