package com.jtech.zemer.playback.sabr

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

/**
 * Everything one SABR session needs, derived from the /player response (see [SabrStreamResolver]).
 *
 * [nTransform] applies the cipher n-parameter transform to a googlevideo URL. Web clients'
 * `serverAbrStreamingUrl` is CIPHERED (its `n=` must be transformed before POSTing — the fix that made
 * the web family usable); direct clients (VISIONOS/…) pass the identity function.
 */
internal class SabrConfig(
    val sabrUrl: String,
    val ustreamerConfig: ByteArray,
    val format: SabrMessages.Format,
    val poToken: ByteArray,
    val clientInfo: SabrMessages.ClientInfo,
    val userAgent: String,
    val nTransform: (String) -> String,
    /** For web clients: the videoId-bound pot appended to the (n-transformed) SABR url as `&pot=`. */
    val urlPot: String? = null,
    // Display metadata surfaced in the song-details sheet (persisted as a FormatEntity by MusicService).
    val streamClientLabel: String = "SABR",
    val mimeType: String = "",
    val bitrate: Int = 0,
    val audioSampleRate: Int? = null,
    /** The roster key of the client this config resolved over (the stall-fallback bookkeeping id). */
    val clientKey: String? = null,
    /** approxDurationMs from the /player format — the byte<->time estimate a seek-restart needs. */
    val durationMs: Long = 0,
    /**
     * The listen's client playback nonce for the watch-time CDN correlation (DIRECT's stampCpn parity):
     * every SABR media POST carries the SAME cpn the stats-beacon session uses, so the CDN sees a
     * consistent playback identity. Read PER REQUEST (the reporter mints per listen). Proven CDN-safe by
     * the harness `CPN=` knob (whole drain with cpn stamped). Null (downloads) stamps nothing.
     */
    val cpn: () -> String? = { null },
)

/**
 * Drives the SABR continuation loop on a background thread, reassembling the audio stream into
 * [buffer] at absolute offsets: POST a [SabrMessages.abrRequest] -> parse the UMP response -> write new
 * segments -> re-POST with the advanced playerTimeMs + bufferedRange (echoing playback cookies + SABR
 * context updates) until the stream's TAIL (from this session's first byte to contentLength) is covered.
 * A faithful port of the proven `tests/sabr-stream.mjs` drain. Two proven extensions:
 *
 *  - SEEK ([startTimeMs] > 0): the session cold-starts at that playerTimeMs and the server serves the
 *    segment containing it (absolute startRange — positional reassembly Just Works); the range echo
 *    anchors at OUR first segment. NOTE: a seeked session may get NO end_segment_number, so completion
 *    is judged by BYTE coverage reaching contentLength (both proven in tests/sabr-seek.mjs).
 *  - DEMAND PACING ([paceAheadBytes] > 0): between POSTs the session waits until the reader has consumed
 *    to within the ahead-window, so playback drains at listening speed (a skip stops the spend); the
 *    server session survives the idle gaps (proven live with 90s pauses).
 */
