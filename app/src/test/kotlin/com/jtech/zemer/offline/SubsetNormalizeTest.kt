package com.jtech.zemer.offline

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the Kotlin normalizer matches `zemer-search/index/normalize.mjs` exactly. Every expected
 * value here was produced by RUNNING the real JS on the same input — so this is a cross-language parity
 * check, not a restatement of the port.
 */
class SubsetNormalizeTest {

    private fun check(input: String, plain: List<String>, skKey: String, skTok: List<String>) {
        assertEquals("plain($input)", plain, SubsetNormalize.plainTokens(input))
        assertEquals("skKey($input)", skKey, SubsetNormalize.skeletonKey(input))
        assertEquals("skTok($input)", skTok, SubsetNormalize.skeletonTokens(input))
    }

    @Test
    fun `matches real JS on representative Hebrew, latin, mixed and apostrophe inputs`() {
        check("Mi Ha'ish", listOf("mi", "haish"), "m s", emptyList())
        check("דודי פולק", listOf("דודי", "פולק"), "dd plk", listOf("dd", "plk"))
        check("Dudi Polak", listOf("dudi", "polak"), "dd plk", listOf("dd", "plk"))
        check("כבקרת", listOf("כבקרת"), "kbkrt", listOf("kbkrt"))
        check("kevakarat", listOf("kevakarat"), "kbkrt", listOf("kbkrt"))
        check("L'Chaim", listOf("lchaim"), "lkm", listOf("lkm"))
        check("ג'רופי", listOf("גרופי"), "grp", listOf("grp"))
        check(
            "Nafshi | SIMCHA LEINER | Acapella",
            listOf("nafshi", "simcha", "leiner", "acapella"),
            "nps smk lnr cpll",
            listOf("nps", "smk", "lnr", "cpll"),
        )
        check("8th Day", listOf("8th", "day"), "8t d", listOf("8t"))
        check("שלום עליכם", listOf("שלום", "עליכם"), "slm lkm", listOf("slm", "lkm"))
        check("Yoni Shlomo", listOf("yoni", "shlomo"), "n slm", listOf("slm"))
        check("MBD", listOf("mbd"), "mbd", listOf("mbd"))
        check(
            "Avraham Fried אברהם פריד",
            listOf("avraham", "fried", "אברהם", "פריד"),
            "brm prd brm prd",
            listOf("brm", "prd", "brm", "prd"),
        )
    }

    @Test
    fun `damerau matches real JS`() {
        assertEquals(1, SubsetNormalize.damerau("kbkrt", "kbkr", 2)) // one deletion
        assertEquals(1, SubsetNormalize.damerau("abc", "acb", 2))    // adjacent transposition
        assertEquals(1, SubsetNormalize.damerau("hello", "helo", 2))
        assertEquals(3, SubsetNormalize.damerau("far", "xyz", 2))    // capped at max+1
    }
}
