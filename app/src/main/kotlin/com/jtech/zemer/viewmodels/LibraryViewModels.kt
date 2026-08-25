@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.R
import com.jtech.zemer.constants.AlbumFilter
import com.jtech.zemer.constants.AlbumFilterKey
import com.jtech.zemer.constants.AlbumSortDescendingKey
import com.jtech.zemer.constants.AlbumSortType
import com.jtech.zemer.constants.AlbumSortTypeKey
import com.jtech.zemer.constants.ArtistFilter
import com.jtech.zemer.constants.ArtistFilterKey
import com.jtech.zemer.constants.ArtistSongSortDescendingKey
import com.jtech.zemer.constants.ArtistSongSortType
import com.jtech.zemer.constants.ArtistSongSortTypeKey
import com.jtech.zemer.constants.ArtistSortDescendingKey
import com.jtech.zemer.constants.ArtistSortType
import com.jtech.zemer.constants.ArtistSortTypeKey
import com.jtech.zemer.constants.MyTopFilter
import com.jtech.zemer.constants.PlaylistSortDescendingKey
import com.jtech.zemer.constants.PlaylistSortType
import com.jtech.zemer.constants.PlaylistSortTypeKey
import com.jtech.zemer.constants.SongFilter
import com.jtech.zemer.constants.SongFilterKey
import com.jtech.zemer.constants.PodcastSortDescendingKey
import com.jtech.zemer.constants.PodcastSortTypeKey
import com.jtech.zemer.constants.SongSortDescendingKey
import com.jtech.zemer.constants.SongSortType
import com.jtech.zemer.constants.SongSortTypeKey
import com.jtech.zemer.constants.TopSize
import com.jtech.zemer.constants.VideoDownloadsInMusicKey
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.PodcastEntity
import com.jtech.zemer.db.entities.Playlist
import com.jtech.zemer.db.entities.PlaylistEntity
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.extensions.isPersonalAccountSignedIn
import com.jtech.zemer.extensions.toast
import com.jtech.zemer.extensions.toEnum
import com.jtech.zemer.playback.DownloadUtil
import com.jtech.zemer.repositories.CachedSongsRepository
import com.jtech.zemer.utils.ContentFilterState
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.utils.SyncUtils
import com.jtech.zemer.utils.NewEpisodesFeed
import com.jtech.zemer.utils.PodcastLibrarySources
import com.jtech.zemer.utils.WhitelistCache
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.reportException
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.ArtistItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class LibrarySongsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    downloadUtil: DownloadUtil,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allSongs =
        context.dataStore.data
            .map {
                Triple(
                    it[SongFilterKey].toEnum(SongFilter.LIKED),
                    it[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE),
                    (it[SongSortDescendingKey] ?: true),
                ) to (it[VideoDownloadsInMusicKey] ?: true)
            }.distinctUntilChanged()
            .flatMapLatest { (filterSort, videosInMusic) ->
                val (filter, sortType, descending) = filterSort
                when (filter) {
                    SongFilter.LIBRARY -> database.songs(sortType, descending)
                    SongFilter.LIKED -> database.likedSongs(sortType, descending)
                    SongFilter.DOWNLOADED -> database.downloadedSongs(sortType, descending, videosInMusic)
                    SongFilter.UPLOADED -> database.uploadedSongs(sortType, descending)
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun syncLikedSongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedSongs() }
    }

    fun syncLibrarySongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLibrarySongs() }
    }

    fun syncUploadedSongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncUploadedSongs() }
    }
}

@HiltViewModel
class LibraryArtistsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    private val database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allArtists =
        context.dataStore.data
            .map {
                Triple(
                    it[ArtistFilterKey].toEnum(ArtistFilter.LIKED),
                    it[ArtistSortTypeKey].toEnum(ArtistSortType.CREATE_DATE),
                    it[ArtistSortDescendingKey] ?: true,
                )
            }.distinctUntilChanged()
            .flatMapLatest { (filter, sortType, descending) ->
                when (filter) {
                    ArtistFilter.LIBRARY -> database.artists(sortType, descending)
                    ArtistFilter.LIKED -> database.artistsBookmarked(sortType, descending)
                }
            }.flatMapLatest { artists: List<com.jtech.zemer.db.entities.Artist> ->
                ContentFilterState.state.map { filters ->
                    val allowed = WhitelistCache.allowedEntries(database, filters).map { entry -> entry.artistId }.toSet()
                    if (allowed.isEmpty()) artists else artists.filter { it.id in allowed }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun sync() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncArtistsSubscriptions() }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            allArtists.collect { artists ->
                artists
                    .map { it.artist }
                    .filter {
                        it.thumbnailUrl == null || Duration.between(
                            it.lastUpdateTime,
                            LocalDateTime.now()
                        ) > Duration.ofDays(10)
                    }.forEach { artist ->
                        YouTube.artist(artist.id).onSuccess { artistPage ->
                            database.query {
                                update(artist, artistPage)
                            }
                        }
                    }
            }
        }
    }
}

