package com.jtech.zemer.playback.sabr

import android.net.Uri
import android.util.Base64
import com.jtech.zemer.playback.VideoDecoderCaps
import com.jtech.zemer.playback.VideoQualityLogic
import com.jtech.zemer.playback.VideoQualityRung
import com.metrolist.innertube.NewPipeUtils
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeClient
import com.zemer.cipher.CipherDeobfuscator
import com.zemer.cipher.potoken.PoTokenGenerator
import com.zemer.cipher.potoken.PoTokenResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Dual-track (video + audio) SABR resolution — the video counterpart of [SabrPlayerResolver]. Fetches the
 * `/player` for the first ENABLED SABR-usable client that exposes SABR inputs, PINS the exact video itag
 * for the requested quality via `preferredVideoFormatId` (field 17, proven in `tests/sabr-video.mjs`) plus
 * the best audio, registers a shared [SabrVideoStream], and returns the `sabrvideo://<id>` uri + the full
 * quality ladder (so the in-player switcher offers the SAME rungs the DIRECT path does). Playback wraps
 * the video with the paired `sabraudio://<id>` in a `MergingMediaSource`. Isolated from DIRECT/RELAY.
 */
object SabrVideoResolver {
    private const val TAG = "SabrVideoResolver"
    private const val AUTO_HEIGHT = 720 // SABR must pin an itag even for AUTO — cap the automatic pick here
    private val poTokenGenerator = PoTokenGenerator()

    private class Spec(
        val key: String,
        val client: YouTubeClient,
        val label: String,
        val web: Boolean,
        val osName: String? = null,
        val osVersion: String? = null,
        val deviceMake: String? = null,
        val deviceModel: String? = null,
        val androidSdk: Int? = null,
    )

    // Same roster + priority as SabrPlayerResolver (the video+audio usable set is identical — proven in
    // tests/sabr-video-clients.mjs), keyed off the same SabrPlayerResolver.KEY_* toggles.
    private val ROSTER = listOf(
        Spec(SabrPlayerResolver.KEY_WEB_REMIX, YouTubeClient.WEB_REMIX, "WEB_REMIX (SABR)", web = true, osName = "Windows", osVersion = "10.0"),
        Spec(SabrPlayerResolver.KEY_VISIONOS, YouTubeClient.VISIONOS, "VISIONOS (SABR)", web = false, osName = "visionOS", osVersion = "26.5.23O471", deviceMake = "Apple", deviceModel = "RealityDevice17,1"),
        Spec(SabrPlayerResolver.KEY_TVHTML5_SIMPLY, YouTubeClient.TVHTML5_SIMPLY, "TVHTML5_SIMPLY (SABR)", web = true),
        Spec(SabrPlayerResolver.KEY_MWEB, YouTubeClient.MWEB, "MWEB (SABR)", web = true),
    )

    private fun decodeBase64(s: String): ByteArray {
        val normalized = s.trim().replace('-', '+').replace('_', '/')
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        return Base64.decode(padded, Base64.NO_WRAP)
    }

    /** A resolved SABR video session: the uri to play, the switcher ladder, and the rung actually pinned. */
    class SabrVideoResult(val uri: Uri, val ladder: List<VideoQualityRung>, val chosen: VideoQualityRung)

    // ---- resolve cache (the DIRECT perf-contract parity) -------------------------------------------
    // ONE /player resolution serves EVERY rung: the SABR request pins the itag per REQUEST (field 17)
    // against the same serverAbrStreamingUrl, so the cached Built (url + ustreamer + pot + per-itag
    // formats + ladder) lets a quality SWITCH and a prefetched ENTRY skip the network entirely — the
    // same "no second round-trip" contract DIRECT keeps via videoRungUrls/prefetchVideoRendition.
    private class Cached(val built: Built, val atMs: Long)
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Cached>()
    private const val CACHE_TTL_MS = 45L * 60 * 1000 // well under the ~6h URL expiry
    private const val CACHE_MAX = 8

    /** Drop a stale/broken cached resolution (a video-mode error invalidates, like DIRECT's caches). */
    fun invalidate(videoId: String) {
        cache.remove(videoId)
    }

