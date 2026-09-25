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

    // --- tracks (videoId, title, artistId, isVideo, durationSec, playCount, uploadDate) --
    private val v1 = SubTrack("v1", "Song One", "UCa", false, 200, 100, null)
    private val v2 = SubTrack("v2", "Song Two", "UCa", false, 100, 300, null)
    private val v3 = SubTrack("v3", "No Plays", "UCa", false, 150, null, null)
    private val v4 = SubTrack("v4", "A Video", "UCa", true, 50, 500, null)
    private val v5 = SubTrack("v5", "Duet (feat. Franciska)", "UCa", false, 120, 999, null) // credited female

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
        // The album's own OP playlist id must ride the header — falling back to the MPRE browseId is
        // what AlbumViewModel would persist as AlbumEntity.playlistId (dead-press radio, wrong shares).
        assertEquals("OLAK1", r.album.playlistId)
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
        // card: cover URL + count + summed runtime.
        // Absolute (not the server's relative path): the offline path bypasses the client-side
        // resolveZemerUrl pass, and Coil cannot load a schemeless URL.
        assertEquals("https://search.zemer.io/zemer-playlists/cover?id=auto-mix", r.playlist.thumbnail)
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

    // --- /artist --------------------------------------------------------------------------------------

    @Test
    fun `artist page splits songs-videos, ranks songs by play count with nulls last, albums year desc`() {
        val r = offlineArtist(corpus, female, "UCa", allowFemale = true, blockVideos = false, kidZone = false)!!
        assertEquals("Alef", r.artist.name)
        // Top songs: v5 (999) > v2 (300) > v1 (100) > v3 (null plays last); v4 is a video.
        assertEquals(listOf("v5", "v2", "v1", "v3"), r.songs.map { it.videoId })
        assertEquals(listOf("v4"), r.videos.map { it.videoId })
        // Albums newest-first (year desc); singles split off.
        assertEquals(listOf("al2", "al1"), r.albums.map { it.id })
        assertEquals(listOf("OLAK2", "OLAK1"), r.albums.map { it.playlistId })
        assertEquals(listOf("al3"), r.singles.map { it.id })
    }

    @Test
    fun `artist gate and featuring rule - female artist 404s and credited-female tracks drop when blocked`() {
        // The female artist's page is a 404 under the gate...
        assertNull(offlineArtist(corpus, female, "UCf", allowFemale = false, blockVideos = false, kidZone = false))
        // ...and a male artist's track FEATURING a female (v5) drops from his page.
        val r = offlineArtist(corpus, female, "UCa", allowFemale = false, blockVideos = false, kidZone = false)!!
        assertEquals(listOf("v2", "v1", "v3"), r.songs.map { it.videoId })
    }

    @Test
    fun `artist videos empty under blockVideos and unknown artist is a 404`() {
        val r = offlineArtist(corpus, female, "UCa", allowFemale = true, blockVideos = true, kidZone = false)!!
        assertTrue(r.videos.isEmpty())
        assertNull(offlineArtist(corpus, female, "UCnope", allowFemale = true, blockVideos = false, kidZone = false))
    }

    // --- additive shards, 2026-09-11: realVideos + radio ---------------------------------------------

    @Test
    fun `home rows flag a track real only when the realvideos shard carries its id`() {
        // v4 is the only video (SubsetReadLayerTest's shared corpus); a snapshot that ships `realvideos`
        // for it flags realVideo=true on the SAME topVideos row every other field already comes from.
        val real = corpus.copy(realVideos = setOf("v4"))
        val r = offlineHomeRows(real, female, allowFemale = true, blockVideos = false, kidZone = false)
        assertTrue(r.topVideos.first { it.videoId == "v4" }.realVideo)

        // A snapshot without the shard (or one that doesn't carry this id) is conservative: false, exactly
        // the prior behaviour — the ViewModel's empty-pool fallback still shows the whole set in the hero.
        val noShard = offlineHomeRows(corpus, female, allowFemale = true, blockVideos = false, kidZone = false)
        assertFalse(noShard.topVideos.first { it.videoId == "v4" }.realVideo)
    }

    @Test
    fun `offline radio applies the same content gate as every other offline surface`() {
        val radioCorpus = corpus.copy(
            radioRows = listOf(SubRadioRow("v1", pop = 50.0, lib = listOf("v2" to 0.9), sess = emptyList())),
            realVideos = setOf("v4"),
        )
        val open = offlineRadio(radioCorpus, female, "song", "v1", allowFemale = true, blockVideos = false)!!
        assertEquals("v1", open.tracks.first().videoId) // the tapped song plays first
        assertTrue(open.tracks.any { it.videoId == "v2" }) // its cooc neighbour follows

        // blockVideos drops v4 from every fallback tier, exactly like offlineHomeRows/offlineArtist.
        val blockedVideos = offlineRadio(radioCorpus, female, "shuffle", null, allowFemale = true, blockVideos = true)!!
        assertTrue(blockedVideos.tracks.none { it.videoId == "v4" })

        // allowFemale=false drops v5 (feat. Franciska) via the same credited-female rule as offlineArtist.
        val blockedFemale = offlineRadio(radioCorpus, female, "shuffle", null, allowFemale = false, blockVideos = false)!!
        assertTrue(blockedFemale.tracks.none { it.videoId == "v5" })

        // realVideo rides the SAME mapping as offlineHomeRows.
        assertTrue(open.tracks.none { it.videoId == "v4" } || open.tracks.first { it.videoId == "v4" }.realVideo)
    }

    @Test
    fun `offline radio kind=genre is unsupported and returns null`() {
        assertNull(offlineRadio(corpus, female, "genre", "nigunim", allowFemale = true, blockVideos = false))
    }

    @Test
    fun `offline radio continuation token round-trips through OfflineRadioToken and pages deterministically`() {
        val radioCorpus = corpus.copy(radioRows = listOf(SubRadioRow("v1", pop = 50.0, lib = emptyList(), sess = emptyList())))
        val first = offlineRadio(radioCorpus, female, "shuffle", null, allowFemale = true, blockVideos = false, limit = 2)!!
        val token = first.continuation!!
        assertTrue(token.startsWith(OfflineRadioToken.PREFIX))
        val parts = OfflineRadioToken.parse(token)!!
        assertEquals("shuffle", parts.kind); assertNull(parts.seed); assertEquals(2, parts.offset)

        val second = offlineRadio(radioCorpus, female, parts.kind, parts.seed, parts.allowFemale, parts.blockVideos, offset = parts.offset)!!
        // The continuation page never repeats a track the first page already served.
        val firstIds = first.tracks.map { it.videoId }.toSet()
        assertTrue(second.tracks.none { it.videoId in firstIds })

        // A live-shaped (non-offline) or corrupt token is never guessed at.
        assertNull(OfflineRadioToken.parse("some-opaque-live-server-token"))
        assertNull(OfflineRadioToken.parse(OfflineRadioToken.PREFIX + "{not json"))
    }

    @Test
    fun `a negative offset in an offline token is rejected, never reaches List-drop and crashes`() {
        // A tampered/corrupted token could carry offset:-1; List.drop(-1) throws, so parse() must
        // reject it here rather than let offlineRadio hand it to radio()'s paging.
        val tampered = OfflineRadioToken.PREFIX + """{"kind":"shuffle","allowFemale":true,"blockVideos":false,"offset":-1}"""
        assertNull(OfflineRadioToken.parse(tampered))

        // A legitimate zero/positive offset still parses (the fix must not reject valid tokens).
        val real = OfflineRadioToken.encode("shuffle", null, allowFemale = true, blockVideos = false, offset = 3)
        assertEquals(3, OfflineRadioToken.parse(real)!!.offset)
    }

    @Test
    fun `offline radio seeds a curated playlist from its FULL membership, not just direct track items`() {
        // "auto-mix" has a direct track (v1) AND an album expansion (al1 -> v1, v2); the radio seed
        // set must cover both, exactly like the playlist's own detail screen (zemerPlaylistTracks) —
        // a hand-filtered `kind == "track"` slice would silently drop v2, the album-only member.
        val radioCorpus = corpus.copy(
            radioRows = listOf(SubRadioRow("v2", pop = 1.0, lib = listOf("v3" to 0.9), sess = emptyList())),
        )
        val page = offlineRadio(radioCorpus, female, "playlist", "auto-mix", allowFemale = true, blockVideos = false)!!
        // v2 (album-only membership) must have contributed as a cooc seed, reaching its own neighbour v3.
        assertTrue("v2's own neighbour (v3) is reached only if v2 entered the seed set", page.tracks.any { it.videoId == "v3" })
    }
}
