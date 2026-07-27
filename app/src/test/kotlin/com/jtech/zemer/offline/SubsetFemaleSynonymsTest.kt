package com.jtech.zemer.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cross-language parity for the female-involvement port ([SubsetFemale]) and the synonym expander
 * ([SubsetSynonyms]). Every expected value here was produced by RUNNING the real JS
 * (`zemer-search/index/{credits,female-owned,synonyms}.mjs`) on the same inputs, not hand-guessed —
 * so this proves the Kotlin matches the server, it does not merely restate the port.
 */
class SubsetFemaleSynonymsTest {

    // Same female whitelist the JS ground-truth generator used to build the matcher.
    private val artists = listOf(
        SubArtist("a1", "Franciska", null, isFemale = true, isChasid = false, isKidZone = false),
        // Shiri Maimon, written in Hebrew — exercises CROSS-SCRIPT skeleton matching.
        SubArtist("a2", "שירי מימון", null, isFemale = true, isChasid = false, isKidZone = false),
        SubArtist("a3", "Avraham Fried", null, isFemale = false, isChasid = false, isKidZone = false),
        SubArtist("a4", "Yaakov Shwekey", null, isFemale = false, isChasid = false, isKidZone = false),
    )
    private val matcher = buildFemaleMatcher(artists)

    @Test
    fun `isFemaleInvolved matches the JS on primary, feat, non-female and cross-script cases`() {
        // primaryIsFemale short-circuits to true.
        assertTrue(isFemaleInvolved("Some Song", "Franciska", true, matcher))
        // male primary, female credited in the title parenthetical.
        assertTrue(isFemaleInvolved("Shiru (feat. Franciska)", "Yaakov Shwekey", false, matcher))
        // no credited female anywhere.
        assertFalse(isFemaleInvolved("Kah Amar", "Avraham Fried", false, matcher))
        // romanized title credit aligns with the Hebrew whitelist entry via the consonant skeleton.
        assertTrue(isFemaleInvolved("Kol Haolam (feat. Shiri Maimon)", "Avraham Fried", false, matcher))
    }

    @Test
    fun `isCommunityFemaleOwned matches the JS author-name check (empty offline id set)`() {
        assertTrue(isCommunityFemaleOwned("Franciska", matcher))
        assertFalse(isCommunityFemaleOwned("Avraham Fried", matcher))
        assertFalse(isCommunityFemaleOwned(null, matcher))
    }

    @Test
    fun `expand unions in synonym-group tokens for a matching query and leaves a non-match unchanged`() {
        // "mbd" hits the ["mbd", "mordechai ben david"] group.
        val mbd = SubsetSynonyms.expand(listOf("mbd"), listOf("mbd"))
        assertEquals(listOf("mbd", "mordechai", "ben", "david"), mbd.plain)
        assertEquals(listOf("mbd", "mrdk", "bn", "dbd"), mbd.skel)

        // "shwekey" overlaps no group — returned unchanged (skeleton token is "sk").
        val shwekey = SubsetSynonyms.expand(listOf("shwekey"), listOf("sk"))
        assertEquals(listOf("shwekey"), shwekey.plain)
        assertEquals(listOf("sk"), shwekey.skel)
    }
}