    private fun cached(videoId: String): Built? =
        cache[videoId]?.takeIf { android.os.SystemClock.elapsedRealtime() - it.atMs < CACHE_TTL_MS }?.built

    private fun cachePut(videoId: String, built: Built) {
        if (cache.size >= CACHE_MAX) cache.entries.minByOrNull { it.value.atMs }?.let { cache.remove(it.key) }
        cache[videoId] = Cached(built, android.os.SystemClock.elapsedRealtime())
    }

    /**
     * Background warm-up (DIRECT's prefetchVideoRendition parity): resolve + cache while the Song/Video
     * pill is showing, so the actual Video tap is served from the cache with no network round-trip.
     * Never registers a stream (no bytes move until the tap). Silent on failure.
     */
    suspend fun prefetch(videoId: String, enabled: Set<String>) = withContext(Dispatchers.IO) {
        if (cached(videoId) != null) return@withContext
        firstWorkingBuilt(videoId, enabled, VideoQualityLogic.AUTO, maxAutoBitrateKbps = null)?.let { cachePut(videoId, it) }
    }

    /**
     * Resolve [videoId] for video playback over the first working [enabled] client at [targetLabel]
     * (a quality label like "720p", or [VideoQualityLogic.AUTO], whose pick honours [maxAutoBitrateKbps]
     * — the same metered-aware cap as DIRECT's automatic pick; an explicit label is never capped).
     * Registers the shared stream and returns the uri + ladder + chosen rung, or null (caller reverts
     * to audio). Served from the resolve cache when fresh (an entry after prefetch, a quality switch).
     */
    suspend fun resolve(videoId: String, enabled: Set<String>, targetLabel: String, maxAutoBitrateKbps: Int? = null, cpn: () -> String? = { null }): SabrVideoResult? =
        withContext(Dispatchers.IO) {
            val (built, rung) = builtFor(videoId, enabled, targetLabel, maxAutoBitrateKbps) ?: return@withContext null
            val config = configForRung(built, rung, cpn) ?: return@withContext null
            SabrVideoRegistry.put(videoId, SabrVideoStream(config, SabrStreamResolver.sharedClient()))
            SabrVideoResult(SabrVideoRegistry.videoUri(videoId), built.ladder, rung)
        }

    /** The cached Built + target rung when fresh, else a network build (cached for the next call). */
    private suspend fun builtFor(videoId: String, enabled: Set<String>, targetLabel: String, maxAutoBitrateKbps: Int?): Pair<Built, VideoQualityRung>? {
        cached(videoId)?.let { hit ->
            val rung = pickRung(hit.ladder, targetLabel, maxAutoBitrateKbps)
            if (rung != null && hit.videoFormats.containsKey(rung.itag)) {
                Timber.tag(TAG).d("SABR video cache hit for $videoId -> ${rung.label}")
                return hit to rung
            }
        }
        val built = firstWorkingBuilt(videoId, enabled, targetLabel, maxAutoBitrateKbps) ?: return null
        cachePut(videoId, built)
        return built to built.chosen
    }

    /** A [SabrVideoConfig] for [rung], cloned from [built]'s base session inputs (no network). [cpn] is
     * injected per resolve (never cached) so a cache hit still stamps the CURRENT listen's nonce. */
    private fun configForRung(built: Built, rung: VideoQualityRung, cpn: () -> String?): SabrVideoConfig? {
        val fmt = built.videoFormats[rung.itag] ?: return null
        val base = built.config
        return SabrVideoConfig(
            sabrUrl = base.sabrUrl,
            ustreamerConfig = base.ustreamerConfig,
            videoFormat = fmt,
            audioFormat = base.audioFormat,
            poToken = base.poToken,
            clientInfo = base.clientInfo,
            userAgent = base.userAgent,
            nTransform = base.nTransform,
            urlPot = base.urlPot,
            streamClientLabel = base.streamClientLabel,
            videoMimeType = rung.mimeType,
            videoBitrate = rung.bitrate,
            cpn = cpn,
        )
    }

