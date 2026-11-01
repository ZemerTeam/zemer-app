package com.jtech.zemer.sig

import com.jtech.zemer.BuildConfig
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The request signature: `X-Zemer-Sig: k=<keyId>;t=<unix seconds>;s=<base64url HMAC-SHA256>` over
 * [SigCanonical.message], with a per-version key supplied at build time (never in the source tree) and held by
 * the native signer ([NativeSig]). A build without a key never sends the header and works exactly the same; a
 * signing failure only drops the header.
 */
object ZemerSig {
    const val HEADER = "X-Zemer-Sig"

    /**
     * The key id the server derives the key for: the versionCode for a release build, "d<versionCode>" for a debug
     * build (keyed separately from release builds).
     */
    internal val keyId: String = (if (BuildConfig.DEBUG) "d" else "") + BuildConfig.VERSION_CODE

    /**
     * The MAC function; null when this build has no key. Each build type gets the native signer only with its own
     * key source, so a debug build signs only as "d<versionCode>", never with a release key. Swappable for tests.
     */
    @Volatile
    internal var signer: ((ByteArray) -> ByteArray?)? = if (NativeSig.available) NativeSig::hmac else null

    /**
     * This install's own key ([InstallRegistration]); null until registered, or when it cannot be read. With it,
     * every version 2 signature uses `k=<install id>` and the install key; version 1 and [INSTALL_PATH] always use
     * the version key.
     */
    @Volatile
    internal var install: InstallCredential? = null

    const val INSTALL_PATH = "/install"

    /** Server time minus device time, in seconds, from the last Zemer response's `Date` (0 until one arrives). */
    @Volatile
    internal var clockOffsetSec = 0L

    /**
     * The header value for a request, or null when this build cannot sign (or signing failed). With the exact
     * [body] bytes sent (empty for none) it is a version 2 signature: a fresh nonce per call, so every attempt and
     * redirect hop gets its own, and the body hash. A null [body] (not readable) signs version 1.
     */
    fun headerFor(
        method: String,
        encodedPath: String,
        encodedQuery: String?,
        body: ByteArray? = null,
        nowMs: Long = System.currentTimeMillis(),
    ): String? {
        val versionSigner = signer ?: return null
        val t = nowMs / 1000 + clockOffsetSec
        val nonce = body?.let { b64(ByteArray(16).also(random::nextBytes)) }
        val bodyHash = body?.let { b64(MessageDigest.getInstance("SHA-256").digest(it)) }
        val message = if (nonce != null && bodyHash != null) {
            SigCanonical.message2(method, encodedPath, encodedQuery, t, nonce, bodyHash)
        } else {
            SigCanonical.message(method, encodedPath, encodedQuery, t)
        }
        val install = install?.takeIf { nonce != null && encodedPath != INSTALL_PATH }
        val k = install?.id ?: keyId
        val sign: (ByteArray) -> ByteArray? = if (install != null) { m -> hmac(install.key, m) } else versionSigner
        val mac = runCatching { sign(message) }.getOrNull()?.takeIf { it.size == 32 } ?: return null
        return if (nonce != null) "v=2;k=$k;t=$t;n=$nonce;b=$bodyHash;s=${b64(mac)}" else "k=$k;t=$t;s=${b64(mac)}"
    }

    private fun hmac(key: ByteArray, message: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(message)

    private val random = SecureRandom()

    private fun b64(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    /** Takes the server clock from an HTTP `Date` header (RFC 1123); an absent or unparsable one is ignored. */
    fun onServerDate(date: String?, nowMs: Long = System.currentTimeMillis()) {
        if (date.isNullOrBlank()) return
        val serverSec = runCatching { ZonedDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond() }.getOrNull() ?: return
        clockOffsetSec = serverSec - nowMs / 1000
    }
}
