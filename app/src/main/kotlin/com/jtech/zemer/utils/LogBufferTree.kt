package com.jtech.zemer.utils

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import java.util.ArrayDeque

/**
 * In-memory ring buffer Timber tree. Keeps the last [MAX_ENTRIES] log entries so the
 * Log viewer screen can show them without touching logcat. Planted in [com.jtech.zemer.App]
 * alongside the Crashlytics tree so every Timber call also lands here for live inspection.
 *
 * Thread-safe: the buffer is guarded by a synchronized lock. [revision] bumps on every
 * mutation so observers re-read [entries] only when the buffer actually changed, keeping
 * the per-log cost a single append (no copy, no flow emission of the list itself).
 */
object LogBufferTree : Timber.Tree() {
    private const val MAX_ENTRIES = 500
    private val buffer = ArrayDeque<LogEntry>(MAX_ENTRIES)
    private val _revision = MutableStateFlow(0L)

    /** Bumped on every [log]/[clear]; collect it and re-read [entries] on change. */
    val revision: StateFlow<Long> get() = _revision

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
        _revision.value++
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        synchronized(buffer) {
            buffer.addLast(LogEntry(System.currentTimeMillis(), priority, tag, message, t))
            while (buffer.size > MAX_ENTRIES) {
                buffer.removeFirst()
            }
        }
        _revision.value++
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