    /** The mime of the video track SABR downloaded, so the caller picks the muxer container (mp4/webm). */
    class VideoDownloadInfo(val videoMimeType: String, val audioMimeType: String, val webm: Boolean)

    /**
     * Download the WHOLE video + audio for [videoId] (over the first working [enabled] client at
     * [targetLabel]) to [videoFile] and [audioFile], byte-exact. AUTO honours [maxAutoBitrateKbps]
     * (DIRECT parity: only the automatic pick is metered-capped; an explicit label downloads as chosen).
     * Returns null on an incomplete drain (a capped client) so the caller retries / falls back — a
     * truncated track is never muxed.
     */
    suspend fun download(videoId: String, enabled: Set<String>, targetLabel: String, maxAutoBitrateKbps: Int?, videoFile: File, audioFile: File): VideoDownloadInfo? =
        withContext(Dispatchers.IO) {
            val pair = builtFor(videoId, enabled, targetLabel, maxAutoBitrateKbps) ?: return@withContext null
            val config = configForRung(pair.first, pair.second, cpn = { null }) ?: return@withContext null
            val videoBuf = SabrBuffer(config.videoFormat.contentLength)
            val audioBuf = SabrBuffer(config.audioFormat.contentLength)
            SabrVideoSession(config, SabrStreamResolver.sharedClient(), videoBuf, audioBuf).run() // blocks until drained
            if (!drainWhole(videoBuf, config.videoFormat.contentLength) || !drainWhole(audioBuf, config.audioFormat.contentLength)) {
                Timber.tag(TAG).w("SABR video download incomplete for $videoId: video ${videoBuf.available()}/${config.videoFormat.contentLength}, audio ${audioBuf.available()}/${config.audioFormat.contentLength}")
                return@withContext null
            }
            writeBuffer(videoBuf, videoFile)
            writeBuffer(audioBuf, audioFile)
            if (videoFile.length() <= 0L || audioFile.length() <= 0L) return@withContext null
            val webm = config.videoMimeType.contains("webm")
            VideoDownloadInfo(config.videoMimeType, if (webm) "audio/webm" else "audio/mp4", webm)
        }

    private fun drainWhole(buf: SabrBuffer, contentLength: Long): Boolean =
        contentLength > 0 && buf.available() >= contentLength

    private fun writeBuffer(buf: SabrBuffer, file: File) {
        val size = buf.available()
        file.outputStream().use { out ->
            val chunk = ByteArray(64 * 1024)
            var pos = 0L
            while (pos < size) {
                val n = buf.read(pos, chunk, 0, chunk.size)
                if (n <= 0) break
                out.write(chunk, 0, n)
                pos += n
            }
        }
    }

    private class Built(
        val config: SabrVideoConfig,
        val ladder: List<VideoQualityRung>,
        val chosen: VideoQualityRung,
        /** Every ladder rung's wire format (itag -> lastModified + contentLength) for no-network switches. */
        val videoFormats: Map<Int, SabrMessages.Format>,
    )

    /** Resolve the first ENABLED client that exposes SABR video inputs into a ready [Built]. */
    private suspend fun firstWorkingBuilt(videoId: String, enabled: Set<String>, targetLabel: String, maxAutoBitrateKbps: Int?): Built? {
        val visitorData = YouTube.visitorData ?: return null
        val pot = poTokenGenerator.getWebClientPoToken(videoId, visitorData) ?: return null
        val sts = NewPipeUtils.getSignatureTimestamp(videoId).getOrNull()
        for (spec in ROSTER) {
            if (spec.key !in enabled) continue
            val built = build(spec, videoId, pot, sts, targetLabel, maxAutoBitrateKbps)
            if (built != null) return built
        }
        return null
    }

    /**
     * Pick the rung for [targetLabel]. AUTO (selectRung returns null) mirrors DIRECT's automatic pick:
     * capped at [AUTO_HEIGHT] AND at [maxAutoBitrateKbps] (the metered-aware cap) — an explicit label is
     * never capped (the "explicit quality is honoured on every connection" rule).
     */
    private fun pickRung(ladder: List<VideoQualityRung>, targetLabel: String, maxAutoBitrateKbps: Int?): VideoQualityRung? =
        VideoQualityLogic.selectRung(ladder, targetLabel)
            ?: ladder.filter { it.height <= AUTO_HEIGHT && (maxAutoBitrateKbps == null || it.bitrate <= maxAutoBitrateKbps * 1000) }
                .maxByOrNull { it.height }
            ?: ladder.filter { it.height <= AUTO_HEIGHT }.maxByOrNull { it.height }
            ?: ladder.minByOrNull { it.height }

