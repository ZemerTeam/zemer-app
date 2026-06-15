package com.jtech.zemer.recognition

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

/**
 * Captures a short window of microphone audio and turns it into a Shazam-compatible fingerprint.
 *
 * This is the Android-facing half of recognition: record → resample to 16 kHz → fingerprint. It is
 * deliberately split from orchestration (search + whitelist filtering live in
 * [com.jtech.zemer.viewmodels.RecognizeMusicViewModel]) so the audio pipeline stays single-purpose.
 *
 * Based on the MusicRecognizer project by Aleksey Saenko
 * (https://github.com/aleksey-saenko/MusicRecognizer), as ported by Metrolist.
 */
object RecognitionAudioCapture {
    private const val TAG = "RecognitionCapture"

    private const val RECORDING_SAMPLE_RATE = 44100
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    // 12 seconds gives the fingerprinter enough material for a reliable match.
    private const val RECORDING_DURATION_MS = 12000L

    /** A generated fingerprint plus the duration of the audio it represents (for the Shazam request). */
    data class Fingerprint(val signature: String, val sampleDurationMs: Long)

    fun hasRecordPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Records [RECORDING_DURATION_MS] of audio and returns its fingerprint.
     *
     * Honors cooperative cancellation: cancelling the calling coroutine stops the recording loop.
     *
     * @throws IllegalStateException if the microphone permission is not granted.
     * @throws Exception if resampling or fingerprint generation fails.
     */
    suspend fun capture(context: Context): Fingerprint = withContext(Dispatchers.IO) {
        check(hasRecordPermission(context)) { "Microphone permission not granted" }

        val audioData = recordAudio()
        Timber.tag(TAG).d("Audio recorded: %d bytes", audioData.size)

        val decodedAudio = DecodedAudio(
            data = audioData,
            channelCount = 1,
            sampleRate = RECORDING_SAMPLE_RATE,
            pcmEncoding = AUDIO_FORMAT,
        )

        val resampledAudio = AudioResampler.resample(
            decodedAudio,
            VibraSignature.REQUIRED_SAMPLE_RATE,
        ).getOrThrow()

        require(
            resampledAudio.channelCount == 1 &&
                resampledAudio.sampleRate == VibraSignature.REQUIRED_SAMPLE_RATE &&
                resampledAudio.pcmEncoding == AudioFormat.ENCODING_PCM_16BIT &&
                ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN &&
                resampledAudio.data.isNotEmpty() &&
                resampledAudio.data.size % 2 == 0,
        ) { "Invalid audio format for fingerprint generation" }

        val signature = VibraSignature.fromI16(resampledAudio.data)
        val sampleDurationMs =
            (resampledAudio.data.size / 2) * 1000L / VibraSignature.REQUIRED_SAMPLE_RATE
        Timber.tag(TAG).d("Fingerprint generated, sampleDurationMs=%d", sampleDurationMs)

        Fingerprint(signature, sampleDurationMs)
    }

    @SuppressLint("MissingPermission")
    private suspend fun recordAudio(): ByteArray = withContext(Dispatchers.IO) {
        val bufferSize = AudioRecord.getMinBufferSize(RECORDING_SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        // getMinBufferSize returns a negative error code (ERROR / ERROR_BAD_VALUE) for an unsupported
        // config; feeding that to ByteArray()/AudioRecord() would crash, so fail fast and clearly.
        check(bufferSize > 0) { "Microphone unavailable: invalid min buffer size ($bufferSize)" }
        Timber.tag(TAG).d(
            "Recording audio: sampleRate=%d, bufferSize=%d, durationMs=%d",
            RECORDING_SAMPLE_RATE,
            bufferSize,
            RECORDING_DURATION_MS,
        )

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            RECORDING_SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize,
        )

        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(bufferSize)
        val startTime = System.currentTimeMillis()

        try {
            // A failed init (mic held by another app, denied at the HAL) leaves the object
            // UNINITIALIZED; startRecording() would silently no-op and every read() would error.
            check(audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                "Microphone unavailable: AudioRecord failed to initialize"
            }
            audioRecord.startRecording()
            check(audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "Microphone unavailable: AudioRecord failed to start"
            }
            Timber.tag(TAG).d("AudioRecord started, recording for %dms", RECORDING_DURATION_MS)

            while (System.currentTimeMillis() - startTime < RECORDING_DURATION_MS && coroutineContext.isActive) {
                val bytesRead = audioRecord.read(buffer, 0, bufferSize)
                when {
                    bytesRead > 0 -> outputStream.write(buffer, 0, bytesRead)
                    // A negative result (ERROR_INVALID_OPERATION / ERROR_DEAD_OBJECT / ERROR) is fatal:
                    // without this the loop would busy-spin for the full window and return empty audio.
                    bytesRead < 0 -> error("Microphone read failed (code $bytesRead)")
                    // bytesRead == 0: no data available yet, keep polling until the window elapses.
                }
            }
        } finally {
            if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                runCatching { audioRecord.stop() }
            }
            audioRecord.release()
        }

        val totalBytes = outputStream.size()
        Timber.tag(TAG).d("Audio recording complete: %d bytes collected", totalBytes)
        outputStream.toByteArray()
    }
}
