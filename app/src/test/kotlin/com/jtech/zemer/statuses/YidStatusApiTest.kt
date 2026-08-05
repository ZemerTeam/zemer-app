package com.jtech.zemer.statuses

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM coverage of the YidStatus feed reduction: the music-category filter, the listable/ad/audio drops,
 * status field mapping, and per-creator ordering. Uses the real `org.json` (test dep). Doc:
 * `docs/status/yidstatus-api.md`.
 */
class YidStatusApiTest {

    // The keyword filter is now config-driven; the tests pin the (baked-in) default set.
    private val keywords = listOf("music", "singer", "kumzits", "simcha", "concert")

    @Test
    fun `parseYidCreators keeps only music categories, drops hidden creators`() {
        val json = """
            [
              {"id":"c1","name":"A Singer","slug":"a","avatar_url":"https://cdn/x.jpg","category":"Music"},
              {"id":"c2","name":"Concert Co","categories":["Business","Concerts"]},
              {"id":"c3","name":"Simcha Band","category":"Simcha / Events"},
              {"id":"c4","name":"A Comedian","category":"Comedy"},
              {"id":"c5","name":"News Desk","category":"News"},
              {"id":"c6","name":"Paused Singer","category":"Singer","paused":true},
              {"id":"c7","name":"Hidden","category":"Kumzits","review_hidden":true}
            ]
        """.trimIndent()
        val creators = parseYidCreators(JSONArray(json), keywords)
        // Music / Singer / Concerts / Simcha kept; Comedy + News dropped; paused/hidden dropped.
        assertEquals(listOf("c1", "c2", "c3"), creators.map { it.id })
        val c1 = creators.first()
        assertEquals(StatusSource.YID_STATUS, c1.source)
        assertEquals("A Singer", c1.displayName)
        assertEquals("https://cdn/x.jpg", c1.avatarPath) // full URL passed through
    }

    @Test
    fun `parseYidStatuses groups by creator, drops ads and audio, maps text, sorts oldest-first`() {
        val music = setOf("c1", "c2")
        val json = """
            [
              {"id":"s2","influencer_id":"c1","type":"image","media_url":"https://cdn/2.jpg","timestamp":"2026-08-02T12:00:00+00:00"},
              {"id":"s1","influencer_id":"c1","type":"video","media_url":"https://cdn/1.mp4","poster_url":"https://cdn/1.jpg","timestamp":"2026-08-02T09:00:00+00:00"},
              {"id":"s3","influencer_id":"c1","type":"text","caption":"Shabbat Shalom","background_color":"#57c9ff","timestamp":"2026-08-02T15:00:00+00:00"},
              {"id":"ad","influencer_id":"c1","type":"image","media_url":"https://cdn/ad.jpg","is_ad":true,"timestamp":"2026-08-02T10:00:00+00:00"},
              {"id":"au","influencer_id":"c1","type":"audio","media_url":"https://cdn/a.ogg","timestamp":"2026-08-02T11:00:00+00:00"},
              {"id":"x","influencer_id":"other","type":"video","media_url":"https://cdn/x.mp4","timestamp":"2026-08-02T09:00:00+00:00"}
            ]
        """.trimIndent()
        val byCreator = parseYidStatuses(JSONArray(json), music)

        // Only c1 has statuses; the ad, the audio, and the non-music creator are dropped.
        assertEquals(setOf("c1"), byCreator.keys)
        val posts = byCreator.getValue("c1")
        assertEquals(listOf("s1", "s2", "s3"), posts.map { it.id }) // oldest-first by timestamp
        posts.forEach { assertEquals(StatusSource.YID_STATUS, it.source) }

        val video = posts[0]
        assertEquals("video", video.kind)
        assertEquals("https://cdn/1.mp4", video.mediaPath)
        assertEquals("https://cdn/1.jpg", video.thumbPath)

        val text = posts[2]
        assertEquals("text", text.kind)
        assertEquals("Shabbat Shalom", text.textBody) // text body lands in textBody, not caption
        assertNull(text.caption)
        assertEquals("#57c9ff", text.textBgColor)
    }

    @Test
    fun `url helpers pass full URLs through and prefix relative paths`() {
        assertEquals("https://x/y.jpg", statusMediaUrl("https://x/y.jpg"))
        assertEquals("https://x/y.jpg", statusAvatarUrl("https://x/y.jpg"))
        assertTrue(statusMediaUrl("a/b.mp4")!!.endsWith("/status-media/a/b.mp4"))
        assertTrue(statusAvatarUrl("a/b.jpg")!!.endsWith("/avatars/a/b.jpg"))
    }
}
