package com.jtech.zemer.utils

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import com.jtech.zemer.constants.LastWhitelistSyncTimeKey
import com.jtech.zemer.constants.LastWhitelistVersionKey
import com.jtech.zemer.constants.LastPodcastWhitelistSyncTimeKey
import com.jtech.zemer.constants.LastPodcastWhitelistVersionKey
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.ArtistEntity
import com.jtech.zemer.db.entities.PlaylistEntity
import com.jtech.zemer.db.entities.PlaylistSongMap
import com.jtech.zemer.db.entities.SongEntity
import com.jtech.zemer.extensions.toSQLiteQuery
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.utils.filterWhitelisted
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.utils.completed
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

data class WhitelistSyncProgress(
    val current: Int = 0,
    val total: Int = 0,
    val currentArtistName: String = "",
    val isComplete: Boolean = false
)

@Singleton
class SyncUtils @Inject constructor(
    private val databaseLazy: dagger.Lazy<MusicDatabase>,
    @ApplicationContext private val context: Context,
) {
    private val database: MusicDatabase
        get() = databaseLazy.get()

    private val syncScope = CoroutineScope(Dispatchers.IO)

    private val isSyncingLikedSongs = MutableStateFlow(false)
    private val isSyncingLibrarySongs = MutableStateFlow(false)
    private val isSyncingUploadedSongs = MutableStateFlow(false)
    private val isSyncingLikedAlbums = MutableStateFlow(false)
    private val isSyncingUploadedAlbums = MutableStateFlow(false)
    private val isSyncingArtists = MutableStateFlow(false)
    private val isSyncingPlaylists = MutableStateFlow(false)
    private val isSyncingWhitelist = MutableStateFlow(false)
    private val isSyncingPodcastWhitelist = MutableStateFlow(false)
    private val isBackfillingThumbs = MutableStateFlow(false)

    val isWhitelistSyncing: StateFlow<Boolean> = isSyncingWhitelist.asStateFlow()
    val isPodcastWhitelistSyncing: StateFlow<Boolean> = isSyncingPodcastWhitelist.asStateFlow()

    private val _whitelistSyncProgress = MutableStateFlow(WhitelistSyncProgress())
    val whitelistSyncProgress: StateFlow<WhitelistSyncProgress> = _whitelistSyncProgress.asStateFlow()

    private val _podcastWhitelistSyncProgress = MutableStateFlow(WhitelistSyncProgress())
    val podcastWhitelistSyncProgress: StateFlow<WhitelistSyncProgress> = _podcastWhitelistSyncProgress.asStateFlow()

    fun runAllSyncs() {
        syncScope.launch {
            syncArtistWhitelist()
            syncPodcastWhitelist()
            syncLikedSongs()
            syncLibrarySongs()
            syncUploadedSongs()
            syncLikedAlbums()
            syncUploadedAlbums()
            syncArtistsSubscriptions()
            syncSavedPlaylists()
            syncPodcastSubscriptions()
            syncEpisodesForLater()
        }
    }

    fun likeSong(s: SongEntity) {
        syncScope.launch {
            YouTube.likeVideo(s.id, s.liked)
        }
    }

    private val _isPushingToRemote = MutableStateFlow(false)
    val isPushingToRemote: StateFlow<Boolean> = _isPushingToRemote.asStateFlow()

    /**
     * Push local data to YouTube account.
     * For users who were previously logged in anonymously.
     */
    suspend fun pushLocalToYouTube(): Result<Int> = withContext(Dispatchers.IO) {
        if (_isPushingToRemote.value) return@withContext Result.failure(Exception("Sync in progress"))
        if (YouTube.isAnonLogin) return@withContext Result.failure(Exception("Login required"))

        _isPushingToRemote.value = true
        var count = 0

        try {
            // Push liked songs
            database.likedSongsByNameAsc().first().forEach { song ->
                YouTube.likeVideo(song.id, true).onSuccess { count++ }
            }

            // Push subscribed artists
            database.artistsBookmarkedByNameAsc().first().forEach { artist ->
                val channelId = artist.artist.channelId ?: YouTube.getChannelId(artist.id).takeIf { it.isNotEmpty() }
                channelId?.let { YouTube.subscribeChannel(it, true).onSuccess { count++ } }
            }

            // Push liked albums
            database.albumsLikedByNameAsc().first().forEach { album ->
                album.album.playlistId?.let { YouTube.likePlaylist(it, true).onSuccess { count++ } }
            }

            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _isPushingToRemote.value = false
        }
    }

    suspend fun syncLikedSongs() {
        if (isSyncingLikedSongs.value) return
        isSyncingLikedSongs.value = true
        try {
            YouTube.playlist("LM").completed().onSuccess { page ->
                val remoteSongs = page.songs
                    .filterWhitelisted(database)
                    .filterIsInstance<SongItem>()
                val remoteIds = remoteSongs.map { it.id }
                val localSongs = database.likedSongsByNameAsc().first()

                localSongs.filterNot { it.id in remoteIds }.forEach {
                    try {
                        database.transaction { update(it.song.localToggleLike()) }
                    } catch (e: Exception) { }
                }

                remoteSongs.forEachIndexed { index, song ->
                    try {
                        val dbSong = database.song(song.id).firstOrNull()
                        val timestamp = LocalDateTime.now().minusSeconds(index.toLong())
                        database.transaction {
                            if (dbSong == null) {
                                insert(song.toMediaMetadata()) { it.copy(liked = true, likedDate = timestamp) }
                            } else if (!dbSong.song.liked || dbSong.song.likedDate != timestamp) {
                                update(dbSong.song.copy(liked = true, likedDate = timestamp))
                            }
                        }
                    } catch (e: Exception) { }
                }
            }
        } catch (e: Exception) {
        } finally {
            isSyncingLikedSongs.value = false
        }
    }

    suspend fun syncLibrarySongs() {
        if (isSyncingLibrarySongs.value) return
        isSyncingLibrarySongs.value = true
        try {
            YouTube.library("FEmusic_liked_videos").completed().onSuccess { page ->
                val remoteSongs = page.items
                    .filterIsInstance<SongItem>()
                    .filterWhitelisted(database)
                    .filterIsInstance<SongItem>()
                    .reversed()
                val remoteIds = remoteSongs.map { it.id }.toSet()
                val localSongs = database.songsByNameAsc().first()
                val feedbackTokens = mutableListOf<String>()

                localSongs.filterNot { it.id in remoteIds }.forEach {
                    if (it.song.libraryAddToken != null && it.song.libraryRemoveToken != null) {
                        feedbackTokens.add(it.song.libraryAddToken)
                    } else {
                        try {
                            database.transaction { update(it.song.toggleLibrary()) }
                        } catch (e: Exception) { }
                    }
                }
                feedbackTokens.chunked(20).forEach { YouTube.feedback(it) }

                remoteSongs.forEach { song ->
                    try {
                        val dbSong = database.song(song.id).firstOrNull()
                        database.transaction {
                            if (dbSong == null) {
                                insert(song.toMediaMetadata()) { it.toggleLibrary() }
                            } else {
                                if (dbSong.song.inLibrary == null) {
                                    update(dbSong.song.toggleLibrary())
                                }
                                addLibraryTokens(song.id, song.libraryAddToken, song.libraryRemoveToken)
                            }
                        }
                    } catch (e: Exception) { }
                }
            }
        } catch (e: Exception) {
        } finally {
            isSyncingLibrarySongs.value = false
        }
    }

    suspend fun syncUploadedSongs() {
        if (isSyncingUploadedSongs.value) return
        isSyncingUploadedSongs.value = true
        try {
            YouTube.library("FEmusic_library_privately_owned_tracks", tabIndex = 1).completed().onSuccess { page ->
                val remoteSongs = page.items
                    .filterIsInstance<SongItem>()
                    .filterWhitelisted(database)
                    .filterIsInstance<SongItem>()
                    .reversed()
                val remoteIds = remoteSongs.map { it.id }.toSet()
                val localSongs = database.uploadedSongsByNameAsc().first()

                localSongs.filterNot { it.id in remoteIds }.forEach { database.update(it.song.toggleUploaded()) }

                remoteSongs.forEach { song ->
                    val dbSong = database.song(song.id).firstOrNull()
                    database.transaction {
                        if (dbSong == null) {
                            insert(song.toMediaMetadata()) { it.toggleUploaded() }
                        } else if (!dbSong.song.isUploaded) {
                            update(dbSong.song.toggleUploaded())
                        }
                    }
                }
            }
        } catch (e: Exception) {
        } finally {
            isSyncingUploadedSongs.value = false
        }
    }

    suspend fun syncLikedAlbums() {
        if (isSyncingLikedAlbums.value) return
        isSyncingLikedAlbums.value = true
        try {
            YouTube.library("FEmusic_liked_albums").completed().onSuccess { page ->
                val remoteAlbums = page.items
                    .filterIsInstance<AlbumItem>()
                    .filterWhitelisted(database)
                    .filterIsInstance<AlbumItem>()
                    .reversed()
                val remoteIds = remoteAlbums.map { it.id }.toSet()
                val localAlbums = database.albumsLikedByNameAsc().first()

                localAlbums.filterNot { it.id in remoteIds }.forEach { database.update(it.album.localToggleLike()) }

                remoteAlbums.forEach { album ->
                    val dbAlbum = database.album(album.id).firstOrNull()
                    YouTube.album(album.browseId).onSuccess { albumPage ->
                        if (dbAlbum == null) {
                            database.insert(albumPage)
                            database.album(album.id).firstOrNull()?.let { newDbAlbum ->
                                database.update(newDbAlbum.album.localToggleLike())
                            }
                        } else if (dbAlbum.album.bookmarkedAt == null) {
                            database.update(dbAlbum.album.localToggleLike())
                        }
                    }
                }
            }
        } catch (e: Exception) {
        } finally {
            isSyncingLikedAlbums.value = false
        }
    }

    suspend fun syncUploadedAlbums() {
        if (isSyncingUploadedAlbums.value) return
        isSyncingUploadedAlbums.value = true
        try {
            YouTube.library("FEmusic_library_privately_owned_releases", tabIndex = 1).completed().onSuccess { page ->
                val remoteAlbums = page.items
                    .filterIsInstance<AlbumItem>()
                    .filterWhitelisted(database)
                    .filterIsInstance<AlbumItem>()
                    .reversed()
                val remoteIds = remoteAlbums.map { it.id }.toSet()
                val localAlbums = database.albumsUploadedByNameAsc().first()

                localAlbums.filterNot { it.id in remoteIds }.forEach { database.update(it.album.toggleUploaded()) }

                remoteAlbums.forEach { album ->
                    val dbAlbum = database.album(album.id).firstOrNull()
                    YouTube.album(album.browseId).onSuccess { albumPage ->
                        if (dbAlbum == null) {
                            database.insert(albumPage)
                            database.album(album.id).firstOrNull()?.let { newDbAlbum ->
                                database.update(newDbAlbum.album.toggleUploaded())
                            }
                        } else if (!dbAlbum.album.isUploaded) {
                            database.update(dbAlbum.album.toggleUploaded())
                        }
                    }.onFailure { reportException(it) }
                }
            }
        } catch (e: Exception) {
        } finally {
            isSyncingUploadedAlbums.value = false
        }
    }

    suspend fun syncArtistsSubscriptions() {
        if (isSyncingArtists.value) return
        isSyncingArtists.value = true
        try {
            YouTube.library("FEmusic_library_corpus_artists").completed().onSuccess { page ->
                val remoteArtists = page.items
                    .filterIsInstance<ArtistItem>()
                    .filterWhitelisted(database)
                    .filterIsInstance<ArtistItem>()
                val remoteIds = remoteArtists.map { it.id }.toSet()
                val localArtists = database.artistsBookmarkedByNameAsc().first()

                localArtists.filterNot { it.id in remoteIds }.forEach { database.update(it.artist.localToggleLike()) }

                remoteArtists.forEach { artist ->
                    val dbArtist = database.artist(artist.id).firstOrNull()
                    database.transaction {
                        if (dbArtist == null) {
                            insert(
                                ArtistEntity(
                                    id = artist.id,
                                    name = artist.title,
                                    thumbnailUrl = artist.thumbnail,
                                    channelId = artist.channelId,
                                    bookmarkedAt = LocalDateTime.now()
                                )
                            )
                        } else if (dbArtist.artist.bookmarkedAt == null) {
                            update(dbArtist.artist.localToggleLike())
                        }
                    }
                }
            }
        } catch (e: Exception) {
        } finally {
            isSyncingArtists.value = false
        }
    }

    suspend fun syncSavedPlaylists() {
        if (isSyncingPlaylists.value) return
        isSyncingPlaylists.value = true
        try {
            YouTube.library("FEmusic_liked_playlists").completed().onSuccess { page ->
                val allPlaylists = page.items
                    .filterIsInstance<PlaylistItem>()
                    .filterNot { it.id == "LM" || it.id == "SE" }
                    .reversed()

                val remotePlaylists = mutableListOf<PlaylistItem>()

                // Filter playlists based on whitelist - only keep those with allowed songs
                val localPlaylists = database.playlistsByNameAsc().first()

                allPlaylists.forEach { playlist ->
                    try {
                        val playlistPage = YouTube.playlist(playlist.id).completed().getOrNull()
                        if (playlistPage != null) {
                            // Filter songs within playlist by whitelist
                            val allowedSongs = playlistPage.songs.filterWhitelisted(database)

                            if (allowedSongs.isNotEmpty()) {
                                // Only add playlist if it has at least one allowed song
                                remotePlaylists.add(playlist)

                                var playlistEntity = localPlaylists.find { it.playlist.browseId == playlist.id }?.playlist
                                if (playlistEntity == null) {
                                    // Create new playlist entity with filtered metadata
                                    playlistEntity = PlaylistEntity(
                                        name = playlist.title,
                                        browseId = playlist.id,
                                        thumbnailUrl = allowedSongs.firstOrNull()?.thumbnail,
                                        isEditable = playlist.isEditable,
                                        bookmarkedAt = LocalDateTime.now(),
                                        remoteSongCount = allowedSongs.size,
                                        playEndpointParams = playlist.playEndpoint?.params,
                                        shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                                        radioEndpointParams = playlist.radioEndpoint?.params
                                    )
                                    database.insert(playlistEntity)
                                } else {
                                    // Update existing playlist entity with filtered metadata
                                    database.update(playlistEntity.copy(
                                        thumbnailUrl = allowedSongs.firstOrNull()?.thumbnail,
                                        remoteSongCount = allowedSongs.size
                                    ))
                                }
                                // Sync only allowed songs for this playlist
                                syncPlaylist(playlist.id, playlistEntity.id, allowedSongs.map { it.id }.toSet())
                                android.util.Log.d("SyncUtils", "Playlist ${playlist.title} synced with ${allowedSongs.size} allowed songs")
                            } else {
                                // If no allowed songs, remove playlist from library
                                localPlaylists.find { it.playlist.browseId == playlist.id }?.let { found ->
                                    database.update(found.playlist.localToggleLike())
                                }
                                android.util.Log.d("SyncUtils", "Playlist ${playlist.title} removed - no allowed songs")
                            }
                        } else {
                            // If playlist fetch failed, remove it from database
                            localPlaylists.find { it.playlist.browseId == playlist.id }?.let { found ->
                                database.update(found.playlist.localToggleLike())
                            }
                        }
                    } catch (e: Exception) {
                        // If playlist fetch fails, remove it from database
                        localPlaylists.find { it.playlist.browseId == playlist.id }?.let { found ->
                            database.update(found.playlist.localToggleLike())
                        }
                        android.util.Log.w("SyncUtils", "Failed to fetch playlist ${playlist.id}: ${e.message}")
                    }
                }

                // Remove playlists that are no longer in remote (not filtered) from database
                val remoteIds = remotePlaylists.map { it.id }.toSet()
                localPlaylists.filterNot { it.playlist.browseId in remoteIds }.filterNot { it.playlist.browseId == null }.forEach { database.update(it.playlist.localToggleLike()) }
            }
        } catch (e: Exception) {
        } finally {
            isSyncingPlaylists.value = false
        }
    }

    private suspend fun syncPlaylist(browseId: String, playlistId: String) {
        try {
            YouTube.playlist(browseId).completed().onSuccess { page ->
                val songs = page.songs
                    .filterWhitelisted(database)
                    .filterIsInstance<SongItem>()
                    .map(SongItem::toMediaMetadata)
                val remoteIds = songs.map { it.id }
                val localIds = database.playlistSongs(playlistId).first().sortedBy { it.map.position }.map { it.song.id }

                if (remoteIds == localIds) return@onSuccess
                if (database.playlist(playlistId).firstOrNull() == null) return@onSuccess

                // Pre-load existing songs to avoid blocking inside transaction
                val existingSongIds = songs.mapNotNull { song ->
                    database.song(song.id).firstOrNull()?.song?.id
                }.toSet()

                database.transaction {
                    clearPlaylist(playlistId)
                    val songEntities = songs.onEach { song ->
                        if (song.id !in existingSongIds) {
                            insert(song)
                        }
                    }
                    val playlistSongMaps = songEntities.mapIndexed { position, song ->
                        PlaylistSongMap(songId = song.id, playlistId = playlistId, position = position, setVideoId = song.setVideoId)
                    }
                    playlistSongMaps.forEach { insert(it) }
                }
            }
        } catch (e: Exception) {
        }
    }

    private suspend fun syncPlaylist(browseId: String, playlistId: String, allowedSongIds: Set<String>) {
        // Only sync if we have pre-filtered allowed songs
        if (allowedSongIds.isEmpty()) {
            // Clear all songs from playlist since no artists are allowed
            database.transaction {
                clearPlaylist(playlistId)
            }
            android.util.Log.d("SyncUtils", "Playlist $playlistId cleared - no allowed songs")
            return
        }

        try {
            YouTube.playlist(browseId).completed().onSuccess { page ->
                val songs = page.songs
                    .filter { it.id in allowedSongIds }  // Only use pre-filtered songs
                    .filterIsInstance<SongItem>()
                    .map(SongItem::toMediaMetadata)
                val remoteIds = songs.map { it.id }
                val localIds = database.playlistSongs(playlistId).first().sortedBy { it.map.position }.map { it.song.id }

                if (remoteIds == localIds) return@onSuccess
                if (database.playlist(playlistId).firstOrNull() == null) return@onSuccess

                // Pre-load existing songs to avoid blocking inside transaction
                val existingSongIds = songs.mapNotNull { song ->
                    database.song(song.id).firstOrNull()?.song?.id
                }.toSet()

                database.transaction {
                    clearPlaylist(playlistId)

                    // Insert new songs that don't exist
                    songs.forEach { mediaMetadata ->
                        if (mediaMetadata.id !in existingSongIds) {
                            insert(
                                SongEntity(
                                    id = mediaMetadata.id,
                                    title = mediaMetadata.title,
                                    duration = mediaMetadata.duration,
                                    thumbnailUrl = mediaMetadata.thumbnailUrl,
                                    explicit = mediaMetadata.explicit,
                                    albumId = mediaMetadata.album?.id,
                                    albumName = mediaMetadata.album?.title
                                )
                            )
                        }
                    }

                    // Add playlist song mappings
                    songs.forEachIndexed { index, mediaMetadata ->
                        insert(
                            PlaylistSongMap(
                                songId = mediaMetadata.id,
                                playlistId = playlistId,
                                position = index
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("SyncUtils", "Error syncing playlist $playlistId: ${e.message}")
        }
    }

    suspend fun clearAllLibraryData() {
        try {
            // Clear all data using existing clear methods first
            database.clearListenHistory()
            database.clearSearchHistory()
            database.clearWhitelist()

            // Use raw SQL queries for the remaining tables
            withContext(Dispatchers.IO) {
                // Clear all playlists and mappings
                database.raw("DELETE FROM playlist_song_map".toSQLiteQuery())
                database.raw("DELETE FROM playlist".toSQLiteQuery())

                // Clear all songs and related data
                database.raw("DELETE FROM playCount".toSQLiteQuery())
                database.raw("DELETE FROM format".toSQLiteQuery())
                database.raw("DELETE FROM lyrics".toSQLiteQuery())
                database.raw("DELETE FROM song".toSQLiteQuery())

                // Clear all albums and mappings
                database.raw("DELETE FROM song_album_map".toSQLiteQuery())
                database.raw("DELETE FROM album_artist_map".toSQLiteQuery())
                database.raw("DELETE FROM album".toSQLiteQuery())

                // Clear all artists and mappings
                database.raw("DELETE FROM song_artist_map".toSQLiteQuery())
                database.raw("DELETE FROM artist".toSQLiteQuery())

                // Clear any remaining related data
                database.raw("DELETE FROM related_song_map".toSQLiteQuery())
            }

            android.util.Log.d("SyncUtils", "All library data cleared successfully")
        } catch (e: Exception) {
            android.util.Log.e("SyncUtils", "Error clearing library data: ${e.message}")
            throw e
        }
    }

    suspend fun syncArtistWhitelist(forceSync: Boolean = false) {
        withContext(Dispatchers.IO) {
            if (isSyncingWhitelist.value) return@withContext

            isSyncingWhitelist.value = true

            _whitelistSyncProgress.value = WhitelistSyncProgress()

            try {
                val remoteVersion = WhitelistFetcher.fetchVersion().getOrNull()
                val localVersion = context.dataStore.get(LastWhitelistVersionKey, 0L)
                val existingWhitelistIds = database.getAllWhitelistedArtistIdsSync()
                val localEmpty = existingWhitelistIds.isEmpty()

                // Always fetch at least once per version (including version 1). Subsequent runs skip if already synced.
                if (!forceSync && remoteVersion != null && remoteVersion <= localVersion && !localEmpty) {
                    runCatching { WhitelistCache.updateAll(database.getWhitelistEntriesSync()) }
                    _whitelistSyncProgress.value = WhitelistSyncProgress(isComplete = true)
                    return@withContext
                }

                val whitelistEntries = WhitelistFetcher.fetchWhitelist { processed, total ->
                    _whitelistSyncProgress.value = WhitelistSyncProgress(
                        current = processed,
                        total = total
                    )
                }.getOrThrow()

                _whitelistSyncProgress.value = WhitelistSyncProgress(
                    current = whitelistEntries.size,
                    total = whitelistEntries.size
                )

                val currentWhitelistIds = database.getAllWhitelistedArtistIdsSync()
                val newWhitelistIds = whitelistEntries.map { it.artistId }.toSet()
                val removedArtistIds = currentWhitelistIds.filterNot { it in newWhitelistIds }

                if (removedArtistIds.isNotEmpty()) {
                    deleteRemovedArtists(removedArtistIds)
                }

                database.transaction {
                    clearWhitelist()
                    insertWhitelist(whitelistEntries)
                    val existingArtistIds = getAllArtistIdsSync().toSet()
                    val missingArtists = whitelistEntries
                        .filter { it.artistId !in existingArtistIds }
                        .map { ArtistEntity(id = it.artistId, name = it.artistName) }
                    if (missingArtists.isNotEmpty()) {
                        insertArtists(missingArtists)
                    }
                }
                WhitelistCache.updateAll(whitelistEntries)

                _whitelistSyncProgress.value = WhitelistSyncProgress(
                    current = whitelistEntries.size,
                    total = whitelistEntries.size,
                    isComplete = true
                )

                context.dataStore.edit { settings ->
                    settings[LastWhitelistSyncTimeKey] = System.currentTimeMillis()
                    remoteVersion?.let { settings[LastWhitelistVersionKey] = it }
                }

                // Backfill artist thumbnails for whitelisted artists missing thumbs (limited to reduce load)
                backfillMissingArtistThumbs(limit = 150)
            } catch (e: Exception) {
                _whitelistSyncProgress.value = WhitelistSyncProgress(isComplete = true)
            } finally {
                isSyncingWhitelist.value = false
            }
        }
    }

    suspend fun syncPodcastWhitelist(forceSync: Boolean = false) {
        withContext(Dispatchers.IO) {
            if (isSyncingPodcastWhitelist.value) return@withContext

            isSyncingPodcastWhitelist.value = true

            _podcastWhitelistSyncProgress.value = WhitelistSyncProgress()

            try {
                val remoteVersion = WhitelistFetcher.fetchPodcastVersion().getOrNull()
                val localVersion = context.dataStore.get(LastPodcastWhitelistVersionKey, 0L)
                val existingPodcastIds = database.getAllWhitelistedPodcastIdsSync()
                val localEmpty = existingPodcastIds.isEmpty()

                // Always fetch at least once per version. Subsequent runs skip if already synced.
                if (!forceSync && remoteVersion != null && remoteVersion <= localVersion && !localEmpty) {
                    runCatching { PodcastWhitelistCache.updateAll(database.getPodcastWhitelistEntriesSync()) }
                    _podcastWhitelistSyncProgress.value = WhitelistSyncProgress(isComplete = true)
                    return@withContext
                }

                val whitelistEntries = WhitelistFetcher.fetchPodcastWhitelist { processed, total ->
                    _podcastWhitelistSyncProgress.value = WhitelistSyncProgress(
                        current = processed,
                        total = total
                    )
                }.getOrThrow()

                _podcastWhitelistSyncProgress.value = WhitelistSyncProgress(
                    current = whitelistEntries.size,
                    total = whitelistEntries.size
                )

                // Preserve existing thumbnails before clearing
                val existingThumbnails = database.getPodcastWhitelistEntriesSync()
                    .associate { it.podcastId to it.thumbnailUrl }

                // Merge existing thumbnails into new entries
                val entriesWithThumbnails = whitelistEntries.map { entry ->
                    val existingThumb = existingThumbnails[entry.podcastId]
                    if (existingThumb != null && entry.thumbnailUrl.isNullOrBlank()) {
                        entry.copy(thumbnailUrl = existingThumb)
                    } else {
                        entry
                    }
                }

                database.transaction {
                    clearPodcastWhitelist()
                    insertPodcastWhitelist(entriesWithThumbnails)
                }
                PodcastWhitelistCache.updateAll(entriesWithThumbnails)

                _podcastWhitelistSyncProgress.value = WhitelistSyncProgress(
                    current = whitelistEntries.size,
                    total = whitelistEntries.size,
                    isComplete = true
                )

                context.dataStore.edit { settings ->
                    settings[LastPodcastWhitelistSyncTimeKey] = System.currentTimeMillis()
                    remoteVersion?.let { settings[LastPodcastWhitelistVersionKey] = it }
                }

                android.util.Log.d("SyncUtils", "Podcast whitelist synced with ${whitelistEntries.size} podcasts")
            } catch (e: Exception) {
                android.util.Log.e("SyncUtils", "Error syncing podcast whitelist: ${e.message}")
                _podcastWhitelistSyncProgress.value = WhitelistSyncProgress(isComplete = true)
            } finally {
                isSyncingPodcastWhitelist.value = false
            }
        }
    }

    fun backfillMissingArtistThumbs(limit: Int = 150) {
        if (isBackfillingThumbs.value) return
        isBackfillingThumbs.value = true
        syncScope.launch {
            performBackfillMissingArtistThumbs(limit)
            isBackfillingThumbs.value = false
        }
    }

    private suspend fun performBackfillMissingArtistThumbs(limit: Int) {
        try {
            val missingIds = database.getWhitelistedArtistIdsMissingThumb(limit)
            if (missingIds.isEmpty()) return

            missingIds.forEachIndexed { index, artistId ->
                runCatching {
                    YouTube.artist(artistId).onSuccess { artistPage ->
                        val thumb = artistPage.artist.thumbnail
                        if (!thumb.isNullOrBlank()) {
                            database.getArtistById(artistId)?.let { existing ->
                                database.update(existing.copy(thumbnailUrl = thumb))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
        }
    }

    suspend fun clearAllSyncedContent() {
        try {
            val likedSongs = database.likedSongsByNameAsc().first()
            val librarySongs = database.songsByNameAsc().first()
            val likedAlbums = database.albumsLikedByNameAsc().first()
            val subscribedArtists = database.artistsBookmarkedByNameAsc().first()
            val savedPlaylists = database.playlistsByNameAsc().first()

            likedSongs.forEach {
                try { database.transaction { update(it.song.copy(liked = false, likedDate = null)) } } catch (e: Exception) { }
            }
            librarySongs.forEach {
                if (it.song.inLibrary != null) {
                    try { database.transaction { update(it.song.copy(inLibrary = null)) } } catch (e: Exception) { }
                }
            }
            likedAlbums.forEach {
                try { database.transaction { update(it.album.copy(bookmarkedAt = null)) } } catch (e: Exception) { }
            }
            subscribedArtists.forEach {
                try { database.transaction { update(it.artist.copy(bookmarkedAt = null)) } } catch (e: Exception) { }
            }
            savedPlaylists.forEach {
                if (it.playlist.browseId != null) {
                    try { database.transaction { delete(it.playlist) } } catch (e: Exception) { }
                }
            }
        } catch (e: Exception) {
        }
    }

    /**
     * Sync subscribed podcasts from YouTube Music to local database.
     * Updates PodcastEntity table with user's podcast subscriptions.
     * Uses both savedPodcastShows() and libraryPodcastChannels() like Metrolist.
     * Skips sync for anonymous users.
     */
    suspend fun syncPodcastSubscriptions() {
        // Skip sync for anonymous users
        if (YouTube.isAnonLogin) {
            Log.d("SyncUtils", "Skipping podcast subscriptions sync - anonymous login")
            return
        }
        withContext(Dispatchers.IO) {
            try {
                // Ensure whitelist cache is loaded before syncing
                if (PodcastWhitelistCache.isEmpty()) {
                    PodcastWhitelistCache.loadFromDatabase(database)
                }

                // Skip sync if whitelist cache is still empty (no whitelist data available)
                if (PodcastWhitelistCache.isEmpty()) {
                    Log.d("SyncUtils", "Skipping podcast subscriptions sync - whitelist cache is empty")
                    return@withContext
                }

                val allRemoteIds = mutableSetOf<String>()

                // Sync saved podcast shows (most common - saved via likePlaylist)
                Log.d("SyncUtils", "Calling savedPodcastShows API...")
                YouTube.savedPodcastShows().onSuccess { allPodcasts ->
                    // Log all podcasts for debugging
                    Log.d("SyncUtils", "savedPodcastShows: Found ${allPodcasts.size} total podcasts")
                    allPodcasts.forEach { podcast ->
                        val isAllowed = PodcastWhitelistCache.isAllowed(podcast.id)
                        Log.d("SyncUtils", "Podcast: '${podcast.title}' | id=${podcast.id} | author=${podcast.author?.name} | allowed=$isAllowed")
                    }

                    // Filter to only whitelisted podcasts
                    val podcasts = allPodcasts.filter { PodcastWhitelistCache.isAllowed(it.id) }
                    Log.d("SyncUtils", "savedPodcastShows: Filtered ${allPodcasts.size} podcasts to ${podcasts.size} whitelisted")
                    allRemoteIds.addAll(podcasts.map { it.id })

                    // Add/update podcasts from YouTube (already filtered)
                    podcasts.forEach { podcastItem ->
                        val existing = database.podcast(podcastItem.id).first()
                        if (existing == null) {
                            database.upsertPodcast(
                                com.jtech.zemer.db.entities.PodcastEntity(
                                    id = podcastItem.id,
                                    title = podcastItem.title,
                                    author = podcastItem.author?.name,
                                    thumbnailUrl = podcastItem.thumbnail,
                                    bookmarkedAt = LocalDateTime.now(),
                                )
                            )
                        } else if (existing.bookmarkedAt == null) {
                            database.upsertPodcast(existing.copy(
                                title = podcastItem.title,
                                author = podcastItem.author?.name,
                                thumbnailUrl = podcastItem.thumbnail,
                                bookmarkedAt = LocalDateTime.now(),
                                lastUpdateTime = LocalDateTime.now()
                            ))
                        }
                    }
                    Log.d("SyncUtils", "Synced ${podcasts.size} whitelisted saved podcast shows")
                }.onFailure {
                    Log.e("SyncUtils", "Failed to fetch saved podcast shows: ${it.message}")
                }

                // Also sync subscribed podcast channels (subscribed via subscribeChannel API)
                Log.d("SyncUtils", "Calling libraryPodcastChannels API...")
                YouTube.libraryPodcastChannels().onSuccess { page ->
                    Log.d("SyncUtils", "libraryPodcastChannels: page has ${page.items.size} total items")
                    val allPodcasts = page.items.filterIsInstance<com.metrolist.innertube.models.PodcastItem>()
                    // Log all podcast channels for debugging
                    Log.d("SyncUtils", "libraryPodcastChannels: Found ${allPodcasts.size} total podcast channels")
                    allPodcasts.forEach { podcast ->
                        val isAllowed = PodcastWhitelistCache.isAllowed(podcast.id)
                        Log.d("SyncUtils", "PodcastChannel: '${podcast.title}' | id=${podcast.id} | author=${podcast.author?.name} | allowed=$isAllowed")
                    }

                    // Filter to only whitelisted podcasts
                    val podcasts = allPodcasts.filter { PodcastWhitelistCache.isAllowed(it.id) }
                    Log.d("SyncUtils", "libraryPodcastChannels: Filtered ${allPodcasts.size} podcasts to ${podcasts.size} whitelisted")
                    allRemoteIds.addAll(podcasts.map { it.id })

                    // Add/update podcasts from YouTube channels
                    podcasts.forEach { podcastItem ->
                        val existing = database.podcast(podcastItem.id).first()
                        if (existing == null) {
                            database.upsertPodcast(
                                com.jtech.zemer.db.entities.PodcastEntity(
                                    id = podcastItem.id,
                                    title = podcastItem.title,
                                    author = podcastItem.author?.name,
                                    thumbnailUrl = podcastItem.thumbnail,
                                    bookmarkedAt = LocalDateTime.now(),
                                )
                            )
                        } else if (existing.bookmarkedAt == null) {
                            database.upsertPodcast(existing.copy(
                                title = podcastItem.title,
                                author = podcastItem.author?.name,
                                thumbnailUrl = podcastItem.thumbnail,
                                bookmarkedAt = LocalDateTime.now(),
                                lastUpdateTime = LocalDateTime.now()
                            ))
                        }
                    }
                    Log.d("SyncUtils", "Synced ${podcasts.size} whitelisted subscribed podcast channels")
                }.onFailure {
                    Log.e("SyncUtils", "Failed to fetch subscribed podcast channels: ${it.message}")
                }

                // Cleanup: Remove local podcasts that are no longer subscribed on YouTube Music
                if (allRemoteIds.isNotEmpty()) {
                    val localPodcasts = database.subscribedPodcasts().first()
                    val localOnlyPodcasts = localPodcasts.filterNot { it.id in allRemoteIds }
                    Log.d("SyncUtils", "Cleanup: removing ${localOnlyPodcasts.size} podcasts not on YTM")

                    localOnlyPodcasts.forEach { podcast ->
                        database.upsertPodcast(podcast.copy(bookmarkedAt = null))
                        Log.d("SyncUtils", "Unsubscribed from local podcast: ${podcast.id}")
                    }
                }
            } catch (e: Exception) {
                Log.e("SyncUtils", "Error syncing podcast subscriptions: ${e.message}")
            }
        }
    }

    /**
     * Sync "Episodes for Later" (SE playlist) from YouTube Music to local database.
     * Uses episodesForLater() API which returns proper setVideoId for removal.
     * Episodes are stored in song table with isEpisode=true and inLibrary set.
     * Skips sync for anonymous users.
     */
    suspend fun syncEpisodesForLater() {
        // Skip sync for anonymous users
        if (YouTube.isAnonLogin) {
            Log.d("SyncUtils", "Skipping episodes for later sync - anonymous login")
            return
        }
        withContext(Dispatchers.IO) {
            try {
                // Ensure whitelist cache is loaded before syncing
                if (PodcastWhitelistCache.isEmpty()) {
                    PodcastWhitelistCache.loadFromDatabase(database)
                }

                // Skip sync if whitelist cache is still empty (no whitelist data available)
                if (PodcastWhitelistCache.isEmpty()) {
                    Log.d("SyncUtils", "Skipping episodes for later sync - whitelist cache is empty")
                    return@withContext
                }

                YouTube.episodesForLater().onSuccess { allEpisodes ->
                    // Log all episodes with their artist info for debugging
                    Log.d("SyncUtils", "episodesForLater: Found ${allEpisodes.size} total episodes")
                    allEpisodes.forEach { episode ->
                        val allArtists = episode.artists.map { "${it.name}=${it.id}" }
                        Log.d("SyncUtils", "Episode: '${episode.title}' | artists=$allArtists")
                    }

                    // Filter to only episodes from whitelisted podcasts
                    // Check all artist IDs - could be MPSP (podcast), UC (channel), or other
                    val remoteEpisodes = allEpisodes.filter { episode ->
                        val podcastId = episode.album?.id

                        // Check if any artist ID matches whitelist (by podcastId or channelId)
                        val allowed = episode.artists.any { artist ->
                            val id = artist.id ?: return@any false
                            // Direct podcast ID match (MPSP...)
                            PodcastWhitelistCache.isAllowed(id) ||
                            // Channel ID match (UC... or other)
                            PodcastWhitelistCache.isAllowedByChannelId(id)
                        } || (podcastId != null && PodcastWhitelistCache.isAllowed(podcastId))

                        if (!allowed) {
                            val artistIds = episode.artists.map { it.id }
                            Log.d("SyncUtils", "FILTERED OUT: ${episode.title} - artistIds=$artistIds not in whitelist")
                        }
                        allowed
                    }
                    Log.d("SyncUtils", "episodesForLater: Filtered ${allEpisodes.size} episodes to ${remoteEpisodes.size} from whitelisted podcasts")

                    val remoteIds = remoteEpisodes.map { it.id }.toSet()
                    val localEpisodes = database.savedEpisodes().first()

                    // Remove episodes that are no longer saved or not from whitelisted podcasts
                    localEpisodes
                        .filter { it.id !in remoteIds }
                        .forEach { episode ->
                            database.transaction {
                                update(episode.song.copy(inLibrary = null))
                            }
                            Log.d("SyncUtils", "Removed local episode not on YTM: ${episode.id}")
                        }

                    // Add/update episodes from YouTube (already filtered to whitelisted)
                    remoteEpisodes.forEach { episode ->
                        val existing = database.song(episode.id).firstOrNull()
                        database.transaction {
                            if (existing == null) {
                                Log.d("SyncUtils", "Inserting new episode: ${episode.id}")
                                insert(episode.toMediaMetadata()) { it.copy(isEpisode = true, inLibrary = LocalDateTime.now()) }
                            } else if (!existing.song.isEpisode || existing.song.inLibrary == null) {
                                Log.d("SyncUtils", "Updating existing song to episode in library: ${episode.id}")
                                update(existing.song.copy(isEpisode = true, inLibrary = existing.song.inLibrary ?: LocalDateTime.now()))
                            }
                            // Store setVideoId for removal capability
                            episode.setVideoId?.let { setVideoId ->
                                Log.d("SyncUtils", "Storing setVideoId for ${episode.id}: $setVideoId")
                                upsertSetVideoId(com.jtech.zemer.db.entities.SetVideoIdEntity(episode.id, setVideoId))
                            }
                        }
                    }
                    Log.d("SyncUtils", "Synced ${remoteEpisodes.size} whitelisted episodes for later from YouTube Music")
                }.onFailure {
                    Log.e("SyncUtils", "Failed to sync episodes for later: ${it.message}")
                }
            } catch (e: Exception) {
                Log.e("SyncUtils", "Error syncing episodes for later: ${e.message}")
            }
        }
    }

    /**
     * Deletes artists and all their associated content from the database.
     * This includes: songs, albums, play history, cached formats, lyrics, and user data.
     * Deletion follows proper order to respect foreign key constraints.
     */
    suspend fun deleteRemovedArtists(removedArtistIds: List<String>) {
        if (removedArtistIds.isEmpty()) return

        try {
            // Process each removed artist
            for (artistId in removedArtistIds) {
                try {
                    // Step 1: Get all song IDs for this artist
                    val songIds = database.getSongIdsByArtist(artistId)

                    // Step 2: Get all album IDs for this artist
                    val albumIds = database.getAlbumIdsByArtist(artistId)

                    // Step 3: Delete song-related data without foreign keys (must be done first)
                    if (songIds.isNotEmpty()) {
                        database.deletePlayCountBySongs(songIds)
                        database.deleteFormatBySongs(songIds)
                        database.deleteLyricsBySongs(songIds)
                    }

                    // Step 4: Delete songs (this will CASCADE DELETE to related tables)
                    // Cascades: song_artist_map, song_album_map, playlist_song_map, related_song_map, event
                    if (songIds.isNotEmpty()) {
                        database.deleteSongsByIds(songIds)
                    }

                    // Step 5: Check and delete albums that have no songs left
                    val albumsToDelete = mutableListOf<String>()
                    for (albumId in albumIds) {
                        val remainingSongCount = database.getAlbumSongCount(albumId)
                        if (remainingSongCount == 0) {
                            albumsToDelete.add(albumId)
                        }
                    }
                    if (albumsToDelete.isNotEmpty()) {
                        database.deleteAlbumsByIds(albumsToDelete)
                    }

                    // Step 6: Delete the artist (this will CASCADE DELETE remaining mappings)
                    database.deleteArtistById(artistId)

                } catch (e: Exception) {
                }
            }

        } catch (e: Exception) {
        }
    }
}
