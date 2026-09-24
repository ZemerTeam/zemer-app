package com.jtech.zemer.playback.sabr

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

/**
 * Everything one DUAL-TRACK (video + audio) SABR session needs. The video counterpart of [SabrConfig].
 * The server serves the EXACT [videoFormat] itag we pin via preferredVideoFormatId (see
 * [SabrMessages.abrRequestVideo]); [nTransform] n-transforms the ciphered web-client SABR url and
 * [urlPot] is the videoId-bound pot appended as `&pot=` (both null/identity for a direct client).
 */
internal class SabrVideoConfig(
    val sabrUrl: String,
    val ustreamerConfig: ByteArray,
    val videoFormat: SabrMessages.Format,
    val audioFormat: SabrMessages.Format,
    val poToken: ByteArray,
    val clientInfo: SabrMessages.ClientInfo,
    val userAgent: String,
    val nTransform: (String) -> String,
    val urlPot: String? = null,
    // Display metadata surfaced in the song-details sheet (persisted as a FormatEntity by MusicService).
    val streamClientLabel: String = "SABR",
    val videoMimeType: String = "",
    val videoBitrate: Int = 0,
    /** The roster key of the client this config resolved over (the stall-fallback bookkeeping id). */
    val clientKey: String? = null,
    /** approxDurationMs from the /player video format — the byte<->time estimate a seek-restart needs. */
    val durationMs: Long = 0,
    /** The listen's cpn for watch-time CDN correlation (DIRECT stampCpn parity); null = download (no stamp). */
    val cpn: () -> String? = { null },
)

/**
 * Drives ONE dual-track SABR continuation loop on a background thread, reassembling the video-only and
 * audio streams into [videoBuffer] and [audioBuffer] respectively. A faithful port of the proven
 * `tests/sabr-video.mjs` drain: each UMP response interleaves both tracks' segments, routed to a track
 * by its MEDIA_HEADER itag; each track reassembles positionally (byte-exact regardless of order); the
 * request advances playerTimeMs to the MINIMUM buffered end across the two tracks (you can't play past
 * the least-buffered track) and echoes playback cookies + SABR context updates until BOTH tracks'
 * TAILS are covered. Seek ([startTimeMs] > 0) and demand pacing follow [SabrSession]'s proven contract
 * (`tests/sabr-video.mjs START_S`: the dual-track cold seek is honoured on both tracks, and a seeked
 * session gets NO end_segment_number — completion is judged by byte coverage).
 */
