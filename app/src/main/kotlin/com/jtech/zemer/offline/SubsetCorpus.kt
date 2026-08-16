package com.jtech.zemer.offline

/**
 * The decoded on-device corpus snapshot — the Kotlin mirror of the zemer-search SQLite tables the
 * shards carry (see [SubsetDecoder] for the wire layout). Read endpoints ([OfflineReadLayer]) run over
 * this in memory, exactly as the server runs them over SQLite, so an offline response matches the
 * live one field-for-field for the reproducible endpoints.
 *
 * Raw tables are plain lists; the maps/groupings the reads need are built lazily on first use so a
 * corpus that is loaded but never queried (e.g. app start with offline search off) costs only the parse.
 */
data class SubsetCorpus(
    val artists: List<SubArtist>,
    val tracks: List<SubTrack>,
    val albums: List<SubAlbum>,
    val albumTracks: List<SubAlbumTrack>,
    val artistPlaylists: List<SubArtistPlaylist>,
    val community: List<SubCommunity>,
    val communityTracks: List<SubCommunityTrack>,
    val homeRank: List<SubHomeRank>,
    val zemerPlaylists: List<SubZemerPlaylist>,
    val zemerItems: List<SubZemerItem>,
    val blocked: SubBlocked,
    // Podcasts (server reply 4 — the on-device subset). Defaulted empty so a pre-podcast snapshot (or a
    // test corpus) needs no podcast shards; the reads below just return nothing. Pre-gated to approved
    // channels server-side (a show is present iff its host UC is approved, or it is grandfathered
    // channel-less); female/KidZone stay per item (channel flag + the `blocked` shard exceptions).
    val podcastChannels: List<SubPodcastChannel> = emptyList(),
    val podcasts: List<SubPodcastShow> = emptyList(),
    val podcastEpisodes: List<SubPodcastEpisode> = emptyList(),
) {
    val artistsById: Map<String, SubArtist> by lazy { artists.associateBy { it.id } }
    val tracksById: Map<String, SubTrack> by lazy { tracks.associateBy { it.videoId } }
    val albumsById: Map<String, SubAlbum> by lazy { albums.associateBy { it.id } }
    /** Album members in stored order, grouped by album id. */
    val albumTracksByAlbum: Map<String, List<SubAlbumTrack>> by lazy {
        albumTracks.groupBy { it.albumId }.mapValues { (_, v) -> v.sortedBy { it.pos } }
    }

    /** Community members in stored order, grouped by community playlist id. */
    val communityTracksByPlaylist: Map<String, List<SubCommunityTrack>> by lazy {
        communityTracks.groupBy { it.playlistId }.mapValues { (_, v) -> v.sortedBy { it.pos } }
    }

    val zemerItemsByPlaylist: Map<String, List<SubZemerItem>> by lazy {
        zemerItems.groupBy { it.playlistId }.mapValues { (_, v) -> v.sortedBy { it.pos } }
    }

    val homeRankByRow: Map<String, List<SubHomeRank>> by lazy {
        homeRank.groupBy { it.row }.mapValues { (_, v) -> v.sortedBy { it.pos } }
    }

    // --- podcasts ---
    val podcastChannelsById: Map<String, SubPodcastChannel> by lazy { podcastChannels.associateBy { it.id } }
    val podcastsById: Map<String, SubPodcastShow> by lazy { podcasts.associateBy { it.id } }

    /** Shows grouped by host channel (UC…), stored shard order preserved. */
    val podcastsByChannel: Map<String, List<SubPodcastShow>> by lazy {
        podcasts.filter { it.channelId != null }.groupBy { it.channelId!! }
    }

    /** Episodes grouped by owning show (MPSP…), stored shard order preserved. */
    val podcastEpisodesByShow: Map<String, List<SubPodcastEpisode>> by lazy {
        podcastEpisodes.groupBy { it.showId }
    }
}

data class SubArtist(
    val id: String,
    val name: String,
    val thumbnail: String?,
    val isFemale: Boolean,
    val isChasid: Boolean,
    val isKidZone: Boolean,
)

data class SubTrack(
    val videoId: String,
    val title: String,
    val artistId: String,
    val isVideo: Boolean,
    val explicit: Boolean,
    val durationSec: Int?,
    val playCount: Long?,
    val uploadDate: String?,
)

data class SubAlbum(
    val id: String,
    val playlistId: String?,
    val title: String,
    val artistId: String,
    /** "album" | "single" | "ep". */
    val type: String,
    val year: Int?,
    val thumbnail: String?,
    val uploadDate: String?,
)

data class SubAlbumTrack(val albumId: String, val videoId: String, val pos: Int)

data class SubArtistPlaylist(val id: String, val title: String, val artistId: String, val thumbnail: String?)

data class SubCommunity(
    val id: String,
    val title: String,
    val author: String?,
    /** The raw curator image; read endpoints override it with a whitelist-derived member cover. */
    val thumbnail: String?,
    val total: Int,
    val whitelisted: Int,
    val viewCount: Long?,
)

data class SubCommunityTrack(
    val playlistId: String,
    val videoId: String,
    val pos: Int,
    /** Discovery-resolved artist channel id for a member with no corpus track; may be null. */
    val artistId: String?,
)

data class SubHomeRank(
    /** "top-albums" | "top-videos" | "top-artists" | "top-community". */
    val row: String,
    /** "album" | "video" | "artist" | "community". */
    val kind: String,
    val refId: String,
    val artistId: String?,
    val pos: Int,
    val score: Double?,
)

data class SubZemerPlaylist(val id: String, val title: String, val pos: Int, val year: Int?)

/** kind = "track" | "album"; refId = videoId or album browseId. */
data class SubZemerItem(val playlistId: String, val kind: String, val refId: String, val pos: Int)

data class SubBlocked(val global: Set<String>, val female: Set<String>)

// --- podcasts (shard rows pinned to build-subset.mjs; see [SubsetDecoder]) ---

/** A host podcast channel (UC…) — the browse-grid row. `flags` bit0=female, bit1=kidZone, bit2=verified. */
data class SubPodcastChannel(
    val id: String,
    val name: String,
    val thumbnail: String?,
    val isFemale: Boolean,
    val isKidZone: Boolean,
    val isVerified: Boolean,
    val showCount: Int,
    val episodeCount: Int,
)

/** A podcast SHOW (MPSP…) on an approved channel. `channelId` null for a grandfathered channel-less show. */
data class SubPodcastShow(
    val id: String,
    val name: String,
    val author: String?,
    val channelId: String?,
    val thumbnail: String?,
    val episodeCountText: String?,
    /** The show's genre slugs (appended shard column; empty on a pre-genres snapshot). */
    val genres: List<String> = emptyList(),
)

/** A podcast EPISODE (played by `videoId` via InnerTube). `durationSec`/`publishedAt` may be absent. */
data class SubPodcastEpisode(
    val videoId: String,
    val showId: String,
    val title: String,
    val thumbnail: String?,
    val durationSec: Int?,
    val publishedAt: String?,
)
