package com.jtech.zemer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoAvailabilityCacheTest {

    @Test
    fun `unknown id ⇒ null`() {
        assertNull(VideoAvailabilityCache().get("nope"))
    }

    @Test
    fun `records music-video type and never clears a known type with null`() {
        val cache = VideoAvailabilityCache()
        cache.recordMusicVideoType("X", "MUSIC_VIDEO_TYPE_OMV")
        assertEquals("MUSIC_VIDEO_TYPE_OMV", cache.get("X")?.musicVideoType)
        cache.recordMusicVideoType("X", null)
        assertEquals("MUSIC_VIDEO_TYPE_OMV", cache.get("X")?.musicVideoType)
    }

    @Test
    fun `counterpart resolution merges without dropping a known type`() {
        val cache = VideoAvailabilityCache()
        cache.recordMusicVideoType("X", "MUSIC_VIDEO_TYPE_ATV")
        cache.recordCounterpartResolution("X", "VID")
        val a = cache.get("X")!!
        assertEquals("MUSIC_VIDEO_TYPE_ATV", a.musicVideoType)
        assertEquals("VID", a.counterpartVideoId)
        assertTrue(a.counterpartResolved)
    }

    @Test
    fun `negative counterpart resolution marks resolved with a null id`() {
        val cache = VideoAvailabilityCache()
        cache.recordCounterpartResolution("X", null)
        val a = cache.get("X")!!
        assertNull(a.counterpartVideoId)
        assertTrue(a.counterpartResolved)
    }

    @Test
    fun `passive counterparts map is folded in`() {
        val cache = VideoAvailabilityCache()
        cache.recordCounterparts(mapOf("S1" to "V1", "S2" to "V2"))
        assertEquals("V1", cache.get("S1")?.counterpartVideoId)
        assertEquals("V2", cache.get("S2")?.counterpartVideoId)
    }

    @Test
    fun `revision increments only on a real change`() {
        val cache = VideoAvailabilityCache()
        val start = cache.revision.value
        cache.recordMusicVideoType("X", "MUSIC_VIDEO_TYPE_OMV")
        val afterFirst = cache.revision.value
        assertTrue(afterFirst > start)
        // A no-op write (same type) must not bump the revision.
        cache.recordMusicVideoType("X", "MUSIC_VIDEO_TYPE_OMV")
        assertEquals(afterFirst, cache.revision.value)
    }

    @Test
    fun `bounded LRU evicts the least-recently-used entry`() {
        val cache = VideoAvailabilityCache(maxEntries = 2)
        cache.recordMusicVideoType("A", "MUSIC_VIDEO_TYPE_OMV")
        cache.recordMusicVideoType("B", "MUSIC_VIDEO_TYPE_OMV")
        cache.get("A") // touch A so B becomes least-recently-used
        cache.recordMusicVideoType("C", "MUSIC_VIDEO_TYPE_OMV")
        assertNull(cache.get("B"))
        assertEquals("MUSIC_VIDEO_TYPE_OMV", cache.get("A")?.musicVideoType)
        assertEquals("MUSIC_VIDEO_TYPE_OMV", cache.get("C")?.musicVideoType)
    }
}