internal class SabrVideoSession(
    private val config: SabrVideoConfig,
    private val client: OkHttpClient,
    private val videoBuffer: SabrBuffer,
    private val audioBuffer: SabrBuffer,
    /** Cold-start playerTimeMs (0 = the plain from-the-top drain). */
    private val startTimeMs: Long = 0,
    /** Optional per-iteration progress hook (contiguous bytes across BOTH tracks) — the download ring. */
    private val onProgress: ((Long) -> Unit)? = null,
    /** Per-track demand-pacing windows; 0 = full-speed drain (downloads). */
    private val paceAheadVideoBytes: Long = 0,
    private val paceAheadAudioBytes: Long = 0,
    /** Fired on an incomplete drain (the stall-fallback bookkeeping). */
    private val onIncomplete: (() -> Unit)? = null,
    /**
     * True when [SabrVideoStream] owns this session's lifecycle and retries. A restartable session NEVER
     * marks its shared buffers errored on failure — a seek-restart reuses the SAME buffers, and a
     * one-way markError from this dying session would poison them for the valid replacement. It only
     * wakes the readers so the stream re-aims; the stream owns the terminal error (MAX_SEEK_RESTARTS /
     * destroy). A standalone (download) session keeps marking so its caller sees the failure.
     */
    private val restartable: Boolean = false,
) : Runnable {

    @Volatile private var cancelled = false
    fun cancel() { cancelled = true }

    // POST-count ceiling — a backstop against a pathological progressing-but-never-completing loop
    // (dry below is the real stall guard); derived from duration so a long video's legitimate drain
    // is never cut off mid-tail.
    private val maxIter: Int = ((config.durationMs / 1000L) * 3L + 1000L).coerceIn(1000L, 200_000L).toInt()

    /** Terminal failure of THIS session — see [SabrSession.failStream]. Marks/wakes BOTH tracks. */
    private fun failStream(message: String, stall: Boolean) {
        if (stall) onIncomplete?.invoke()
        if (restartable) { videoBuffer.notifyWaiters(); audioBuffer.notifyWaiters() }
        else { videoBuffer.markError(message); audioBuffer.markError(message) }
    }

    /** Absolute first-byte offsets of this session's tracks (-1 until known) — the seek anchors. */
    @Volatile var firstVideoOffset: Long = -1L
        private set
    @Volatile var firstAudioOffset: Long = -1L
        private set

    /** Start time this session was asked to serve from (for the stream's restart decisions). */
    val requestedStartMs: Long get() = startTimeMs

    private val protobuf = "application/x-protobuf".toMediaType()

    /** Per-track drain state (segment bookkeeping the SabrBuffer doesn't hold). */
    private inner class Track(val itag: Int, val format: SabrMessages.Format, val buffer: SabrBuffer) {
        val seen = HashSet<Int>()
        var initWritten = false
        var firstSeq = 0
        var firstMs = 0L
        var firstOff = -1L
        var lastSeq = 0
        var bufEndMs = startTimeMs
        var endSeg = 0
        fun state(): SabrMessages.TrackState? =
            if (lastSeq > 0) SabrMessages.TrackState(format, bufEndMs, lastSeq, firstMs, firstSeq) else null
        /** This session's tail — its first written byte to contentLength — fully covered? */
        fun tailDone(): Boolean = firstOff >= 0 && buffer.coverageEndFrom(firstOff) >= format.contentLength
    }

    private fun prepared(rawUrl: String): String {
        var u = config.nTransform(rawUrl)
        config.urlPot?.let { u += (if (u.contains("?")) "&" else "?") + "pot=" + java.net.URLEncoder.encode(it, "UTF-8") }
        config.cpn()?.let { u += (if (u.contains("?")) "&" else "?") + "cpn=" + it }
        return u
    }

    override fun run() {
        try {
            loop()
        } catch (e: Exception) {
            // A thread interrupt IS a cancellation (runInterruptible on a cancelled download Job), not
            // a stream failure — no error mark, no stall record; the caller discards the buffers.
            val interrupted = e is java.io.InterruptedIOException || e is InterruptedException || Thread.currentThread().isInterrupted
            if (!cancelled && !interrupted) failStream(e.message ?: e.javaClass.simpleName, stall = false)
        }
    }

    private fun loop() {
        val video = Track(config.videoFormat.itag, config.videoFormat, videoBuffer)
        val audio = Track(config.audioFormat.itag, config.audioFormat, audioBuffer)
        val tracks = mapOf(video.itag to video, audio.itag to audio)
        var url = prepared(config.sabrUrl)
        var playerTimeMs = startTimeMs
        var cookie: ByteArray? = null
        val ctxByType = LinkedHashMap<Long, ByteArray>()
        var iter = 0
        var dry = 0
        var attestationStalls = 0

        while (!cancelled && iter < maxIter && dry < 6) {
            iter++
            // Demand pacing: pause only while BOTH tracks are comfortably ahead of their readers.
            if (paceAheadVideoBytes > 0 || paceAheadAudioBytes > 0) awaitDualDemand()
            if (cancelled) return
            val anySelected = video.lastSeq > 0 || audio.lastSeq > 0
            val body = SabrMessages.abrRequestVideo(
                ustreamerConfig = config.ustreamerConfig,
                videoFormat = config.videoFormat,
                audioFormat = config.audioFormat,
                video = video.state(),
                audio = audio.state(),
                poToken = config.poToken,
                clientInfo = config.clientInfo,
                playerTimeMs = playerTimeMs,
                cookie = cookie,
                sabrContexts = ctxByType.values.toList(),
                selected = anySelected,
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
                Timber.tag(TAG).w("SABR_ERROR at iter=$iter (video ${video.lastSeq}, audio ${audio.lastSeq}) — server rejected the request")
                failStream("SABR_ERROR", stall = true); return
            }

            // Which headers are NEW per track (init once, each sequence once). Resends are skipped.
            val newIds = headers.values.filter { h ->
                val t = tracks[h.itag] ?: return@filter false
                if (h.isInit) !t.initWritten else !t.seen.contains(h.seq)
            }.map { it.headerId }.toHashSet()

            // Write each new segment's media bytes at its ABSOLUTE offset into its track's buffer.
            val writeCursor = HashMap<Int, Long>()
            for (p in parts) {
                if (p.type != SabrUmp.MEDIA) continue
                val (id, prefix) = SabrMessages.mediaHeaderId(p.payload)
                if (id !in newIds) continue
                val header = headers[id] ?: continue
                val t = tracks[header.itag] ?: continue
                val cursor = writeCursor.getOrPut(id) { header.startRange }
                val len = p.payload.size - prefix
                t.buffer.writeAt(cursor, p.payload, prefix, len)
                writeCursor[id] = cursor + len
            }
            var newSeg = false
            for (h in headers.values) {
                if (h.headerId !in newIds) continue
                val t = tracks[h.itag] ?: continue
                if (h.isInit) { t.initWritten = true } else {
                    t.seen.add(h.seq)
                    if (t.firstSeq == 0 || h.seq < t.firstSeq) { t.firstSeq = h.seq; t.firstMs = h.startMs }
                    if (t.firstOff < 0 || h.startRange < t.firstOff) {
                        t.firstOff = h.startRange
                        if (t === video) firstVideoOffset = t.firstOff else firstAudioOffset = t.firstOff
                    }
                    if (h.seq > t.lastSeq) t.lastSeq = h.seq
                    val end = h.startMs + h.durMs
                    if (end > t.bufEndMs) t.bufEndMs = end
                    newSeg = true
                }
            }
            // Advance to the least-buffered track, floored at the requested start until both land.
            playerTimeMs = maxOf(startTimeMs, minOf(video.bufEndMs.orStartIfEmpty(video), audio.bufEndMs.orStartIfEmpty(audio)))
            onProgress?.invoke(videoBuffer.available() + audioBuffer.available())

            if (redirect != null && !newSeg) { url = prepared(redirect); continue }
            dry = if (newSeg || gotContext) 0 else dry + 1
            // Attestation cap (see SabrSession): a client that can't satisfy stream protection is served a
            // free window then only STREAM_PROTECTION_STATUS>=2 — bail fast so the fallback moves on.
            attestationStalls = SabrProtection.nextStalls(protStatus, madeProgress = newSeg, prev = attestationStalls)
            if (SabrProtection.capped(attestationStalls)) {
                Timber.tag(TAG).w("SABR video attestation cap (STREAM_PROTECTION_STATUS=$protStatus) — this client cannot satisfy attestation")
                failStream("attestation-capped (protection=$protStatus)", stall = true); return
            }
            if (video.tailDone() && audio.tailDone()) break
        }

        when {
            cancelled -> return
            video.tailDone() && audio.tailDone() -> {
                if (videoBuffer.completeFromZero()) videoBuffer.markComplete() else videoBuffer.notifyWaiters()
                if (audioBuffer.completeFromZero()) audioBuffer.markComplete() else audioBuffer.notifyWaiters()
            }
            else -> {
                // A shortfall on either track marks BOTH errored, never complete: a clean EOF silently
                // truncated playback mid-item, and one whole track is useless without its sibling. The
                // buffers still serve their reassembled bytes first (SabrBuffer), so playback reaches
                // the stall point and surfaces a real error; the download path rejects the shortfall
                // before ever reading.
                Timber.tag(TAG).w("SABR video incomplete: video ${videoBuffer.available()}/${config.videoFormat.contentLength}, audio ${audioBuffer.available()}/${config.audioFormat.contentLength} (start=$startTimeMs)")
                val msg = "incomplete drain: video ${videoBuffer.available()}/${config.videoFormat.contentLength}, audio ${audioBuffer.available()}/${config.audioFormat.contentLength}"
                failStream(msg, stall = true)
            }
        }
    }

    /**
     * Pause while BOTH tracks are ahead of their readers by more than their windows — the drain keeps
     * feeding whichever track playback is closer to exhausting, and a fully-idle player (pause, or the
     * user left) stops the spend. A short poll (not a cross-buffer wait) keeps this simple; the server
     * session survives the idle gaps (proven live with 90s pauses).
     */
    private fun awaitDualDemand() {
        while (!cancelled) {
            if (videoBuffer.failed() || audioBuffer.failed()) return
            val vGap = videoBuffer.demandGap()
            val aGap = audioBuffer.demandGap()
            if (vGap <= paceAheadVideoBytes || aGap <= paceAheadAudioBytes) return
            Thread.sleep(150)
        }
    }

    private fun Long.orStartIfEmpty(t: Track): Long = if (t.lastSeq > 0) this else startTimeMs

    companion object {
        private const val TAG = "SabrVideoSession"
    }
}
