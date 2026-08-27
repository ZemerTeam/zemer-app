package com.jtech.zemer.utils.mp4

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer

/**
 * Framework-backed container remuxing (no re-encode) used before the pure-Kotlin taggers:
 * - [flattenMp4] rebuilds a fragmented/DASH M4A into a flat `moov`+`mdat` MP4 so
 *   [Mp4MetadataWriter] can retag it (it refuses fragmented input).
 * - [webmOpusToOgg] rewraps a WebM/Opus stream into a standards Ogg/Opus file so
 *   [com.jtech.zemer.utils.ogg.OggOpusTagger] can tag it and MediaStore accepts it as
 *   audio/ogg. Ogg output needs `MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG` (API 29+).
 *
 * Both are stream-copies (MediaExtractor -> MediaMuxer), so no quality is lost. They wrap
 * the same failure discipline as VideoMuxer and never throw to the caller.
 */
object AudioRemux {

    val oggMuxSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** Flatten a (possibly fragmented) MP4/M4A to a flat MP4. False on failure (output deleted). */
    fun flattenMp4(input: File, output: File): Boolean =
        remuxAudio(input, output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4, "audio/")

    /** Rewrap WebM/Opus to Ogg/Opus. False on failure or pre-Q (output deleted). */
    fun webmOpusToOgg(input: File, output: File): Boolean {
        if (!oggMuxSupported) return false
        return remuxAudio(input, output, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG, "audio/")
    }

    private fun remuxAudio(input: File, output: File, muxerFormat: Int, mimePrefix: String): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(input.absolutePath)
            val inFormat = selectTrack(extractor, mimePrefix) ?: return fail(output, "no audio track")
            muxer = MediaMuxer(output.absolutePath, muxerFormat)
            val outTrack = muxer.addTrack(inFormat)
            muxer.start()

            val bufSize = runCatching { inFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) }
                .getOrDefault(0).coerceAtLeast(1 shl 20)
            val buffer = ByteBuffer.allocate(bufSize)
            val info = MediaCodec.BufferInfo()
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = extractor.sampleTime
                info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else {
                    0
                }
                muxer.writeSampleData(outTrack, buffer, info)
                extractor.advance()
            }
            muxer.stop()
            output.isFile && output.length() > 0
        } catch (t: Throwable) {
            Timber.e(t, "AudioRemux: remux failed (${input.name} -> ${output.name})")
            output.delete()
            false
        } finally {
            runCatching { muxer?.release() }
            extractor.release()
        }
    }

    private fun fail(output: File, why: String): Boolean {
        Timber.w("AudioRemux: $why")
        output.delete()
        return false
    }

    private fun selectTrack(extractor: MediaExtractor, mimePrefix: String): MediaFormat? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith(mimePrefix) == true) {
                extractor.selectTrack(i)
                return format
            }
        }
        return null
    }
}
