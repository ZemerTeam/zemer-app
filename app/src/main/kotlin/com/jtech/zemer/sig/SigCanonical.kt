package com.jtech.zemer.sig

import java.io.ByteArrayOutputStream

/**
 * The canonical message a request signature covers (contract: zemer-search `server/sig.mjs`, pinned by the
 * shared `sig-vectors.json`): `"zsig1" \0 METHOD \0 decodedPath \0 t \0` then, for each query parameter
 * decoded with form rules and sorted by key then value (UTF-16 order), `key \0 value \0`. Decoding and sorting
 * make the signature immune to URL re-encoding on the way (Cloudflare normalization); the host is not signed.
 * Version 2 ([message2]) also covers a one-time nonce and the body hash: `"zsig2" \0 METHOD \0 decodedPath \0 t
 * \0 n \0 b \0` then the same query pairs.
 */
internal object SigCanonical {
    fun message(method: String, encodedPath: String, encodedQuery: String?, t: Long): ByteArray =
        build("zsig1", method, encodedPath, encodedQuery, listOf(t.toString()))

    /** [nonce] and [bodyHash] are the header's `n` and `b` values (base64url, no padding). */
    fun message2(method: String, encodedPath: String, encodedQuery: String?, t: Long, nonce: String, bodyHash: String): ByteArray =
        build("zsig2", method, encodedPath, encodedQuery, listOf(t.toString(), nonce, bodyHash))

    private fun build(version: String, method: String, encodedPath: String, encodedQuery: String?, fields: List<String>): ByteArray {
        val parts = ArrayList<String>()
        parts += version
        parts += method.uppercase()
        // an empty path is sent as "/" (and the server's URL parser reads it so)
        parts += decodePath(encodedPath.ifEmpty { "/" })
        parts += fields
        queryPairs(encodedQuery)
            .sortedWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })
            .forEach { (k, v) -> parts += k; parts += v }
        val out = ByteArrayOutputStream()
        parts.forEach { out.write(it.toByteArray(Charsets.UTF_8)); out.write(0) }
        return out.toByteArray()
    }

    /** Like JavaScript's `decodeURIComponent`: `%XX` sequences only (a `+` stays a plus); the raw path when malformed. */
    fun decodePath(encoded: String): String = percentDecode(encoded, plusIsSpace = false, strict = true) ?: encoded

    /** Like `URLSearchParams`: `&`-separated, empty segments skipped, the first `=` splits, form decoding. */
    fun queryPairs(encodedQuery: String?): List<Pair<String, String>> =
        encodedQuery.orEmpty().split('&').filter { it.isNotEmpty() }.map { segment ->
            val eq = segment.indexOf('=')
            val key = if (eq < 0) segment else segment.substring(0, eq)
            val value = if (eq < 0) "" else segment.substring(eq + 1)
            formDecode(key) to formDecode(value)
        }

    private fun formDecode(s: String): String = percentDecode(s, plusIsSpace = true, strict = false)!!

    /**
     * Percent-decodes [s] as UTF-8. [strict] (path) returns null on a malformed `%` sequence or invalid UTF-8,
     * like `decodeURIComponent` throwing; lenient (query) keeps a malformed `%` literally and replaces invalid
     * UTF-8, like `URLSearchParams`.
     */
    private fun percentDecode(s: String, plusIsSpace: Boolean, strict: Boolean): String? {
        if (s.indexOf('%') < 0 && !(plusIsSpace && s.indexOf('+') >= 0)) return s
        val bytes = ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '%' && i + 2 < s.length && hex(s[i + 1]) >= 0 && hex(s[i + 2]) >= 0 -> {
                    bytes.write(hex(s[i + 1]) * 16 + hex(s[i + 2]))
                    i += 3
                    continue
                }
                c == '%' && strict -> return null
                c == '+' && plusIsSpace -> bytes.write(' '.code)
                else -> {
                    // a whole code point, so a surrogate pair is never split
                    val n = Character.charCount(s.codePointAt(i))
                    bytes.write(s.substring(i, i + n).toByteArray(Charsets.UTF_8))
                    i += n
                    continue
                }
            }
            i++
        }
        val raw = bytes.toByteArray()
        if (strict) {
            val decoder = Charsets.UTF_8.newDecoder()
            return runCatching { decoder.decode(java.nio.ByteBuffer.wrap(raw)).toString() }.getOrNull()
        }
        return String(raw, Charsets.UTF_8)
    }

    private fun hex(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> -1
    }
}
