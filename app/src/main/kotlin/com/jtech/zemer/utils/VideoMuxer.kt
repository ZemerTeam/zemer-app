package com.jtech.zemer.utils

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer

/**
 * On-device remux of a separately-downloaded video-only stream + audio stream into ONE playable file,
 * using the FRAMEWORK [MediaExtractor]/[MediaMuxer] — a pure sample copy (no re-encode, no new
 * native/library dependency, zero APK size). This is what lets a video download exceed YouTube's
 * progressive ceiling: >360p/720p rungs exist only as adaptive video-only formats, so the download
 * fetches video + audio and merges them here (avc1+AAC → MP4; vp9+Opus → WebM for the 1440p/2160p
 * rungs, which are vp9-only — hard data in tests/video-qualities.mjs).
 *
 * Samples are copied interleaved by timestamp (whichever track's next sample is earlier) so the
 * output is a normally-interleaved file, not two concatenated track blobs.
 */
object VideoMuxer {

    /**
     * Mux [videoFile]'s video track + [audioFile]'s audio track into [outputFile]. Returns true on
     * success; on ANY failure returns false and deletes the partial output (callers treat false as a
     * failed download attempt — never commit a half-muxed file).
     */
    fun mux(videoFile: File, audioFile: File, outputFile: File, webm: Boolean): Boolean {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            videoExtractor.setDataSource(videoFile.path)
            audioExtractor.setDataSource(audioFile.path)
            val videoFormat = selectTrack(videoExtractor, "video/")
                ?: error("no video track in ${videoFile.name}")
            val audioFormat = selectTrack(audioExtractor, "audio/")
                ?: error("no audio track in ${audioFile.name}")

            muxer = MediaMuxer(
                outputFile.path,
                if (webm) MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
            )
            val videoTrack = muxer.addTrack(videoFormat)
            val audioTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val buffer = ByteBuffer.allocateDirect(bufferSize(videoFormat, audioFormat))
            val info = MediaCodec.BufferInfo()
            var videoDone = false
            var audioDone = false
            // Interleave by presentation time: always advance the track whose next sample is earlier.
            while (!videoDone || !audioDone) {
                val videoTime = if (videoDone) Long.MAX_VALUE else videoExtractor.sampleTime
                val audioTime = if (audioDone) Long.MAX_VALUE else audioExtractor.sampleTime
                if (videoTime <= audioTime) {
                    videoDone = !writeSample(videoExtractor, muxer, videoTrack, buffer, info)
                } else {
                    audioDone = !writeSample(audioExtractor, muxer, audioTrack, buffer, info)
                }
            }
            muxer.stop()
            true
        } catch (t: Throwable) {
            Timber.e(t, "VideoMuxer: mux failed (${videoFile.name} + ${audioFile.name} -> ${outputFile.name})")
            outputFile.delete()
            false
        } finally {
            runCatching { muxer?.release() }
            videoExtractor.release()
            audioExtractor.release()
        }
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

    private fun bufferSize(vararg formats: MediaFormat): Int {
        val declared = formats.maxOf { format ->
            runCatching { format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) }.getOrDefault(0)
        }
        // 4K vp9 keyframes can be large; a generous floor beats a mid-file BufferOverflow.
        return maxOf(declared, 4 * 1024 * 1024)
    }

    /** Copy the extractor's current sample; returns false when the track is exhausted. */
    private fun writeSample(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        track: Int,
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
    ): Boolean {
        buffer.clear()
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) return false
        info.offset = 0
        info.size = size
        info.presentationTimeUs = extractor.sampleTime
        info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
            MediaCodec.BUFFER_FLAG_KEY_FRAME
        } else {
            0
        }
        muxer.writeSampleData(track, buffer, info)
        extractor.advance()
        return true
    }
}
