package com.jtech.zemer.lyrics.zemer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pins the Apple TTML -> LRC conversion to a live paxsenix reply (`apple-1571752969.json`, Apple song 1571752969). */
class AppleTtmlLrcGoldenTest {
    private fun res(name: String) = javaClass.classLoader!!.getResourceAsStream("lyrics/$name")!!.readBytes().toString(Charsets.UTF_8)

    @Test
    fun `live reply converts to the golden LRC`() {
        val lrc = AppleTtmlLrc.fromReply(res("apple-1571752969.json"))!!
        assertEquals(res("apple-1571752969.expected.lrc").trimEnd(), lrc)
        assertEquals(51, lrc.lines().size)
        // the line onsets are Apple's: the mirror's own millisecond LRC rendering agrees on every line (to the centisecond)
        val theirs = res("apple-1571752969.paxsenix.lrc").lines().filter { it.startsWith("[0") }
        assertEquals(theirs.map { it.substringAfter(']') }, lrc.lines().map { it.substringAfter("] ") })
        val clock = { tag: String -> tag.substring(1, 3).toInt() * 60 + tag.substring(4, tag.indexOf(']')).toDouble() }
        theirs.zip(lrc.lines()).forEach { (t, m) -> assertEquals(t, clock(t), clock(m), 0.006) }
    }

    @Test
    fun `unsynced reply serves its plain text without section labels, and the TTML is the synced fallback`() {
        val body = AppleTtmlLrc.fromReply(res("apple-unsynced-reply.json"))!!
        assertEquals("לפעמים", body.lines().first())
        assertEquals(29, body.lines().size)
        assertEquals(false, body.contains("[Verse]"))
        assertEquals(false, body.contains("["))
        // a synced reply whose ready lrc is missing or thin converts its TTML instead; an unsynced one never syncs
        val ttml = res("apple-1571752969.json")
        assertEquals(res("apple-1571752969.expected.lrc").trimEnd(), AppleTtmlLrc.fromReply(ttml.replaceFirst("{", """{"type":"Line","lrc":"[00:01.00] a\n[00:02.00] b",""")))
        assertEquals(res("apple-1571752969.expected.lrc").trimEnd(), AppleTtmlLrc.fromReply(ttml.replaceFirst("{", """{"type":"Line","lrc":null,""")))
        // plain text under four lines, or labels only, is nothing
        assertNull(AppleTtmlLrc.plainBody("[Verse]\na\nb\nc"))
        assertNull(AppleTtmlLrc.fromReply("""{"type":"None","plain":"[Chorus]\n[Verse]"}"""))
    }

    @Test
    fun `clock forms, word spans, entities and the sanity rules`() {
        assertEquals(16.833, AppleTtmlLrc.seconds("16.833")!!, 1e-9)
        assertEquals(64.448, AppleTtmlLrc.seconds("1:04.448")!!, 1e-9)
        assertEquals(3723.5, AppleTtmlLrc.seconds("1:02:03.5")!!, 1e-9)
        assertNull(AppleTtmlLrc.seconds("1:2:3:4"))
        assertNull(AppleTtmlLrc.seconds("abc"))
        val wordTimed = """<tt><body><div><p begin="1.0" end="2.0"><span begin="1.0" end="1.4">Hello</span> <span begin="1.5" end="2.0">world &amp; all</span></p>""" +
            """<p begin="1:03.0" end="4.0">two</p><p begin="1:05.0" end="6.0">three</p><p begin="1:07.25" end="8.0">four</p></div></body></tt>"""
        assertEquals("[00:01.00] Hello world & all\n[01:03.00] two\n[01:05.00] three\n[01:07.25] four", AppleTtmlLrc.toLrc(wordTimed))
        // fewer than four lines, or a backwards onset, is never sync
        assertNull(AppleTtmlLrc.toLrc("""<p begin="1.0">a</p><p begin="2.0">b</p><p begin="3.0">c</p>"""))
        assertNull(AppleTtmlLrc.toLrc("""<p begin="1.0">a</p><p begin="5.0">b</p><p begin="3.0">c</p><p begin="7.0">d</p>"""))
        assertNull(AppleTtmlLrc.fromReply("not json"))
        assertNull(AppleTtmlLrc.fromReply("""{"type":"Line","lrc":"only"}"""))
    }
}
