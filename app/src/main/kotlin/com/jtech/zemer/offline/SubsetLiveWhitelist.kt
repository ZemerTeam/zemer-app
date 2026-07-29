package com.jtech.zemer.offline

import java.util.concurrent.TimeUnit

/**
 * The snapshot may serve for at most this long after its last successful sync. The auto-update is
 * daily, so a healthy device is always far inside the window; the cap exists for the device that
 * CAN'T sync (e.g. cellular-only with the WiFi-only default) — without it the snapshot ages
 * unboundedly and keeps serving content the whitelist has since dropped.
 */
val SUBSET_MAX_SNAPSHOT_AGE_MS: Long = TimeUnit.DAYS.toMillis(14)

/** Pure freshness rule for the offline snapshot (unit-tested); 0/absent [lastSyncedAtMs] = never synced. */
fun subsetSnapshotIsFresh(lastSyncedAtMs: Long, nowMs: Long): Boolean =
    lastSyncedAtMs > 0 && nowMs - lastSyncedAtMs <= SUBSET_MAX_SNAPSHOT_AGE_MS

/**
 * Overlays the app's live, Firestore-synced artist whitelist onto a decoded snapshot. The shard flag
 * bits are only as fresh as the last sync (up to [SUBSET_MAX_SNAPSHOT_AGE_MS] old); the live
 * whitelist ([com.jtech.zemer.utils.WhitelistCache]) is minutes-fresh — so at corpus load:
 *
 * - an artist absent from [live] (de-whitelisted since the snapshot was built) is DROPPED, along
 *   with every row that references it: its tracks, albums, album memberships, artist playlists,
 *   community members resolved to it, home-rank rows and curated-playlist items;
 * - `isFemale` is overridden by the live flag, so a since-flagged artist is hidden the moment the
 *   app's whitelist sync lands, not on the next snapshot download.
 *
 * An EMPTY [live] map means the whitelist has not synced on this device yet — the overlay is a
 * no-op (never wipe the snapshot on a fresh install where it may be the only whitelist knowledge).
 * [live] maps artist channel id → the live `isFemale` flag.
 */
fun SubsetCorpus.withLiveWhitelist(live: Map<String, Boolean>): SubsetCorpus {
    if (live.isEmpty()) return this
    var changed = false
    val overlaidArtists = ArrayList<SubArtist>(artists.size)
    for (a in artists) {
        val female = live[a.id]
        if (female == null) {
            changed = true // de-whitelisted → dropped
            continue
        }
        if (female != a.isFemale) {
            changed = true
            overlaidArtists.add(a.copy(isFemale = female))
        } else {
            overlaidArtists.add(a)
        }
    }
    if (!changed) return this

    val keptArtists = overlaidArtists.mapTo(HashSet()) { it.id }
    val keptTracks = tracks.filterTo(ArrayList()) { it.artistId in keptArtists }
    val keptTrackIds = keptTracks.mapTo(HashSet()) { it.videoId }
    val droppedTrackIds = tracks.mapTo(HashSet()) { it.videoId }.apply { removeAll(keptTrackIds) }
    val keptAlbums = albums.filterTo(ArrayList()) { it.artistId in keptArtists }
    val keptAlbumIds = keptAlbums.mapTo(HashSet()) { it.id }
    val droppedAlbumIds = albums.mapTo(HashSet()) { it.id }.apply { removeAll(keptAlbumIds) }

    return SubsetCorpus(
        artists = overlaidArtists,
        tracks = keptTracks,
        albums = keptAlbums,
        albumTracks = albumTracks.filter { it.albumId !in droppedAlbumIds && it.videoId !in droppedTrackIds },
        artistPlaylists = artistPlaylists.filter { it.artistId in keptArtists },
        community = community,
        // A member is dropped when it POSITIVELY references dropped content: a corpus track of a
        // dropped artist, or a discovery-resolved artist id the live whitelist no longer carries.
        communityTracks = communityTracks.filter { ct ->
            ct.videoId !in droppedTrackIds && (ct.artistId == null || ct.artistId in live)
        },
        homeRank = homeRank.filter { r ->
            val refKept = when (r.kind) {
                "album" -> r.refId !in droppedAlbumIds
                "video" -> r.refId !in droppedTrackIds
                "artist" -> r.refId in keptArtists
                "community" -> true
                else -> true
            }
            refKept && (r.artistId == null || r.artistId in live)
        },
        zemerPlaylists = zemerPlaylists,
        zemerItems = zemerItems.filter {
            when (it.kind) {
                "track" -> it.refId !in droppedTrackIds
                "album" -> it.refId !in droppedAlbumIds
                else -> true
            }
        },
        blocked = blocked,
    )
}

/**
 * Cheap order-independent fingerprint of the live overlay input, so [OfflineReadProvider] can keep
 * its decoded-corpus cache while detecting a whitelist sync landing mid-process (membership or a
 * female flag changing must rebuild the overlaid corpus).
 */
fun liveWhitelistFingerprint(live: Map<String, Boolean>): Long {
    var fp = live.size.toLong()
    for ((id, female) in live) fp += id.hashCode().toLong() * (if (female) 31L else 7L)
    return fp
}
