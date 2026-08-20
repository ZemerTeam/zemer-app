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
    /** The listen's cpn for watch-time CDN correlation (DIRECT stampCpn parity); null = download (no stamp). */
    val cpn: () -> String? = { null },
)

/**
 * Drives ONE dual-track SABR continuation loop on a background thread, reassembling the video-only and
 * audio streams into [videoBuffer] and [audioBuffer] respectively. A faithful port of the proven
 * `tests/sabr-video.mjs` drain: each UMP response interleaves both tracks' segments, routed to a track
 * by its MEDIA_HEADER itag; each track reassembles positionally (byte-exact regardless of order); the
 * request advances playerTimeMs to the MINIMUM buffered end across the two tracks (you can't play past
 * the least-buffered track) and echoes playback cookies + SABR context updates until BOTH tracks are
 * whole. Isolated: touches nothing else.
 */
internal class SabrVideoSession(
    private val config: SabrVideoConfig,
    private val client: OkHttpClient,
    private val videoBuffer: SabrBuffer,
    private val audioBuffer: SabrBuffer,
) : Runnable {

    @Volatile private var cancelled = false
    fun cancel() { cancelled = true }

    private val protobuf = "application/x-protobuf".toMediaType()

    /** Per-track drain state (segment bookkeeping the SabrBuffer doesn't hold). */
    private class Track(val itag: Int, val format: SabrMessages.Format, val buffer: SabrBuffer) {
        val seen = HashSet<Int>()
        var initWritten = false
        var lastSeq = 0
        var bufEndMs = 0L
        fun state(): SabrMessages.TrackState? = if (lastSeq > 0) SabrMessages.TrackState(format, bufEndMs, lastSeq) else null
        fun whole(): Boolean = buffer.available() >= format.contentLength && format.contentLength > 0
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
            if (!cancelled) { videoBuffer.markError(e.message ?: e.javaClass.simpleName); audioBuffer.markError(e.message ?: e.javaClass.simpleName) }
        }
    }

    private fun loop() {
        val video = Track(config.videoFormat.itag, config.videoFormat, videoBuffer)
        val audio = Track(config.audioFormat.itag, config.audioFormat, audioBuffer)
        val tracks = mapOf(video.itag to video, audio.itag to audio)
        var url = prepared(config.sabrUrl)
        var playerTimeMs = 0L
        var cookie: ByteArray? = null
        val ctxByType = LinkedHashMap<Long, ByteArray>()
        var iter = 0
        var dry = 0

        while (!cancelled && iter < 800 && dry < 6) {
            iter++
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
            val bytes = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) { videoBuffer.markError("HTTP ${resp.code}"); audioBuffer.markError("HTTP ${resp.code}"); return }
                resp.body?.bytes() ?: ByteArray(0)
            }
            if (cancelled) return

            val parts = SabrUmp.parse(bytes)
            val headers = HashMap<Int, SabrMessages.MediaHeader>()
            var redirect: String? = null
            var sabrError = false
            var gotContext = false
            for (p in parts) when (p.type) {
                SabrUmp.MEDIA_HEADER -> { val h = SabrMessages.parseMediaHeader(p.payload); headers[h.headerId] = h }
                SabrUmp.NEXT_REQUEST_POLICY -> SabrMessages.parsePlaybackCookie(p.payload)?.let { cookie = it }
                SabrUmp.SABR_CONTEXT_UPDATE -> { val (type, ctx) = SabrMessages.parseContextUpdate(p.payload); ctxByType[type] = ctx; gotContext = true }
                SabrUmp.SABR_REDIRECT -> redirect = SabrMessages.parseRedirectUrl(p.payload)
                SabrUmp.SABR_ERROR -> sabrError = true
                SabrUmp.STREAM_PROTECTION_STATUS -> {
                    val status = SabrProto.read(p.payload).longAt(1)
                    Timber.tag(TAG).d("STREAM_PROTECTION_STATUS=$status (1=OK,2=pending,3=attestation-required)")
                }
            }
            if (sabrError) {
                Timber.tag(TAG).w("SABR_ERROR at iter=$iter (video ${video.lastSeq}, audio ${audio.lastSeq}) — server rejected the request")
                videoBuffer.markError("SABR_ERROR"); audioBuffer.markError("SABR_ERROR"); return
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
                    if (h.seq > t.lastSeq) t.lastSeq = h.seq
                    val end = h.startMs + h.durMs
                    if (end > t.bufEndMs) t.bufEndMs = end
                    newSeg = true
                }
            }
            // Advance to the least-buffered track (a track with nothing yet holds it at 0).
            playerTimeMs = minOf(video.bufEndMs.orZeroIfEmpty(video), audio.bufEndMs.orZeroIfEmpty(audio))

            if (redirect != null && !newSeg) { url = prepared(redirect); continue }
            dry = if (newSeg || gotContext) 0 else dry + 1
            if (video.whole() && audio.whole()) break
        }

        // Mark both complete: a whole track EOFs cleanly; a short track (a capped client) EOFs where it
        // stopped (the download path detects the shortfall via available() < contentLength and discards).
        if (!cancelled) {
            if (!video.whole() || !audio.whole()) {
                Timber.tag(TAG).w("SABR video incomplete: video ${videoBuffer.available()}/${config.videoFormat.contentLength}, audio ${audioBuffer.available()}/${config.audioFormat.contentLength}")
            }
            videoBuffer.markComplete(); audioBuffer.markComplete()
        }
    }

    private fun Long.orZeroIfEmpty(t: Track): Long = if (t.lastSeq > 0) this else 0L

    companion object { private const val TAG = "SabrVideoSession" }
}
