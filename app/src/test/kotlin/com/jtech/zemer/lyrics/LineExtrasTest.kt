package com.jtech.zemer.lyrics

import com.jtech.zemer.lyrics.zemer.LineTimesLrc
import com.jtech.zemer.lyrics.zemer.ZemerLyricsClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The per-line extras pair by the text-free line key, never by index; blank entries and absent languages render nothing. */
class LineExtrasTest {
    private val bayit = "בַּיִת הוֹ בַּיִת"
    private val shalom = "שלום עליכם"
    private fun k(line: String) = LineTimesLrc.lineKey(line)

    private val wire = ZemerLyricsClient.LineExtras(
        keys = listOf(k(bayit), k(shalom), k("unused line")),
        en = listOf("A home oh a home", "", "never shown"),
        roman = listOf("bayit ho bayit", "shalom aleichem", ""),
        source = "machine",
    )

    @Test
    fun `pairs each parsed line by key regardless of the app's own line split and order`() {
        val extras = LineExtras.from(wire)!!
        // The app's body: points stripped by the key, a different order, an extra line the server never keyed.
        val lines = listOf(shalom, "בית הו בית", "a line the server did not key")
        assertEquals(listOf(null, "A home oh a home", null), extras.forLines(lines, LineExtrasLanguage.ENGLISH))
        assertEquals(listOf("shalom aleichem", "bayit ho bayit", null), extras.forLines(lines, LineExtrasLanguage.ROMANIZED))
        assertEquals("A home oh a home", extras.textFor("בית הו בית", LineExtrasLanguage.ENGLISH))
    }

    @Test
    fun `a repeated chorus line takes the same extra each time`() {
        val extras = LineExtras.from(wire)!!
        assertEquals(listOf("bayit ho bayit", "bayit ho bayit"), extras.forLines(listOf(bayit, bayit), LineExtrasLanguage.ROMANIZED))
    }

    @Test
    fun `an absent language, OFF, and a blank entry all render nothing`() {
        val extras = LineExtras.from(wire)!!
        assertEquals(listOf(null, null), extras.forLines(listOf(bayit, shalom), LineExtrasLanguage.HEBREW))
        assertEquals(listOf(null, null), extras.forLines(listOf(bayit, shalom), LineExtrasLanguage.OFF))
        assertNull(extras.textFor(shalom, LineExtrasLanguage.ENGLISH))
        assertEquals(setOf(LineExtrasLanguage.ENGLISH, LineExtrasLanguage.ROMANIZED), extras.languages)
    }

    @Test
    fun `machine source is a per-song fact, absent or empty wire folds to null`() {
        assertTrue(LineExtras.from(wire)!!.isMachine)
        assertFalse(LineExtras.from(wire.copy(source = "editor"))!!.isMachine)
        assertNull(LineExtras.from(null))
        assertNull(LineExtras.from(ZemerLyricsClient.LineExtras(keys = listOf("abcd1234"), en = listOf(""))))
        assertNull(LineExtras.from(ZemerLyricsClient.LineExtras(keys = listOf("abcd1234"))))
    }

    @Test
    fun `a texts list shorter than keys never throws, the missing tail renders nothing`() {
        val extras = LineExtras.from(ZemerLyricsClient.LineExtras(keys = listOf(k("a"), k("b")), en = listOf("A")))!!
        assertEquals(listOf("A", null), extras.forLines(listOf("a", "b"), LineExtrasLanguage.ENGLISH))
    }

    /** An aligned reply pairs to the lines the app SENT (index-parallel), keyed locally, never by the server's keys. */
    @Test
    fun `aligned reply pairs by the app's own lines and carries yiddish`() {
        val lines = listOf("בית הו בית", "", "שלום עליכם")
        val reply = ZemerLyricsClient.LineExtras(keys = listOf("ignored", "ignored", "ignored"), en = listOf("A home", "", "Peace"), yi = listOf("א היים", "", ""), source = "machine")
        val extras = LineExtras.aligned(lines, reply)!!
        assertEquals(listOf("A home", null, "Peace"), extras.forLines(lines, LineExtrasLanguage.ENGLISH))
        assertEquals(listOf("א היים", null, null), extras.forLines(lines, LineExtrasLanguage.YIDDISH))
        assertEquals("Peace", extras.textFor("שָׁלוֹם עֲלֵיכֶם", LineExtrasLanguage.ENGLISH))
        assertNull(LineExtras.aligned(lines, null))
    }

    @Test
    fun `wire lang, transliteration rides the English request and OFF sends nothing`() {
        assertEquals("en", LineExtrasLanguage.ROMANIZED.wireLang)
        assertEquals("yi", LineExtrasLanguage.YIDDISH.wireLang)
        assertNull(LineExtrasLanguage.OFF.wireLang)
        assertTrue(LineExtras.linesHash(listOf("a", "b")) != LineExtras.linesHash(listOf("ab")))
    }
}
