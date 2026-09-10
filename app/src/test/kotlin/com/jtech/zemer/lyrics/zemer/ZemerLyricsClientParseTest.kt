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
    fun `lineExtras parses additively and is null when the server omits it`() {
        val with = ZemerLyricsClient.json.decodeFromString(ZemerLyricsClient.Resolved.serializer(),
            """{"videoId":"v","verified":true,"sources":[],"lineExtras":{"keys":["3f1a9c0e","b02d1111"],"en":["A home oh a home",""],"roman":["bayit ho bayit","x"],"source":"machine","future":1}}""")
        assertEquals(listOf("3f1a9c0e", "b02d1111"), with.lineExtras!!.keys)
        assertEquals(listOf("A home oh a home", ""), with.lineExtras!!.en)
        assertNull(with.lineExtras!!.he)
        assertNull(with.lineExtras!!.yi)
        assertEquals("machine", with.lineExtras!!.source)
        assertNull(ZemerLyricsClient.json.decodeFromString(ZemerLyricsClient.Resolved.serializer(), body).lineExtras)
    }

    @Test
    fun `lineTimes, manual origin, zemer richSync and explicit nulls parse from live responses`() {
        val zing = ZemerLyricsClient.json.decodeFromString(ZemerLyricsClient.Resolved.serializer(), res("resolve-zingmusic-linetimes.json"))
        assertEquals("zingmusic", zing.lineTimes!!.type)
        assertEquals(60, zing.lineTimes!!.keys.size)
        assertEquals(60, zing.lineTimes!!.times.size)
        assertEquals(26.08, zing.lineTimes!!.times[0], 0.0)
        val zemer = ZemerLyricsClient.json.decodeFromString(ZemerLyricsClient.Resolved.serializer(), res("resolve-zemer-richsync.json"))
        assertNull(zemer.lineTimes)
        assertTrue(zemer.sources[0].richSync!!.contains("<00:17.45>"))
        val manual = ZemerLyricsClient.json.decodeFromString(ZemerLyricsClient.Resolved.serializer(),
            """{"videoId":"3SY60gjC4po","lang":"yi","verified":true,"sources":[{"type":"manual","origin":"telegram","ref":"tg:x/121","plain":"a\nb\nc\nd","synced":false,"syncedLrc":null}]}""")
        assertEquals("telegram", manual.sources[0].origin)
        assertEquals("tg:x/121", manual.sources[0].ref)
        assertNull(manual.sources[0].syncedLrc)
        val apple = ZemerLyricsClient.json.decodeFromString(ZemerLyricsClient.Resolved.serializer(), res("resolve-apple-linetimes.json"))
        assertEquals("1571752969", apple.sources[0].catalogId)
        assertTrue(apple.sources[0].synced)
        assertEquals("apple", apple.lineTimes!!.type)
        assertEquals(51, apple.lineTimes!!.times.size)
        // the extras any source may carry, incl. the integer-valued publicDomain the server emits for an all-liturgy text
        val extras = ZemerLyricsClient.json.decodeFromString(ZemerLyricsClient.Resolved.serializer(),
            """{"videoId":"x","sources":[{"type":"manual","origin":"jyrics","publicDomain":1,"borrowedFrom":"abc","syncedTruncated":0.4,"wordSyncPartial":true,"plain":"a\nb\nc\nd"},{"type":"zemer","provenance":"zemer-align","admittedBy":"certified","plain":"a\nb\nc\nd"}],"lineTimes":{"type":"youtube","count":1,"times":[1.5],"keys":["00000000"],"offsetSec":-0.42,"offsetFrom":"measured"},"syncTruncated":0.61}""")
        assertEquals(1.0, extras.sources[0].publicDomain!!, 0.0)
        assertEquals("abc", extras.sources[0].borrowedFrom)
        assertEquals(0.4, extras.sources[0].syncedTruncated!!, 0.0)
        assertEquals(true, extras.sources[0].wordSyncPartial)
        assertEquals("zemer-align", extras.sources[1].provenance)
        assertEquals("certified", extras.sources[1].admittedBy)
        assertEquals(-0.42, extras.lineTimes!!.offsetSec!!, 0.0)
        assertEquals("measured", extras.lineTimes!!.offsetFrom)
        assertEquals(0.61, extras.syncTruncated!!, 0.0)
    }

    private fun res(name: String) = javaClass.classLoader!!.getResourceAsStream("lyrics/$name")!!.readBytes().toString(Charsets.UTF_8)

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
