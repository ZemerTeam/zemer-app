package com.jtech.zemer.pass

import com.jtech.zemer.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.Base64

/** A pass-route answer other than 2xx; [status] 403 = refused (re-attest or give up), 400 = bad/expired nonce. */
class PassRefusedException(val status: Int, message: String) : IOException(message)

@Serializable
data class PassGrant(val pass: String, val keyId: String, val expiresAt: Long)

/** The content server's pass issuer (`/pass/challenge`, `/pass/attest`, `/pass/renew`). */
internal class PassClient(
    baseUrl: String = BuildConfig.CONTENT_MIRROR_URL,
    private val client: HttpClient = defaultClient,
) {
    private val baseUrl = baseUrl.trimEnd('/')

    @Serializable
    private data class Challenge(val nonce: String)

    @Serializable
    private data class AttestBody(val nonce: String, val chain: List<String>)

    @Serializable
    private data class RenewBody(val keyId: String, val nonce: String, val sig: String)

    /** A single-use nonce: the server's base64url text and its raw bytes. */
    class Nonce(val text: String) {
        val bytes: ByteArray get() = Base64.getUrlDecoder().decode(text)
    }

    suspend fun challenge(): Nonce = Nonce(json.decodeFromString(Challenge.serializer(), client.get("$baseUrl/pass/challenge").checked()).nonce)

    suspend fun attest(nonce: Nonce, chain: List<String>): PassGrant = post("/pass/attest", json.encodeToString(AttestBody.serializer(), AttestBody(nonce.text, chain)))

    suspend fun renew(keyId: String, nonce: Nonce, sig: String): PassGrant = post("/pass/renew", json.encodeToString(RenewBody.serializer(), RenewBody(keyId, nonce.text, sig)))

    private suspend fun post(path: String, body: String): PassGrant =
        json.decodeFromString(PassGrant.serializer(), client.post("$baseUrl$path") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.checked())

    private suspend fun HttpResponse.checked(): String {
        val text = bodyAsText()
        if (!status.isSuccess()) throw PassRefusedException(status.value, "pass ${status.value}: ${text.take(200)}")
        return text
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val defaultClient by lazy {
            HttpClient(CIO) { install(HttpTimeout) { connectTimeoutMillis = 10_000; requestTimeoutMillis = 20_000 } }
        }
    }
}
