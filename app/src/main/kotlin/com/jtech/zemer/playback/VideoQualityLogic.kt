package com.jtech.zemer.playback

import com.metrolist.innertube.models.response.PlayerResponse

/**
 * One selectable rung of a video's quality ladder — the UI/selection model behind the in-player
 * quality switcher and quality-aware downloads.
 *
 * @param label the server's qualityLabel ("1080p", "720p60") — the switcher's display string and the
 *   stable selection vocabulary.
 * @param progressive true for a muxed progressive format (carries its own audio; plays/downloads as
 *   ONE stream — today's ≤720p path). False = adaptive video-only: playback merges it with the audio
 *   stream ([MergingMediaSource][androidx.media3.exoplayer.source.MergingMediaSource]) and a download
 *   fetches video+audio separately and remuxes on-device.
 * @param mp4Avc true when the rung is avc1-in-mp4 — the on-device remux (framework MediaMuxer, MP4
 *   output) requirement; non-avc rungs are stream-only.
 */
data class VideoQualityRung(
    val label: String,
    val height: Int,
    val width: Int,
    val itag: Int,
    val bitrate: Int,
    val mimeType: String,
    val progressive: Boolean,
    val mp4Avc: Boolean,
)

/**
 * Pure quality-ladder logic — no player, no Android, fully JVM-unit-tested ([VideoQualityLogic]'s
 * relationship to [VideoModeController] mirrors [VideoModeLogic]'s). The ladder is built from a live
 * player response's streamingData and drives BOTH the streaming switcher and download quality.
 *
 * Hard data (tests/video-qualities.mjs, 2026-08-11): the app's stream resolution serves an avc1
 * adaptive video-only ladder (144p…1080p, itags 160/133/134/135/136/137) plus progressive muxed 360p
 * (itag 18, sometimes 720p itag 22) and opus/aac audio; for uploads that HAVE them it also serves
 * vp9-only 1440p/2160p (itags 271/313) — measured live streaming + full-download of the 369.6 MB
 * 2160p file. So a rung is usually avc1/mp4 (remux to MP4) but can be vp9 (remux to WebM on API 29+);
 * av01 is stream-only. Codecs are ranked avc1 > vp9 > av01, and the whole ladder is
 * decoder-capability-filtered ([VideoDecoderCaps]) so a device is never offered a rung it can't play.
 */
object VideoQualityLogic {
    /** The persisted/selection sentinel for "no explicit rung — the automatic progressive pick". */
    const val AUTO = "auto"

    /**
     * The Settings default-quality TARGET heights (the fixed preference vocabulary; the in-player
     * switcher shows the video's real ladder instead). Selection resolves a target to the best rung
     * the video actually serves at or below it — see [selectRung].
     */
    val TARGET_HEIGHTS = listOf(2160, 1440, 1080, 720, 480, 360)

    /** Mid-play stalls within this window that trigger a downgrade — see [shouldDowngradeForRebuffer]. */
    const val REBUFFER_DOWNGRADE_COUNT = 2
    const val REBUFFER_WINDOW_MS = 45_000L

    /**
     * The rebuffer guard's decision: downgrade only when [REBUFFER_DOWNGRADE_COUNT] mid-play stalls
     * land within [REBUFFER_WINDOW_MS] ([stallTimesMs]; seek-caused buffering already excluded by the
     * caller). REQUIRING two stalls is deliberate — a SINGLE transient buffering blip (a momentary
     * network hiccup, routine on any connection) must NOT permanently drop the user's chosen quality;
     * only a repeated pattern means the rung genuinely can't sustain.
     */
    fun shouldDowngradeForRebuffer(stallTimesMs: List<Long>, nowMs: Long): Boolean =
        stallTimesMs.count { nowMs - it <= REBUFFER_WINDOW_MS } >= REBUFFER_DOWNGRADE_COUNT

    /**
     * The rung a stall-downgrade lands on: exactly ONE step DOWN from [currentLabel] in the high→low
     * ladder, or null at the bottom/unknown. Deliberately single-step (not a bandwidth-gated jump):
     * playback settles on the highest rung that actually plays. A rung's `bitrate` is its PEAK (well
     * above its sustained average) and the bandwidth estimate is depressed right after a stall, so a
     * bandwidth-gated jump over-dropped (2160p → 480p when 720p was fine).
     */
    fun rungBelow(rungs: List<VideoQualityRung>, currentLabel: String): VideoQualityRung? {
        val index = rungs.indexOfFirst { it.label == currentLabel }
        return if (index >= 0 && index + 1 < rungs.size) rungs[index + 1] else null
    }

    /** A muxed progressive video format (video mime + its own audio) — plays/downloads as one stream. */
    fun isProgressiveVideo(format: PlayerResponse.StreamingData.Format): Boolean =
        format.mimeType.startsWith("video") && (format.audioQuality != null || format.audioChannels != null)

    /** An adaptive video-only format (video mime, no audio track) — needs the audio merge/remux partner. */
    fun isVideoOnly(format: PlayerResponse.StreamingData.Format): Boolean =
        format.mimeType.startsWith("video") && format.audioQuality == null && format.audioChannels == null

    private fun codecOf(mimeType: String): String {
        val start = mimeType.indexOf("codecs=\"")
        if (start < 0) return ""
        val body = mimeType.substring(start + 8).substringBefore('"')
        return body.substringBefore('.').substringBefore(',')
    }

