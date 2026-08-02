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
        // Empty avatar_path -> null.
        assertNull(creators[1].avatarPath)
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

    @Test
    fun `caughtUpOnLatest keys off the newest (last) recent id, not older ones`() {
        // recent_post_ids is oldest-first, so p2 is the newest.
        val creator = StatusCreator(
            id = "c1", slug = "s", displayName = "S", avatarPath = null,
            recentPostIds = listOf("p1", "p2"),
        )
        assertFalse(creator.caughtUpOnLatest(emptySet()))
        // Seeing only the OLDER status does not count as caught up.
        assertFalse(creator.caughtUpOnLatest(setOf("p1")))
        // Seeing the newest (last) status => caught up, even if an older one is still unseen.
        assertTrue(creator.caughtUpOnLatest(setOf("p2")))
        // A creator with no known statuses is never caught up (so it never sinks on an empty ring).
        assertFalse(creator.copy(recentPostIds = emptyList()).caughtUpOnLatest(setOf("p2")))
    }

    @Test
    fun `mergeStatusCreators drops cross-platform name duplicates, primary wins`() {
        fun c(id: String, name: String, source: StatusSource) =
            StatusCreator(id = id, slug = id, displayName = name, avatarPath = null, source = source)
        val jewish = listOf(
            c("j1", "The Shira Choir", StatusSource.JEWISH_STATUS),
            c("j2", "Yonasan Weinberger", StatusSource.JEWISH_STATUS),
        )
        val yid = listOf(
            c("y1", "the shira  choir!", StatusSource.YID_STATUS), // same act (case/space/punct) -> dropped
            c("y2", "Moishe Green", StatusSource.YID_STATUS),      // unique -> kept
        )
        val merged = mergeStatusCreators(jewish, yid)
        assertEquals(listOf("j1", "j2", "y2"), merged.map { it.id })
        // The kept YidStatus creator retains its source (for the See-all sections).
        assertEquals(StatusSource.YID_STATUS, merged.last().source)
    }

    @Test
    fun `applyStatusFilter hides the chosen kinds and keeps order, video always passes`() {
        fun p(id: String, kind: String) = StatusPost(
            id = id, kind = kind, mediaPath = null, thumbPath = null, caption = null,
            textBody = null, textBgColor = null, linkUrl = null, durationSeconds = null,
            postedAt = "2026-08-01T00:00:00+00:00",
        )
        val posts = listOf(p("v", "video"), p("i", "image"), p("t", "text"))

        // Default: hide text only.
        assertEquals(
            listOf("v", "i"),
            posts.applyStatusFilter(StatusContentFilter(hideText = true, hideImage = false)).map { it.id },
        )
        // Hide image only.
        assertEquals(
            listOf("v", "t"),
            posts.applyStatusFilter(StatusContentFilter(hideText = false, hideImage = true)).map { it.id },
        )
        // Hide both -> only video survives.
        assertEquals(
            listOf("v"),
            posts.applyStatusFilter(StatusContentFilter(hideText = true, hideImage = true)).map { it.id },
        )
        // Neither -> untouched.
        assertEquals(3, posts.applyStatusFilter(StatusContentFilter(hideText = false, hideImage = false)).size)
    }

    @Test
    fun `sortedByUnseenFirst sinks caught-up creators to the end, stable otherwise`() {
        fun c(id: String, vararg ids: String) = StatusCreator(
            id = id, slug = id, displayName = id, avatarPath = null,
            recentPostIds = ids.toList(),
        )
        val a = c("a", "a1", "a2")   // newest a2 unseen -> stays
        val b = c("b", "b1", "b2")   // newest b2 seen   -> sinks
        val d = c("d", "d1", "d2")   // newest d2 unseen -> stays
        val ordered = listOf(a, b, d).sortedByUnseenFirst(setOf("b2", "a1"))
        assertEquals(listOf("a", "d", "b"), ordered.map { it.id })
    }
}
