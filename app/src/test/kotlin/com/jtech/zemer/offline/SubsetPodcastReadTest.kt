package com.jtech.zemer.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Assembly-rule parity for the offline PODCAST reads ([offlinePodcast] / [offlinePodcastChannel] /
 * [offlinePodcastsNewEpisodes]) and the search folding (server reply 4). Asserted over a tiny hand-built
 * [SubsetCorpus] — no Android runtime/network. Covers: channel-inherited female/KidZone gate, per-show
 * `blocked`-shard female exception, newest-first episode ordering, the `k`/one-page caps, and 404s.
 */
class SubsetPodcastReadTest {

    // channels (id, name, thumbnail, isFemale, isKidZone, isVerified, showCount, episodeCount)
    private val chNorm = SubPodcastChannel("UCn", "Normal Cast", "tn", isFemale = false, isKidZone = false, isVerified = true, showCount = 2, episodeCount = 3)
    private val chFem = SubPodcastChannel("UCw", "Women Cast", "tw", isFemale = true, isKidZone = false, isVerified = false, showCount = 1, episodeCount = 1)

    // shows (id, name, author, channelId, thumbnail, episodeCountText)
    private val s1 = SubPodcastShow("MPS1", "Alpha Show", "Host A", "UCn", "ts1", "2 episodes")
    private val s2 = SubPodcastShow("MPS2", "Beta Show", "Host B", "UCn", "ts2", null) // female-blocked per item
    private val sf = SubPodcastShow("MPSF", "Ladies Show", "Host F", "UCw", "tsf", null)

    // episodes (videoId, showId, title, thumbnail, durationSec, publishedAt)
    private val e1 = SubPodcastEpisode("ve1", "MPS1", "Ep One", "te1", 100, "2026-05-01")
    private val e2 = SubPodcastEpisode("ve2", "MPS1", "Ep Two", "te2", 120, "2026-06-01") // newer than e1
    private val e3 = SubPodcastEpisode("ve3", "MPS2", "Ep Three", "te3", 90, null) // null date → sorts last
    private val ef = SubPodcastEpisode("vef", "MPSF", "Ladies Ep", "tef", 80, "2026-04-01")

    private val corpus = SubsetCorpus(
        artists = emptyList(),
        tracks = emptyList(),
        albums = emptyList(),
        albumTracks = emptyList(),
        artistPlaylists = emptyList(),
        community = emptyList(),
        communityTracks = emptyList(),
        homeRank = emptyList(),
        zemerPlaylists = emptyList(),
        zemerItems = emptyList(),
        // MPS2 is a female show on a mixed (non-female) channel → rides the `blocked` shard.
        blocked = SubBlocked(global = emptySet(), female = setOf("MPS2")),
        podcastChannels = listOf(chNorm, chFem),
        podcasts = listOf(s1, s2, sf),
        podcastEpisodes = listOf(e1, e2, e3, ef),
    )

    private val matcher = buildFemaleMatcher(corpus.artists)

    @Test
    fun `channel returns gated shows and newest-first episodes`() {
        val r = offlinePodcastChannel(corpus, "UCn", allowFemale = true, blockVideos = false, kidZone = false)!!
        assertEquals("Normal Cast", r.channel!!.name)
        assertEquals(listOf("MPS1", "MPS2"), r.shows.map { it.id })
        // newest-first: ve2 (2026-06) → ve1 (2026-05) → ve3 (null last)
        assertEquals(listOf("ve2", "ve1", "ve3"), r.episodes.map { it.videoId })
        assertEquals("Alpha Show", r.episodes.first().podcastName)
    }

    @Test
    fun `female channel is a 404 when female blocked and served when allowed`() {
        assertNull(offlinePodcastChannel(corpus, "UCw", allowFemale = false, blockVideos = false, kidZone = false))
        val allowed = offlinePodcastChannel(corpus, "UCw", allowFemale = true, blockVideos = false, kidZone = false)
        assertNotNull(allowed)
        assertEquals(listOf("MPSF"), allowed!!.shows.map { it.id })
    }

    @Test
    fun `unknown channel is a 404`() {
        assertNull(offlinePodcastChannel(corpus, "UCzzz", allowFemale = true, blockVideos = false, kidZone = false))
    }

    @Test
    fun `show blocked-female id is a 404 when female blocked, served when allowed`() {
        assertNull(offlinePodcast(corpus, "MPS2", 0, allowFemale = false, blockVideos = false, kidZone = false))
        assertNotNull(offlinePodcast(corpus, "MPS2", 0, allowFemale = true, blockVideos = false, kidZone = false))
    }

    @Test
    fun `show returns its whole episode list newest-first in one page`() {
        val r = offlinePodcast(corpus, "MPS1", 0, allowFemale = true, blockVideos = false, kidZone = false)!!
        assertEquals("Alpha Show", r.podcast!!.name)
        assertEquals(listOf("ve2", "ve1"), r.episodes.map { it.videoId })
        assertNull(r.nextOffset)
        // a paged follow-up call returns nothing more (offline serves everything at offset 0)
        assertTrue(offlinePodcast(corpus, "MPS1", 2, allowFemale = true, blockVideos = false, kidZone = false)!!.episodes.isEmpty())
    }

    @Test
    fun `new episodes are newest-first, k-capped and gated`() {
        val open = offlinePodcastsNewEpisodes(corpus, k = 2, allowFemale = true, blockVideos = false, kidZone = false)
        assertEquals(listOf("ve2", "ve1"), open.episodes.map { it.videoId })
        // female blocked: MPS2 (blocked.female) + MPSF (female channel) drop → only MPS1's episodes remain.
        val blocked = offlinePodcastsNewEpisodes(corpus, k = 10, allowFemale = false, blockVideos = false, kidZone = false)
        assertEquals(listOf("ve2", "ve1"), blocked.episodes.map { it.videoId })
    }

    @Test
    fun `search folds podcast shows and episodes with the channel gate`() {
        val shows = offlineSearch(corpus, matcher, "Alpha", 10, allowFemale = true, blockVideos = false, kidZone = false)
        assertTrue(shows.categories.podcasts.any { it.id == "MPS1" })

        // A female-channel episode is dropped from search when female is blocked.
        val ladiesBlocked = offlineSearch(corpus, matcher, "Ladies", 10, allowFemale = false, blockVideos = false, kidZone = false)
        assertTrue(ladiesBlocked.categories.episodes.none { it.videoId == "vef" })
        val ladiesAllowed = offlineSearch(corpus, matcher, "Ladies", 10, allowFemale = true, blockVideos = false, kidZone = false)
        assertTrue(ladiesAllowed.categories.episodes.any { it.videoId == "vef" })
    }
}
