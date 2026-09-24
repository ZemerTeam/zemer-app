package com.jtech.zemer.lyrics.zemer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Kotlin port must produce byte-identical output to the server parser (`parseShironet`) on the shared fixture. */
class ShironetParserGoldenTest {
    private fun res(name: String) = javaClass.classLoader!!.getResourceAsStream("lyrics/$name")!!.readBytes().toString(Charsets.UTF_8)

    @Test
    fun `matches server golden output on every fixture`() {
        for (g in Json.parseToJsonElement(res("shironet-golden.json")).jsonArray) {
            val o = g.jsonObject
            val parsed = ShironetParser.parse(res(o["file"]!!.jsonPrimitive.content))
            assertEquals(o["title"]!!.jsonPrimitive.content, parsed.title)
            assertEquals(o["artist"]!!.jsonPrimitive.content, parsed.artist)
            assertEquals(o["plain"]!!.jsonPrimitive.content, parsed.plain)
        }
    }

    @Test
    fun `br lines, stanza breaks, section labels dropped, entities unescaped`() {
        val html = "<html><head><title>מילים לשיר אהבת חיי - חנן בן ארי - שירונט</title></head><body>" +
            "<span class=\"artist_lyrics_text\">שורה ראשונה <br>שורה שנייה, עם פסיק <br> <br> <br>פזמון: <br>שורה שלישית&#39; <br>שורה רביעית <br> <br></span></body></html>"
        val p = ShironetParser.parse(html)
        assertEquals("אהבת חיי", p.title)
        assertEquals("חנן בן ארי", p.artist)
        assertEquals("שורה ראשונה\nשורה שנייה, עם פסיק\n\nשורה שלישית'\nשורה רביעית", p.plain)
        assertEquals("", ShironetParser.parse("<html><title>x</title></html>").plain)
    }
}
