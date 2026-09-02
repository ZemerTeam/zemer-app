package com.jtech.zemer.lyrics.zemer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Kotlin port must produce byte-identical output to the server's `zingToPlain` on real tracks. */
class ZingParserGoldenTest {
    private fun res(name: String) = javaClass.classLoader!!.getResourceAsStream("lyrics/$name")!!.readBytes().toString(Charsets.UTF_8)

    @Test
    fun `matches server golden output on every track`() {
        for (g in Json.parseToJsonElement(res("zing-golden.json")).jsonArray) {
            val o = g.jsonObject
            assertEquals("track ${o["id"]}", o["plain"]!!.jsonPrimitive.content, ZingParser.toPlain(o["html"]!!.jsonPrimitive.content))
        }
    }

    @Test
    fun `p and br lines, pre blocks, entities, section markers dropped`() {
        assertEquals("שמע קולנו\nה׳ אלוקינו\n\nורחם & עלינו", ZingParser.toPlain("<p>שמע קולנו</p><p>ה׳ אלוקינו</p><p><br></p><p>ורחם &amp; עלינו</p>"))
        assertEquals("קום קום\nוהתהלך בארץ", ZingParser.toPlain("<pre class=\"ql-syntax\">[פתיחה]\nקום קום\n[בית 1]\nוהתהלך בארץ\n</pre>"))
        assertEquals("", ZingParser.toPlain(null))
    }
}
