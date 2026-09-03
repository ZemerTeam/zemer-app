package com.jtech.zemer.lyrics.zemer

import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The `/lyrics/resolve` wire shape: unknown fields ignored, absent optionals null, sources typed by `type`. */
class ZemerLyricsClientParseTest {
    private val body = """{"videoId":"oBCuhsTm0Ss","verified":true,"hasSynced":false,"future":1,
        "sources":[{"type":"shironet","url":"https://shironet.mako.co.il/x","extra":"ignored"},
                   {"type":"jkaraoke","songId":1971,"feedPage":28,"feedUrl":"https://jkaraoke.com/feed?page=28"},
                   {"type":"booklet","plain":"line 1\nline 2","synced":false}]}"""

    @Test
    fun `resolved parses with optionals absent and unknown keys ignored`() {
        val r = ZemerLyricsClient.json.decodeFromString(ZemerLyricsClient.Resolved.serializer(), body)
        assertEquals("oBCuhsTm0Ss", r.videoId)
        assertTrue(r.verified)
        assertFalse(r.hasSynced)
        assertNull(r.lang)
        assertEquals(listOf("shironet", "jkaraoke", "booklet"), r.sources.map { it.type })
        assertEquals(1971L, r.sources[1].songId)
        assertEquals("line 1\nline 2", r.sources[2].plain)
        assertNull(r.sources[0].songId)
    }

    @Test
    fun `an empty resolve carries no sources`() {
        val r = ZemerLyricsClient.json.decodeFromString(ZemerLyricsClient.Resolved.serializer(), """{"videoId":"x"}""")
        assertTrue(r.sources.isEmpty())
        assertFalse(r.verified)
    }

    @Test
    fun `source lists round-trip`() {
        val list = listOf(ZemerLyricsClient.Source(type = "manual", plain = "a\nb\nc\nd"))
        val s = ZemerLyricsClient.json.encodeToString(ListSerializer(ZemerLyricsClient.Source.serializer()), list)
        assertEquals(list, ZemerLyricsClient.json.decodeFromString(ListSerializer(ZemerLyricsClient.Source.serializer()), s))
    }
}
