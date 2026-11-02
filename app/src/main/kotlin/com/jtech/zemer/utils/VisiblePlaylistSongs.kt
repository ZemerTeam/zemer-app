package com.jtech.zemer.utils

import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.PlaylistSong
import com.jtech.zemer.db.entities.Song
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * A local playlist shows, plays and downloads only the songs that pass the content filter, like Liked songs.
 * Its stored rows can hold anything (an M3U / CSV import before it was filtered, an artist later removed from
 * the whitelist), so every reader of a local playlist's songs goes through here. A pure filter: nothing is
 * deleted, and a song reappears when its artist is whitelisted again. Sync reads the stored rows unfiltered.
 */

/** A stored song as [filterWhitelisted] sees it: its local artists, and the episode flag for the podcast gate. */
internal fun Song.asFilterItem(): SongItem = SongItem(
    id = id,
    title = title,
    artists = artists.map { Artist(name = it.name, id = it.id) },
    thumbnail = thumbnailUrl.orEmpty(),
    isEpisode = song.isEpisode,
)

/** The items of this list whose song passes the content filter under [config]; filters off keeps everything. */
suspend fun <T> List<T>.withoutFilteredSongs(
    database: MusicDatabase,
    config: ContentFilterConfig = ContentFilterState.current,
    songOf: (T) -> Song,
): List<T> {
    if (isEmpty() || !config.filtersEnabled) return this
    val visible = map { songOf(it).asFilterItem() }.filterWhitelisted(database, config).mapTo(HashSet()) { it.id }
    return filter { songOf(it).id in visible }
}

/** [withoutFilteredSongs] on every emission, re-applied when the filter, the blocked list or the whitelist changes. */
fun Flow<List<PlaylistSong>>.hideFilteredPlaylistSongs(database: MusicDatabase): Flow<List<PlaylistSong>> =
    combine(this, ContentFilterState.state, BlockedIdsCache.revision, WhitelistCache.entries) { songs, config, _, _ ->
        songs.withoutFilteredSongs(database, config) { it.song }
    }

/**
 * The stored positions for a drag between visible rows [from] and [to] of a playlist shown in its stored order.
 * Hidden songs keep their positions, so a visible row index is not a position: the dragged song takes the
 * stored position of the row it was dropped on.
 */
fun storedMovePositions(visiblePositions: List<Int>, from: Int, to: Int): Pair<Int, Int> =
    visiblePositions[from] to visiblePositions[to]
