package com.jtech.zemer.sync.models

import com.jtech.zemer.utils.ContentFilterConfig
import com.jtech.zemer.utils.ContentFilterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * blockPodcasts must sync to/from Firestore exactly like blockVideos does — this pins the
 * DeviceContentFilters <-> ContentFilterConfig round-trip that the anonymous/Google account sync relies on.
 */
class DeviceContentFiltersTest {

    @Test
    fun `fromConfig carries blockPodcasts both ways`() {
        assertEquals(
            true,
            DeviceContentFilters().fromConfig(ContentFilterConfig(blockPodcasts = true)).blockPodcasts
        )
        assertEquals(
            false,
            DeviceContentFilters().fromConfig(ContentFilterConfig(blockPodcasts = false)).blockPodcasts
        )
    }

    @Test
    fun `toConfig restores an explicit blockPodcasts value verbatim`() {
        assertTrue(DeviceContentFilters(blockPodcasts = true).toConfig().blockPodcasts)
        // Explicit false is respected even when videos are blocked (user allowed podcasts on purpose).
        assertFalse(DeviceContentFilters(blockVideos = true, blockPodcasts = false).toConfig().blockPodcasts)
    }

    @Test
    fun `an unset blockPodcasts is coupled to blockVideos on restore`() {
        // Sync-account video-blocker whose server doc predates the field: podcasts come back blocked.
        assertTrue(DeviceContentFilters(blockVideos = true, blockPodcasts = null).toConfig().blockPodcasts)
        // Not a video-blocker and unset: podcasts stay allowed.
        assertFalse(DeviceContentFilters(blockVideos = false, blockPodcasts = null).toConfig().blockPodcasts)
    }

    @Test
    fun `full round-trip preserves blockPodcasts alongside the other filters`() {
        val original = ContentFilterConfig(
            filtersEnabled = true,
            allowFemaleSingers = true,
            blockVideos = true,
            blockPodcasts = true,
            femalePasscodeHash = "hash",
        )
        val roundTripped = DeviceContentFilters().fromConfig(original).toConfig()
        assertEquals(original.filtersEnabled, roundTripped.filtersEnabled)
        assertEquals(original.allowFemaleSingers, roundTripped.allowFemaleSingers)
        assertEquals(original.blockVideos, roundTripped.blockVideos)
        assertEquals(original.blockPodcasts, roundTripped.blockPodcasts)
        assertEquals(original.femalePasscodeHash, roundTripped.femalePasscodeHash)
    }

    @Test
    fun `a Firestore doc written before this field decodes the key as unset`() {
        // @IgnoreExtraProperties + the nullable default make a missing key decode to null (unset), NOT
        // false — that is what lets toConfig couple it to blockVideos. No migration needed.
        assertNull(DeviceContentFilters().blockPodcasts)
        assertFalse(DeviceContentFilters().toConfig().blockPodcasts) // unset + no video block -> allowed
    }

    @Test
    fun `blocking podcasts alone counts as an active content filter`() {
        ContentFilterState.current = ContentFilterConfig(filtersEnabled = true, allowFemaleSingers = true)
        assertFalse(ContentFilterState.hasActiveFilters)
        ContentFilterState.current = ContentFilterConfig(
            filtersEnabled = true,
            allowFemaleSingers = true,
            blockPodcasts = true,
        )
        assertTrue(ContentFilterState.hasActiveFilters)
    }
}
