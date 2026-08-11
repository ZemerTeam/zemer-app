package com.jtech.zemer.playback

import android.media.MediaCodecList
import java.util.concurrent.ConcurrentHashMap

/**
 * Device decoder capability gate for quality rungs: can this device decode a rung's codec at its
 * resolution? Backed by [MediaCodecList] (framework — zero APK size), memoised per (mime, w, h).
 * WEB_REMIX's 1440p/2160p rungs are vp9-only (hard data, tests/video-qualities.mjs), so without this
 * gate a weak SoC would be offered a 4K rung it can only error on. Fail-open on an unknown codec
 * string is deliberately NOT done — a rung whose decoder we can't even name is not offered.
 */
object VideoDecoderCaps {
    private val cache = ConcurrentHashMap<String, Boolean>()

    fun supports(rung: VideoQualityRung): Boolean {
        val decoderMime = VideoQualityLogic.decoderMimeType(rung.mimeType) ?: return false
        if (rung.width <= 0 || rung.height <= 0) return true
        return cache.getOrPut("$decoderMime:${rung.width}x${rung.height}") {
            runCatching { querySupport(decoderMime, rung.width, rung.height) }.getOrDefault(false)
        }
    }

    private fun querySupport(decoderMime: String, width: Int, height: Int): Boolean =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            !info.isEncoder &&
                info.supportedTypes.any { it.equals(decoderMime, ignoreCase = true) } &&
                runCatching {
                    info.getCapabilitiesForType(decoderMime).videoCapabilities
                        ?.isSizeSupported(width, height) == true
                }.getOrDefault(false)
        }
}
