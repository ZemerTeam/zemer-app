package com.jtech.zemer.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scoring + category-assembly parity for the offline search port ([SubsetSearch] / [SubsetCategories]).
 * The ranking laws are asserted directly against [searchIndex] over tiny hand-built indexes (so a
 * precision-floor cut-off can't hide a ranking bug), and the content filters + category split are asserted
 * end-to-end through [offlineSearch] over a small hand-built [SubsetCorpus]. Deterministic; no Android
 * runtime, network or files. (Cross-language byte parity vs the LIVE server is covered separately by the
 * full-corpus end-to-end check run during the port.)
 */
class SubsetSearchTest {

    /** Minimal [SearchDoc] for the pure-ranking assertions. */
    private data class Doc(
        override val title: String,
        override val artistName: String,
        override val sortId: String,
    ) : SearchDoc

    private fun index(vararg docs: Doc) = buildSubsetIndex(docs.toList())

    // --- ranking laws (search.mjs) ----------------------------------------------------------------

    @Test
    fun `exact-title match outranks a fuzzy (typo) match`() {
        val exact = Doc(title = "Hallelujah", artistName = "Miami Boys Choir", sortId = "aaaaaaaaaaa")
        val fuzzy = Doc(title = "Hallelujeh", artistName = "Miami Boys Choir", sortId = "bbbbbbbbbbb") // 1-edit typo
        val hits = searchIndex(index(exact, fuzzy), "hallelujah", 10)
        assertEquals(2, hits.size)
        assertEquals("aaaaaaaaaaa", hits[0].doc.sortId) // exact first
        assertTrue("exact must score strictly above fuzzy", hits[0].score > hits[1].score)
    }

    @Test
    fun `artist affinity (origCount ge 2) lifts a track BY the searched artist above a title-only match`() {
        // `byArtist` only CONTAINS the query in its artist name (begins with "The"), so without the
        // multi-word ARTIST_AFFINITY bonus the title-exact `titleHit` (+2.0 boost) would win. Affinity flips it.
        val byArtist = Doc(title = "Achas", artistName = "The Simcha Leiner Band", sortId = "by000000000")
        val titleHit = Doc(title = "Simcha Leiner", artistName = "Someone Else", sortId = "ti000000000")
        val hits = searchIndex(index(byArtist, titleHit), "simcha leiner", 10)
        assertEquals("by000000000", hits.first().doc.sortId)
        val byScore = hits.first { it.doc.sortId == "by000000000" }.score
        val tiScore = hits.first { it.doc.sortId == "ti000000000" }.score
        assertTrue("affinity must lift the by-artist track above the title-only match", byScore > tiScore)
    }

    @Test
    fun `precision floor drops a weak hit that still clears the coverage gate`() {
        val strong = Doc(title = "Shalom", artistName = "Miami Boys Choir", sortId = "strong00000")
        val weak = Doc(title = "Shabom", artistName = "Miami Boys Choir", sortId = "weak0000000") // fuzzy-only, no boosts
        val hits = searchIndex(index(strong, weak), "shalom", 10)
        // `weak` matches (coverage 1 >= need 1) but scores below REL_FLOOR * top, so it is cut.
        assertEquals(1, hits.size)
        assertEquals("strong00000", hits[0].doc.sortId)
    }

    // --- content filters + category assembly (categories.mjs + api.mjs /search) --------------------

    // female primary
    private val fem = SubArtist("a_fem", "Franciska", null, isFemale = true, isChasid = false, isKidZone = false)
    private val leiner = SubArtist("a_leiner", "Simcha Leiner", null, isFemale = false, isChasid = false, isKidZone = false)
    private val miami = SubArtist("a_miami", "Miami Boys Choir", null, isFemale = false, isChasid = false, isKidZone = false)
    private val kids = SubArtist("a_kids", "KidZone Singers", null, isFemale = false, isChasid = false, isKidZone = true)

    private fun track(videoId: String, title: String, artistId: String, isVideo: Boolean = false) =
        SubTrack(videoId, title, artistId, isVideo = isVideo, explicit = false, durationSec = 100, playCount = null, uploadDate = null)

    private fun album(id: String, title: String, type: String) =
        SubAlbum(id, playlistId = null, title = title, artistId = "a_miami", type = type, year = 2020, thumbnail = null, uploadDate = null)

    private val corpus = SubsetCorpus(
        artists = listOf(fem, leiner, miami, kids),
        tracks = listOf(
            track("song_femm00", "Kol Isha", "a_fem"),                 // female primary
            track("song_feat00", "Kol Shiru (feat. Franciska)", "a_leiner"), // male primary, female credited -> involved
            track("song_male00", "Kol Nidrei", "a_miami"),
            track("song_kid000", "Kol Sasson", "a_kids"),               // KidZone
            track("video_vid00", "Kol Live", "a_miami", isVideo = true), // a VIDEO
        ),
        albums = listOf(
            album("MPREb_albm00", "Zbumba", "album"),
            album("MPREb_sngl00", "Zbumba", "single"),
        ),
        albumTracks = emptyList(),
        artistPlaylists = emptyList(),
        community = emptyList(),
        communityTracks = emptyList(),
        homeRank = emptyList(),
        zemerPlaylists = emptyList(),
        zemerItems = emptyList(),
        blocked = SubBlocked(emptySet(), emptySet()),
    )
    private val matcher = buildFemaleMatcher(corpus.artists)

    private fun songIds(allowFemale: Boolean = true, blockVideos: Boolean = false, kidZone: Boolean = false) =
        offlineSearch(corpus, matcher, "kol", 8, allowFemale, blockVideos, kidZone).categories.songs.map { it.videoId }.toSet()

    private fun videoIds(allowFemale: Boolean = true, blockVideos: Boolean = false, kidZone: Boolean = false) =
        offlineSearch(corpus, matcher, "kol", 8, allowFemale, blockVideos, kidZone).categories.videos.map { it.videoId }.toSet()

    @Test
    fun `song vs video split routes tracks by isVideo`() {
        assertTrue("audio track is a song", "song_male00" in songIds())
        assertFalse("a video is never in songs", "video_vid00" in songIds())
        assertEquals("the video is in videos", setOf("video_vid00"), videoIds())
    }

    @Test
    fun `album vs single split routes by type`() {
        val cats = offlineSearch(corpus, matcher, "zbumba", 8, allowFemale = true, blockVideos = false, kidZone = false).categories
        assertEquals(setOf("MPREb_albm00"), cats.albums.map { it.id }.toSet())
        assertEquals(setOf("MPREb_sngl00"), cats.singles.map { it.id }.toSet())
    }

    @Test
    fun `allowFemale=false drops female-primary AND female-credited tracks`() {
        val open = songIds(allowFemale = true)
        assertTrue("Kol Isha present when female allowed", "song_femm00" in open)
        assertTrue("feat. Franciska present when female allowed", "song_feat00" in open)

        val blocked = songIds(allowFemale = false)
        assertFalse("female primary dropped", "song_femm00" in blocked)
        assertFalse("female-credited (feat.) dropped", "song_feat00" in blocked)
        assertTrue("male track survives", "song_male00" in blocked)
    }

    @Test
    fun `kidZone keeps only KidZone artists across every category`() {
        assertEquals(setOf("song_kid000"), songIds(kidZone = true))
        assertTrue("non-KidZone video is dropped under kidZone", videoIds(kidZone = true).isEmpty())
    }

    @Test
    fun `blockVideos empties the videos category and leaves songs intact`() {
        assertTrue("videos empty under blockVideos", videoIds(blockVideos = true).isEmpty())
        assertTrue("songs unaffected by blockVideos", "song_male00" in songIds(blockVideos = true))
    }

    @Test
    fun `coverage gate drops a hit that matches too few query words`() {
        val hi = track("cov_high000", "Aleph Beis Gimel", "a_miami")
        val lo = track("cov_low0000", "Gimel", "a_miami") // matches only 1 of 3 words -> cov < need(2)
        val c = SubsetCorpus(
            artists = listOf(miami), tracks = listOf(hi, lo), albums = emptyList(), albumTracks = emptyList(),
            artistPlaylists = emptyList(), community = emptyList(), communityTracks = emptyList(),
            homeRank = emptyList(), zemerPlaylists = emptyList(), zemerItems = emptyList(),
            blocked = SubBlocked(emptySet(), emptySet()),
        )
        val songs = offlineSearch(c, buildFemaleMatcher(c.artists), "aleph beis gimel", 8, allowFemale = true, blockVideos = false, kidZone = false)
            .categories.songs.map { it.videoId }.toSet()
        assertTrue("full-coverage hit kept", "cov_high000" in songs)
        assertFalse("single-word hit dropped by the coverage gate", "cov_low0000" in songs)
    }
}
