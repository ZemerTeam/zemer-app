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

    /**
     * The stream URL for [rawMediaId]. The media id is the videoId (MediaItems set customCacheKey = id);
     * a `video:<id>` rendition key is reduced to its base id because the relay serves audio only, so an
     * accidental video-mode open degrades to audio rather than failing.
     */
    fun streamUrl(rawMediaId: String): String = "$BASE/stream?v=${videoId(rawMediaId)}"

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
