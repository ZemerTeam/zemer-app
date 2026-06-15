package com.jtech.zemer.recognition.shazam

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.UUID
import kotlin.random.Random

/**
 * Shazam music recognition with rate limiting and bounded concurrency.
 *
 * Ported from Metrolist's `shazamkit` module (no API key required — the public `amp.shazam.com`
 * discovery endpoint is used with spoofed user-agents and a randomized geolocation). Only the audio
 * fingerprint leaves the device, never raw audio.
 *
 * Concurrency is bounded by a [Semaphore] (callers suspend on a permit) and the per-request throttle
 * by a [Mutex] — both cooperative with structured cancellation, so a cancelled caller (e.g. the user
 * retrying) actually cancels its in-flight request instead of leaking it.
 */
object Shazam {
    private const val TAG = "ShazamApi"

    private const val MAX_CONCURRENT_REQUESTS = 2
    private const val MIN_REQUEST_INTERVAL_MS = 1000L
    private const val MAX_RETRIES = 3
    private const val INITIAL_RETRY_DELAY_MS = 2000L

    /** Typed result of a recognition attempt, so callers never string-match on exception messages. */
    sealed interface Outcome {
        data class Found(val result: RecognitionResult) : Outcome
        data object NoMatch : Outcome
        data class Failed(val error: Throwable) : Outcome
    }

    // Bounds in-flight requests without a hand-rolled queue; excess callers suspend on the permit.
    private val concurrencyLimiter = Semaphore(MAX_CONCURRENT_REQUESTS)

