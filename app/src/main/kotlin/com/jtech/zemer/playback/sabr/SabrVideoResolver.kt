package com.jtech.zemer.playback.sabr

import android.net.Uri
import android.util.Base64
import com.jtech.zemer.playback.VideoDecoderCaps
import com.jtech.zemer.playback.VideoQualityLogic
import com.jtech.zemer.playback.VideoQualityRung
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

    /**
     * A resolved SABR video session: the uri to play, the switcher ladder, the rung actually pinned, and
     * the ready (not yet started) [stream]. The stream is deliberately NOT registered here: the caller
     * installs it in [SabrVideoRegistry] only when it commits the swap on the main thread — registering
     * from the resolve (IO) thread destroyed the CURRENTLY-PLAYING stream before the caller's
     * stillOurs guard could veto, and an abandoned resolve then left playback parked on dead buffers.
     */
    class SabrVideoResult internal constructor(
        val uri: Uri,
        val ladder: List<VideoQualityRung>,
        val chosen: VideoQualityRung,
        internal val stream: SabrVideoStream,
    )

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
     * Returns the uri + ladder + chosen rung + the READY stream, or null (caller reverts to audio).
     * The caller registers [SabrVideoResult.stream] when it commits the swap (see the result doc) —
     * nothing here touches the registry, so an abandoned resolve never disturbs live playback.
     * Served from the resolve cache when fresh (an entry after prefetch, a quality switch).
     */
    suspend fun resolve(videoId: String, enabled: Set<String>, targetLabel: String, maxAutoBitrateKbps: Int? = null, cpn: () -> String? = { null }): SabrVideoResult? =
        withContext(Dispatchers.IO) {
            val (built, rung) = builtFor(videoId, enabled, targetLabel, maxAutoBitrateKbps) ?: return@withContext null
            val config = configForRung(built, rung, cpn) ?: return@withContext null
            val stream = SabrVideoStream(videoId, config, SabrStreamResolver.sharedClient())
            SabrVideoResult(SabrVideoRegistry.videoUri(videoId), built.ladder, rung, stream)
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
     * injected per resolve (never cached) so a cache hit still stamps the CURRENT listen's nonce.
     * [audioFormat] overrides the streaming audio pick — the download path passes its container-matched
     * partner (webm video -> Opus, mp4 video -> AAC) so the on-device mux inputs always agree. */
    private fun configForRung(
        built: Built,
        rung: VideoQualityRung,
        cpn: () -> String?,
        audioFormat: SabrMessages.Format = built.config.audioFormat,
    ): SabrVideoConfig? {
        val fmt = built.videoFormats[rung.itag] ?: return null
        val base = built.config
        return SabrVideoConfig(
            sabrUrl = base.sabrUrl,
            ustreamerConfig = base.ustreamerConfig,
            videoFormat = fmt,
            audioFormat = audioFormat,
            poToken = base.poToken,
            clientInfo = base.clientInfo,
            userAgent = base.userAgent,
            nTransform = base.nTransform,
            urlPot = base.urlPot,
            streamClientLabel = base.streamClientLabel,
            videoMimeType = rung.mimeType,
            videoBitrate = rung.bitrate,
            clientKey = base.clientKey,
            durationMs = base.durationMs,
            cpn = cpn,
        )
    }

    /** The mime of the video track SABR downloaded, so the caller picks the muxer container (mp4/webm). */
    class VideoDownloadInfo(val videoMimeType: String, val audioMimeType: String, val webm: Boolean)

    /**
     * Download the WHOLE video + audio for [videoId] (over the first working [enabled] client at
     * [targetLabel]) to [videoFile] and [audioFile], byte-exact. AUTO honours [maxAutoBitrateKbps]
     * (DIRECT parity: only the automatic pick is metered-capped; an explicit label downloads as chosen).
     * DIRECT's download gates apply (YTPlayerUtils parity): the rung pick is restricted to REMUX-CAPABLE
     * rungs ([VideoQualityLogic.isDownloadableRung] — no av01, and webm/vp9 only where the framework
     * muxer accepts Opus-in-WebM, API 29+), and the audio partner is CONTAINER-MATCHED to the chosen
     * rung (mp4/avc -> AAC, webm/vp9 -> Opus) so the mux inputs always agree — an ungated pick drained
     * hundreds of MB into a deterministic INCOMPATIBLE mux. Returns null on an incomplete drain (a
     * capped client) so the caller retries / falls back — a truncated track is never muxed. Runs
     * [kotlinx.coroutines.runInterruptible] so a cancelled download Job interrupts the blocking drain
     * immediately, and reports [onProgress] (reassembled bytes across both tracks / total).
     */
    suspend fun download(
        videoId: String,
        enabled: Set<String>,
        targetLabel: String,
        maxAutoBitrateKbps: Int?,
        videoFile: File,
        audioFile: File,
        opusWebmMuxSupported: Boolean = android.os.Build.VERSION.SDK_INT >= 29,
        onProgress: ((bytesDownloaded: Long, totalBytes: Long) -> Unit)? = null,
    ): VideoDownloadInfo? =
        withContext(Dispatchers.IO) {
            val built = builtFor(videoId, enabled, targetLabel, maxAutoBitrateKbps)?.first ?: return@withContext null
            // Re-pick with the download gates (the builtFor pick is the STREAMING pick, which may land
            // on a rung no on-device mux can save).
            val rung = pickRung(
                built.ladder.filter { built.videoFormats.containsKey(it.itag) },
                targetLabel, maxAutoBitrateKbps,
                downloadable = true, opusWebmMuxSupported = opusWebmMuxSupported,
            ) ?: return@withContext null
            val webm = rung.mimeType.contains("webm")
            val audioPick = (if (webm) built.audioWebm else built.audioMp4)
                ?: run {
                    // No container-matched audio in the response — fall back to the streaming pick
                    // (the mux classifies a mismatch as INCOMPATIBLE, same as before this gate).
                    Timber.tag(TAG).w("SABR video download: no ${if (webm) "webm" else "mp4"} audio for $videoId, falling back to the streaming pick")
                    AudioPick(built.config.audioFormat, if (webm) "audio/webm" else "audio/mp4")
                }
            val config = configForRung(built, rung, cpn = { null }, audioFormat = audioPick.format) ?: return@withContext null
            if (!SabrBuffer.lengthValid(config.videoFormat.contentLength) || !SabrBuffer.lengthValid(config.audioFormat.contentLength)) return@withContext null
            val totalBytes = config.videoFormat.contentLength + config.audioFormat.contentLength
            val videoBuf = SabrBuffer(config.videoFormat.contentLength, SabrSpool.downloadPart(videoId, "dv"))
            val audioBuf = SabrBuffer(config.audioFormat.contentLength, SabrSpool.downloadPart(videoId, "da"))
            try {
                val session = SabrVideoSession(
                    config, SabrStreamResolver.sharedClient(), videoBuf, audioBuf,
                    onProgress = { onProgress?.invoke(it, totalBytes) },
                    onIncomplete = { config.clientKey?.let { SabrPlayerResolver.recordStall(videoId, it) } },
                )
                // Blocks until drained; a Job cancel interrupts the in-flight OkHttp call.
                kotlinx.coroutines.runInterruptible { session.run() }
                if (!drainWhole(videoBuf, config.videoFormat.contentLength) || !drainWhole(audioBuf, config.audioFormat.contentLength)) {
                    Timber.tag(TAG).w("SABR video download incomplete for $videoId: video ${videoBuf.available()}/${config.videoFormat.contentLength}, audio ${audioBuf.available()}/${config.audioFormat.contentLength}")
                    return@withContext null
                }
                writeBuffer(videoBuf, videoFile)
                writeBuffer(audioBuf, audioFile)
                if (videoFile.length() <= 0L || audioFile.length() <= 0L) return@withContext null
                VideoDownloadInfo(config.videoMimeType, audioPick.mimeType, webm)
            } finally {
                videoBuf.release(deleteFile = true)
                audioBuf.release(deleteFile = true)
            }
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

    /** One audio format candidate + its mime (the wire [SabrMessages.Format] carries no mime). */
    internal class AudioPick(val format: SabrMessages.Format, val mimeType: String)

    private class Built(
        val config: SabrVideoConfig,
        val ladder: List<VideoQualityRung>,
        val chosen: VideoQualityRung,
        /** Every ladder rung's wire format (itag -> lastModified + contentLength) for no-network switches. */
        val videoFormats: Map<Int, SabrMessages.Format>,
        /** Best AAC (audio/mp4) candidate — the download partner for an mp4/avc rung. */
        val audioMp4: AudioPick? = null,
        /** Best Opus (audio/webm) candidate — the download partner for a webm/vp9 rung. */
        val audioWebm: AudioPick? = null,
    )

    /** Resolve the first ENABLED client that exposes SABR video inputs into a ready [Built]. */
    private suspend fun firstWorkingBuilt(videoId: String, enabled: Set<String>, targetLabel: String, maxAutoBitrateKbps: Int?): Built? {
        val visitorData = YouTube.visitorData ?: return null
        val pot = poTokenGenerator.getWebClientPoToken(videoId, visitorData) ?: return null
        // STS from the cipher player - the single sts/decipher source (see SabrPlayerResolver).
        val sts = try {
            CipherDeobfuscator.signatureTimestamp()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Cipher player STS fetch FAILED")
            null
        }
        // Stall fallback (shared with the audio resolver): a client that drained this id incomplete is
        // deprioritized, so a replay advances the roster instead of truncating identically forever.
        val stalled = SabrPlayerResolver.stalledFor(videoId)
        val order = ROSTER.filter { it.key in enabled && it.key !in stalled } +
            ROSTER.filter { it.key in enabled && it.key in stalled }
        for (spec in order) {
            val built = build(spec, videoId, pot, sts, targetLabel, maxAutoBitrateKbps)
            if (built != null) return built
        }
        return null
    }

    /**
     * Pick the rung for [targetLabel]. AUTO (selectRung returns null) mirrors DIRECT's automatic pick:
     * capped at [AUTO_HEIGHT] AND at [maxAutoBitrateKbps] (the metered-aware cap) — an explicit label is
     * never capped (the "explicit quality is honoured on every connection" rule). [downloadable]
     * restricts the WHOLE pool (explicit target and AUTO fallbacks alike) to remux-capable rungs
     * (DIRECT's YTPlayerUtils gate) — a download must never pick a rung no on-device mux can save.
     */
    internal fun pickRung(
        ladder: List<VideoQualityRung>,
        targetLabel: String,
        maxAutoBitrateKbps: Int?,
        downloadable: Boolean = false,
        opusWebmMuxSupported: Boolean = true,
    ): VideoQualityRung? {
        val pool = if (downloadable) ladder.filter { VideoQualityLogic.isDownloadableRung(it, opusWebmMuxSupported) } else ladder
        return VideoQualityLogic.selectRung(pool, targetLabel)
            ?: pool.filter { it.height <= AUTO_HEIGHT && (maxAutoBitrateKbps == null || it.bitrate <= maxAutoBitrateKbps * 1000) }
                .maxByOrNull { it.height }
            ?: pool.filter { it.height <= AUTO_HEIGHT }.maxByOrNull { it.height }
            ?: pool.minByOrNull { it.height }
    }

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

            val audioCandidates = streaming.adaptiveFormats.filter {
                it.isAudio && it.isOriginal && it.contentLength?.let(SabrBuffer::lengthValid) == true
            }
            val audio = audioCandidates.maxByOrNull { it.bitrate } ?: return null
            val audioLen = audio.contentLength ?: return null
            // The download partners, per container (mp4/avc -> AAC, webm/vp9 -> Opus) — see download().
            fun audioPick(container: String): AudioPick? =
                audioCandidates.filter { it.mimeType.startsWith("audio/$container") }
                    .maxByOrNull { it.bitrate }
                    ?.let { AudioPick(SabrMessages.Format(it.itag, it.lastModified ?: 0L, it.contentLength!!), it.mimeType) }
            // The same ladder the DIRECT switcher renders, minus progressive (SABR video is dual-track,
            // video-only + audio) and minus rungs this device can't decode (never pin an undecodable itag).
            val ladder = VideoQualityLogic.rungs(streaming).filter { !it.progressive && VideoDecoderCaps.supports(it) }
            if (ladder.isEmpty()) return null
            // Every rung's wire format, so a later quality switch clones the config with NO network.
            // Rungs whose contentLength the reassembly buffer cannot hold are excluded here, so neither
            // playback nor a download can ever pin one.
            val videoFormats = buildMap {
                for (r in ladder) {
                    val f = streaming.adaptiveFormats.firstOrNull { it.itag == r.itag } ?: continue
                    val len = f.contentLength?.takeIf(SabrBuffer::lengthValid) ?: continue
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
                clientKey = spec.key,
                // approxDurationMs: the byte<->time estimate the stream's seek-restart needs.
                durationMs = videoFmt.approxDurationMs?.toLongOrNull() ?: 0L,
            )
            Built(config, ladder, chosen, videoFormats, audioMp4 = audioPick("mp4"), audioWebm = audioPick("webm"))
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "SABR video resolve via ${spec.label} failed for $videoId: ${e.message}")
            null
        }
    }
}