@HiltViewModel
class LibraryAlbumsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allAlbums =
        context.dataStore.data
            .map {
                Triple(
                    it[AlbumFilterKey].toEnum(AlbumFilter.LIKED),
                    it[AlbumSortTypeKey].toEnum(AlbumSortType.CREATE_DATE),
                    it[AlbumSortDescendingKey] ?: true,
                )
            }.distinctUntilChanged()
            .flatMapLatest { filterSort ->
                val (filter, sortType, descending) = filterSort
                when (filter) {
                    AlbumFilter.LIBRARY -> database.albums(sortType, descending)
                    AlbumFilter.LIKED -> database.albumsLiked(sortType, descending)
                    AlbumFilter.UPLOADED -> database.albumsUploaded(sortType, descending)
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun sync() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedAlbums() }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            allAlbums.collect { albums ->
                albums
                    .filter {
                        it.album.songCount == 0
                    }.forEach { album ->
                        YouTube
                            .album(album.id)
                            .onSuccess { albumPage ->
                                database.query {
                                    update(album.album, albumPage, album.artists)
                                }
                            }.onFailure {
                                reportException(it)
                                if (it.message?.contains("NOT_FOUND") == true) {
                                    database.query {
                                        delete(album.album)
                                    }
                                }
                            }
                    }
            }
        }
    }
}

@HiltViewModel
class LibraryPlaylistsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allPlaylists =
        context.dataStore.data
            .map {
                it[PlaylistSortTypeKey].toEnum(PlaylistSortType.CREATE_DATE) to (it[PlaylistSortDescendingKey]
                    ?: true)
            }.distinctUntilChanged()
            .flatMapLatest { (sortType, descending) ->
                database.playlists(sortType, descending)
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun sync() {
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.syncArtistWhitelist()
            syncUtils.syncSavedPlaylists()
        }
    }

    val topValue =
        context.dataStore.data
            .map { it[TopSize] ?: "50" }
            .distinctUntilChanged()
}

@HiltViewModel
class ArtistSongsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val artistId = requireNotNull(savedStateHandle.get<String>("artistId")) {
        "artistId is required but was not provided in navigation arguments"
    }
    val artist =
        database
            .artist(artistId)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val songs =
        context.dataStore.data
            .map {
                it[ArtistSongSortTypeKey].toEnum(ArtistSongSortType.CREATE_DATE) to (it[ArtistSongSortDescendingKey]
                    ?: true)
            }.distinctUntilChanged()
            .flatMapLatest { (sortType, descending) ->
                database.artistSongs(artistId, sortType, descending)
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

@HiltViewModel
class LibraryMixViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val syncAllLibrary = {
         viewModelScope.launch(Dispatchers.IO) {
             syncUtils.syncArtistWhitelist()
             syncUtils.syncLikedSongs()
             syncUtils.syncLibrarySongs()
             syncUtils.syncArtistsSubscriptions()
             syncUtils.syncLikedAlbums()
             syncUtils.syncSavedPlaylists()
         }
    }
    val topValue =
        context.dataStore.data
            .map { it[TopSize] ?: "50" }
            .distinctUntilChanged()
    var artists =
        database
            .artistsBookmarked(
                ArtistSortType.CREATE_DATE,
                true,
            ).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    var albums = database.albumsLiked(AlbumSortType.CREATE_DATE, true)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    var playlists = database.playlists(PlaylistSortType.CREATE_DATE, true)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            albums.collect { albums ->
                albums
                    .filter {
                        it.album.songCount == 0
                    }.forEach { album ->
                        YouTube
                            .album(album.id)
                            .onSuccess { albumPage ->
                                database.query {
                                    update(album.album, albumPage, album.artists)
                                }
                            }.onFailure {
                                reportException(it)
                                if (it.message?.contains("NOT_FOUND") == true) {
                                    database.query {
                                        delete(album.album)
                                    }
                                }
                            }
                    }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            artists.collect { artists ->
                artists
                    .map { it.artist }
                    .filter {
                        it.thumbnailUrl == null ||
                                Duration.between(
                                    it.lastUpdateTime,
                                    LocalDateTime.now(),
                                ) > Duration.ofDays(10)
                    }.forEach { artist ->
                        YouTube.artist(artist.id).onSuccess { artistPage ->
                            database.query {
                                update(artist, artistPage)
                            }
                        }
                    }
            }
        }
    }
}