    /** avc1 first (universal hardware decode), then vp9, then av01, then unknown. */
    fun codecRank(mimeType: String): Int = when (codecOf(mimeType)) {
        "avc1" -> 0
        "vp9", "vp09" -> 1
        "av01" -> 2
        else -> 3
    }

    fun isMp4Avc(mimeType: String): Boolean =
        mimeType.startsWith("video/mp4") && codecOf(mimeType) == "avc1"

    /**
     * Whether a rung can be DOWNLOADED: progressive plays as-is; adaptive avc1 remuxes to MP4 and
     * adaptive vp9 remuxes to WebM (both framework-MediaMuxer outputs — [com.jtech.zemer.utils.VideoMuxer]).
     * av01/unknown codecs are stream-only (no reliable framework mux target), and the WebM path needs
     * [opusWebmMuxSupported] (the framework muxer accepts Opus in WebM only from API 29).
     */
    fun isDownloadableRung(rung: VideoQualityRung, opusWebmMuxSupported: Boolean = true): Boolean =
        rung.progressive || rung.mp4Avc || (opusWebmMuxSupported && codecRank(rung.mimeType) == 1)

    private fun labelOf(format: PlayerResponse.StreamingData.Format): String =
        format.qualityLabel ?: "${format.height ?: 0}p"

    /**
     * The quality ladder: ONE format per qualityLabel, sorted high→low by height. A progressive format
     * wins its label's rung over an adaptive one (single stream, no merge); within adaptive candidates
     * the better codec ([codecRank]) wins, then the higher bitrate.
     */
    fun ladderFormats(streamingData: PlayerResponse.StreamingData?): List<PlayerResponse.StreamingData.Format> {
        if (streamingData == null) return emptyList()
        val byLabel = LinkedHashMap<String, PlayerResponse.StreamingData.Format>()
        val consider = { format: PlayerResponse.StreamingData.Format ->
            val label = labelOf(format)
            val current = byLabel[label]
            val take = when {
                current == null -> true
                isProgressiveVideo(current) -> false
                isProgressiveVideo(format) -> true
                codecRank(format.mimeType) != codecRank(current.mimeType) ->
                    codecRank(format.mimeType) < codecRank(current.mimeType)
                else -> format.bitrate > current.bitrate
            }
            if (take) byLabel[label] = format
        }
        streamingData.formats.orEmpty().filter(::isProgressiveVideo).forEach(consider)
        streamingData.adaptiveFormats.filter(::isVideoOnly).forEach(consider)
        return byLabel.values.sortedByDescending { it.height ?: 0 }
    }

    /** The UI/selection model of [ladderFormats]. */
    fun rungs(streamingData: PlayerResponse.StreamingData?): List<VideoQualityRung> =
        ladderFormats(streamingData).map { format ->
            VideoQualityRung(
                label = labelOf(format),
                height = format.height ?: 0,
                width = format.width ?: 0,
                itag = format.itag,
                bitrate = format.bitrate,
                mimeType = format.mimeType,
                progressive = isProgressiveVideo(format),
                mp4Avc = isMp4Avc(format.mimeType),
            )
        }

    /**
     * The Android decoder MIME a rung needs ("video/avc", "video/x-vnd.on2.vp9", "video/av01"), or
     * null for an unknown codec — feeds the device-capability gate ([VideoDecoderCaps]) so the
     * switcher never offers a rung the device cannot decode (vp9 2160p on a weak SoC).
     */
    fun decoderMimeType(mimeType: String): String? = when (codecOf(mimeType)) {
        "avc1" -> "video/avc"
        "vp9", "vp09" -> "video/x-vnd.on2.vp9"
        "av01" -> "video/av01"
        else -> null
    }

    /** The ladder format with this exact itag (across progressive AND adaptive), or null. */
    fun formatForItag(
        streamingData: PlayerResponse.StreamingData?,
        itag: Int,
    ): PlayerResponse.StreamingData.Format? =
        (streamingData?.formats.orEmpty() + streamingData?.adaptiveFormats.orEmpty())
            .filter { isProgressiveVideo(it) || isVideoOnly(it) }
            .firstOrNull { it.itag == itag }

    /** The leading digits of a rung label ("1080p60" → 1080); null when the label carries none. */
    fun heightOfLabel(label: String): Int? =
        label.takeWhile { it.isDigit() }.toIntOrNull()

    /**
     * The rung an explicit quality choice selects, treating [targetLabel] as a TARGET height: the
     * exact label when present, else the highest rung not above the target, else the lowest rung
     * (fail-soft — an explicit choice always yields SOME rung when a ladder exists). [AUTO]/null,
     * an unparsable label, or an empty ladder return null (callers then use the automatic pick).
     * [downloadable] restricts to remux-capable rungs ([isDownloadableRung]).
     */
    fun selectRung(
        rungs: List<VideoQualityRung>,
        targetLabel: String?,
        downloadable: Boolean = false,
        opusWebmMuxSupported: Boolean = true,
    ): VideoQualityRung? {
        if (targetLabel == null || targetLabel == AUTO) return null
        val target = heightOfLabel(targetLabel) ?: return null
        val pool = if (downloadable) rungs.filter { isDownloadableRung(it, opusWebmMuxSupported) } else rungs
        if (pool.isEmpty()) return null
        pool.firstOrNull { it.label == targetLabel }?.let { return it }
        return pool.filter { it.height <= target }.maxByOrNull { it.height }
            ?: pool.minByOrNull { it.height }
    }
}
