package com.jtech.zemer.statuses

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM coverage of the JewishStatus response mapping ([parseCreators] / [parsePosts]) — the field
 * mapping and empty/null handling the whole feature depends on. Uses the real `org.json` (test dep).
 */
class StatusesApiTest {

    @Test
    fun `parseCreators maps fields and treats empty avatar as null`() {
        val json = """
            [
              {"id":"c1","slug":"shia","display_name":"Shia Scharf","avatar_path":"c1/a.jpg","live_now":true},
              {"id":"c2","slug":"anon","display_name":"No Avatar","avatar_path":"","live_now":false}
            ]
        """.trimIndent()
        val creators = parseCreators(JSONArray(json))
        assertEquals(2, creators.size)
        val a = creators[0]
        assertEquals("c1", a.id)
        assertEquals("shia", a.slug)
        assertEquals("Shia Scharf", a.displayName)
        assertEquals("c1/a.jpg", a.avatarPath)
        assertTrue(a.liveNow)
        // Empty avatar_path -> null; live_now defaults handled.
        assertNull(creators[1].avatarPath)
        assertFalse(creators[1].liveNow)
    }

    @Test
    fun `parsePosts maps kind, media, caption, duration and null duration`() {
        val json = """
            [
              {"id":"p1","kind":"video","media_path":"c1/v.mp4","thumb_path":"c1/t.jpg",
               "caption":"hello","link_url":null,"duration_seconds":42,"posted_at":"2026-08-01T19:32:52+00:00",
               "view_count":5,"download_count":2},
              {"id":"p2","kind":"text","media_path":"","thumb_path":"","caption":"just text",
               "link_url":"","duration_seconds":null,"posted_at":"2026-07-31T10:00:00+00:00"}
            ]
        """.trimIndent()
        val posts = parsePosts(JSONArray(json))
        assertEquals(2, posts.size)
        val v = posts[0]
        assertEquals("video", v.kind)
        assertEquals("c1/v.mp4", v.mediaPath)
        assertEquals("hello", v.caption)
        assertNull(v.linkUrl)                 // explicit JSON null -> null
        assertEquals(42, v.durationSeconds)
        assertEquals(5, v.viewCount)
        val t = posts[1]
        assertEquals("text", t.kind)
        assertNull(t.mediaPath)               // empty string -> null
        assertEquals("just text", t.caption)
        assertNull(t.linkUrl)                 // empty string -> null
        assertNull(t.durationSeconds)         // JSON null -> null (viewer falls back to 7s)
        assertEquals(0, t.downloadCount)      // absent -> default 0
    }

    @Test
    fun `JSON-null caption maps to null, never the string null`() {
        // Regression: Android's org.json optString returns the literal "null" for a JSON null, which
        // rendered a text status body as "null". optStringOrNull must return a real null.
        val json = """[{"id":"t","kind":"text","caption":null,"posted_at":"2026-08-01T00:00:00+00:00"}]"""
        val post = parsePosts(JSONArray(json)).single()
        assertNull(post.caption)
        assertNull(post.mediaPath)   // absent -> null
    }

    @Test
    fun `parses recent_post_ids for the ring and text_body for text posts`() {
        val creators = parseCreators(
            JSONArray("""[{"id":"c","slug":"s","display_name":"D","recent_post_ids":["a","b","c"]}]""")
        )
        assertEquals(listOf("a", "b", "c"), creators.single().recentPostIds)

        val post = parsePosts(
            JSONArray(
                """[{"id":"p","kind":"text","text_body":"Shabbat Shalom","text_bg_color":"#112233",
                    "posted_at":"2026-08-01T00:00:00+00:00"}]"""
            )
        ).single()
        assertEquals("Shabbat Shalom", post.textBody)
        assertEquals("#112233", post.textBgColor)
        assertTrue(post.caption == null) // text posts carry their body in text_body, not caption
    }

    @Test
    fun `creator with no recent_post_ids yields an empty list`() {
        val creators = parseCreators(JSONArray("""[{"id":"c","slug":"s","display_name":"D"}]"""))
        assertTrue(creators.single().recentPostIds.isEmpty())
    }

    @Test
    fun `empty array yields empty lists`() {
        assertTrue(parseCreators(JSONArray("[]")).isEmpty())
        assertTrue(parsePosts(JSONArray("[]")).isEmpty())
    }

    @Test
    fun `media and avatar url builders prefix the CDN and pass null through`() {
        assertNull(statusAvatarUrl(null))
        assertNull(statusMediaUrl(null))
        assertTrue(statusAvatarUrl("c1/a.jpg")!!.endsWith("/avatars/c1/a.jpg"))
        assertTrue(statusMediaUrl("c1/v.mp4")!!.endsWith("/status-media/c1/v.mp4"))
    }
}
