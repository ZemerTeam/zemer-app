package com.jtech.zemer.playback.sabr

import com.jtech.zemer.playback.sabr.SabrProto.bField
import com.jtech.zemer.playback.sabr.SabrProto.concat
import com.jtech.zemer.playback.sabr.SabrProto.sField
import com.jtech.zemer.playback.sabr.SabrProto.vField

/**
 * SABR protobuf message builders + response parsers. Field numbers are transcribed from the
 * reverse-engineered protos (LuanRT/googlevideo, coletdjnz/yt-dlp-ytse) and pinned to the proven Node
 * reference (`tests/sabr-stream.mjs`). See [SabrProto] for the wire primitives.
 */
internal object SabrMessages {

    /** The audio format a SABR session streams — from the /player response's chosen adaptiveFormat. */
    class Format(val itag: Int, val lastModified: Long, val contentLength: Long)

    /** Identity for the SABR streamerContext.clientInfo (must match the /player request client). */
    class ClientInfo(
        val clientName: Int,
        val clientVersion: String,
        val osName: String? = null,
        val osVersion: String? = null,
        val deviceMake: String? = null,
        val deviceModel: String? = null,
        val androidSdkVersion: Int? = null,
    )

    // misc.FormatId { itag=1, lastModified=2 }
    private fun formatId(f: Format): ByteArray =
        concat(vField(1, f.itag.toLong()), if (f.lastModified != 0L) vField(2, f.lastModified) else ByteArray(0))

    // StreamerContext.ClientInfo { deviceMake=12, deviceModel=13, clientName=16, clientVersion=17, osName=18, osVersion=19, androidSdkVersion=64 }
    private fun clientInfo(c: ClientInfo): ByteArray = concat(
        c.deviceMake?.let { sField(12, it) } ?: ByteArray(0),
        c.deviceModel?.let { sField(13, it) } ?: ByteArray(0),
        vField(16, c.clientName.toLong()),
        sField(17, c.clientVersion),
        c.osName?.let { sField(18, it) } ?: ByteArray(0),
        c.osVersion?.let { sField(19, it) } ?: ByteArray(0),
        c.androidSdkVersion?.let { vField(64, it.toLong()) } ?: ByteArray(0),
    )

    // StreamerContext.SabrContext { type=1, value=2 } (echoed back from a SABR_CONTEXT_UPDATE)
    fun sabrContext(type: Long, value: ByteArray): ByteArray = concat(vField(1, type), bField(2, value))

    // StreamerContext { clientInfo=1, poToken=2, playbackCookie=3, sabrContexts=5(repeated) }
    private fun streamerContext(c: ClientInfo, poToken: ByteArray, cookie: ByteArray?, contexts: List<ByteArray>): ByteArray =
        concat(
            bField(1, clientInfo(c)),
            bField(2, poToken),
            cookie?.let { bField(3, it) } ?: ByteArray(0),
            concat(contexts.map { bField(5, it) }),
        )

    // BufferedRange { formatId=1, startTimeMs=2, durationMs=3, startSegmentIndex=4, endSegmentIndex=5 }
    private fun bufferedRange(f: Format, endMs: Long, endSeg: Int): ByteArray =
        concat(bField(1, formatId(f)), vField(2, 0), vField(3, endMs), vField(4, 1), vField(5, endSeg.toLong()))

    // ClientAbrState { playerTimeMs=28, enabledTrackTypesBitfield=40 }  (40=1 -> audio only)
    private fun clientAbrState(playerTimeMs: Long): ByteArray = concat(vField(28, playerTimeMs), vField(40, 1))

    /**
     * VideoPlaybackAbrRequest { clientAbrState=1, selectedFormatId=2, bufferedRange=3(repeated),
     * playerTimeMs=4, videoPlaybackUstreamerConfig=5, preferredAudioFormatId=16, streamerContext=19 }.
     */
    fun abrRequest(
        ustreamerConfig: ByteArray,
        format: Format,
        poToken: ByteArray,
        clientInfo: ClientInfo,
        playerTimeMs: Long,
        bufferedEndMs: Long,
        bufferedEndSeg: Int,
        cookie: ByteArray?,
        sabrContexts: List<ByteArray>,
        selected: Boolean,
    ): ByteArray = concat(
        bField(1, clientAbrState(playerTimeMs)),
        if (selected) bField(2, formatId(format)) else ByteArray(0),
        if (bufferedEndSeg > 0) bField(3, bufferedRange(format, bufferedEndMs, bufferedEndSeg)) else ByteArray(0),
        if (playerTimeMs > 0) vField(4, playerTimeMs) else ByteArray(0),
        bField(5, ustreamerConfig),
        bField(16, formatId(format)),
        bField(19, streamerContext(clientInfo, poToken, cookie, sabrContexts)),
    )

