package com.jtech.zemer.crash

import android.content.Context
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.jtech.zemer.BuildConfig
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Crash and non-fatal reporting to Zemer's own server (contract: handoff-docs/zemer-app-crash-reporting-request.md).
 *
 * - A fatal crash is written to the on-disk queue in the uncaught-exception handler and sent there and then, on a
 *   separate thread for at most [FATAL_SEND_WAIT_MS], so even a crash loop at startup gets through; whatever does
 *   not go out is sent on the next launch.
 * - Non-fatals are counted per launch: the first of each kind (by [CrashReport.fingerprint]) is held for
 *   [NON_FATAL_FLUSH_MS] collecting repeats into its `count`, then queued and sent; later repeats in the same launch
 *   are dropped, and at most [MAX_NON_FATALS_PER_LAUNCH] kinds are reported.
 * - Breadcrumbs: the recent log trail, attached (scrubbed) to every report.
 *
 * Reporting never affects the app: every step is guarded and failures are dropped.
 */
object CrashReporter {
    private const val MAX_NON_FATALS_PER_LAUNCH = 20
    private const val NON_FATAL_FLUSH_MS = 10_000L
    private const val FATAL_SEND_WAIT_MS = 3_000L
    private const val STARTUP_SEND_DELAY_MS = 5_000L

    private class Pending(val throwable: Throwable, val thread: String, val occurredAt: Long, val breadcrumbs: List<String>)

    private val lock = Any()
    private val breadcrumbs = ArrayDeque<String>()
    private val nonFatals = PerLaunchDedup<Pending>(MAX_NON_FATALS_PER_LAUNCH)
    private var flushTask: ScheduledFuture<*>? = null

    private val executor = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "zemer-crash").apply { isDaemon = true } }

    @Volatile
    private var queue: CrashQueue? = null

    @Volatile
    private var uploader: CrashUploader? = null

    fun install(context: Context) {
        val q = CrashQueue(File(context.filesDir, "crash_reports"))
        queue = q
        uploader = CrashUploader(q)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { onFatal(thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
        executor.schedule({ sendQueued() }, STARTUP_SEND_DELAY_MS, TimeUnit.MILLISECONDS)
    }

    fun breadcrumb(line: String) = synchronized(lock) {
        breadcrumbs.addLast(line)
        while (breadcrumbs.size > CrashReport.MAX_BREADCRUMBS) breadcrumbs.removeFirst()
    }

    fun nonFatal(throwable: Throwable) = runCatching {
        if (CrashReport.isCancellation(throwable)) return@runCatching // normal coroutine cancellation, not an error
        val key = CrashReport.fingerprint(throwable)
        synchronized(lock) {
            val isNew = nonFatals.offer(key) {
                Pending(throwable, Thread.currentThread().name, System.currentTimeMillis(), breadcrumbs.toList())
            }
            if (isNew && flushTask == null) {
                flushTask = executor.schedule({ flush(); sendQueued() }, NON_FATAL_FLUSH_MS, TimeUnit.MILLISECONDS)
            }
        }
    }

    private fun onFatal(thread: Thread, throwable: Throwable) {
        val q = queue ?: return
        flush()
        q.add(report("fatal", throwable, thread.name, System.currentTimeMillis(), 1, synchronized(lock) { breadcrumbs.toList() }))
        // a new thread (network is not allowed on the main thread), bounded so the crash is not held up
        Thread { sendQueued() }.apply { isDaemon = true; start() }.join(FATAL_SEND_WAIT_MS)
    }

    /** Queues the held non-fatals with their counts. */
    private fun flush() {
        val q = queue ?: return
        val batch = synchronized(lock) {
            flushTask = null
            nonFatals.drain()
        }
        for ((p, count) in batch) {
            runCatching { q.add(report("nonfatal", p.throwable, p.thread, p.occurredAt, count, p.breadcrumbs)) }
        }
    }

    private val sendLock = Any()

    private fun sendQueued() {
        val u = uploader ?: return
        // one sender at a time, so a report is never sent twice
        synchronized(sendLock) { runCatching { runBlocking { u.sendAll() } } }
    }

    private fun report(kind: String, t: Throwable, thread: String, at: Long, count: Int, crumbs: List<String>) =
        CrashReport.build(kind, t, thread, at, count, crumbs, environment(), secrets())

    private fun secrets(): List<String> = listOfNotNull(runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull())

    private fun environment() = CrashEnvironment(
        versionCode = BuildConfig.VERSION_CODE,
        versionName = BuildConfig.VERSION_NAME,
        commit = BuildConfig.COMMIT_HASH,
        runNumber = BuildConfig.RUN_NUMBER,
        buildType = BuildConfig.BUILD_TYPE,
        sdk = Build.VERSION.SDK_INT,
        manufacturer = Build.MANUFACTURER.orEmpty(),
        model = Build.MODEL.orEmpty(),
        abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
        debug = BuildConfig.DEBUG,
    )
}