@HiltViewModel
class LibraryAutoPlaylistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val cachedSongsRepository: CachedSongsRepository,
) : ViewModel() {

    data class AutoPlaylistsState(
        val liked: Playlist? = null,
        val downloaded: Playlist? = null,
        val cached: Playlist? = null,
        val top: Playlist? = null,
    )

    private val topSize =
        context.dataStore.data
            .map { it[TopSize] ?: "50" }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Lazily, "50")

    private val likedSongs = database.likedSongs(SongSortType.CREATE_DATE, true)
    private val downloadedSongs = context.dataStore.data
        .map { it[VideoDownloadsInMusicKey] ?: true }
        .distinctUntilChanged()
        .flatMapLatest { database.downloadedSongs(SongSortType.CREATE_DATE, true, it) }
    private val topSongs =
        topSize
            .flatMapLatest { top ->
                val topCount = top.toIntOrNull() ?: 50
                database.mostPlayedSongs(MyTopFilter.ALL_TIME.toTimeMillis(), topCount).map { songs ->
                    songs to topCount
                }
            }

    private fun List<Song>.toAutoPlaylist(id: String, name: String): Playlist? {
        if (isEmpty()) return null

        val thumbnails = mapNotNull { it.song.thumbnailUrl }.distinct().take(4)

        return Playlist(
            playlist = PlaylistEntity(
                id = id,
                name = name,
                isEditable = false,
                bookmarkedAt = LocalDateTime.now(),
            ),
            songCount = size,
            songThumbnails = thumbnails,
        )
    }

    val autoPlaylists =
        combine(
            likedSongs,
            downloadedSongs,
            cachedSongsRepository.cachedSongs,
            topSongs,
        ) { liked, downloaded, cached, top ->
            val (topList, topCount) = top

            AutoPlaylistsState(
                liked =
                    liked.toAutoPlaylist(
                        PlaylistEntity.LIKED_PLAYLIST_ID,
                        context.getString(R.string.liked)
                    ),
                downloaded =
                    downloaded.toAutoPlaylist(
                        PlaylistEntity.DOWNLOADED_PLAYLIST_ID,
                        context.getString(R.string.offline)
                    ),
                cached = cached.toAutoPlaylist(CACHED_PLAYLIST_ID, context.getString(R.string.cached_playlist)),
                top =
                    topList.toAutoPlaylist(
                        TOP_PLAYLIST_ID,
                        context.getString(R.string.my_top) + " $topCount"
                    ),
            )
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AutoPlaylistsState())

    companion object {
        const val CACHED_PLAYLIST_ID = "LP_CACHED"
        const val TOP_PLAYLIST_ID = "LP_TOP"
    }
}

