package com.jtech.zemer.utils

import com.jtech.zemer.db.entities.PodcastWhitelistEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for the channel-vs-show keying bug: the podcast whitelist is keyed by the host CHANNEL
 * id (`UC…`), but subscribed rows were being filtered by the SHOW id (`MPSP…`) against it, which never
 * matches — so once the whitelist populated, the subscribed list and New Episodes went permanently empty.
 * [PodcastLibrarySources.subscribedPodcastAllowed] must key off the channel id.
 */
class PodcastLibrarySourcesTest {

    private fun seed(vararg channelIds: String) {
        PodcastWhitelistCache.updateAll(channelIds.map { PodcastWhitelistEntity(channelId = it, name = it) })
    }

    @Test
    fun `an approved host channel passes`() {
        seed("UCapproved")
        assertTrue(PodcastLibrarySources.subscribedPodcastAllowed("UCapproved"))
    }

    @Test
    fun `a non-approved channel is dropped`() {
        seed("UCapproved")
        assertFalse(PodcastLibrarySources.subscribedPodcastAllowed("UCother"))
    }

    @Test
    fun `a SHOW id never matches the channel-keyed whitelist (the bug)`() {
        // MPSP… is a show id; the cache holds only channel ids, so passing a show id must be false —
        // which is exactly why the filter has to use channelId, not the PodcastEntity's own id.
        seed("UCapproved")
        assertFalse(PodcastLibrarySources.subscribedPodcastAllowed("MPSPshow123"))
    }

    @Test
    fun `a null channelId is kept (grandfathered or not-yet-synced subscription)`() {
        seed("UCapproved")
        assertTrue(PodcastLibrarySources.subscribedPodcastAllowed(null))
    }
}
