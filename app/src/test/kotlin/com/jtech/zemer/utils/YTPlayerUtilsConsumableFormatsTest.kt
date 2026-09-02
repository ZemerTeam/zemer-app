package com.jtech.zemer.utils

import com.metrolist.innertube.models.response.PlayerResponse
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gate behind the stream-client table refresh on a "no playable format" resolution: only the
 * SABR-only shape (every client OK, not one consumable format) is a client-kill signal.
 */
class YTPlayerUtilsConsumableFormatsTest {

    private fun format(url: String? = null, signatureCipher: String? = null) =
        PlayerResponse.StreamingData.Format(
            itag = 251, url = url, mimeType = "audio/webm", bitrate = 1, width = null, height = null,
            contentLength = null, quality = "tiny", fps = null, qualityLabel = null, averageBitrate = null,
            audioQuality = null, approxDurationMs = null, audioSampleRate = null, audioChannels = null,
            loudnessDb = null, lastModified = null, signatureCipher = signatureCipher, audioTrack = null,
        )

    private fun streaming(formats: List<PlayerResponse.StreamingData.Format>?, adaptive: List<PlayerResponse.StreamingData.Format>) =
        PlayerResponse.StreamingData(formats = formats, adaptiveFormats = adaptive, expiresInSeconds = 1, serverAbrStreamingUrl = "https://sabr")

    @Test
    fun `a direct url or a signatureCipher in either list is consumable`() {
        assertTrue(YTPlayerUtils.hasConsumableFormats(streaming(null, listOf(format(url = "https://cdn")))))
        assertTrue(YTPlayerUtils.hasConsumableFormats(streaming(null, listOf(format(signatureCipher = "s=..")))))
        assertTrue(YTPlayerUtils.hasConsumableFormats(streaming(listOf(format(url = "https://cdn")), emptyList())))
    }

    @Test
    fun `the SABR-only shape has formats but nothing consumable`() {
        assertFalse(YTPlayerUtils.hasConsumableFormats(streaming(listOf(format()), listOf(format(), format()))))
        assertFalse(YTPlayerUtils.hasConsumableFormats(streaming(null, emptyList())))
        assertFalse(YTPlayerUtils.hasConsumableFormats(null))
    }
}