@HiltViewModel
class LibraryPodcastsViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val syncUtils: SyncUtils,
    private val zemerRepository: ZemerSearchRepository,
) : ViewModel() {
    // Subscribed shows (whitelist-filtered) - shared source so the filter can't drift between VMs.
    val subscribedPodcasts = PodcastLibrarySources.whitelistedSubscribedPodcasts(database)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // "Episodes for Later" (local songs flagged isEpisode + inLibrary). LOCAL -> works for anon.
    val savedEpisodes = database.savedEpisodes()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Downloaded episodes, reacting to the podcast-only sort prefs (NOT the main Songs library keys).
    val downloadedEpisodes =
        context.dataStore.data
            .map {
                it[PodcastSortTypeKey].toEnum(SongSortType.CREATE_DATE) to (it[PodcastSortDescendingKey] ?: true)
            }.distinctUntilChanged()
            .flatMapLatest { (sortType, descending) -> database.downloadedEpisodes(sortType, descending) }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // New Episodes feed (shared holder so this VM and WhitelistedPodcastsViewModel can't drift): the
    // whitelist-pure server /podcasts/new-episodes feed, filtered to subscribed shows. Anon-capable (it
    // is NOT the personal-account RDPN InnerTube feed), so the Library card shows for every session.
    private val newEpisodesFeed = NewEpisodesFeed(zemerRepository, context, database)
    val newEpisodes = newEpisodesFeed.episodes
    val isLoadingNewEpisodes = newEpisodesFeed.isLoading

    // Podcast host channels. Personal accounts get them from the account library; every session also
    // gets the channels derivable from locally-subscribed shows (channelId + author). The API read is
    // personal-only (no pooled-account read for anon); the local derivation is always safe.
    private val _apiPodcastChannels = MutableStateFlow<List<PodcastChannel>>(emptyList())
    val podcastChannels: StateFlow<List<PodcastChannel>> =
        combine(
            _apiPodcastChannels,
            subscribedPodcasts,
            database.bookmarkedPodcastChannels(),
            ContentFilterState.state,
        ) { api, local, bookmarked, filters ->
            // Channels the user subscribed to from a channel page (bookmarked ArtistEntity flagged
            // isPodcastChannel) - the primary source, matching Metrolist's bookmarkedPodcastChannels.
            val bookmarkedChannels = bookmarked.map {
                PodcastChannel(id = it.id, name = it.name, thumbnailUrl = it.thumbnailUrl)
            }
            // Plus channels derivable from locally-subscribed shows (channelId + author).
            val localChannels = local.mapNotNull { p ->
                p.channelId?.takeIf { it.isNotBlank() }?.let {
                    PodcastChannel(id = it, name = p.author ?: p.title, thumbnailUrl = p.thumbnailUrl)
                }
            }
            // Channel-whitelist gate (single chokepoint): the API source is the user's RAW YouTube-Music
            // podcast follows, which include non-whitelisted (non-kosher) host channels. Gate the whole
            // merged list (membership + female) so a non-approved channel's identity can never leak into
            // the Channels tab.
            (api + bookmarkedChannels + localChannels)
                .filter { PodcastLibrarySources.podcastChannelAllowed(it.id, filters) }
                .distinctBy { it.id }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        fetchNewEpisodes()
        refreshChannels()
        syncEpisodesForLater()
        syncPodcastSubscriptions()
    }

    fun fetchNewEpisodes() = newEpisodesFeed.fetch(viewModelScope)

    fun refreshChannels() {
        if (!isPersonalAccountSignedIn) return
        viewModelScope.launch(Dispatchers.IO) {
            YouTube.libraryPodcastChannels().onSuccess { page ->
                _apiPodcastChannels.value = page.items
                    .filterIsInstance<ArtistItem>()
                    .map { PodcastChannel(id = it.id, name = it.title, thumbnailUrl = it.thumbnail) }
            }
        }
    }

    fun syncPodcastSubscriptions() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncPodcastSubscriptions() }
    }

    fun syncEpisodesForLater() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncEpisodesForLater() }
    }

    /**
     * Toggle an episode's "saved for later" state. OPTIMISTIC: the local `inLibrary` flips at once
     * (drives the bookmark icon), then the server SE-playlist write runs and is reverted (with a
     * toast) on failure. Un-save always clears local even when the setVideoId needed for the server
     * removal is unknown, so an episode can never get stuck saved. Anonymous sessions are local-only.
     */
    // Save/unsave an episode - delegates to the one shared implementation (the episode analogue of
    // likeSong) so the optimistic-flip + account-sync + revert logic lives in exactly one place.
    fun toggleEpisodeSaved(song: Song) {
        syncUtils.toggleSaveEpisode(song.song)
    }

    // Remove a subscribed show from the library: clear the local bookmark, and (personal login only)
    // unsave on the account. Anonymous sessions stay local-only.
    fun unsubscribeShow(podcast: PodcastEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            database.query { updatePodcast(podcast.copy(bookmarkedAt = null)) }
            if (isPersonalAccountSignedIn) YouTube.savePodcast(podcast.id, false)
        }
    }
}

/** A podcast host channel row in the Library -> Podcasts CHANNELS tab. */
data class PodcastChannel(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
)
