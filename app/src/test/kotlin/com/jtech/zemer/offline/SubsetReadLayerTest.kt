package com.jtech.zemer.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Assembly-rule parity for the offline read layer ([SubsetReadLayer]) — the on-device port of the
 * `/album`, `/home-rows` and `/zemer-playlists` handlers. Each rule is asserted deterministically
 * over a tiny hand-built [SubsetCorpus] (no Android runtime, network or files); byte-for-byte parity vs the
 * LIVE server over the full corpus is covered separately by the end-to-end check run during the port.
 *
 * Covered: album `trackNumber = pos+1` + per-track filter + header; `home_rank` ranked order + a blockVideos/female
 * filter; curated item expansion + first-position-wins dedup + `auto-*` raw-order rank; a playlist with no
 * surviving member hidden from the list and 404 on detail.
 */
class SubsetReadLayerTest {

    // --- artists (id, name, thumbnail, isFemale, isChasid, isKidZone) -----------------------------
    private val alef = SubArtist("UCa", "Alef", "ta", isFemale = false, isChasid = false, isKidZone = false)
    private val fem = SubArtist("UCf", "Franciska", "tf", isFemale = true, isChasid = false, isKidZone = false)
    private val kid = SubArtist("UCk", "KidStar", "tk", isFemale = false, isChasid = false, isKidZone = true)

    // --- tracks (videoId, title, artistId, isVideo, explicit, durationSec, playCount, uploadDate) --
    private val v1 = SubTrack("v1", "Song One", "UCa", false, false, 200, 100, null)
    private val v2 = SubTrack("v2", "Song Two", "UCa", false, false, 100, 300, null)
    private val v3 = SubTrack("v3", "No Plays", "UCa", false, false, 150, null, null)
    private val v4 = SubTrack("v4", "A Video", "UCa", true, false, 50, 500, null)
    private val v5 = SubTrack("v5", "Duet (feat. Franciska)", "UCa", false, false, 120, 999, null) // credited female

    // --- albums (id, playlistId, title, artistId, type, year, thumbnail, uploadDate) --------------
    private val al1 = SubAlbum("al1", "OLAK1", "Album X", "UCa", "album", 2020, "art1", null)
    private val al2 = SubAlbum("al2", "OLAK2", "Album Y", "UCa", "album", 2022, "art2", null)
    private val al3 = SubAlbum("al3", "OLAK3", "Single Z", "UCa", "single", 2021, "art3", null)
    private val alf = SubAlbum("alf", null, "Fem Album", "UCf", "album", 2023, "artf", null)

    private val corpus = SubsetCorpus(
        artists = listOf(alef, fem, kid),
        tracks = listOf(v1, v2, v3, v4, v5),
        albums = listOf(al1, al2, al3, alf),
        albumTracks = listOf(
            SubAlbumTrack("al1", "v1", 0), SubAlbumTrack("al1", "v2", 1), // al1 = {v1, v2}
            SubAlbumTrack("al2", "v3", 0), SubAlbumTrack("al2", "v4", 1), // al2 = {v3, v4(video)}
        ),
        artistPlaylists = emptyList(),
        community = emptyList(),
        communityTracks = emptyList(),
        homeRank = listOf(
            SubHomeRank("top-albums", "album", "al2", "UCa", 0, null),
            SubHomeRank("top-albums", "album", "al1", "UCa", 1, null),
            SubHomeRank("top-albums", "album", "alf", "UCf", 2, null),
            SubHomeRank("top-videos", "video", "v4", "UCa", 0, null),
            SubHomeRank("top-videos", "video", "v1", "UCa", 1, null), // NOT a video → skipped
            SubHomeRank("top-artists", "artist", "UCa", null, 0, null),
            SubHomeRank("top-artists", "artist", "UCf", null, 1, null),
        ),
        zemerPlaylists = listOf(
            SubZemerPlaylist("auto-mix", "Auto Mix", 0, null),
            SubZemerPlaylist("female-only", "Female Only", 1, null),
        ),
        zemerItems = listOf(
            SubZemerItem("auto-mix", "track", "v1", 0), // direct
            SubZemerItem("auto-mix", "album", "al1", 1), // expands to v1, v2
            SubZemerItem("female-only", "track", "v5", 0),
        ),
        blocked = SubBlocked(global = emptySet(), female = emptySet()),
    )

