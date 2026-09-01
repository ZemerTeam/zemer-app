package com.jtech.zemer.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The offline snapshot's kosher defenses: the freshness gate (an unsyncable device must not serve an
 * ever-aging copy) and the live-whitelist overlay (a de-whitelisted or since-female-flagged artist is
 * hidden the moment the app's whitelist sync lands, not on the next snapshot download).
 */
class SubsetLiveWhitelistTest {

    private fun corpus(
        artists: List<SubArtist> = listOf(
            SubArtist("UC1", "Kept", null, isFemale = false, isChasid = false, isKidZone = false),
            SubArtist("UC2", "Dropped", null, isFemale = false, isChasid = false, isKidZone = false),
        ),
    ) = SubsetCorpus(
        artists = artists,
        tracks = listOf(
            SubTrack("v1", "T1", "UC1", isVideo = false, durationSec = null, playCount = null, uploadDate = null),
            SubTrack("v2", "T2", "UC2", isVideo = false, durationSec = null, playCount = null, uploadDate = null),
        ),
        albums = listOf(
            SubAlbum("AL1", null, "A1", "UC1", "album", null, null, null),
            SubAlbum("AL2", null, "A2", "UC2", "album", null, null, null),
        ),
        albumTracks = listOf(
            SubAlbumTrack("AL1", "v1", 0),
            SubAlbumTrack("AL2", "v2", 0),
        ),
        artistPlaylists = listOf(
            SubArtistPlaylist("PL1", "P1", "UC1", null),
            SubArtistPlaylist("PL2", "P2", "UC2", null),
        ),
        community = listOf(SubCommunity("C1", "Comm", null, null, total = 2, whitelisted = 2, viewCount = null)),
        communityTracks = listOf(
            SubCommunityTrack("C1", "v1", 0, null),
            SubCommunityTrack("C1", "v2", 1, null),
            SubCommunityTrack("C1", "vx", 2, "UC2"), // discovery member resolved to the dropped artist
            SubCommunityTrack("C1", "vy", 3, null), // unknown member — untouched by the overlay
        ),
        homeRank = listOf(
            SubHomeRank("top-albums", "album", "AL1", "UC1", 0, null),
            SubHomeRank("top-albums", "album", "AL2", "UC2", 1, null),
            SubHomeRank("top-videos", "video", "v2", null, 0, null),
            SubHomeRank("top-artists", "artist", "UC2", null, 0, null),
        ),
        zemerPlaylists = listOf(SubZemerPlaylist("z1", "Z", 0, null)),
        zemerItems = listOf(
            SubZemerItem("z1", "track", "v1", 0),
            SubZemerItem("z1", "track", "v2", 1),
            SubZemerItem("z1", "album", "AL2", 2),
        ),
        blocked = SubBlocked(emptySet(), emptySet()),
    )

    // --- freshness gate ---

    @Test
    fun `snapshot is fresh only within the max age and only when ever synced`() {
        val now = 1_000_000_000_000L
        assertTrue(subsetSnapshotIsFresh(now - 1, now))
        assertTrue(subsetSnapshotIsFresh(now - SUBSET_MAX_SNAPSHOT_AGE_MS, now))
        assertFalse("past the cap", subsetSnapshotIsFresh(now - SUBSET_MAX_SNAPSHOT_AGE_MS - 1, now))
        assertFalse("never synced", subsetSnapshotIsFresh(0L, now))
    }

    // --- live overlay ---

    @Test
    fun `empty live whitelist is a no-op - never wipe the snapshot before the first sync`() {
        val c = corpus()
        assertSame(c, c.withLiveWhitelist(emptyMap()))
    }

    @Test
    fun `unchanged live whitelist returns the same corpus`() {
        val c = corpus()
        assertSame(c, c.withLiveWhitelist(mapOf("UC1" to false, "UC2" to false)))
    }

    @Test
    fun `a de-whitelisted artist is dropped with every row referencing it`() {
        val c = corpus().withLiveWhitelist(mapOf("UC1" to false))

        assertEquals(listOf("UC1"), c.artists.map { it.id })
        assertEquals(listOf("v1"), c.tracks.map { it.videoId })
        assertEquals(listOf("AL1"), c.albums.map { it.id })
        assertEquals(listOf("AL1"), c.albumTracks.map { it.albumId })
        assertEquals(listOf("PL1"), c.artistPlaylists.map { it.id })
        // Community: the dropped artist's corpus track and its discovery member go; the unknown stays.
        assertEquals(listOf("v1", "vy"), c.communityTracks.map { it.videoId })
        // Home rank: rows referencing the dropped album/video/artist go.
        assertEquals(listOf("AL1"), c.homeRank.map { it.refId })
        // Curated items: the dropped track and album go.
        assertEquals(listOf("v1"), c.zemerItems.map { it.refId })
    }

    @Test
    fun `live isFemale overrides the shard flag`() {
        val c = corpus().withLiveWhitelist(mapOf("UC1" to true, "UC2" to false))
        assertTrue(c.artistsById.getValue("UC1").isFemale)
        assertFalse(c.artistsById.getValue("UC2").isFemale)
    }

    // --- fingerprint (cache key for the overlaid corpus) ---

    @Test
    fun `fingerprint changes on membership and on a female flag flip`() {
        val base = liveWhitelistFingerprint(mapOf("UC1" to false, "UC2" to false))
        assertNotEquals(base, liveWhitelistFingerprint(mapOf("UC1" to false)))
        assertNotEquals(base, liveWhitelistFingerprint(mapOf("UC1" to true, "UC2" to false)))
        // Order-independent: same content → same fingerprint.
        assertEquals(base, liveWhitelistFingerprint(linkedMapOf("UC2" to false, "UC1" to false)))
    }
}
