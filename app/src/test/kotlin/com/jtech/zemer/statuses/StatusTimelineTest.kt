package com.jtech.zemer.statuses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * JVM coverage of the story viewer's pure timeline math ([resumePos] / [statusDateGroups] plus the
 * date helpers), extracted from StoryScreen so it can be tested without an Android runtime. A fixed
 * UTC zone is passed so the assertions are deterministic regardless of the CI machine's default zone.
 */
class StatusTimelineTest {

    private val utc = ZoneId.of("UTC")

    // Posts are oldest-first (the API/repo guarantee the viewer relies on). Only id + postedAt matter
    // to the timeline math, so the rest is filler.
    private fun post(id: String, postedAt: String) = StatusPost(
        id = id, kind = "image", mediaPath = null, thumbPath = null, caption = null,
        textBody = null, textBgColor = null, linkUrl = null, durationSeconds = null,
        postedAt = postedAt,
    )

    @Test
    fun `statusLocalDate converts to the given zone and returns null on garbage`() {
        // 23:30 UTC is still the SAME day in UTC but the NEXT day in a +05:30 zone.
        assertEquals("2026-08-01", statusLocalDate("2026-08-01T23:30:00+00:00", utc))
        assertEquals("2026-08-02", statusLocalDate("2026-08-01T23:30:00+00:00", ZoneId.of("Asia/Kolkata")))
        assertNull(statusLocalDate("not-a-date", utc))
    }

    @Test
    fun `resumePos on empty posts is the origin`() {
        assertEquals(ResumePos(0, 0), resumePos(emptyList(), emptySet(), "2026-08-02", utc))
    }

    @Test
    fun `resumePos defaults to today's window and its first unseen status`() {
        val posts = listOf(
            post("y1", "2026-08-01T09:00:00+00:00"), // yesterday
            post("t1", "2026-08-02T08:00:00+00:00"), // today
            post("t2", "2026-08-02T09:00:00+00:00"), // today
            post("t3", "2026-08-02T10:00:00+00:00"), // today
        )
        // Nothing seen -> today window starts at index 1, first unseen is t1 (index 1). floor = window start.
        assertEquals(ResumePos(1, 1), resumePos(posts, emptySet(), "2026-08-02", utc))
        // t1 seen -> resume on the first UNSEEN of today (t2, index 2); floor stays at the window start.
        assertEquals(ResumePos(1, 2), resumePos(posts, setOf("t1"), "2026-08-02", utc))
    }

    @Test
    fun `resumePos lands on today's newest when all of today is seen`() {
        val posts = listOf(
            post("t1", "2026-08-02T08:00:00+00:00"),
            post("t2", "2026-08-02T09:00:00+00:00"),
        )
        // Whole day seen -> caught up: sit on the newest (last) status, not past it.
        assertEquals(ResumePos(0, 1), resumePos(posts, setOf("t1", "t2"), "2026-08-02", utc))
    }

    @Test
    fun `resumePos falls back to the newest date window when nothing is from today`() {
        val posts = listOf(
            post("a1", "2026-07-30T09:00:00+00:00"), // older day
            post("b1", "2026-07-31T08:00:00+00:00"), // newest day
            post("b2", "2026-07-31T09:00:00+00:00"), // newest day
        )
        // No post from 2026-08-02 -> default to the newest date (07-31), floor at its window start (1).
        assertEquals(ResumePos(1, 1), resumePos(posts, emptySet(), "2026-08-02", utc))
    }

    @Test
    fun `statusDateGroups splits contiguous same-day runs with correct counts`() {
        val posts = listOf(
            post("a1", "2026-07-30T09:00:00+00:00"),
            post("b1", "2026-07-31T08:00:00+00:00"),
            post("b2", "2026-07-31T20:00:00+00:00"),
            post("c1", "2026-08-01T10:00:00+00:00"),
        )
        val groups = statusDateGroups(posts, utc)
        assertEquals(
            listOf(
                StatusDateGroup("2026-07-30", 0, 1),
                StatusDateGroup("2026-07-31", 1, 2),
                StatusDateGroup("2026-08-01", 3, 1),
            ),
            groups,
        )
    }

    @Test
    fun `statusDateGroups handles null and empty as empty`() {
        assertTrue(statusDateGroups(null, utc).isEmpty())
        assertTrue(statusDateGroups(emptyList(), utc).isEmpty())
    }

    @Test
    fun `formatPostedAt renders in the given zone and empties on garbage`() {
        // 13:05 UTC formatted in UTC.
        assertEquals("Aug 1 · 1:05 PM", formatPostedAt("2026-08-01T13:05:00+00:00", utc))
        assertEquals("", formatPostedAt("nonsense", utc))
    }
}
