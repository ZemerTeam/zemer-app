package com.jtech.zemer.lyrics.zemer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The Kotlin port must produce byte-identical output to the server parser on the shared fixtures. */
class JyricsParserGoldenTest {
    private fun res(name: String) = javaClass.classLoader!!.getResourceAsStream("lyrics/$name")!!.readBytes().toString(Charsets.UTF_8)

    @Test
    fun `matches server golden output on every fixture`() {
        val golden = Json.parseToJsonElement(res("jyrics-golden.json")).jsonObject
        for ((file, expected) in golden) {
            val parsed = JyricsParser.parse(res(file))
            assertEquals("header of $file", expected.jsonObject["header"]!!.jsonPrimitive.content, parsed.header)
            assertEquals("plain of $file", expected.jsonObject["plain"]!!.jsonPrimitive.content, parsed.plain)
        }
    }

    @Test
    fun `related-songs list never leaks and credits are dropped`() {
        val html = "<article><div><h4>LYRIC</h4></div><p>T- A</p><p>(Later Recorded by X)</p><p>שלום<br />\nעולם<br />\nשיר<br />\nחדש</p><ul class=\"related-list\"><li>Other – אחר</li></ul></article>"
        val p = JyricsParser.parse(html)
        assertEquals("שלום\nעולם\nשיר\nחדש", p.plain)
        assertFalse(p.plain.contains("אחר"))
    }
}
