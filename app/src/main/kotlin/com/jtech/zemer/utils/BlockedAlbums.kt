package com.jtech.zemer.utils

import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.Album
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
 * the album reappears on the next emission once a block lifts. An album with no stored songs is kept.
 */
fun albumFullyBlocked(songIds: Collection<String>, isBlocked: (String) -> Boolean): Boolean =
    songIds.isNotEmpty() && songIds.all(isBlocked)

suspend fun List<Album>.withoutFullyBlocked(
    database: MusicDatabase,
    config: ContentFilterConfig = ContentFilterState.current,
): List<Album> {
    if (isEmpty() || !BlockedIdsCache.anyApplies(config)) return this
    val songsByAlbum = database.songAlbumMaps(map { it.id }).groupBy({ it.albumId }, { it.songId })
    return filterNot { album ->
        albumFullyBlocked(songsByAlbum[album.id].orEmpty()) { BlockedIdsCache.isBlocked(it, config) }
    }
}

/** [withoutFullyBlocked] on every emission, re-applied when the content filter changes. */
fun Flow<List<Album>>.hideFullyBlockedAlbums(database: MusicDatabase): Flow<List<Album>> =
    combine(this, ContentFilterState.state) { albums, config -> albums.withoutFullyBlocked(database, config) }
