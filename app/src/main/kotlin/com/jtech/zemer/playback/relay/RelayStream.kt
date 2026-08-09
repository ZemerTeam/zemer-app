package com.jtech.zemer.playback.relay

import com.jtech.zemer.playback.VideoRendition

/** Pure builder for the Zemer relay URLs (see the handoff doc for the contract). */
object RelayStream {
    const val BASE = "https://stream.zemer.io"

    /** Audio stream for [rawMediaId] (a `video:` key reduces to its base id). */
    fun streamUrl(rawMediaId: String): String = "$BASE/stream?v=${videoId(rawMediaId)}"

    /** Ranged playback: audio for a plain id, 360p muxed mp4 (`&kind=video`) for a `video:` rendition key. */
    fun playbackUrl(rawMediaId: String): String {
        val id = videoId(rawMediaId)
        return if (VideoRendition.isVideoKey(rawMediaId)) "$BASE/stream?v=$id&kind=video" else "$BASE/stream?v=$id"
    }

    /** Full-file download endpoint (server-side chunked failover, one clean response) for a plain one-shot GET. */
    fun downloadUrl(rawMediaId: String): String = "$BASE/download?v=${videoId(rawMediaId)}"

    fun healthUrl(): String = "$BASE/health"

    /** The bare videoId, stripping a `video:` rendition prefix if present. */
    fun videoId(rawMediaId: String): String =
        if (VideoRendition.isVideoKey(rawMediaId)) VideoRendition.renditionId(rawMediaId) else rawMediaId
}