    private val female = buildFemaleMatcher(corpus.artists)

    // --- /album ---------------------------------------------------------------------------------------

    @Test
    fun `album lists members in pos order with trackNumber pos+1 and header from the artist`() {
        val r = offlineAlbum(corpus, female, "al1", allowFemale = true, blockVideos = false, kidZone = false)!!
        assertEquals("al1", r.album.id)
        assertEquals("Alef", r.album.artist)
        assertEquals(2020, r.album.year)
        assertEquals(listOf("v1", "v2"), r.tracks.map { it.videoId })
        assertEquals(listOf(1, 2), r.tracks.map { it.trackNumber })
    }

    @Test
    fun `album per-track video filter drops video members when videos blocked`() {
        // al2 = {v3 (audio), v4 (video)}; blockVideos drops v4 only, header still resolves.
        val r = offlineAlbum(corpus, female, "al2", allowFemale = true, blockVideos = true, kidZone = false)!!
        assertEquals(listOf("v3"), r.tracks.map { it.videoId })
        assertEquals(listOf(1), r.tracks.map { it.trackNumber }) // v3 is pos 0 → trackNumber 1
    }

    // --- /home-rows -----------------------------------------------------------------------------------

    @Test
    fun `home rows follow home_rank order, skip non-videos, and honor filters`() {
        val open = offlineHomeRows(corpus, female, allowFemale = true, blockVideos = false, kidZone = false)
        assertEquals(listOf("al2", "al1", "alf"), open.topAlbums.map { it.id }) // ranked order
        assertEquals(listOf("v4"), open.topVideos.map { it.videoId }) // v1 is not a video → dropped
        assertEquals(listOf("UCa", "UCf"), open.topArtists.map { it.id })
        assertEquals("UCa", open.topVideos.first().artistId) // artistId carried for the home dedup

        val filtered = offlineHomeRows(corpus, female, allowFemale = false, blockVideos = true, kidZone = false)
        assertEquals(listOf("al2", "al1"), filtered.topAlbums.map { it.id }) // female-artist album gone
        assertTrue("blockVideos empties top-videos", filtered.topVideos.isEmpty())
        assertEquals(listOf("UCa"), filtered.topArtists.map { it.id }) // female artist gone
    }

    // --- /zemer-playlists -----------------------------------------------------------------------------

    @Test
    fun `curated detail expands albums, dedupes first-position-wins, and ranks by raw order`() {
        val r = offlineCuratedPlaylist(corpus, female, "auto-mix", allowFemale = true, blockVideos = false, kidZone = false)!!
        // v1 appears directly (pos 0) AND inside al1 — kept once, at its first (direct) position.
        assertEquals(listOf("v1", "v2"), r.tracks.map { it.videoId })
        assertFalse("v1's kept position is the direct pick → fromAlbum=false", r.tracks[0].fromAlbum)
        assertTrue("v2 only arrives via the album expansion → fromAlbum=true", r.tracks[1].fromAlbum)
        // rank = 1-based position in the RAW stored track order (only the direct 'track' item, v1).
        assertEquals(1, r.tracks[0].rank)
        assertNull("v2 is not a raw track item → no rank", r.tracks[1].rank)
        // the album item surfaces as a browsable row (its members serve here).
        assertEquals(listOf("al1"), r.albums.map { it.id })
        // card: relative cover URL + count + summed runtime.
        assertEquals("/zemer-playlists/cover?id=auto-mix", r.playlist.thumbnail)
        assertEquals(2, r.playlist.trackCount)
        assertEquals(300, r.playlist.totalDurationSec)
    }

    @Test
    fun `a playlist with no surviving member is hidden from the list and 404 on detail`() {
        val open = offlineCuratedPlaylists(corpus, female, allowFemale = true, blockVideos = false, kidZone = false)
        assertEquals(listOf("auto-mix", "female-only"), open.playlists.map { it.id }) // editorial order

        val blocked = offlineCuratedPlaylists(corpus, female, allowFemale = false, blockVideos = false, kidZone = false)
        assertEquals("female-only's sole member is credited-female → hidden", listOf("auto-mix"), blocked.playlists.map { it.id })
        assertNull("and its detail is a 404", offlineCuratedPlaylist(corpus, female, "female-only", allowFemale = false, blockVideos = false, kidZone = false))
    }
}
