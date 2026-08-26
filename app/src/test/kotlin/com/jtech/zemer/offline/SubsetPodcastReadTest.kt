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

    // shows (id, name, author, channelId, thumbnail, episodeCountText, genres)
    private val s1 = SubPodcastShow("MPS1", "Alpha Show", "Host A", "UCn", "ts1", "2 episodes", genres = listOf("gemara", "history"))
    private val s2 = SubPodcastShow("MPS2", "Beta Show", "Host B", "UCn", "ts2", null, genres = listOf("gemara")) // female-blocked per item
    private val sf = SubPodcastShow("MPSF", "Ladies Show", "Host F", "UCw", "tsf", null, genres = listOf("gemara"))
    // Per-SHOW kid flag on a mixed (non-kid) channel — the 2026-08-26 shard column.
    private val sk = SubPodcastShow("MPSK", "Kids Show", "Host K", "UCn", "tsk", null, genres = listOf("gemara"), isKidZone = true)

    // episodes (videoId, showId, title, thumbnail, durationSec, publishedAt)
    private val e1 = SubPodcastEpisode("ve1", "MPS1", "Ep One", "te1", 100, "2026-05-01")
    private val e2 = SubPodcastEpisode("ve2", "MPS1", "Ep Two", "te2", 120, "2026-06-01") // newer than e1
    private val e3 = SubPodcastEpisode("ve3", "MPS2", "Ep Three", "te3", 90, null) // null date → sorts last
    private val ef = SubPodcastEpisode("vef", "MPSF", "Ladies Ep", "tef", 80, "2026-04-01")
    private val ek = SubPodcastEpisode("vek", "MPSK", "Kids Ep", "tek", 70, "2026-07-01")

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
        podcasts = listOf(s1, s2, sf, sk),
        podcastEpisodes = listOf(e1, e2, e3, ef, ek),
    )

    private val matcher = buildFemaleMatcher(corpus.artists)

    @Test
    fun `channel returns gated shows and newest-first episodes`() {
        val r = offlinePodcastChannel(corpus, "UCn", allowFemale = true, blockVideos = false, kidZone = false)!!
        assertEquals("Normal Cast", r.channel!!.name)
        // The kid show stays visible on the channel DETAIL shelf without the flag (server carve-out).
        assertEquals(listOf("MPS1", "MPS2", "MPSK"), r.shows.map { it.id })
        // newest-first: vek (2026-07) → ve2 (2026-06) → ve1 (2026-05) → ve3 (null last)
        assertEquals(listOf("vek", "ve2", "ve1", "ve3"), r.episodes.map { it.videoId })
        assertEquals("Kids Show", r.episodes.first().podcastName)
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

    @Test
    fun `search drops a blocked show's episodes by show id, not just videoId`() {
        // MPS2 rides blocked.female: its episode ve3 must vanish from offline search when female is
        // blocked, even though ve3's own videoId is not in the shard.
        val blocked = offlineSearch(corpus, matcher, "Three", 10, allowFemale = false, blockVideos = false, kidZone = false)
        assertTrue(blocked.categories.episodes.none { it.videoId == "ve3" })
        val allowed = offlineSearch(corpus, matcher, "Three", 10, allowFemale = true, blockVideos = false, kidZone = false)
        assertTrue(allowed.categories.episodes.any { it.videoId == "ve3" })
    }

    @Test
    fun `live podcast whitelist overlay drops de-approved channels with their shows and episodes`() {
        val overlaid = corpus.withLivePodcastWhitelist(setOf("UCn"))
        assertEquals(listOf("UCn"), overlaid.podcastChannels.map { it.id })
        assertEquals(listOf("MPS1", "MPS2", "MPSK"), overlaid.podcasts.map { it.id })
        assertTrue(overlaid.podcastEpisodes.none { it.videoId == "vef" })
        // empty live set = not synced yet -> no-op, never wipes the snapshot
        assertTrue(corpus === corpus.withLivePodcastWhitelist(emptySet()))
        // a null-channelId show is grandfathered
        val orphan = SubPodcastShow("MPSO", "Orphan Show", null, null, null, null)
        val withOrphan = corpus.copy(podcasts = corpus.podcasts + orphan)
        assertTrue(withOrphan.withLivePodcastWhitelist(setOf("UCn")).podcasts.any { it.id == "MPSO" })
    }

    @Test
    fun `genre catalog counts are post-filter, most-populated first, titles capitalized`() {
        // allowFemale: all three shows carry "gemara" (3); only s1 carries "history" (1).
        val open = offlinePodcastGenres(corpus, allowFemale = true, blockVideos = false, kidZone = false)
        assertEquals(listOf("gemara", "history"), open.genres.map { it.id }) // count desc → gemara first
        assertEquals(3, open.genres.first { it.id == "gemara" }.showCount)
        assertEquals("Gemara", open.genres.first { it.id == "gemara" }.title) // slug capitalized offline
        assertEquals(1, open.genres.first { it.id == "history" }.showCount)

        // female blocked: MPS2 (blocked.female) + MPSF (female channel) drop → gemara down to 1 (s1 only).
        val blocked = offlinePodcastGenres(corpus, allowFemale = false, blockVideos = false, kidZone = false)
        assertEquals(1, blocked.genres.first { it.id == "gemara" }.showCount)
    }

    @Test
    fun `genre detail lists gated member shows, 404 when none`() {
        val open = offlinePodcastGenre(corpus, "gemara", allowFemale = true, blockVideos = false, kidZone = false)!!
        assertEquals("Gemara", open.genre.title)
        assertEquals(listOf("MPS1", "MPS2", "MPSF"), open.shows.map { it.id })

        val blocked = offlinePodcastGenre(corpus, "gemara", allowFemale = false, blockVideos = false, kidZone = false)!!
        assertEquals(listOf("MPS1"), blocked.shows.map { it.id })

        // history has only s1; an unknown slug is a 404.
        assertEquals(listOf("MPS1"), offlinePodcastGenre(corpus, "history", allowFemale = true, blockVideos = false, kidZone = false)!!.shows.map { it.id })
        assertNull(offlinePodcastGenre(corpus, "nonexistent", allowFemale = true, blockVideos = false, kidZone = false))
    }

    // --- kid semantics (server parity, 2026-08-26: per-show flag; default-exclude on browse,
    // kidZone=1-only, carve-out on detail opens by id) ---

    @Test
    fun `browse surfaces default-exclude kid shows and serve only them under kidZone`() {
        assertEquals(
            listOf("MPS1", "MPS2", "MPSF"),
            offlinePodcasts(corpus, allowFemale = true, blockVideos = false, kidZone = false).podcasts.map { it.id },
        )
        assertEquals(
            listOf("MPSK"),
            offlinePodcasts(corpus, allowFemale = true, blockVideos = false, kidZone = true).podcasts.map { it.id },
        )
        // ek (2026-07-01) is the newest episode but rides a kid show: excluded by default, alone under the flag.
        assertEquals(
            listOf("vek"),
            offlinePodcastsNewEpisodes(corpus, k = 10, allowFemale = true, blockVideos = false, kidZone = true).episodes.map { it.videoId },
        )
        // Genre pages: gemara has s1+s2+sf+sk; default excludes the kid show, the flag serves only it.
        assertTrue(offlinePodcastGenre(corpus, "gemara", allowFemale = true, blockVideos = false, kidZone = false)!!.shows.none { it.id == "MPSK" })
        assertEquals(
            listOf("MPSK"),
            offlinePodcastGenre(corpus, "gemara", allowFemale = true, blockVideos = false, kidZone = true)!!.shows.map { it.id },
        )
    }

    @Test
    fun `detail opens by id keep the server carve-out - a kid show serves without the flag`() {
        // A saved subscription/deep-link must keep working with no flag sent.
        assertNotNull(offlinePodcast(corpus, "MPSK", 0, allowFemale = true, blockVideos = false, kidZone = false))
        // Under the flag a NON-kid show 404s (the server's kidZone=1 contract).
        assertNull(offlinePodcast(corpus, "MPS1", 0, allowFemale = true, blockVideos = false, kidZone = true))
    }

    @Test
    fun `a mixed channel hosting a kid show serves under kidZone with only its kid shows`() {
        // Server contract: 404 only unless the channel hosts a kid show; the shelf filters to them.
        val r = offlinePodcastChannel(corpus, "UCn", allowFemale = true, blockVideos = false, kidZone = true)!!
        assertEquals(listOf("MPSK"), r.shows.map { it.id })
        assertEquals(listOf("vek"), r.episodes.map { it.videoId })
        // A channel with no kid content 404s under the flag.
        assertNull(offlinePodcastChannel(corpus, "UCw", allowFemale = true, blockVideos = false, kidZone = true))
    }

    @Test
    fun `search default-excludes kid shows and their episodes, kidZone serves only them`() {
        val def = offlineSearch(corpus, matcher, "Kids", 10, allowFemale = true, blockVideos = false, kidZone = false)
        assertTrue(def.categories.podcasts.none { it.id == "MPSK" })
        assertTrue(def.categories.episodes.none { it.videoId == "vek" })
        val kid = offlineSearch(corpus, matcher, "Kids", 10, allowFemale = true, blockVideos = false, kidZone = true)
        assertTrue(kid.categories.podcasts.any { it.id == "MPSK" })
        assertTrue(kid.categories.episodes.any { it.videoId == "vek" })
    }
}
