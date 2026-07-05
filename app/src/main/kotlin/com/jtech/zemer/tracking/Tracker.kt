package com.jtech.zemer.tracking

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.jtech.zemer.BuildConfig
import com.jtech.zemer.constants.TrackingDeviceIdKey
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.getSuspend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import timber.log.Timber
import java.io.File
import java.util.UUID

/**
 * Anonymous usage telemetry (the tracking spec: `docs/tracking/README.md`). The whole feature is
 * fire-and-forget by contract: nothing here may block UI, delay playback, or surface an error —
 * every entry point is a cheap `scope.launch` onto a single-threaded dispatcher, and every failure
 * is silent (a dropped event is fine; broken playback is not).
 *
 * Identity is one random UUID minted on first use and stored in DataStore — never any account,
 * device, or location identifier. The server 400s non-canonical device ids, so only
 * [UUID.randomUUID] output is ever sent.
 *
 * Flush triggers (spec §2): queue ≥ [FLUSH_THRESHOLD], 60 s with a non-empty queue, or the app
 * going to background. One in-flight upload at a time; failures back off via [trackingRetryDelayMs];
 * a 400 drops the batch; the queue caps at 500 dropping oldest ([TrackingQueue]).
 */
object Tracker {

    /** mediaId → play source registry, fed by the player service as queues are built. */
    val playSources = PlaySourceResolver()

    @Suppress("OPT_IN_USAGE")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val ready = CompletableDeferred<Unit>()

    private var queue: TrackingQueue? = null
    private var uploader = TrackingUploader()
    private var deviceId: String? = null
    private var appVer: String = BuildConfig.VERSION_NAME

    private var inFlight = false
    private var consecutiveFailures = 0
    private var pendingFlushJob: Job? = null

    /** Idempotent; called once from [com.jtech.zemer.App.onCreate]. */
    fun initialize(context: Context) {
        if (ready.isCompleted) return
        val appContext = context.applicationContext
        scope.launch {
            if (ready.isCompleted) return@launch
            queue = TrackingQueue(File(appContext.filesDir, QUEUE_FILE))
            deviceId = runCatching {
                val stored = appContext.dataStore.getSuspend(TrackingDeviceIdKey, "")
                if (isCanonicalUuid(stored)) {
                    stored
                } else {
                    UUID.randomUUID().toString().also { fresh ->
                        appContext.dataStore.edit { it[TrackingDeviceIdKey] = fresh }
                    }
                }
            }.getOrElse { UUID.randomUUID().toString() } // unpersisted fallback: still report this session
            ready.complete(Unit)
            // Anything queued from a previous run uploads on the first trigger of this one.
            if ((queue?.size ?: 0) > 0) scheduleFlush(TIMER_FLUSH_MS)
        }
    }

    fun open() = enqueue { TrackingEvents.open(now()) }

    fun search(q: String, results: Int) = enqueue { TrackingEvents.search(now(), q, results) }

    fun click(q: String, id: String, kind: String, rank: Int) =
        enqueue { TrackingEvents.click(now(), q, id, kind, rank) }

    fun play(videoId: String, secs: Int, dur: Int?, source: String) {
        val stream = streamInfo[videoId]
        enqueue { TrackingEvents.play(now(), videoId, secs, dur, source, stream?.first, stream?.second) }
    }

    /**
     * The player service reports which stream client (and, for deciphered web clients, which
     * player_ias hash) served a videoId, so the listen's `play` event can carry it. Bounded: cleared
     * once it outgrows any realistic queue so it can never accumulate across a long session.
     */
    fun onStreamResolved(videoId: String, client: String, playerHash: String?) {
        if (streamInfo.size > STREAM_INFO_MAX) streamInfo.clear()
        streamInfo[videoId] = client to playerHash?.takeIf { it.isNotBlank() }
    }

    private val streamInfo = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String?>>()

    fun action(kind: String, id: String) = enqueue { TrackingEvents.action(now(), kind, id) }

    /** Background transition: flush whatever is queued (spec §2). */
    fun onAppBackgrounded() {
        scope.launch {
            ready.await()
            flush()
        }
    }

    private fun enqueue(build: () -> JsonObject) {
        // Build the event NOW (correct `t`), enqueue it off-thread.
        val line = runCatching { build().toString() }.getOrNull() ?: return
        scope.launch {
            ready.await()
            val q = queue ?: return@launch
            q.append(line)
            if (q.size >= FLUSH_THRESHOLD) {
                flush()
            } else if (pendingFlushJob?.isActive != true) {
                scheduleFlush(TIMER_FLUSH_MS)
            }
        }
    }

    /** Runs on the confined dispatcher. Single in-flight upload; drains in ≤100-event batches. */
    private suspend fun flush() {
        val q = queue ?: return
        val device = deviceId ?: return
        if (inFlight || q.size == 0) return
        inFlight = true
        try {
            val batch = q.peekBatch()
            when (val result = uploader.upload(device, appVer, batch)) {
                TrackingUploader.Result.Success, TrackingUploader.Result.DropBatch -> {
                    q.removeFirst(batch.size)
                    consecutiveFailures = 0
                    if (result == TrackingUploader.Result.DropBatch) {
                        Timber.tag(TAG).w("Server rejected a batch as malformed; dropped ${batch.size} events")
                    }
                    if (q.size > 0) scheduleFlush(0L)
                }
                TrackingUploader.Result.RateLimited, TrackingUploader.Result.Retry -> {
                    consecutiveFailures++
                    scheduleFlush(
                        trackingRetryDelayMs(
                            consecutiveFailures,
                            rateLimited = result == TrackingUploader.Result.RateLimited,
                        )
                    )
                }
            }
        } finally {
            inFlight = false
        }
    }

    private fun scheduleFlush(delayMs: Long) {
        pendingFlushJob?.cancel()
        pendingFlushJob = scope.launch {
            delay(delayMs)
            flush()
        }
    }

    private fun now() = System.currentTimeMillis()

    private const val TAG = "Zemer_Tracker"
    private const val QUEUE_FILE = "tracking/events.jsonl"
    private const val FLUSH_THRESHOLD = 20
    private const val TIMER_FLUSH_MS = 60_000L
    private const val STREAM_INFO_MAX = 300
}

/** The server rejects any device id that isn't a canonical UUID (verified live) — enforce it here. */
internal fun isCanonicalUuid(value: String): Boolean =
    Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$").matches(value)