    private suspend fun build(spec: Spec, videoId: String, pot: PoTokenResult, sts: Int?, targetLabel: String, maxAutoBitrateKbps: Int?): Built? {
        return try {
            val response = YouTube.player(
                videoId,
                client = spec.client,
                signatureTimestamp = if (spec.web) sts else null,
                webPlayerPot = if (spec.web) pot.playerRequestPoToken else null,
            ).getOrNull() ?: return null
            if (response.playabilityStatus.status != "OK") return null
            val streaming = response.streamingData ?: return null
            val sabrUrl = streaming.serverAbrStreamingUrl ?: return null
            val ustreamer = response.playerConfig?.mediaCommonConfig?.mediaUstreamerRequestConfig?.videoPlaybackUstreamerConfig
                ?: return null

            val audio = streaming.adaptiveFormats.filter { it.isAudio && it.isOriginal }.maxByOrNull { it.bitrate } ?: return null
            val audioLen = audio.contentLength ?: return null
            // The same ladder the DIRECT switcher renders, minus progressive (SABR video is dual-track,
            // video-only + audio) and minus rungs this device can't decode (never pin an undecodable itag).
            val ladder = VideoQualityLogic.rungs(streaming).filter { !it.progressive && VideoDecoderCaps.supports(it) }
            if (ladder.isEmpty()) return null
            // Every rung's wire format, so a later quality switch clones the config with NO network.
            val videoFormats = buildMap {
                for (r in ladder) {
                    val f = streaming.adaptiveFormats.firstOrNull { it.itag == r.itag } ?: continue
                    val len = f.contentLength ?: continue
                    put(r.itag, SabrMessages.Format(f.itag, f.lastModified ?: 0L, len))
                }
            }
            val chosen = pickRung(ladder.filter { videoFormats.containsKey(it.itag) }, targetLabel, maxAutoBitrateKbps) ?: return null
            val videoFmt = streaming.adaptiveFormats.firstOrNull { it.itag == chosen.itag } ?: return null
            val videoLen = videoFmt.contentLength ?: return null

            Timber.tag(TAG).d("SABR video resolved $videoId via ${spec.label}: video itag=${chosen.itag} ${chosen.label}, audio itag=${audio.itag}")
            val nTransform: (String) -> String =
                if (spec.web) { url -> runBlocking { CipherDeobfuscator.transformNParamInUrl(url) } } else { url -> url }
            val config = SabrVideoConfig(
                sabrUrl = sabrUrl,
                ustreamerConfig = decodeBase64(ustreamer),
                videoFormat = SabrMessages.Format(videoFmt.itag, videoFmt.lastModified ?: 0L, videoLen),
                audioFormat = SabrMessages.Format(audio.itag, audio.lastModified ?: 0L, audioLen),
                poToken = decodeBase64(pot.playerRequestPoToken),
                clientInfo = SabrMessages.ClientInfo(
                    clientName = spec.client.clientId.toInt(),
                    clientVersion = spec.client.clientVersion,
                    osName = spec.osName,
                    osVersion = spec.osVersion,
                    deviceMake = spec.deviceMake,
                    deviceModel = spec.deviceModel,
                    androidSdkVersion = spec.androidSdk,
                ),
                userAgent = spec.client.userAgent,
                nTransform = nTransform,
                urlPot = if (spec.web) pot.streamingDataPoToken else null,
                streamClientLabel = spec.label,
                videoMimeType = videoFmt.mimeType,
                videoBitrate = videoFmt.bitrate,
            )
            Built(config, ladder, chosen, videoFormats)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "SABR video resolve via ${spec.label} failed for $videoId: ${e.message}")
            null
        }
    }
}
