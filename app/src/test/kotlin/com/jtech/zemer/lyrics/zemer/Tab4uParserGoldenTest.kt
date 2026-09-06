package com.jtech.zemer.lyrics.zemer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Kotlin port must produce the text the server stored and audio-verified from the same page (`extractSheet`). */
class Tab4uParserGoldenTest {
    private fun res(name: String) = javaClass.classLoader!!.getResourceAsStream("lyrics/$name")!!.readBytes().toString(Charsets.UTF_8)

    @Test
    fun `matches the server output on the fixture page`() {
        assertEquals(res("tab4u-72017.expected.txt").trimEnd(), Tab4uParser.parse(res("tab4u-72017.html")))
    }

    @Test
    fun `chords rows, section cells and latin annotations are skipped and a stub page yields nothing`() {
        val row = { cls: String, t: String -> "<tr><td class=\"$cls\" style=\"x\">$t</td></tr>" }
        val html = "<table>" + row("chords", "Am G") + row("song", "פתיחה:") + row("song", "שורה &quot;אחת&quot;&nbsp;") +
            row("song", "Latin only") + row("song", "<b>שורה</b> שתיים") + (1..4).joinToString("") { row("song", "שורה $it") } + "</table>"
        assertEquals("שורה \"אחת\"\nשורה שתיים\nשורה 1\nשורה 2\nשורה 3\nשורה 4", Tab4uParser.parse(html))
        assertNull(Tab4uParser.parse(row("song", "שורה אחת") + row("song", "שורה שתיים")))
    }
}
