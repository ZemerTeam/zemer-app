package com.jtech.zemer.utils

import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.Album
import com.jtech.zemer.db.entities.SongAlbumMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * An album whose stored songs are ALL blocked under the current content filter is hidden from the lists
 * the app builds from its own database (Library, Library mix, the artist page, Keep listening, local
 * search hits, Android Auto). The server blocks such an album's card itself, but the content mirror's
 * id list carries only the songs, so a saved album would otherwise stay listed with nothing playable.
 *
 * Same rules as [BlockedIdsCache.isBlocked]: a `global` song hides for every filtering user, a `female`
 * song only when female singers are blocked, so the album hides as `global` when every song is global and
 * as `female` when every song is global or female. A pure filter: nothing is deleted from the device, and
 * the album reappears once a block lifts. An album with no stored songs is kept. "Stored songs" are the
 * song_album_map rows AND songs linked only through `song.albumId` (see [MusicDatabase.songAlbumMaps]).
 */
fun albumFullyBlocked(songIds: Collection<String>, isBlocked: (String) -> Boolean): Boolean =
    songIds.isNotEmpty() && songIds.all(isBlocked)

/** The albums in [links] (album-song pairs from any source, duplicates fine) whose every song is blocked. */
internal fun fullyBlockedAlbumIds(links: List<SongAlbumMap>, isBlocked: (String) -> Boolean): Set<String> =
    links.groupBy({ it.albumId }, { it.songId }).filterValues { albumFullyBlocked(it, isBlocked) }.keys

suspend fun List<Album>.withoutFullyBlocked(
    database: MusicDatabase,
    config: ContentFilterConfig = ContentFilterState.current,
): List<Album> {
    if (isEmpty() || !BlockedIdsCache.anyApplies(config)) return this
    val hidden = fullyBlockedAlbumIds(database.songAlbumMaps(map { it.id })) { BlockedIdsCache.isBlocked(it, config) }
    return filterNot { it.id in hidden }
}

/** [withoutFullyBlocked] on every emission, re-applied when the content filter or the blocked list changes. */
fun Flow<List<Album>>.hideFullyBlockedAlbums(database: MusicDatabase): Flow<List<Album>> =
    combine(this, ContentFilterState.state, BlockedIdsCache.revision) { albums, config, _ ->
        albums.withoutFullyBlocked(database, config)
    }
