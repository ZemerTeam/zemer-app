package com.jtech.zemer.utils

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import com.jtech.zemer.constants.BlockedContentIdsKey
import com.jtech.zemer.constants.LastWhitelistSyncTimeKey
import com.jtech.zemer.constants.LastWhitelistVersionKey
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.ArtistEntity
import com.jtech.zemer.db.entities.PlaylistEntity
import com.jtech.zemer.db.entities.PlaylistSongMap
import com.jtech.zemer.db.entities.SongEntity
import com.jtech.zemer.extensions.isPersonalAccountSignedIn
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
    private val isBackfillingThumbs = MutableStateFlow(false)

    val isWhitelistSyncing: StateFlow<Boolean> = isSyncingWhitelist.asStateFlow()

    private val _whitelistSyncProgress = MutableStateFlow(WhitelistSyncProgress())
    val whitelistSyncProgress: StateFlow<WhitelistSyncProgress> = _whitelistSyncProgress.asStateFlow()

    fun runAllSyncs() {
        syncScope.launch {
            syncArtistWhitelist()
            syncLikedSongs()
            syncLibrarySongs()
            syncUploadedSongs()
            syncLikedAlbums()
            syncUploadedAlbums()
            syncArtistsSubscriptions()
            syncSavedPlaylists()
        }
    }

    fun likeSong(s: SongEntity) {
        if (!isPersonalAccountSignedIn) return
        syncScope.launch {
            YouTube.likeVideo(s.id, s.liked)
        }
    }

    suspend fun syncLikedSongs() {
        if (!isPersonalAccountSignedIn) return
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
        if (!isPersonalAccountSignedIn) return
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
        if (!isPersonalAccountSignedIn) return
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
        if (!isPersonalAccountSignedIn) return
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
        if (!isPersonalAccountSignedIn) return
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
        if (!isPersonalAccountSignedIn) return
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
        if (!isPersonalAccountSignedIn) return
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

                // 100% whitelisted: never reconcile playlists without a loaded whitelist. With no
                // allow-set no song can be verified, and filtering against it would wipe every synced
                // playlist; skip this pass and leave the already-filtered local copies intact.
                val allowedArtistIds = WhitelistCache.allowedEntries(database, ContentFilterState.current)
                    .map { it.artistId }.toHashSet()
                if (allowedArtistIds.isEmpty()) return@onSuccess

                allPlaylists.forEach { playlist ->
                    try {
                        val playlistPage = YouTube.playlist(playlist.id).completed().getOrNull()
                        // A failed or empty per-playlist read must never restructure the library
                        // (issue #130): keep the playlist and leave its local songs untouched.
                        if (playlistPage != null && playlistPage.songs.isNotEmpty()) {
                            // Keep only whitelisted songs (100% whitelist), resolving each song's
                            // artist from the local DB row so a sparse/mismatched playlist renderer
                            // can't drop a user-added song that is actually by a whitelisted artist.
                            val allowedSongs = playlistPage.songs.filterWhitelistedWithLocalArtists(database, allowedArtistIds)

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
                                        // Editability comes from the playlist's OWN page
                                        // (musicEditablePlaylistDetailHeaderRenderer), not the library
                                        // grid item — the FEmusic_liked_playlists tiles never carry an
                                        // EDIT menu icon, so playlist.isEditable is always false there,
                                        // which hid every synced playlist from "Add to playlist"
                                        // (its query is `WHERE isEditable`). The page signal correctly
                                        // marks the user's own playlists editable and saved/system ones not.
                                        isEditable = playlistPage.playlist.isEditable,
                                        bookmarkedAt = LocalDateTime.now(),
                                        remoteSongCount = allowedSongs.size,
                                        playEndpointParams = playlist.playEndpoint?.params,
                                        shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                                        radioEndpointParams = playlist.radioEndpoint?.params
                                    )
                                    database.insert(playlistEntity)
                                } else {
                                    // Update existing playlist entity with filtered metadata.
                                    // Refresh isEditable from the page signal too, so playlists synced
                                    // before this fix (stored isEditable=false) become addable on the
                                    // next sync without needing a re-login.
                                    database.update(playlistEntity.copy(
                                        thumbnailUrl = allowedSongs.firstOrNull()?.thumbnail,
                                        remoteSongCount = allowedSongs.size,
                                        isEditable = playlistPage.playlist.isEditable
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
                            // Fetch failed or returned no songs — do NOT delete. The playlist is still
                            // listed remotely; keep it and protect it from the removal sweep below,
                            // leaving its local songs untouched (issue #130).
                            remotePlaylists.add(playlist)
                            android.util.Log.d("SyncUtils", "Playlist ${playlist.title} read empty/failed - kept, not clobbered")
                        }
                    } catch (e: Exception) {
                        // A transient error must not delete the playlist: keep it and protect it from
                        // the removal sweep, leaving local songs untouched.
                        remotePlaylists.add(playlist)
                        android.util.Log.w("SyncUtils", "Failed to fetch playlist ${playlist.id}, kept: ${e.message}")
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
                // Never wipe the local playlist on an empty/partial re-read (issue #130): an empty
                // remote read is treated as transient, not as an emptied playlist.
                if (page.songs.isEmpty()) return@onSuccess
                val songs = page.songs
                    .filter { it.id in allowedSongIds }  // Only use pre-filtered songs
                    .filterIsInstance<SongItem>()
                    .map(SongItem::toMediaMetadata)
                val remoteIds = songs.map { it.id }
                val localIds = database.playlistSongs(playlistId).first().sortedBy { it.map.position }.map { it.song.id }

                if (remoteIds == localIds) return@onSuccess
                // Filtering left nothing while the playlist locally still has songs — skip rather than
                // clear, so a transient sparse read can't empty an otherwise-populated playlist.
                if (songs.isEmpty() && localIds.isNotEmpty()) return@onSuccess
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
                    refreshBlockedIds()
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
                        .map { ArtistEntity(id = it.artistId, name = it.artistName, thumbnailUrl = it.thumbnailUrl) }
                    if (missingArtists.isNotEmpty()) {
                        insertArtists(missingArtists)
                    }
                    // Populate the per-artist thumbnail (carried in the synced whitelist) onto EXISTING artist
                    // rows too. Batched inside this transaction, so it's a single flow re-emit — not the old
                    // write-per-thumbnail recompose storm. A null/blank thumbnail never wipes an existing one.
                    whitelistEntries.forEach { entry ->
                        val thumb = entry.thumbnailUrl
                        if (!thumb.isNullOrBlank() && entry.artistId in existingArtistIds) {
                            updateArtistThumbnailUrl(entry.artistId, thumb)
                        }
                    }
                }
                WhitelistCache.updateAll(whitelistEntries)
                refreshBlockedIds()

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

    /**
     * Refresh the id-level block overrides (see [BlockedIdsCache]) into the in-memory cache and the
     * persisted DataStore snapshot. Read-only and best-effort: a failed/throwing fetch is swallowed so it
     * can never break the whitelist sync, and the previous blocklist is left intact (never unblocks).
     * Runs as part of the automatic whitelist sync — no user interaction.
     */
    private suspend fun refreshBlockedIds() {
        runCatching {
            WhitelistFetcher.fetchBlockedIds().onSuccess { overrides ->
                BlockedIdsCache.updateAll(overrides)
                context.dataStore.edit { it[BlockedContentIdsKey] = BlockedIdsCache.serialize(overrides) }
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
