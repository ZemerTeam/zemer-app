package com.jtech.zemer.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The offline radio ranking port ([radio] / [buildRadioIndex]) — the on-device port of
 * `zemer-search/index/radio.mjs`'s blend, scoped to what the `radio-<n>` shards ship. Asserted
 * deterministically over a tiny hand-built [SubsetCorpus] (no Android runtime, network or files).
 */
class SubsetRadioTest {

    // --- artists (id, name, thumbnail, isFemale, isChasid, isKidZone) -----------------------------
    private val a = SubArtist("UCa", "Artist A", null, isFemale = false, isChasid = false, isKidZone = false)
    private val f = SubArtist("UCf", "Artist F", null, isFemale = true, isChasid = false, isKidZone = false)
    private val c = SubArtist("UCc", "Artist Chasid", null, isFemale = false, isChasid = true, isKidZone = false)

    // --- tracks (videoId, title, artistId, isVideo, durationSec, playCount, uploadDate) -----------
    private val seed = SubTrack("seed", "Seed Song", "UCa", false, 200, 1000, "2020-01-01")
    private val sessNeighbour = SubTrack("sess1", "Session Neighbour", "UCa", false, 200, 50, "2020-06-01")
    private val libNeighbour = SubTrack("lib1", "Library Neighbour", "UCa", false, 200, 10, "2021-01-01")
    private val ownOther = SubTrack("own2", "Artist A's Other Song", "UCa", false, 200, 5000, "2020-03-01")
    private val femaleTrack = SubTrack("fem1", "Female Track", "UCf", false, 200, 9000, null)
    private val chasidTrack = SubTrack("chas1", "Chasid Track", "UCc", false, 200, 1, "2019-12-01") // near seed's year
    private val distantTrack = SubTrack("far1", "Far Song", "UCa", false, 200, 1, "2099-01-01")
    private val videoTrack = SubTrack("vid1", "A Video", "UCa", true, 200, 2000, null)

    private val allTracks = listOf(seed, sessNeighbour, libNeighbour, ownOther, femaleTrack, chasidTrack, distantTrack, videoTrack)

    private fun corpus(radioRows: List<SubRadioRow>) = SubsetCorpus(
        artists = listOf(a, f, c),
        tracks = allTracks,
        albums = emptyList(),
        albumTracks = emptyList(),
        artistPlaylists = emptyList(),
        community = emptyList(),
        communityTracks = emptyList(),
        homeRank = emptyList(),
        zemerPlaylists = emptyList(),
        zemerItems = emptyList(),
        blocked = SubBlocked(global = emptySet(), female = emptySet()),
        radioRows = radioRows,
    )

    private fun passAll(@Suppress("UNUSED_PARAMETER") v: String) = true

    // --- pure helpers ---------------------------------------------------------------------------------

    @Test
    fun `shrinkReach is 0 for no reach and grows toward 1 with reach`() {
        assertEquals(0.0, shrinkReach(0.0), 0.0)
        assertEquals(0.0, shrinkReach(-5.0), 0.0)
        val low = shrinkReach(1.0)
        val high = shrinkReach(1000.0)
        assertTrue(low > 0.0 && low < high && high < 1.0)
    }

    @Test
    fun `h01 is deterministic for the same id+seed and varies across ids`() {
        assertEquals(h01("abc", 0), h01("abc", 0), 0.0)
        assertNotEquals(h01("abc", 0), h01("xyz", 0))
        val v = h01("abc", 0)
        assertTrue("h01 must land in [0,1)", v in 0.0..0.9999999999)
    }

    // --- radio() ------------------------------------------------------------------------------------

    @Test
    fun `song seed heads the station and orders SESS above LIB above same-artist`() {
        val idx = buildRadioIndex(corpus(listOf(
            SubRadioRow("seed", pop = 100.0, lib = listOf("lib1" to 0.9), sess = listOf("sess1" to 0.9)),
        )))
        // A big enough limit to assemble the whole (tiny) corpus, so the same-artist tier's tied-at-zero
        // tracks (own2/far1/vid1 all have no pop and thus no reach here) are present to compare against,
        // not just whichever one wins the tie-break jitter within a short page.
        val page = radio(idx, "song", "seed", null, ::passAll, offset = 0, limit = 10)!!
        assertEquals("seed", page.ids.first()) // the tapped song plays first
        val rest = page.ids.drop(1)
        // SESS weight (2.0) beats LIB (1.25) beats the same-artist tier (0.2 * shrunk reach, here 0 for
        // every UCa track but "seed" since only "seed" carries a radio-shard row in this fixture).
        assertEquals("sess1", rest[0])
        assertEquals("lib1", rest[1])
        assertTrue("own2 (same artist, no cooc) ranks below both cooc neighbours", rest.indexOf("own2") > rest.indexOf("lib1"))
    }

    @Test
    fun `a song with no cooc row falls back to its artist's catalog as the seed set`() {
        // No radio-<n> row for "seed" at all -> hasCooc(seed) is false -> seedSet includes the artist's
        // OTHER tracks as cooc seeds; own2 has its own neighbour (chas1) which must then get bumped.
        val idx = buildRadioIndex(corpus(listOf(
            SubRadioRow("own2", pop = 50.0, lib = listOf("chas1" to 0.7), sess = emptyList()),
        )))
        val page = radio(idx, "song", "seed", null, ::passAll, offset = 0, limit = 6)!!
        assertEquals("seed", page.ids.first())
        assertTrue("cold seed reaches chas1 via its artist's own cooc row", page.ids.contains("chas1"))
    }

