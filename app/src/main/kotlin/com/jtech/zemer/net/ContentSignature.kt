package com.jtech.zemer.net

import com.jtech.zemer.sig.SigCanonical
import com.jtech.zemer.tracking.Tracker
import io.ktor.client.statement.HttpResponse
import com.google.crypto.tink.subtle.Ed25519Verify
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.util.Base64

/**
 * The servers sign the content the app applies (the allow-lists, the block overrides, the status sources, the
 * offline snapshot manifest): `X-Zemer-Content-Sig: k=<keyId>;t=<unix s>;s=<base64url Ed25519>` over
 * `"zcs1" \0 decodedPath \0 t \0 base64url(SHA-256(body)) \0`, the body as received after HTTP decompression
 * (contract + vectors: zemer-search `server/content-sig.mjs` / `content-sig-vectors.json`).
 *
 * An INVALID signature is never applied. A MISSING one is applied only on a first sync, when there is no previous
 * copy to keep (a network that strips the header must never leave a fresh install without its lists); with a
 * previous copy it is kept. Both are reported as `fetch_fail` (`bad_sig` / `no_sig`).
 */
object ContentSignature {
    const val HEADER = "X-Zemer-Content-Sig"

    /** Ed25519 public keys by id, as SPKI DER (base64); a later key is added here beside the old one. */
    private val KEYS: Map<String, ByteArray> = mapOf(
        "1" to rawKey("MCowBQYDK2VwAyEAFMojieqcnaStywSStpF2MOBuiR/HapbRay5oSAO70Eg="),
    )

    enum class Verdict { VALID, MISSING, INVALID }

    /** The raw 32-byte Ed25519 key: the last 32 bytes of its SPKI DER. */
    internal fun rawKey(spkiB64: String): ByteArray = Base64.getDecoder().decode(spkiB64).takeLast(32).toByteArray()

    internal fun message(decodedPath: String, t: Long, body: ByteArray): ByteArray {
        val bodyHash = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(body))
        return "zcs1\u0000$decodedPath\u0000$t\u0000$bodyHash\u0000".toByteArray(Charsets.UTF_8)
    }

    fun verify(decodedPath: String, header: String?, body: ByteArray, keys: Map<String, ByteArray> = KEYS): Verdict {
        if (header.isNullOrBlank()) return Verdict.MISSING
        val fields = header.split(';').associate { it.substringBefore('=').trim() to it.substringAfter('=', "").trim() }
        val key = keys[fields["k"]] ?: return Verdict.INVALID
        val t = fields["t"]?.toLongOrNull() ?: return Verdict.INVALID
        val signature = runCatching { Base64.getUrlDecoder().decode(fields["s"]) }.getOrNull()?.takeIf { it.size == 64 }
            ?: return Verdict.INVALID
        // only a bad signature is INVALID; anything else (a broken crypto library) propagates as a failed read
        return try {
            Ed25519Verify(key).verify(signature, message(decodedPath, t, body))
            Verdict.VALID
        } catch (_: GeneralSecurityException) {
            Verdict.INVALID
        }
    }

    /** The failure kind for a [verdict], or null when the content may be applied ([firstSync]: nothing local yet). */
    internal fun rejection(verdict: Verdict, firstSync: Boolean): String? = when (verdict) {
        Verdict.VALID -> null
        Verdict.MISSING -> if (firstSync) null else NO_SIG
        Verdict.INVALID -> BAD_SIG
    }

    /**
     * Checks [response]'s [body] before the caller applies it; throws [ContentSignatureException] when it must not
     * be, and reports any unsigned or badly signed response once per session ([host] and [label] as in
     * `fetch_fail`: a camelCase label, never a URL path, which tracking refuses).
     */
    fun requireApplicable(host: String, label: String, response: HttpResponse, body: ByteArray, firstSync: Boolean) {
        val path = SigCanonical.decodePath(response.call.request.url.encodedPath.ifEmpty { "/" })
        val verdict = verify(path, response.headers[HEADER], body)
        if (verdict == Verdict.VALID) return
        Tracker.fetchFail(host = host, path = label, kind = if (verdict == Verdict.MISSING) NO_SIG else BAD_SIG)
        rejection(verdict, firstSync)?.let { throw ContentSignatureException(it, path) }
    }

    const val BAD_SIG = "bad_sig"
    const val NO_SIG = "no_sig"
}

/** Content that must not be applied: [kind] is [ContentSignature.BAD_SIG] or [ContentSignature.NO_SIG]. */
class ContentSignatureException(val kind: String, path: String) : RuntimeException("content signature $kind for $path")
