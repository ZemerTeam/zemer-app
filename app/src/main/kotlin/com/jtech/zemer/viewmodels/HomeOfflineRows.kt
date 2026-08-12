package com.jtech.zemer.viewmodels

import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.Album
import com.jtech.zemer.db.entities.Artist
import com.jtech.zemer.db.entities.LocalItem
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.models.toAlbumItem
import com.jtech.zemer.models.toArtistItem
import com.jtech.zemer.models.toSongItem
import com.jtech.zemer.utils.BlockedIdsCache
import com.jtech.zemer.utils.ContentFilterConfig
import com.jtech.zemer.utils.ContentFilterState
import com.jtech.zemer.utils.IsraeliArtistRegistry
import com.jtech.zemer.utils.WhitelistCache
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.flow.first
import kotlin.random.Random

/**
 * The offline-mode Home rows (manual offline mode, #366): the same Home surface, sourced entirely
 * from downloaded content in Room. Assembled here rather than inside [HomeViewModel] so the
 * god-file doesn't grow and the row math stays JVM-testable ([offlineQuickPicks],
 * [filterKeepListeningToDownloaded], [downloadedOnly]).
 */
data class OfflineHomeRows(
    val quickPicks: List<Song>,
    val artists: List<ArtistItem>,
    val albums: List<AlbumItem>,
    val videos: List<SongItem>,
    val downloadedSongIds: Set<String>,
    val downloadedAlbumIds: Set<String>,
    val downloadedArtistIds: Set<String>,
)

/**
 * The offline content gate: the female flag, the id-override table and the Israeli registry, the
 * same rules the online surfaces apply to LOCAL Room content ([com.jtech.zemer.viewmodels]'
 * isAllowed / dropBlocked paths). All three sources are on-device caches, so toggling offline mode
 * can never resurface content the (passcode-protected) filter hides online. Pure over its lambdas
 * for the JVM test; unknown artists fail OPEN (a plain downloaded song with no whitelist row keeps
 * playing, exactly like the Library Downloaded list).
 */
internal fun offlineContentGatePasses(
    itemId: String?,
    artistIds: List<String>,
    config: ContentFilterConfig,
    isFemaleArtist: (String) -> Boolean = { WhitelistCache.get(it)?.isFemale == true },
    isBlockedId: (String?, ContentFilterConfig) -> Boolean = BlockedIdsCache::isBlocked,
    isIsraeli: (String) -> Boolean = IsraeliArtistRegistry::isIsraeli,
): Boolean {
    if (isBlockedId(itemId, config)) return false
    if (artistIds.any(isIsraeli)) return false
    if (config.filtersEnabled && !config.allowFemaleSingers && artistIds.any(isFemaleArtist)) return false
    return true
}

/** [offlineContentGatePasses] over a Room song list (the one filter every offline song list runs). */
internal fun List<Song>.offlineContentGated(
    config: ContentFilterConfig = ContentFilterState.current,
): List<Song> = filter { s -> offlineContentGatePasses(s.id, s.artists.map { it.id }, config) }

/** Reads the downloaded catalog once and builds the offline featured rows (newest-download first). */
suspend fun buildOfflineHomeRows(database: MusicDatabase, includeVideos: Boolean): OfflineHomeRows {
    val config = ContentFilterState.current
    val downloaded = database.downloadedSongsByCreateDateAsc(includeVideos).first().asReversed()
        .offlineContentGated(config)
    val artists = database.downloadedArtists(includeVideos).first()
        .filter { offlineContentGatePasses(itemId = null, artistIds = listOf(it.id), config = config) }
    val albums = database.downloadedAlbums(includeVideos).first()
        .filter { a -> offlineContentGatePasses(a.id, a.artists.map { it.id }, config) }
    val videos = database.downloadedVideosByCreateDateAsc().first().asReversed()
        .offlineContentGated(config)
    return OfflineHomeRows(
        quickPicks = offlineQuickPicks(downloaded),
        artists = artists.map { it.toArtistItem() },
        albums = albums.map { it.toAlbumItem() },
        videos = videos.map { it.toSongItem() },
        downloadedSongIds = downloaded.mapTo(HashSet()) { it.id },
        downloadedAlbumIds = albums.mapTo(HashSet()) { it.id },
        downloadedArtistIds = artists.mapTo(HashSet()) { it.id },
    )
}

/** Offline Quick Picks: a per-load shuffle over the downloaded songs, capped like the online row. */
internal fun offlineQuickPicks(
    downloaded: List<Song>,
    max: Int = 20,
    random: Random = Random.Default,
): List<Song> = downloaded.shuffled(random).take(max)

/**
 * Scopes a Keep-Listening mix (songs + albums + artists) to downloaded content: a song survives when
 * it is itself downloaded, an album/artist when at least one of its songs is. Unknown [LocalItem]
 * kinds are dropped — offline mode must never surface something it can't play.
 */
internal fun filterKeepListeningToDownloaded(
    items: List<LocalItem>,
    downloadedSongIds: Set<String>,
    downloadedAlbumIds: Set<String>,
    downloadedArtistIds: Set<String>,
): List<LocalItem> = items.filter { item ->
    when (item) {
        is Song -> item.id in downloadedSongIds
        is Album -> item.id in downloadedAlbumIds
        is Artist -> item.id in downloadedArtistIds
        else -> false
    }
}

/** The downloaded-only song predicate shared by the offline Forgotten-Favorites filter. */
internal fun downloadedOnly(songs: List<Song>, includeVideos: Boolean): List<Song> =
    songs.filter { it.song.isDownloaded && !it.song.isEpisode && (includeVideos || !it.song.isVideo) }
