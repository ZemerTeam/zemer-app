package com.jtech.zemer.tracking

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.jtech.zemer.BuildConfig
import com.jtech.zemer.constants.TrackingDeviceIdKey
import com.jtech.zemer.utils.OfflineModeState
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
import java.util.concurrent.ConcurrentHashMap

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
 * going to background. One in-flight upload at a time, and EVERY trigger honors the failure
 * backoff ([FlushSchedule]): while the server fails/rate-limits, threshold and background triggers
 * wait out the 30 s → 2 min → 10 min ladder instead of re-hammering. A 400 drops the batch; the
 * queue caps at 500 dropping oldest ([TrackingQueue]).
 */
object Tracker {

    /** mediaId → play source registry, fed by the player service as queues are built. */
    val playSources = PlaySourceResolver()

    @Suppress("OPT_IN_USAGE")
    private val confined = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + confined)
    private val ready = CompletableDeferred<Unit>()

    private var queue: TrackingQueue? = null
    private var uploader = TrackingUploader()
    private var deviceId: String? = null
    private var appVer: String = BuildConfig.VERSION_NAME

    private var inFlight = false
    private val schedule = FlushSchedule(::now)
    private var pendingFlushJob: Job? = null
    private var pendingFlushAt = Long.MAX_VALUE

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

    fun search(q: String, results: Int, provider: String? = null) =
        enqueue { TrackingEvents.search(now(), q, results, provider) }

    fun click(q: String, id: String, kind: String, rank: Int) =
        enqueue { TrackingEvents.click(now(), q, id, kind, rank) }

    fun play(videoId: String, secs: Int, dur: Int?, source: String) {
        val stream = streamInfo[videoId]
        enqueue { TrackingEvents.play(now(), videoId, secs, dur, source, stream?.first, stream?.second) }
    }

    fun action(kind: String, id: String) = enqueue { TrackingEvents.action(now(), kind, id) }

    /**
     * One rendered row/screen: the videoIds actually on screen, on [surface].
     *
     * Impressions are the ONLY event type that may be thrown away rather than queued, and this is
     * deliberate on two fronts. They arrive an order of magnitude more often than plays and share
     * the one 500-event drop-OLDEST queue, so left unchecked a scroll-heavy session would evict the
     * plays we actually care about — the highest-value events losing to the cheapest. So they are
     * dropped outright while the upload backoff window is open (a server blip must not turn into
     * lost listens) and once the queue is over [IMPRESSION_QUEUE_CEILING], which reserves the rest
     * of it for everything else.
     *
     * Dropping them for those two reasons is free by contract: the server ranks on DISTINCT devices,
     * and both drops are song-independent — they depend on when the server was unhealthy and on how
     * full the queue is, never on what was on screen — so they shrink exposure counts without
     * skewing the exposed/instrumented share the dampener divides by. (The per-POST row cap in
     * [capImpressionRows] is what keeps the SERVER from dropping rows, which would not have that
     * property: its truncation always lands on whatever was queued last.)
     */
    fun impression(ids: List<String>, surface: String?) {
        if (ids.isEmpty()) return
        val t = now()
        scope.launch {
            ready.await()
            val q = queue ?: return@launch
            if (schedule.delayUntilAllowed() > 0) return@launch
            if (q.size >= IMPRESSION_QUEUE_CEILING) return@launch
            // Evict BEFORE the add pass: clearing afterwards would wipe the very ids this call just
            // reported, so the next dwell on those same rows would report them a second time and
            // double their exposure — over-counting, the direction that silently penalises a song.
            if (seenImpressions.size > SEEN_IMPRESSIONS_MAX) seenImpressions.clear()
            // isVideoId first: albums/artists/playlists share these rows and would otherwise fill
            // the dedup set with ids that can never be reported anyway.
            val fresh = ids.filter { isVideoId(it) && seenImpressions.add(impressionKey(surface, it)) }
            val chunks = impressionChunks(fresh)
            if (chunks.isEmpty()) return@launch // every id already seen — nothing to flush
            chunks.forEach { chunk ->
                q.append(TrackingEvents.impression(t, chunk, surface).toString())
            }
            scheduleFlush(if (q.size >= FLUSH_THRESHOLD) 0L else TIMER_FLUSH_MS)
        }
    }

    /**
     * Per-`(surface, videoId)` dedup for the process lifetime — the normative definition of an
     * impression. The server tolerates repeats (it aggregates distinct devices), but scroll jitter,
     * recomposition and back-navigation would otherwise re-report the same row indefinitely, which
     * is what actually consumes the per-POST cap and the queue. Bounded like [streamInfo]: cleared
     * wholesale once it outgrows any realistic session rather than growing without limit.
     */
    private val seenImpressions = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    // '|' is outside both alphabets (surface `[\w:.-]`, videoId `[A-Za-z0-9_-]`), so it can never
    // appear inside either half and the key stays unambiguous. Deliberately NOT a NUL byte: one
    // raw 0x00 anywhere in a source file makes git treat the whole file as binary, which hides
    // every diff and turns a merge conflict into a silent take-one-side.
    private fun impressionKey(surface: String?, videoId: String) = "${surface.orEmpty()}|$videoId"

    /**
     * The player service reports which stream client (and, for deciphered web clients, which
     * player_ias hash) served a videoId, so the listen's `play` event can carry it. Bounded: cleared
     * once it outgrows any realistic queue so it can never accumulate across a long session.
     */
    fun onStreamResolved(videoId: String, client: String, playerHash: String?) {
        if (streamInfo.size > STREAM_INFO_MAX) streamInfo.clear()
        streamInfo[videoId] = client to playerHash?.takeIf { it.isNotBlank() }
    }

    private val streamInfo = ConcurrentHashMap<String, Pair<String, String?>>()

    /**
     * Upload path for the one-shot history backfill ([PlayHistoryBackfill]): bypasses the live
     * QUEUE — its 500 cap must never be flooded by thousands of backfill rows — but shares
     * everything else: identity, debug flag, uploader, the single-in-flight discipline AND the
     * failure backoff ([FlushSchedule]), so a rate-limited/failing server is never poked by the
     * backfill while the live path is waiting out the ladder (and vice versa: a backfill failure
     * opens the same window for the live path). Null until initialization provides a device id.
     */
    internal suspend fun uploadBackfill(eventLines: List<String>): TrackingUploader.Result? {
        ready.await()
        return kotlinx.coroutines.withContext(confined) {
            val device = deviceId ?: return@withContext null
            // Manual offline mode: no telemetry POSTs; the backfill run aborts and resumes from its
            // cursor on a later (online) launch.
            if (OfflineModeState.enabled) return@withContext null
            // Honor the shared backoff window and never overlap a live upload.
            while (true) {
                val wait = schedule.delayUntilAllowed()
                if (wait > 0) {
                    delay(wait)
                    continue
                }
                if (inFlight) {
                    delay(IN_FLIGHT_POLL_MS)
                    continue
                }
                break
            }
            inFlight = true
            try {
                val result = uploader.upload(device, appVer, BuildConfig.DEBUG, eventLines)
                when (result) {
                    TrackingUploader.Result.Success, TrackingUploader.Result.DropBatch ->
                        schedule.onSuccess()
                    TrackingUploader.Result.RateLimited -> schedule.onFailure(rateLimited = true)
                    TrackingUploader.Result.Retry -> schedule.onFailure(rateLimited = false)
                }
                result
            } finally {
                inFlight = false
            }
        }
    }

    /** Background transition: flush whatever is queued (spec §2), still honoring the backoff. */
    fun onAppBackgrounded() {
        scope.launch {
            ready.await()
            if ((queue?.size ?: 0) > 0) scheduleFlush(0L)
        }
    }

    private fun enqueue(build: () -> JsonObject) {
        // Build the event NOW (correct `t`), enqueue it off-thread.
        val line = runCatching { build().toString() }.getOrNull() ?: return
        scope.launch {
            ready.await()
            val q = queue ?: return@launch
            q.append(line)
            // Both triggers route through scheduleFlush, which enforces the failure backoff — a
            // threshold-full queue during a server outage must NOT fire a POST per new event.
            scheduleFlush(if (q.size >= FLUSH_THRESHOLD) 0L else TIMER_FLUSH_MS)
        }
    }

    /** Runs on the confined dispatcher. Single in-flight upload; drains in ≤100-event batches. */
    private suspend fun flush() {
        val q = queue ?: return
        val device = deviceId ?: return
        if (inFlight || q.size == 0) return
        // Manual offline mode: hold uploads entirely (zero network) — events keep queueing under the
        // normal 500-event drop-oldest cap and flush on the first trigger after the mode turns off.
        if (OfflineModeState.enabled) return
        // Belt-and-braces: even a mistimed trigger never violates the backoff window.
        schedule.delayUntilAllowed().takeIf { it > 0 }?.let {
            scheduleFlush(it)
            return
        }
        inFlight = true
        try {
            // Event-counted drain, row-counted server limit: cap the batch so the server never has
            // to truncate it (its truncation always falls on the newest events, which would bias
            // exposure toward whatever the user scrolled to first).
            val batch = capImpressionRows(q.peekBatch())
            when (val result = uploader.upload(device, appVer, BuildConfig.DEBUG, batch)) {
                TrackingUploader.Result.Success, TrackingUploader.Result.DropBatch -> {
                    q.removeBatch(batch)
                    schedule.onSuccess()
                    if (result == TrackingUploader.Result.DropBatch) {
                        Timber.tag(TAG).w("Server rejected a batch as malformed; dropped ${batch.size} events")
                    }
                    if (q.size > 0) scheduleFlush(0L)
                }
                TrackingUploader.Result.RateLimited, TrackingUploader.Result.Retry -> {
                    val delayMs = schedule.onFailure(
                        rateLimited = result == TrackingUploader.Result.RateLimited,
                    )
                    scheduleFlush(delayMs)
                }
            }
        } finally {
            inFlight = false
        }
    }

    /**
     * Schedules a flush attempt, keeping the EARLIEST pending one (a 60 s timer must not push out
     * an imminent threshold flush, nor a threshold trigger cancel a sooner retry) and never earlier
     * than the backoff window allows.
     */
    private fun scheduleFlush(delayMs: Long) {
        val target = now() + maxOf(delayMs, schedule.delayUntilAllowed())
        if (pendingFlushJob?.isActive == true && pendingFlushAt <= target) return
        pendingFlushJob?.cancel()
        pendingFlushAt = target
        pendingFlushJob = scope.launch {
            delay((target - now()).coerceAtLeast(0L))
            pendingFlushAt = Long.MAX_VALUE
            // Past the delay this job IS the upload, and cancelling it mid-POST would abandon a
            // batch the server may already have stored — so drop the handle before running. A
            // later trigger then schedules a fresh job instead of killing this one; the flush
            // reschedules itself for whatever is left in the queue. (Single-threaded dispatcher,
            // so this is not a race.)
            pendingFlushJob = null
            flush()
        }
    }

    private fun now() = System.currentTimeMillis()

    private const val TAG = "Zemer_Tracker"
    private const val QUEUE_FILE = "tracking/events.jsonl"
    private const val FLUSH_THRESHOLD = 20
    private const val TIMER_FLUSH_MS = 60_000L
    private const val STREAM_INFO_MAX = 300
    private const val IN_FLIGHT_POLL_MS = 250L

    /** Impressions stop being queued here, reserving the rest of the 500-event queue for plays. */
    private const val IMPRESSION_QUEUE_CEILING = TrackingQueue.MAX_SIZE / 2
    private const val SEEN_IMPRESSIONS_MAX = 2_000
}

/** The server rejects any device id that isn't a canonical UUID (verified live) — enforce it here. */
internal fun isCanonicalUuid(value: String): Boolean =
    Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$").matches(value)