    /** One track's buffered progress in a multi-track (video+audio) request. */
    class TrackState(val format: Format, val bufferedEndMs: Long, val bufferedEndSeg: Int)

    /**
     * VideoPlaybackAbrRequest for a DUAL-TRACK (video + audio) SABR session. Differs from [abrRequest]:
     * enabledTrackTypesBitfield = 0 (video+audio, not 1=audio-only); both preferred formats are pinned —
     * `preferredAudioFormatId=16` AND `preferredVideoFormatId=17` (field 17 is the lever that makes the
     * server serve the EXACT requested video itag, proven in `tests/sabr-video.mjs`); and the selected
     * ids + buffered ranges are sent PER track. Field numbers pinned to `tests/sabr-video-clients.mjs`.
     */
    fun abrRequestVideo(
        ustreamerConfig: ByteArray,
        videoFormat: Format,
        audioFormat: Format,
        video: TrackState?,
        audio: TrackState?,
        poToken: ByteArray,
        clientInfo: ClientInfo,
        playerTimeMs: Long,
        cookie: ByteArray?,
        sabrContexts: List<ByteArray>,
        selected: Boolean,
    ): ByteArray {
        val states = listOfNotNull(video, audio)
        return concat(
            bField(1, concat(vField(28, playerTimeMs), vField(40, 0))), // enabledTrackTypesBitfield=0 => video+audio
            if (selected) concat(states.map { bField(2, formatId(it.format)) }) else ByteArray(0),
            concat(states.filter { it.bufferedEndSeg > 0 }.map { bField(3, bufferedRange(it.format, it.bufferedEndMs, it.bufferedEndSeg)) }),
            if (playerTimeMs > 0) vField(4, playerTimeMs) else ByteArray(0),
            bField(5, ustreamerConfig),
            bField(16, formatId(audioFormat)), // preferredAudioFormatId
            bField(17, formatId(videoFormat)), // preferredVideoFormatId (pins the exact video itag)
            bField(19, streamerContext(clientInfo, poToken, cookie, sabrContexts)),
        )
    }

    // ---- response part parsers ----

    /** MediaHeader { header_id=1, itag=3, start_range=6, is_init_seg=8, sequence_number=9, content_length=14, time_range=15 } */
    class MediaHeader(val headerId: Int, val itag: Int, val seq: Int, val isInit: Boolean, val startRange: Long, val contentLength: Long, val startMs: Long, val durMs: Long)

    fun parseMediaHeader(payload: ByteArray): MediaHeader {
        val m = SabrProto.read(payload)
        val tr = m.bytesAt(15)?.let { parseTimeRangeMs(it) } ?: (0L to 0L)
        return MediaHeader(
            headerId = m.longAt(1).toInt(),
            itag = m.longAt(3).toInt(),
            seq = m.longAt(9).toInt(),
            isInit = m.longAt(8) != 0L,
            startRange = m.longAt(6),
            contentLength = m.longAt(14),
            startMs = tr.first,
            durMs = tr.second,
        )
    }

    /** TimeRange { start_ticks=1, duration_ticks=2, timescale=3 } -> (startMs, durMs). */
    private fun parseTimeRangeMs(payload: ByteArray): Pair<Long, Long> {
        val m = SabrProto.read(payload)
        val ts = m.longAt(3).let { if (it == 0L) 1000L else it }
        val startMs = m.longAt(1) * 1000 / ts
        val durMs = m.longAt(2) * 1000 / ts
        return startMs to durMs
    }

    /** FormatInitializationMetadata { end_segment_number=4 } — total segment count. */
    fun parseEndSegment(payload: ByteArray): Int = SabrProto.read(payload).longAt(4).toInt()

    /** NextRequestPolicy.playback_cookie is field 7 (a PlaybackCookie message we echo verbatim). */
    fun parsePlaybackCookie(payload: ByteArray): ByteArray? = SabrProto.read(payload).bytesAt(7)

    /** SabrContextUpdate { type=1, value=3 } -> (type, a StreamerContext.SabrContext to echo back). */
    fun parseContextUpdate(payload: ByteArray): Pair<Long, ByteArray> {
        val m = SabrProto.read(payload)
        val type = m.longAt(1)
        return type to sabrContext(type, m.bytesAt(3) ?: ByteArray(0))
    }

    /** SabrRedirect: a length-delimited url at field 1. */
    fun parseRedirectUrl(payload: ByteArray): String? = SabrProto.read(payload).bytesAt(1)?.toString(Charsets.UTF_8)

    /** The header_id prefix on a MEDIA part -> (headerId, prefixBytes). */
    fun mediaHeaderId(payload: ByteArray): Pair<Int, Int> {
        val (v, s) = SabrProto.readVarint(payload, 0)
        return v.toInt() to s
    }
}