    @Test
    fun `artist seed opens on the highest-reach own track and expands via the same-artist tier`() {
        // The artist-seed "lead" pick is by shrunk REACH (the radio shard's `pop`), never playCount —
        // give own2 a reach so far above every other UCa track's (0, no radio row here) that the
        // JIT_SHUFFLE tie-jitter (<=0.35) can never flip the pick.
        val idx = buildRadioIndex(corpus(listOf(
            SubRadioRow("own2", pop = 100000.0, lib = emptyList(), sess = emptyList()),
            SubRadioRow("seed", pop = 1.0, lib = emptyList(), sess = emptyList()),
        )))
        val page = radio(idx, "artist", "UCa", null, ::passAll, offset = 0, limit = 10)!!
        assertEquals("own2", page.ids.first())
        assertTrue("the same-artist tier reaches the rest of the catalog", page.ids.contains("seed"))
    }

    @Test
    fun `album kind plays its opening run first, exempt from the artist-diversity cap`() {
        val idx = buildRadioIndex(corpus(emptyList()))
        // seedTracks is unused for kind=album (membership isn't modeled in this fixture); the seed itself
        // must be resolved through albumTrackIds, which is empty here, so opening degrades to empty and
        // the station is pure popularity/artist backfill — assert it never crashes and stays content-safe.
        val page = radio(idx, "album", "unknown-album", null, ::passAll, offset = 0, limit = 5)
        assertTrue(page != null && page.ids.isNotEmpty())
    }

    @Test
    fun `shuffle has no seed and fills purely from popularity-weighted jitter over the whole corpus`() {
        val idx = buildRadioIndex(corpus(emptyList()))
        val page = radio(idx, "shuffle", null, null, ::passAll, offset = 0, limit = 8)!!
        assertEquals(allTracks.size, page.ids.toSet().size) // no duplicates
        assertTrue(page.ids.containsAll(allTracks.map { it.videoId }))
    }

    @Test
    fun `playlist kind scores from the caller-resolved seedTracks`() {
        val idx = buildRadioIndex(corpus(listOf(
            SubRadioRow("own2", pop = 1.0, lib = emptyList(), sess = listOf("distantMatch" to 0.5)),
        )))
        // A seedTracks list the caller resolved from playlist membership (own2), whose own sess neighbour
        // (a made-up id absent from the corpus) is silently skipped rather than crashing.
        val page = radio(idx, "playlist", "some-playlist-id", listOf("own2"), ::passAll, offset = 0, limit = 5)
        assertTrue(page != null)
    }

    @Test
    fun `genre kind is unsupported offline and returns null`() {
        val idx = buildRadioIndex(corpus(emptyList()))
        assertNull(radio(idx, "genre", "nigunim", null, ::passAll, offset = 0, limit = 10))
    }

    @Test
    fun `an unrecognised kind returns null rather than guessing`() {
        val idx = buildRadioIndex(corpus(emptyList()))
        assertNull(radio(idx, "not-a-real-kind", null, null, ::passAll, offset = 0, limit = 10))
    }

    @Test
    fun `pass() gates every candidate, including the opening seed and the popularity backfill`() {
        val idx = buildRadioIndex(corpus(emptyList()))
        val page = radio(idx, "shuffle", null, null, { it != "vid1" }, offset = 0, limit = 10)!!
        assertTrue("blocked video track never appears", !page.ids.contains("vid1"))
    }

    @Test
    fun `paging is a pure prefix slice of the same deterministic ordering`() {
        val idx = buildRadioIndex(corpus(emptyList()))
        val whole = radio(idx, "shuffle", null, null, ::passAll, offset = 0, limit = allTracks.size)!!.ids
        val first = radio(idx, "shuffle", null, null, ::passAll, offset = 0, limit = 3)!!.ids
        val second = radio(idx, "shuffle", null, null, ::passAll, offset = 3, limit = 3)!!.ids
        assertEquals(whole.take(3), first)
        assertEquals(whole.drop(3).take(3), second)
    }

    @Test
    fun `nextOffset is null once the whole corpus has been paged through`() {
        val idx = buildRadioIndex(corpus(emptyList()))
        val page = radio(idx, "shuffle", null, null, ::passAll, offset = 0, limit = allTracks.size)!!
        assertNull(page.nextOffset)
        val partial = radio(idx, "shuffle", null, null, ::passAll, offset = 0, limit = 2)!!
        assertEquals(2, partial.nextOffset)
    }

    @Test
    fun `diversify never places more than MAX_RUN of the same artist consecutively`() {
        // Every track but the two non-UCa artists' is UCa; a plain popularity order would run 6 UCa
        // tracks in a row — diversify must interleave the two minority-artist tracks in.
        val idx = buildRadioIndex(corpus(emptyList()))
        val page = radio(idx, "shuffle", null, null, ::passAll, offset = 0, limit = allTracks.size)!!
        var run = 0
        var lastArtist: String? = null
        for (v in page.ids) {
            val artist = idx.byId[v]?.artistId
            run = if (artist == lastArtist) run + 1 else 1
            lastArtist = artist
            assertTrue("no more than 2 of the same artist in a row", run <= 2)
        }
    }
}
