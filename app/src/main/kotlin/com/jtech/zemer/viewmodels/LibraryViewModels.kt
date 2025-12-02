@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
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
import com.jtech.zemer.constants.HideExplicitKey
import com.jtech.zemer.constants.LibraryFilter
import com.jtech.zemer.constants.MyTopFilter
import com.jtech.zemer.constants.PlaylistSortDescendingKey
import com.jtech.zemer.constants.PlaylistSortType
import com.jtech.zemer.constants.PlaylistSortTypeKey
import com.jtech.zemer.constants.SongFilter
import com.jtech.zemer.constants.SongFilterKey
import com.jtech.zemer.constants.SongSortDescendingKey
import com.jtech.zemer.constants.SongSortType
import com.jtech.zemer.constants.SongSortTypeKey
import com.jtech.zemer.constants.TopSize
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.Playlist
import com.jtech.zemer.db.entities.PlaylistEntity
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.extensions.filterExplicit
import com.jtech.zemer.extensions.filterExplicitAlbums
import com.jtech.zemer.extensions.toEnum
import com.jtech.zemer.playback.DownloadUtil
import com.jtech.zemer.repositories.CachedSongsRepository
import com.jtech.zemer.utils.ContentFilterState
import com.jtech.zemer.utils.SyncUtils
import com.jtech.zemer.utils.WhitelistCache
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.reportException
import com.metrolist.innertube.YouTube
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
                Pair(
                    Triple(
                        it[SongFilterKey].toEnum(SongFilter.LIKED),
                        it[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE),
                        (it[SongSortDescendingKey] ?: true),
                    ),
                    it[HideExplicitKey] ?: false
                )
            }.distinctUntilChanged()
            .flatMapLatest { (filterSort, hideExplicit) ->
                val (filter, sortType, descending) = filterSort
                when (filter) {
                    SongFilter.LIBRARY -> database.songs(sortType, descending).map { it.filterExplicit(hideExplicit) }
                    SongFilter.LIKED -> database.likedSongs(sortType, descending).map { it.filterExplicit(hideExplicit) }
                    SongFilter.DOWNLOADED -> database.downloadedSongs(sortType, descending).map { it.filterExplicit(hideExplicit) }
                    SongFilter.UPLOADED -> database.uploadedSongs(sortType, descending).map { it.filterExplicit(hideExplicit) }
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
                Pair(
                    Triple(
                        it[AlbumFilterKey].toEnum(AlbumFilter.LIKED),
                        it[AlbumSortTypeKey].toEnum(AlbumSortType.CREATE_DATE),
                        it[AlbumSortDescendingKey] ?: true,
                    ),
                    it[HideExplicitKey] ?: false
                )
            }.distinctUntilChanged()
            .flatMapLatest { (filterSort, hideExplicit) ->
                val (filter, sortType, descending) = filterSort
                when (filter) {
                    AlbumFilter.LIBRARY -> database.albums(sortType, descending).map { it.filterExplicitAlbums(hideExplicit) }
                    AlbumFilter.LIKED -> database.albumsLiked(sortType, descending).map { it.filterExplicitAlbums(hideExplicit) }
                    AlbumFilter.UPLOADED -> database.albumsUploaded(sortType, descending).map { it.filterExplicitAlbums(hideExplicit) }
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
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncSavedPlaylists() }
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
    private val artistId = requireNotNull(savedStateHandle.get<String>("artistId")) {
        "artistId is required but was not provided in navigation arguments"
    }
    val artist =
        database
            .artist(artistId)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val songs =
        context.dataStore.data
            .map {
                Pair(
                    it[ArtistSongSortTypeKey].toEnum(ArtistSongSortType.CREATE_DATE) to (it[ArtistSongSortDescendingKey]
                        ?: true),
                    it[HideExplicitKey] ?: false
                )
            }.distinctUntilChanged()
            .flatMapLatest { (sortDesc, hideExplicit) ->
                val (sortType, descending) = sortDesc
                database.artistSongs(artistId, sortType, descending).map { it.filterExplicit(hideExplicit) }
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
    var albums = context.dataStore.data
        .map { it[HideExplicitKey] ?: false }
        .distinctUntilChanged()
        .flatMapLatest { hideExplicit ->
            database.albumsLiked(AlbumSortType.CREATE_DATE, true).map { it.filterExplicitAlbums(hideExplicit) }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
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
    private val downloadedSongs = database.downloadedSongs(SongSortType.CREATE_DATE, true)
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
class LibraryViewModel
@Inject
constructor() : ViewModel() {
    private val curScreen = mutableStateOf(LibraryFilter.LIBRARY)
    val filter: MutableState<LibraryFilter> = curScreen
}
