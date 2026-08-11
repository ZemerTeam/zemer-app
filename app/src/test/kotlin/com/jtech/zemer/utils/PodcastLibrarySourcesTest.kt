package com.jtech.zemer.utils

import com.jtech.zemer.db.entities.PodcastWhitelistEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the podcast library gate:
 *  - the channel-vs-show keying bug: the podcast whitelist is keyed by the host CHANNEL id (`UC…`), but
 *    subscribed rows were being filtered by the SHOW id (`MPSP…`) against it, which never matches — so
 *    once the whitelist populated, the subscribed list and New Episodes went permanently empty.
 *  - the female gate: display surfaces must hide a wholly-female host channel when female filtering is on
 *    (they were using membership-only `isChannelWhitelisted`, leaking a female channel into the library).
 * [PodcastLibrarySources.subscribedPodcastAllowed]/[podcastChannelAllowed] key off the channel id AND
 * apply the female gate.
 */
class PodcastLibrarySourcesTest {

    private fun seed(vararg entries: PodcastWhitelistEntity) {
        PodcastWhitelistCache.updateAll(entries.toList())
    }

    private fun channel(id: String, female: Boolean = false) =
        PodcastWhitelistEntity(channelId = id, name = id, isFemale = female)

    /** Filters ON, female not allowed — the strictest state (the one the leak showed under). */
    private val strict = ContentFilterConfig(filtersEnabled = true, allowFemaleSingers = false)

    @Test
    fun `an approved host channel passes`() {
        seed(channel("UCapproved"))
        assertTrue(PodcastLibrarySources.subscribedPodcastAllowed("UCapproved", strict))
    }

    @Test
    fun `a non-approved channel is dropped`() {
        seed(channel("UCapproved"))
        assertFalse(PodcastLibrarySources.subscribedPodcastAllowed("UCother", strict))
    }

    @Test
    fun `a SHOW id never matches the channel-keyed whitelist (the bug)`() {
        // MPSP… is a show id; the cache holds only channel ids, so passing a show id must be false —
        // which is exactly why the filter has to use channelId, not the PodcastEntity's own id.
        seed(channel("UCapproved"))
        assertFalse(PodcastLibrarySources.subscribedPodcastAllowed("MPSPshow123", strict))
    }

    @Test
    fun `a null channelId is kept (grandfathered or not-yet-synced subscription)`() {
        seed(channel("UCapproved"))
        assertTrue(PodcastLibrarySources.subscribedPodcastAllowed(null, strict))
    }

    @Test
    fun `a wholly-female approved channel is hidden when female filtering is on (the leak)`() {
        seed(channel("UCfemale", female = true))
        assertFalse(PodcastLibrarySources.subscribedPodcastAllowed("UCfemale", strict))
        assertFalse(PodcastLibrarySources.podcastChannelAllowed("UCfemale", strict))
    }

    @Test
    fun `a wholly-female approved channel passes when female singers are allowed`() {
        seed(channel("UCfemale", female = true))
        val allowFemale = ContentFilterConfig(filtersEnabled = true, allowFemaleSingers = true)
        assertTrue(PodcastLibrarySources.subscribedPodcastAllowed("UCfemale", allowFemale))
        assertTrue(PodcastLibrarySources.podcastChannelAllowed("UCfemale", allowFemale))
    }

    @Test
    fun `a wholly-female approved channel passes when filtering is off entirely`() {
        seed(channel("UCfemale", female = true))
        val filtersOff = ContentFilterConfig(filtersEnabled = false, allowFemaleSingers = false)
        assertTrue(PodcastLibrarySources.subscribedPodcastAllowed("UCfemale", filtersOff))
        assertTrue(PodcastLibrarySources.podcastChannelAllowed("UCfemale", filtersOff))
    }
}
