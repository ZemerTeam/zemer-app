package com.jtech.zemer.utils

import com.jtech.zemer.db.entities.PodcastWhitelistEntity
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The channel-keyed podcast whitelist gate. Membership is pure; [PodcastWhitelistCache.channelPasses]
 * adds the female gate used on the browse grid + `filterWhitelisted` so a wholly-female host channel is
 * hidden when female filtering is on (matching the server, the offline layer, and the artist browse).
 */
class PodcastWhitelistCacheTest {

    @After
    fun reset() = PodcastWhitelistCache.updateAll(emptyList())

    private fun entry(id: String, isFemale: Boolean = false) =
        PodcastWhitelistEntity(channelId = id, name = id, isFemale = isFemale)

    @Test
    fun `membership is female-agnostic - channelPasses is not`() {
        PodcastWhitelistCache.updateAll(listOf(entry("UCmale"), entry("UCfemale", isFemale = true)))
        // Pure membership sees both (used for routing / whitelist loading).
        assertTrue(PodcastWhitelistCache.isChannelWhitelisted("UCmale"))
        assertTrue(PodcastWhitelistCache.isChannelWhitelisted("UCfemale"))
    }

    @Test
    fun `a female channel is hidden when female filtering is on, kept when allowed`() {
        PodcastWhitelistCache.updateAll(listOf(entry("UCfemale", isFemale = true)))
        assertFalse(PodcastWhitelistCache.channelPasses("UCfemale", allowFemale = false))
        assertTrue(PodcastWhitelistCache.channelPasses("UCfemale", allowFemale = true))
    }

    @Test
    fun `a non-female channel always passes and a non-member never does`() {
        PodcastWhitelistCache.updateAll(listOf(entry("UCmale")))
        assertTrue(PodcastWhitelistCache.channelPasses("UCmale", allowFemale = false))
        assertTrue(PodcastWhitelistCache.channelPasses("UCmale", allowFemale = true))
        assertFalse(PodcastWhitelistCache.channelPasses("UCunknown", allowFemale = true))
    }
}
