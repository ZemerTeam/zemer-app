package com.metrolist.lrclib

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression: a same-length song by another artist ("Koi Koi Koi", a Japanese track) was served for Baruch Levine. */
class LrcLibIdentityTest {
    @Test
    fun `duration alone never matches`() {
        assertFalse(LrcLib.identityMatches("こいこいこい", "Some J-Pop Artist", "Koi Koi Koi", "Baruch Levine", 200.0, 200))
        assertFalse(LrcLib.identityMatches("Koi Koi Koi", "Some J-Pop Artist", "Koi Koi Koi", "Baruch Levine", 200.0, 200))
    }

    @Test
    fun `title plus artist plus duration match`() {
        assertTrue(LrcLib.identityMatches("Koi Koi Koi", "Baruch Levine", "Koi Koi Koi (feat. X)", "Baruch Levine", 201.0, 200))
        assertFalse("duration off by 30s", LrcLib.identityMatches("Koi Koi Koi", "Baruch Levine", "Koi Koi Koi", "Baruch Levine", 230.0, 200))
        assertTrue(LrcLib.identityMatches("Vezakeini", "Baruch Levine", "Vezakeini", "Baruch Levine & Shira Choir", 210.0, -1))
    }
}
