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
    /**
     * The listen's client playback nonce for the watch-time CDN correlation (DIRECT's stampCpn parity):
     * every SABR media POST carries the SAME cpn the stats-beacon session uses, so the CDN sees a
     * consistent playback identity. Read PER REQUEST (the reporter mints per listen). Proven CDN-safe by
     * the harness `CPN=` knob (whole drain with cpn stamped). Null (downloads) stamps nothing.
     */
    val cpn: () -> String? = { null },
)

/**
 * Drives the SABR continuation loop on a background thread, reassembling the audio stream in order into
 * [buffer]: POST a [SabrMessages.abrRequest] -> parse the UMP response -> append new segments -> re-POST
 * with the advanced playerTimeMs + bufferedRange (echoing playback cookies + SABR context updates) until
 * every segment (init + 1..endSegment) has arrived. A faithful port of the proven `tests/sabr-stream.mjs`
 * drain, whose whole-song delivery is validated against the live CDN. Isolated: touches nothing else.
 */
internal class SabrSession(
    private val config: SabrConfig,
    private val client: OkHttpClient,
    private val buffer: SabrBuffer,
) : Runnable {

    @Volatile private var cancelled = false
    fun cancel() { cancelled = true }

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
            if (!cancelled) buffer.markError(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun loop() {
        var url = prepared(config.sabrUrl)
        var playerTimeMs = 0L
        var bufEndMs = 0L
        var lastSeq = 0
        var endSeg = 0
        var cookie: ByteArray? = null
        var initWritten = false
        val seen = HashSet<Int>()
        val ctxByType = LinkedHashMap<Long, ByteArray>()
        var iter = 0
        var dry = 0

        while (!cancelled && iter < 500 && dry < 6) {
            iter++
            val body = SabrMessages.abrRequest(
                ustreamerConfig = config.ustreamerConfig,
                format = config.format,
                poToken = config.poToken,
                clientInfo = config.clientInfo,
                playerTimeMs = playerTimeMs,
                bufferedEndMs = bufEndMs,
                bufferedEndSeg = lastSeq,
                cookie = cookie,
                sabrContexts = ctxByType.values.toList(),
                selected = lastSeq > 0,
            )
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", config.userAgent)
                .post(body.toRequestBody(protobuf))
                .build()
            val bytes = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) { buffer.markError("HTTP ${resp.code}"); return }
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
                SabrUmp.FORMAT_INITIALIZATION_METADATA -> SabrMessages.parseEndSegment(p.payload).let { if (it > 0) endSeg = it }
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
                Timber.tag(TAG).w("SABR_ERROR at iter=$iter (segs so far: $lastSeq/$endSeg) — server rejected the request")
                buffer.markError("SABR_ERROR"); return
            }
            if (iter == 1) Timber.tag(TAG).d("SABR first response: parts=${parts.size}, mediaHeaders=${headers.size}, endSeg=$endSeg")

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
                    if (h.seq > lastSeq) lastSeq = h.seq
                    val end = h.startMs + h.durMs
                    if (end > bufEndMs) bufEndMs = end
                    newSeg = true
                }
            }
            playerTimeMs = bufEndMs

            if (redirect != null && !newSeg) { url = prepared(redirect); continue }
            dry = if (newSeg || gotContext) 0 else dry + 1
            if (endSeg > 0 && lastSeq >= endSeg) break
        }

        if (buffer.available() >= config.format.contentLength && config.format.contentLength > 0) {
            buffer.markComplete()
        } else if (!cancelled) {
            // Delivered less than the whole format (e.g. an identity-capped client on restricted content).
            Timber.tag(TAG).w("SABR incomplete: ${buffer.available()}/${config.format.contentLength} (seq $lastSeq/$endSeg)")
            buffer.markComplete()
        }
    }

    companion object { private const val TAG = "SabrSession" }
}
