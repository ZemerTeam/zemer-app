package com.jtech.zemer.sig

import com.jtech.zemer.BuildConfig
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64

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

    /** Server time minus device time, in seconds, from the last Zemer response's `Date` (0 until one arrives). */
    @Volatile
    internal var clockOffsetSec = 0L

    /** The header value for a request, or null when this build cannot sign (or signing failed). */
    fun headerFor(method: String, encodedPath: String, encodedQuery: String?, nowMs: Long = System.currentTimeMillis()): String? {
        val sign = signer ?: return null
        val t = nowMs / 1000 + clockOffsetSec
        val mac = runCatching { sign(SigCanonical.message(method, encodedPath, encodedQuery, t)) }.getOrNull()
            ?.takeIf { it.size == 32 } ?: return null
        return "k=$keyId;t=$t;s=${Base64.getUrlEncoder().withoutPadding().encodeToString(mac)}"
    }

    /** Takes the server clock from an HTTP `Date` header (RFC 1123); an absent or unparsable one is ignored. */
    fun onServerDate(date: String?, nowMs: Long = System.currentTimeMillis()) {
        if (date.isNullOrBlank()) return
        val serverSec = runCatching { ZonedDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond() }.getOrNull() ?: return
        clockOffsetSec = serverSec - nowMs / 1000
    }
}
