package com.jtech.zemer.recognition.acrcloud

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Acrcloud {
    private const val TAG = "AcrcloudApi"

    private const val HOST = "identify-us-west-2.acrcloud.com"
    private const val ACCESS_KEY = "fe6ccc8baa774f017cacc442b364abeb"
    private const val ACCESS_SECRET = "PBHXxHmwrwIbvIvN8lqLUfRo7iLiU0crxZNPj3Hi"

    private val json = Json { ignoreUnknownKeys = true }

    private val client by lazy {
        HttpClient(CIO) {
            engine {
                requestTimeout = 30000
            }
        }
    }

    data class HummingResult(
        val title: String,
        val artist: String,
        val score: Double,
        val album: String? = null,
    )

    sealed interface Outcome {
        data class Found(val result: HummingResult) : Outcome
        data object NoMatch : Outcome
        data class Failed(val error: Throwable) : Outcome
    }

    suspend fun recognize(wavData: ByteArray): Outcome {
        return try {
            val timestamp = (System.currentTimeMillis() / 1000).toString()
            val uri = "/v1/identify"
            val signature = buildSignature("POST", uri, ACCESS_KEY, ACCESS_SECRET, timestamp)

            val response = client.post("https://$HOST$uri") {
                setBody(MultiPartFormDataContent(formData {
                    append("access_key", ACCESS_KEY)
                    append("data_type", "audio")
                    append("signature_version", "1")
                    append("signature", signature)
                    append("timestamp", timestamp)
                    append("sample_bytes", wavData.size.toString())
                    append("query_type", "humming")
                    append("sample", wavData, Headers.build {
                        append(HttpHeaders.ContentType, "audio/wav")
                        append(HttpHeaders.ContentDisposition, "filename=\"sample.wav\"")
                    })
                }))
            }

            val bodyText = response.bodyAsText()
            Timber.tag(TAG).d("ACRCloud response: %s", bodyText.take(300))

            val acrResponse = json.decodeFromString<AcrcloudResponseJson>(bodyText)

            when (acrResponse.status.code) {
                0 -> {
                    val tracks = acrResponse.metadata?.humming
                        ?: acrResponse.metadata?.music
                    val best = tracks?.maxByOrNull { it.score ?: 0.0 }
                    if (best != null && best.title != null) {
                        val artistName = best.artists?.joinToString(", ") { it.name }
                            ?: "Unknown Artist"
                        Outcome.Found(
                            HummingResult(
                                title = best.title,
                                artist = artistName,
                                score = best.score ?: 0.0,
                                album = best.album?.name,
                            ),
                        )
                    } else {
                        Outcome.NoMatch
                    }
                }
                3001, 3002, 3003 -> {
                    Timber.tag(TAG).w("ACRCloud no match (code=%d)", acrResponse.status.code)
                    Outcome.NoMatch
                }
                else -> {
                    Timber.tag(TAG).w("ACRCloud error (code=%d): %s", acrResponse.status.code, acrResponse.status.msg)
                    Outcome.Failed(Exception("ACRCloud error ${acrResponse.status.code}: ${acrResponse.status.msg}"))
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "ACRCloud request failed")
            Outcome.Failed(e)
        }
    }

    private fun buildSignature(
        method: String,
        uri: String,
        accessKey: String,
        accessSecret: String,
        timestamp: String,
    ): String {
        val stringToSign = listOf(method, uri, accessKey, "audio", "1", timestamp).joinToString("\n")
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(accessSecret.toByteArray(), "HmacSHA1"))
        return android.util.Base64.encodeToString(mac.doFinal(stringToSign.toByteArray()), android.util.Base64.NO_WRAP)
    }

    fun pcmToWav(pcmData: ByteArray, sampleRate: Int = 44100, channels: Int = 1, bitsPerSample: Int = 16): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val fileSize = 36 + dataSize

        val output = ByteArrayOutputStream()
        val dos = DataOutputStream(output)

        dos.writeBytes("RIFF")
        dos.writeInt(Integer.reverseBytes(fileSize))
        dos.writeBytes("WAVE")
        dos.writeBytes("fmt ")
        dos.writeInt(Integer.reverseBytes(16))
        dos.writeShort(Integer.reverseBytes(1))
        dos.writeShort(Integer.reverseBytes(channels))
        dos.writeInt(Integer.reverseBytes(sampleRate))
        dos.writeInt(Integer.reverseBytes(byteRate))
        dos.writeShort(Integer.reverseBytes(blockAlign))
        dos.writeShort(Integer.reverseBytes(bitsPerSample))
        dos.writeBytes("data")
        dos.writeInt(Integer.reverseBytes(dataSize))
        dos.write(pcmData)

        dos.flush()
        return output.toByteArray()
    }
}
