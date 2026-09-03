package com.jtech.zemer.lyrics.zemer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JkaraokeLrcGoldenTest {
    private fun res(name: String) = javaClass.classLoader!!.getResourceAsStream("lyrics/$name")!!.readBytes().toString(Charsets.UTF_8)

    @Test
    fun `feed page to LRC matches the server golden output`() {
        val golden = Json.parseToJsonElement(res("jkaraoke-1971-golden.json")).jsonObject
        val lrc = JkaraokeLrc.fromFeedPage(res("jkaraoke-page28.json"), 1971)!!
        assertEquals(golden["plain"]!!.jsonPrimitive.content, lrc.plain)
        assertEquals(golden["synced"]!!.jsonPrimitive.content, lrc.synced)
    }

    @Test
    fun `sanity rules mirror the server`() {
        val l = { s: Double, t: String -> JkaraokeLrc.FeedLine(start = s, text = t) }
        assertEquals("00:00.00", JkaraokeLrc.lrcTime(0.0)); assertEquals("01:05.50", JkaraokeLrc.lrcTime(65.5)); assertEquals("10:05.13", JkaraokeLrc.lrcTime(605.126))
        assertNull(JkaraokeLrc.toLrc(listOf(l(1.0, "a"), l(2.0, "b"), l(3.0, "c")), 10))
        assertNull(JkaraokeLrc.toLrc(listOf(l(5.0, "a"), l(2.0, "b"), l(7.0, "c"), l(8.0, "d")), 10))
        assertNull(JkaraokeLrc.toLrc(listOf(l(1.0, "a"), l(2.0, "b"), l(3.0, "c"), l(40.0, "d")), 10))
        assertEquals("[00:01.20] שלום\n[00:03.50] עולם\n[00:07.00] a\n[00:09.25] b", JkaraokeLrc.toLrc(listOf(l(1.2, " שלום "), l(3.5, "עולם"), l(7.0, "a"), l(9.25, "b")), 12)!!.synced)
    }
}