    // Serializes the rate-limit window so two concurrent requests can't both skip the throttle.
    private val rateLimitMutex = Mutex()
    private var lastRequestTime = 0L

    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        isLenient = true
                        ignoreUnknownKeys = true
                        encodeDefaults = true
                    },
                )
            }
            expectSuccess = false

            engine {
                requestTimeout = 30000
            }
        }
    }

    private val userAgents = listOf(
        "Dalvik/2.1.0 (Linux; U; Android 5.0.2; VS980 4G Build/LRX22G)",
        "Dalvik/1.6.0 (Linux; U; Android 4.4.2; SM-T210 Build/KOT49H)",
        "Dalvik/2.1.0 (Linux; U; Android 5.1.1; SM-P905V Build/LMY47X)",
        "Dalvik/2.1.0 (Linux; U; Android 6.0.1; SM-G920F Build/MMB29K)",
        "Dalvik/2.1.0 (Linux; U; Android 5.0; SM-G900F Build/LRX21T)",
    )

    private val timezones = listOf(
        "Europe/Paris", "Europe/London", "America/New_York",
        "America/Los_Angeles", "Asia/Tokyo", "Asia/Dubai",
    )

    /**
     * Recognize music from an audio signature.
     *
     * @param signature Audio signature in Shazam DejaVu format
     * @param sampleDurationMs Sample duration in milliseconds
     * @return a typed [Outcome]: a confirmed track, a clean "no match", or a failure.
     */
    suspend fun recognize(signature: String, sampleDurationMs: Long): Outcome =
        concurrencyLimiter.withPermit { executeWithRetry(signature, sampleDurationMs) }

    private suspend fun executeWithRetry(signature: String, sampleDurationMs: Long): Outcome {
        var lastError: Throwable? = null
        for (attempt in 0 until MAX_RETRIES) {
            enforceRateLimit()
            try {
                return performRecognition(signature, sampleDurationMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RetryableException) {
                lastError = e
                Timber.tag(TAG).w(e, "Retryable failure on attempt %d/%d", attempt + 1, MAX_RETRIES)
                if (attempt < MAX_RETRIES - 1) {
                    delay(INITIAL_RETRY_DELAY_MS * (1L shl attempt))
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Recognition failed (non-retryable)")
                return Outcome.Failed(e)
            }
        }
        return Outcome.Failed(lastError ?: Exception("Recognition failed after $MAX_RETRIES attempts"))
    }

    private suspend fun performRecognition(signature: String, sampleDurationMs: Long): Outcome {
        val timestamp = System.currentTimeMillis() / 1000
        val uuid1 = UUID.randomUUID().toString().uppercase()
        val uuid2 = UUID.randomUUID().toString()

        val request = ShazamRequestJson(
            geolocation = ShazamRequestJson.Geolocation(
                altitude = Random.nextDouble() * 400 + 100,
                latitude = Random.nextDouble() * 180 - 90,
                longitude = Random.nextDouble() * 360 - 180,
            ),
            signature = ShazamRequestJson.Signature(
                samplems = sampleDurationMs,
                timestamp = timestamp,
                uri = signature,
            ),
            timestamp = timestamp,
            timezone = timezones.random(),
        )

        Timber.tag(TAG).d("Sending recognition request to Shazam API")
        val response = client.post("https://amp.shazam.com/discovery/v5/en/US/android/-/tag/$uuid1/$uuid2") {
            parameter("sync", "true")
            parameter("webv3", "true")
            parameter("sampling", "true")
            parameter("connected", "")
            parameter("shazamapiversion", "v3")
            parameter("sharehub", "true")
            parameter("video", "v3")
            header("User-Agent", userAgents.random())
            header("Content-Language", "en_US")
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        if (!response.status.isSuccess()) {
            val statusCode = response.status.value
            Timber.tag(TAG).w("Shazam API returned HTTP %d", statusCode)
            return when (statusCode) {
                404 -> Outcome.NoMatch
                429 -> throw RetryableException("Too many requests (429)")
                in 500..599 -> throw RetryableException("Shazam service temporarily unavailable ($statusCode)")
                else -> Outcome.Failed(Exception("Recognition failed (error $statusCode)"))
            }
        }

        val shazamResponse = response.body<ShazamResponseJson>()
        Timber.tag(TAG).d("Shazam API response received, hasTrack=%s", shazamResponse.track != null)
        val result = shazamResponse.toRecognitionResult() ?: return Outcome.NoMatch
        return Outcome.Found(result)
    }

    private suspend fun enforceRateLimit() = rateLimitMutex.withLock {
        val now = System.currentTimeMillis()
        val sinceLast = now - lastRequestTime
        if (sinceLast < MIN_REQUEST_INTERVAL_MS) {
            delay(MIN_REQUEST_INTERVAL_MS - sinceLast)
        }
        lastRequestTime = System.currentTimeMillis()
    }

    /** A transient error (429 / 5xx) worth retrying with backoff, as opposed to a hard failure. */
    private class RetryableException(message: String) : Exception(message)

    private fun ShazamResponseJson.toRecognitionResult(): RecognitionResult? {
        val track = this.track ?: return null

        val songSection = track.sections?.find { it?.type == "SONG" }
        val metadata = songSection?.metadata
        val album = metadata?.find { it?.title == "Album" }?.text
        val label = metadata?.find { it?.title == "Label" }?.text
        val releaseDate = metadata?.find { it?.title == "Released" }?.text

        val lyricsSection = track.sections?.find { it?.type == "LYRICS" }
        val lyrics = lyricsSection?.text

        val appleAction = track.hub?.options?.firstOrNull {
            it?.providername?.contains("apple", ignoreCase = true) == true
        }?.actions?.firstOrNull()

        val spotifyProvider = track.hub?.providers?.find {
            it?.caption?.contains("spotify", ignoreCase = true) == true
        }

        val youtubeAction = track.hub?.options?.find {
            it?.type?.contains("video", ignoreCase = true) == true
        }?.actions?.firstOrNull()

        val youtubeVideoId = youtubeAction?.uri?.let { uri ->
            uri.substringAfterLast("v=", "").takeIf { it.isNotEmpty() }
                ?: uri.substringAfterLast("/", "").takeIf { it.isNotEmpty() && it.length == 11 }
        }

        return RecognitionResult(
            trackId = track.key ?: tagid ?: "",
            title = track.title ?: "",
            artist = track.subtitle ?: "",
            album = album,
            coverArtUrl = track.images?.coverart,
            coverArtHqUrl = track.images?.coverarthq,
            genre = track.genres?.primary,
            releaseDate = releaseDate,
            label = label,
            lyrics = lyrics,
            shazamUrl = track.url,
            appleMusicUrl = appleAction?.uri,
            spotifyUrl = spotifyProvider?.actions?.firstOrNull()?.uri,
            isrc = track.isrc,
            youtubeVideoId = youtubeVideoId,
        )
    }
}
