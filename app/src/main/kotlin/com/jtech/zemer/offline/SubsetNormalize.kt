package com.jtech.zemer.offline

import java.text.Normalizer
import kotlin.math.abs

/**
 * Hebrew-aware normalization / transliteration — the cross-script fuzzy layer, ported verbatim from
 * `zemer-search/index/normalize.mjs` (pure string ops, deterministic, no ICU). Two token forms per
 * string:
 *  - **plain** — NFD-strip diacritics/niqqud, lowercase, fold to `[a-z0-9]` + Hebrew block, tokens.
 *  - **skeleton** — a Hebrew-aware CONSONANT skeleton: romanize the strong consonants, DROP the matres
 *    lectionis (א ה ו י ע) + latin vowels, fold ambiguous pairs (b/v, k/ch, p/f, s/sh, t/th, tz), so a
 *    romanized query ("kevakarat", "dudi polak") aligns with the Hebrew title (כבקרת, דודי פולק).
 *
 * Must stay byte-for-byte equivalent to the JS (verified in [SubsetNormalizeTest] against real JS output).
 */
object SubsetNormalize {

    private val COMBINING = Regex("\\p{Mn}+")
    // In-word marks (ASCII/curly apostrophes, backtick, acute, double-quote, Hebrew geresh/gershayim) are
    // REMOVED, not split — so "L'Chaim"/"LChaim"/"lchaim" and "ג'רופי"/"גרופי" tokenize identically.
    private val JOINMARK = Regex("['’‘`´\"׳״]")
    private val NON_TOKEN = Regex("[^a-z0-9֐-׿]+")

    // Hebrew strong consonants → folded latin class. Matres lectionis (א ה ו י ע) intentionally absent → dropped.
    private val HEB = mapOf(
        'ב' to "b", 'ג' to "g", 'ד' to "d", 'ז' to "z", 'ח' to "k", 'ט' to "t",
        'כ' to "k", 'ך' to "k", 'ל' to "l", 'מ' to "m", 'ם' to "m", 'נ' to "n", 'ן' to "n",
        'ס' to "s", 'פ' to "p", 'ף' to "p", 'צ' to "c", 'ץ' to "c", 'ק' to "k", 'ר' to "r",
        'ש' to "s", 'ת' to "t",
    )

    private fun romanizeHebrewToSkeleton(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            val mapped = HEB[ch]
            when {
                mapped != null -> sb.append(mapped)
                ch in '֐'..'׿' -> Unit // matres lectionis / other Hebrew → dropped
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private val R_SH = Regex("sh|ş")
    private val R_CH = Regex("ch|kh|ḥ|x")
    private val R_TZ = Regex("tz|ts")
    private val R_TH = Regex("th")
    private val R_VOWEL = Regex("[aeiou]")
    private val R_SEMI = Regex("[wyh']")
    private val R_NONALNUM = Regex("[^a-z0-9]")

    /** Fold a latin run to the same consonant alphabet as the Hebrew skeleton (order is load-bearing). */
    private fun latinToSkeleton(s: String): String =
        s.replace(R_SH, "s").replace(R_CH, "k").replace(R_TZ, "c").replace(R_TH, "t")
            .replace(R_VOWEL, "").replace(R_SEMI, "")
            .replace("v", "b").replace("f", "p").replace("q", "k").replace(R_NONALNUM, "")

    fun plainTokens(str: String?): List<String> =
        Normalizer.normalize(str ?: "", Normalizer.Form.NFD)
            .replace(COMBINING, "")
            .lowercase()
            .replace(JOINMARK, "")
            .replace(NON_TOKEN, " ")
            .split(" ")
            .filter { it.isNotEmpty() }

    /**
     * Word-ALIGNED skeleton key: one slot per plain token (a token skeletonizing to nothing keeps its
     * plain form). Used ONLY for exact/begins-with ranking boosts; matching uses [skeletonTokens].
     */
    fun skeletonKey(str: String?): String =
        plainTokens(str).joinToString(" ") { tok -> latinToSkeleton(romanizeHebrewToSkeleton(tok)).ifEmpty { tok } }

    fun skeletonTokens(str: String?): List<String> {
        val cleaned = Normalizer.normalize(str ?: "", Normalizer.Form.NFD)
            .replace(COMBINING, "").lowercase().replace(JOINMARK, "")
        val out = ArrayList<String>()
        for (word in cleaned.split(NON_TOKEN).filter { it.isNotEmpty() }) {
            val sk = latinToSkeleton(romanizeHebrewToSkeleton(word))
            if (sk.length >= 2) out.add(sk)
        }
        return out
    }

    /**
     * Damerau-Levenshtein (optimal string alignment) with an early cap; adjacent transposition costs 1.
     * Returns `max + 1` once the best row value exceeds [max] (early exit).
     */
    fun damerau(a: String, b: String, max: Int = 2): Int {
        val al = a.length
        val bl = b.length
        if (abs(al - bl) > max) return max + 1
        if (al == 0) return if (bl <= max) bl else max + 1
        if (bl == 0) return if (al <= max) al else max + 1
        val d = Array(al + 1) { IntArray(bl + 1) }
        for (i in 0..al) d[i][0] = i
        for (j in 0..bl) d[0][j] = j
        for (i in 1..al) {
            var best = max + 1
            for (j in 1..bl) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    v = minOf(v, d[i - 2][j - 2] + 1)
                }
                d[i][j] = v
                if (v < best) best = v
            }
            if (best > max) return max + 1
        }
        return d[al][bl]
    }
}
