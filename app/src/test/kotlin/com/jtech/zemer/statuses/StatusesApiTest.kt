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
               "caption":"hello","duration_seconds":42,"posted_at":"2026-08-01T19:32:52+00:00",
               "view_count":5,"download_count":2},
              {"id":"p2","kind":"text","media_path":"","thumb_path":"","caption":"just text",
               "duration_seconds":null,"posted_at":"2026-07-31T10:00:00+00:00"}
            ]
        """.trimIndent()
        val posts = parsePosts(JSONArray(json))
        assertEquals(2, posts.size)
        val v = posts[0]
        assertEquals("video", v.kind)
        assertEquals("c1/v.mp4", v.mediaPath)
        assertEquals("hello", v.caption)
        assertEquals(42, v.durationSeconds)
        assertEquals(5, v.viewCount)
        val t = posts[1]
        assertEquals("text", t.kind)
        assertNull(t.mediaPath)               // empty string -> null
        assertEquals("just text", t.caption)
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

    private val showAll = StatusContentFilter(hideText = false, hideImage = false)

    @Test
    fun `caughtUpOnLatest keys off the newest (last) recent id, not older ones`() {
        // recent_post_ids is oldest-first, so p2 is the newest.
        val creator = StatusCreator(
            id = "c1", slug = "s", displayName = "S", avatarPath = null,
            recentPostIds = listOf("p1", "p2"),
        )
        assertFalse(creator.caughtUpOnLatest(emptySet(), showAll))
        // Seeing only the OLDER status does not count as caught up.
        assertFalse(creator.caughtUpOnLatest(setOf("p1"), showAll))
        // Seeing the newest (last) status => caught up, even if an older one is still unseen.
        assertTrue(creator.caughtUpOnLatest(setOf("p2"), showAll))
        // A creator with no known statuses is never caught up (so it never sinks on an empty ring).
        assertFalse(creator.copy(recentPostIds = emptyList()).caughtUpOnLatest(setOf("p2"), showAll))
    }

    @Test
    fun `visibleRecentIds drops hidden kinds, keys off newest visible for caught-up`() {
        // v(video) t(text) i(image), oldest-first; kinds aligned 1:1.
        val creator = StatusCreator(
            id = "c1", slug = "s", displayName = "S", avatarPath = null,
            recentPostIds = listOf("v", "i", "t"),
            recentPostKinds = listOf("video", "image", "text"),
        )
        // Default filter (hide text): text drops, ring shows v + i.
        val hideText = StatusContentFilter(hideText = true, hideImage = false)
        assertEquals(listOf("v", "i"), creator.visibleRecentIds(hideText))
        // Caught up is now keyed off the newest VISIBLE (i), not the hidden newest (t).
        assertTrue(creator.caughtUpOnLatest(setOf("i"), hideText))
        assertFalse(creator.caughtUpOnLatest(setOf("t"), hideText))
        // Hide both text and image -> only the video remains.
        assertEquals(listOf("v"), creator.visibleRecentIds(StatusContentFilter(hideText = true, hideImage = true)))
        // Unknown kinds (no recentPostKinds) -> show everything, never hide more than we can prove.
        assertEquals(listOf("v", "i", "t"), creator.copy(recentPostKinds = emptyList()).visibleRecentIds(hideText))
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
            textBody = null, textBgColor = null, durationSeconds = null,
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
        val ordered = listOf(a, b, d).sortedByUnseenFirst(setOf("b2", "a1"), showAll)
        assertEquals(listOf("a", "d", "b"), ordered.map { it.id })
    }

    // --- Pinned provider + Sponsored posts ---

    private fun creator(id: String, vararg ids: String, pinned: String? = null, source: StatusSource = StatusSource.JEWISH_STATUS) =
        StatusCreator(
            id = id, slug = id, displayName = id, avatarPath = null,
            recentPostIds = ids.toList(), source = source, pinnedSection = pinned,
        )

    @Test
    fun `sortedByUnseenFirst keeps pinned creators first even when fully seen`() {
        val zemer = creator("zemer", "z1", pinned = "Zemer")
        val a = creator("a", "a1")
        val b = creator("b", "b1")
        // Zemer watched (z1 seen), b caught up: pinned still leads, the rest unseen-first.
        val ordered = listOf(a, b, zemer).sortedByUnseenFirst(setOf("z1", "b1"), showAll)
        assertEquals(listOf("zemer", "a", "b"), ordered.map { it.id })
        // Two pinned creators keep their provider (input) order, even when the first is caught up.
        val other = creator("other", "o1", pinned = "Other")
        assertEquals(
            listOf("zemer", "other", "a"),
            listOf(a, zemer, other).sortedByUnseenFirst(emptySet(), showAll).map { it.id },
        )
        assertEquals(
            listOf("zemer", "other", "a"),
            listOf(a, zemer, other).sortedByUnseenFirst(setOf("z1"), showAll).map { it.id },
        )
    }

    @Test
    fun `mergeStatusCreators never lets a same-named creator displace a pinned one`() {
        val pinnedFeed = creator("zemer", "z1", pinned = "Zemer", source = StatusSource.YID_STATUS)
            .copy(displayName = "Zemer")
        val jewishNamesake = creator("j1", "j").copy(displayName = "zemer")
        // A pinned creator from the SECONDARY list leads and its primary namesake is dropped (never two).
        assertEquals(listOf("zemer"), mergeStatusCreators(listOf(jewishNamesake), listOf(pinnedFeed)).map { it.id })
        // And from the primary side a same-named secondary creator is dropped as before.
        val yidNamesake = creator("y1", "y", source = StatusSource.YID_STATUS).copy(displayName = "ZEMER")
        val pinnedPrimary = pinnedFeed.copy(source = StatusSource.JEWISH_STATUS)
        assertEquals(listOf("zemer"), mergeStatusCreators(listOf(pinnedPrimary), listOf(yidNamesake)).map { it.id })
    }

    @Test
    fun `mergeStatusCreators emits one pinned creator even when both families pin it`() {
        val fromJewish = creator("zemer", "z1", pinned = "Zemer").copy(displayName = "Zemer")
        val fromYid = fromJewish.copy(source = StatusSource.YID_STATUS)
        val merged = mergeStatusCreators(listOf(fromJewish, creator("a", "a1")), listOf(fromYid))
        assertEquals(listOf("zemer", "a"), merged.map { it.id })
    }

    @Test
    fun `parsePosts reads the additive sponsored fields, absent on normal posts`() {
        val json = """
            [
              {"id":"meh","kind":"video","media_path":"https://m/v.mp4","caption":"Meh","posted_at":"2026-10-15T19:20:00Z"},
              {"id":"ad","kind":"image","media_path":"https://m/a.jpg","caption":"Sponsored · Acme\nBuy\nhttps://acme.example",
               "posted_at":"2026-10-15T20:00:00Z","sponsored":true,"advertiser":"Acme","link":"https://acme.example",
               "ends_at":"2026-10-16T20:00:00Z"},
              {"id":"ad2","kind":"text","text_body":"Hi","posted_at":"2026-10-15T21:00:00Z","sponsored":true,
               "advertiser":"Beta","link":null}
            ]
        """.trimIndent()
        val (meh, ad, ad2) = parsePosts(JSONArray(json))
        assertFalse(meh.sponsored)
        assertNull(meh.advertiser)
        assertNull(meh.link)
        assertNull(meh.sponsoredLabel())
        assertTrue(ad.sponsored)
        assertEquals("Acme", ad.advertiser)
        assertEquals("https://acme.example", ad.link)
        assertNull(ad2.link)
        // The badge replaces the server's leading caption label; a normal post's text is untouched.
        assertEquals("Buy\nhttps://acme.example", ad.withoutSponsoredLabel(ad.caption))
        assertEquals("Hi", ad2.withoutSponsoredLabel("Hi\n\nSponsored · Beta"))
        assertEquals("Meh", meh.withoutSponsoredLabel("Meh"))
        // No advertiser: the server's bare "Sponsored ·" line is still recognized.
        val anon = ad.copy(advertiser = null)
        assertEquals("Buy", anon.withoutSponsoredLabel("Sponsored ·\nBuy"))
    }

    private fun post(id: String, postedAt: String, sponsored: Boolean = false) = StatusPost(
        id = id, kind = "video", mediaPath = null, thumbPath = null, caption = null, textBody = null,
        textBgColor = null, durationSeconds = null, postedAt = postedAt, sponsored = sponsored,
    )

    @Test
    fun `rotateSponsored is deterministic per seed and keeps normal posts in their chronological slots`() {
        val utc = java.time.ZoneOffset.UTC
        val posts = listOf(
            post("n1", "2026-10-15T08:00:00Z"),
            post("adA", "2026-10-15T09:00:00Z", sponsored = true),
            post("n2", "2026-10-15T10:00:00Z"),
            post("adB", "2026-10-15T11:00:00Z", sponsored = true),
            post("adC", "2026-10-15T12:00:00Z", sponsored = true),
            post("n3", "2026-10-16T08:00:00Z"),
        )
        val seed = "device-1|2026-10-15"
        val once = posts.rotateSponsored(seed, utc).map { it.id }
        assertEquals(once, posts.rotateSponsored(seed, utc).map { it.id })
        // Input order of the ads never changes the result (content-determined shuffle).
        val swapped = posts.toMutableList().apply { this[3] = posts[4]; this[4] = posts[3] }
        assertEquals(once, swapped.rotateSponsored(seed, utc).map { it.id })
        // Normal posts never move; ads stay in ad slots.
        assertEquals(listOf("n1", "n2", "n3"), listOf(once[0], once[2], once[5]))
        assertEquals(setOf("adA", "adB", "adC"), setOf(once[1], once[3], once[4]))
        // Different devices/days see different first ads (no advertiser always plays first).
        val firstAds = (1..40).map { posts.rotateSponsored("device-$it|2026-10-15", utc)[1].id }.toSet()
        assertEquals(setOf("adA", "adB", "adC"), firstAds)
        // No ads -> the very same list.
        val plain = posts.filterNot { it.sponsored }
        assertTrue(plain === plain.rotateSponsored(seed, utc))
    }
}
