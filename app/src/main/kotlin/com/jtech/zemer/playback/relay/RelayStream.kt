package com.jtech.zemer.playback.relay

import com.jtech.zemer.playback.VideoRendition

/**
 * The Zemer playback relay (see handoff-docs/zemer-app-filtered-playback-relay-request.md). In RELAY mode
 * the app hands ExoPlayer a URL on this whitelisted host instead of resolving `/player` + fetching from
 * `googlevideo` on-device, so playback works on kosher-filtered devices that block YouTube. The relay does
 * the resolution and the `googlevideo` fetch server-side and streams the audio (Opus/webm, itag 251, or
 * occasionally mp4) back over `zemer.io`, honoring Range requests.
 *
 * This object is deliberately tiny and pure so it is unit-testable and carries no Android dependency; the
 * MusicService relay data-source factory is the only consumer.
 */
object RelayStream {
    /** The relay host. Whitelisted, served over the same Cloudflare tunnel as the other *.zemer.io hosts. */
    const val BASE = "https://stream.zemer.io"

    /** The AUDIO stream URL for [rawMediaId] (a `video:` rendition key is reduced to its base id). */
    fun streamUrl(rawMediaId: String): String = "$BASE/stream?v=${videoId(rawMediaId)}"

    /**
     * The playback URL for a media key: the AUDIO stream for a plain id, or the 360p muxed VIDEO stream
     * (`&kind=video`, itag 18 mp4) for a `video:<id>` rendition key. The relay `404`s a video URL for an
     * audio-only id, which the player's error path reverts to audio. This is what the relay data-source
     * resolves each open to when streaming.
     */
    fun playbackUrl(rawMediaId: String): String {
        val id = videoId(rawMediaId)
        return if (VideoRendition.isVideoKey(rawMediaId)) "$BASE/stream?v=$id&kind=video" else "$BASE/stream?v=$id"
    }

    /**
     * The DOWNLOAD URL for [rawMediaId]. Distinct from [streamUrl]: `/download` resolves and pulls the
     * whole file server-side (internal per-chunk proxy failover) and returns ONE clean response with an
     * accurate Content-Length and clean close, so the app does a plain one-shot GET -> save. `/stream`
     * stays range-based for playback. Relay serves audio, so a `video:` rendition key reduces to its base.
     */
    fun downloadUrl(rawMediaId: String): String = "$BASE/download?v=${videoId(rawMediaId)}"

    /** The liveness/pool probe (`{"ok":true,"pool":N,"cached":M}`). */
    fun healthUrl(): String = "$BASE/health"

    /** The bare videoId for [rawMediaId], stripping a `video:` rendition prefix if present. */
    fun videoId(rawMediaId: String): String =
        if (VideoRendition.isVideoKey(rawMediaId)) VideoRendition.renditionId(rawMediaId) else rawMediaId
}
