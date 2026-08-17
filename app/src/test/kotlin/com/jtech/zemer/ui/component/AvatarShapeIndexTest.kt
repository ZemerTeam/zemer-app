package com.jtech.zemer.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for [avatarPolygonIndex] - the pure selection behind the expressive avatar shapes.
 * The rest of the expressive UI (loaders, wavy bars, toggle chips, the carousel, the press bounce) is
 * Compose-only and not unit-testable without Robolectric, which this project does not have.
 */
class AvatarShapeIndexTest {

    private val count = 4

    @Test
    fun `same key always maps to the same slot`() {
        assertEquals(avatarPolygonIndex("UC_someArtistId", count), avatarPolygonIndex("UC_someArtistId", count))
        assertEquals(avatarPolygonIndex(42, count), avatarPolygonIndex(42, count))
    }

    @Test
    fun `index is always within 0 until count, including negative-hash keys`() {
        // "polygenelubricants".hashCode() == Integer.MIN_VALUE, the classic negative-hash string that a
        // plain `%` would turn negative and crash the palette lookup; floorMod must keep it in range.
        val keys = listOf("a", "bcd", "zzz", "UC123", "polygenelubricants", 7, -7, null)
        for (k in keys) {
            val i = avatarPolygonIndex(k, count)
            assertTrue("index $i out of range for key $k", i in 0 until count)
        }
    }

    @Test
    fun `null key falls to the first slot`() {
        assertEquals(0, avatarPolygonIndex(null, count))
    }
}