internal class SabrSession(
    private val config: SabrConfig,
    private val client: OkHttpClient,
    private val buffer: SabrBuffer,
    /** Cold-start playerTimeMs (0 = the plain from-the-top drain). */
    private val startTimeMs: Long = 0,
    /** Optional per-iteration progress hook (contiguous bytes reassembled) — the download UI ring. */
    private val onProgress: ((Long) -> Unit)? = null,
    /** Demand-pacing window; 0 = full-speed drain (downloads). */
    private val paceAheadBytes: Long = 0,
    /** Fired on an incomplete drain (the stall-fallback bookkeeping — see SabrPlayerResolver.recordStall). */
    private val onIncomplete: (() -> Unit)? = null,
    /**
     * True when a STREAM owns this session's lifecycle and retries (playback — [SabrAudioStream]). A
     * restartable session NEVER marks the shared buffer errored on its own failure: the buffer is reused
     * by the replacement session a seek-restart starts, and a one-way markError from this dying session
     * would poison it (writes refused forever) even though a valid session was launched. It only wakes
     * the reader so the stream re-aims; the stream is the sole terminal-error authority (its
     * MAX_SEEK_RESTARTS / destroy paths markError). A standalone (download) session keeps marking so the
     * caller sees the failure.
     */
    private val restartable: Boolean = false,
) : Runnable {

    @Volatile private var cancelled = false
    fun cancel() { cancelled = true }

    // POST-count ceiling — a backstop against a pathological progressing-but-never-completing loop
    // (the dry counter below is the real stall guard). Derived from the track duration so a long
    // podcast/audiobook whose drain legitimately needs many round-trips is never cut off mid-tail.
    private val maxIter: Int = ((config.durationMs / 1000L) * 3L + 1000L).coerceIn(1000L, 200_000L).toInt()

    /**
     * Terminal failure of THIS session. A [restartable] (playback) session leaves the buffer unmarked so
     * the stream can restart on the SAME buffer — it only wakes the parked reader to re-aim; a standalone
     * (download) session marks the buffer so its caller sees the failure. [stall] records the client stall
     * (roster-fallback bookkeeping); an HTTP failure passes false (usually an expired url, not this
     * client's fault).
     */
    private fun failStream(message: String, stall: Boolean) {
        if (stall) onIncomplete?.invoke()
        if (restartable) buffer.notifyWaiters() else buffer.markError(message)
    }

    /** Absolute byte offset of this session's FIRST media segment (-1 until known) — the seek anchor. */
    @Volatile var firstWrittenOffset: Long = -1L
        private set

    /** Start time this session was asked to serve from (for the stream's restart decisions). */
    val requestedStartMs: Long get() = startTimeMs

    private val protobuf = "application/x-protobuf".toMediaType()

    private fun prepared(rawUrl: String): String {
        var u = config.nTransform(rawUrl)
        config.urlPot?.let { u += (if (u.contains("?")) "&" else "?") + "pot=" + java.net.URLEncoder.encode(it, "UTF-8") }
        // Stamp the watch-time cpn on every media POST (DIRECT's stampCpn parity) — CDN-safe (harness).
        config.cpn()?.let { u += (if (u.contains("?")) "&" else "?") + "cpn=" + it }
        return u
    }

    override fun run() {
        try {
            loop()
        } catch (e: Exception) {
            // A thread interrupt IS a cancellation (runInterruptible on a cancelled download Job), not
            // a stream failure — no error mark, no stall record; the caller discards the buffer.
            val interrupted = e is java.io.InterruptedIOException || e is InterruptedException || Thread.currentThread().isInterrupted
            if (!cancelled && !interrupted) failStream(e.message ?: e.javaClass.simpleName, stall = false)
        }
    }

    /** Whether this session's tail — first written byte to contentLength — is fully covered. */
    private fun tailDone(): Boolean {
        val first = firstWrittenOffset
        return first >= 0 && buffer.coverageEndFrom(first) >= config.format.contentLength
    }

    private fun loop() {
        var url = prepared(config.sabrUrl)
        var playerTimeMs = startTimeMs
        var bufEndMs = startTimeMs
        var firstSeq = 0
        var firstMs = 0L
        var lastSeq = 0
        var endSeg = 0
        var cookie: ByteArray? = null
        var initWritten = false
        val seen = HashSet<Int>()
        val ctxByType = LinkedHashMap<Long, ByteArray>()
        var iter = 0
        var dry = 0
        var attestationStalls = 0

        while (!cancelled && iter < maxIter && dry < 6) {
            iter++
            if (paceAheadBytes > 0) buffer.awaitDemand(paceAheadBytes) { cancelled }
            if (cancelled) return
            val body = SabrMessages.abrRequest(
                ustreamerConfig = config.ustreamerConfig,
                format = config.format,
                poToken = config.poToken,
                clientInfo = config.clientInfo,
                playerTimeMs = playerTimeMs,
                range = if (lastSeq > 0) SabrMessages.TrackState(config.format, bufEndMs, lastSeq, firstMs, firstSeq) else null,
                cookie = cookie,
                sabrContexts = ctxByType.values.toList(),
                selected = lastSeq > 0,
            )
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", config.userAgent)
                .post(body.toRequestBody(protobuf))
                .build()
            // An HTTP failure is NOT recorded as a client stall — a 403 is usually an expired URL, not
            // this client's fault; the error mark surfaces it and the refresh path re-resolves fresh.
            val bytes = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) { failStream("HTTP ${resp.code}", stall = false); return }
                resp.body?.bytes() ?: ByteArray(0)
            }
            if (cancelled) return

            val parts = SabrUmp.parse(bytes)
            val headers = HashMap<Int, SabrMessages.MediaHeader>()
            var redirect: String? = null
            var sabrError = false
            var gotContext = false
            var protStatus = 0L
            for (p in parts) when (p.type) {
                SabrUmp.MEDIA_HEADER -> { val h = SabrMessages.parseMediaHeader(p.payload); headers[h.headerId] = h }
                SabrUmp.FORMAT_INITIALIZATION_METADATA -> SabrMessages.parseEndSegment(p.payload).let { if (it > 0) endSeg = it }
                SabrUmp.NEXT_REQUEST_POLICY -> SabrMessages.parsePlaybackCookie(p.payload)?.let { cookie = it }
                SabrUmp.SABR_CONTEXT_UPDATE -> { val (type, ctx) = SabrMessages.parseContextUpdate(p.payload); ctxByType[type] = ctx; gotContext = true }
                SabrUmp.SABR_REDIRECT -> redirect = SabrMessages.parseRedirectUrl(p.payload)
                SabrUmp.SABR_ERROR -> sabrError = true
                SabrUmp.STREAM_PROTECTION_STATUS -> {
                    protStatus = SabrProto.read(p.payload).longAt(1)
                    Timber.tag(TAG).d("STREAM_PROTECTION_STATUS=$protStatus (1=OK,2=pending,3=attestation-required)")
                }
            }
            if (sabrError) {
                Timber.tag(TAG).w("SABR_ERROR at iter=$iter (segs so far: $lastSeq/$endSeg) — server rejected the request")
                failStream("SABR_ERROR", stall = true); return
            }
            if (iter == 1) Timber.tag(TAG).d("SABR first response: parts=${parts.size}, mediaHeaders=${headers.size}, endSeg=$endSeg, startTimeMs=$startTimeMs")

            // Which headers are NEW (not yet written): the init segment once, each sequence number once.
            // Resends of an already-written segment are skipped so a byte is never written twice.
            val newIds = headers.values.filter { if (it.isInit) !initWritten else !seen.contains(it.seq) }.map { it.headerId }.toHashSet()
            // Write each new segment's media bytes at its ABSOLUTE offset (startRange, then advance per part)
            // so reassembly is byte-exact regardless of arrival order - matches the proven harness.
            val writeCursor = HashMap<Int, Long>()
            var newSeg = false
            for (p in parts) {
                if (p.type != SabrUmp.MEDIA) continue
                val (id, prefix) = SabrMessages.mediaHeaderId(p.payload)
                if (id !in newIds) continue
                val header = headers[id] ?: continue
                val cursor = writeCursor.getOrPut(id) { header.startRange }
                val len = p.payload.size - prefix
                buffer.writeAt(cursor, p.payload, prefix, len)
                writeCursor[id] = cursor + len
            }
            for (h in headers.values) {
                if (h.headerId !in newIds) continue
                if (h.isInit) { initWritten = true } else {
                    seen.add(h.seq)
                    if (firstSeq == 0 || h.seq < firstSeq) { firstSeq = h.seq; firstMs = h.startMs }
                    if (firstWrittenOffset < 0 || h.startRange < firstWrittenOffset) firstWrittenOffset = h.startRange
                    if (h.seq > lastSeq) lastSeq = h.seq
                    val end = h.startMs + h.durMs
                    if (end > bufEndMs) bufEndMs = end
                    newSeg = true
                }
            }
            playerTimeMs = maxOf(startTimeMs, bufEndMs)
            onProgress?.invoke(buffer.available())

            if (redirect != null && !newSeg) { url = prepared(redirect); continue }
            dry = if (newSeg || gotContext) 0 else dry + 1
            // Attestation cap: a client whose pot can't satisfy stream protection (MWEB/IOS-class) gets a
            // small free window, then the server serves ONLY STREAM_PROTECTION_STATUS>=2 with no media
            // (proven live: tests/probe-mweb-sabr.mjs). Bail FAST with a clear reason instead of grinding
            // to the dry cap — the roster/stall fallback then moves to a client that can attest.
            attestationStalls = SabrProtection.nextStalls(protStatus, madeProgress = newSeg, prev = attestationStalls)
            if (SabrProtection.capped(attestationStalls)) {
                Timber.tag(TAG).w("SABR attestation cap (STREAM_PROTECTION_STATUS=$protStatus): ${buffer.available()}/${config.format.contentLength} at seq $lastSeq/$endSeg — this client cannot satisfy attestation")
                failStream("attestation-capped (protection=$protStatus): ${buffer.available()}/${config.format.contentLength}", stall = true); return
            }
            // Completion: end_segment_number when the server sent one, else BYTE coverage reaching
            // contentLength (a seeked session gets no endSeg — proven live).
            if (endSeg > 0 && lastSeq >= endSeg) break
            if (tailDone()) break
        }

        when {
            cancelled -> return
            buffer.completeFromZero() -> buffer.markComplete()
            tailDone() -> buffer.notifyWaiters() // the session's region is whole; the head gap (a seek) is another session's job
            else -> {
                // Delivered less than its tail (dry-counter expiry / the iteration cap — e.g. an
                // identity-capped client, or a mid-stream context-challenge stall). Mark ERROR, never
                // complete: markComplete converted the shortfall into a clean EOF, silently truncating
                // the track. The reader still serves every reassembled byte first (SabrBuffer), so
                // playback reaches the stall point and then surfaces a real player error — the same
                // shortfall the download path rejects.
                Timber.tag(TAG).w("SABR incomplete: ${buffer.available()}/${config.format.contentLength} (seq $lastSeq/$endSeg, start=$startTimeMs)")
                failStream("incomplete drain: ${buffer.available()}/${config.format.contentLength}", stall = true)
            }
        }
    }

    companion object {
        private const val TAG = "SabrSession"
    }
}
