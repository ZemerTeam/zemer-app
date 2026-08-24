package com.jtech.zemer.utils

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import com.jtech.zemer.constants.BlockedContentIdsKey
import com.jtech.zemer.constants.DisplayNamesBackfilledKey
import com.jtech.zemer.constants.LastWhitelistVersionKey
import com.jtech.zemer.constants.LastPodcastWhitelistVersionKey
import com.jtech.zemer.constants.LastPodcastWhitelistSyncTimeKey
import timber.log.Timber
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.ArtistEntity
import com.jtech.zemer.db.entities.PlaylistEntity
import com.jtech.zemer.db.entities.PodcastEntity
import com.jtech.zemer.db.entities.PodcastWhitelistEntity
import com.jtech.zemer.db.entities.SetVideoIdEntity
import com.jtech.zemer.db.entities.SongEntity
import com.jtech.zemer.R
import com.jtech.zemer.extensions.isPersonalAccountSignedIn
import com.jtech.zemer.extensions.toSQLiteQuery
import com.jtech.zemer.extensions.toast
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.di.zemerSearchRepository
import com.jtech.zemer.search.zemerSearchOptions
import com.jtech.zemer.sync.PodcastSyncLogic
import com.jtech.zemer.utils.filterWhitelisted
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.utils.completed
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    private val isSyncingPodcastSubscriptions = MutableStateFlow(false)
    private val isSyncingEpisodes = MutableStateFlow(false)

    val isWhitelistSyncing: StateFlow<Boolean> = isSyncingWhitelist.asStateFlow()

    private val _whitelistSyncProgress = MutableStateFlow(WhitelistSyncProgress())
    val whitelistSyncProgress: StateFlow<WhitelistSyncProgress> = _whitelistSyncProgress.asStateFlow()

    // Podcast whitelist sync (the SEPARATE podcastsWhitelist collection, mirroring the artist whitelist).
    private val isSyncingPodcastWhitelist = MutableStateFlow(false)
    val isPodcastWhitelistSyncing: StateFlow<Boolean> = isSyncingPodcastWhitelist.asStateFlow()
    private val _podcastWhitelistSyncProgress = MutableStateFlow(WhitelistSyncProgress())
    val podcastWhitelistSyncProgress: StateFlow<WhitelistSyncProgress> = _podcastWhitelistSyncProgress.asStateFlow()

    /**
     * Bidirectional podcast-subscription sync, structured exactly like the music library syncs
     * (personal-account-gated, in-flight-guarded, whitelist-filtered, server-first). YouTube Music is
     * the source of truth: subscribed shows/channels are pulled into the local `podcast` table and
     * shows the user unsubscribed on YTM are dropped locally. The PUSH side (save/subscribe on toggle)
     * already runs at the toggle chokepoints.
     *
     * "Same whitelist filtering like music": the remote lists go through `filterWhitelisted`, which
     * gates podcasts on the SEPARATE podcast whitelist (filters-off passes everything). A show that is
     * not (or no longer) podcast-whitelisted never enters the library and is cleaned out if present.
     */
    suspend fun syncPodcastSubscriptions() {
        if (!isPersonalAccountSignedIn) return
        if (isSyncingPodcastSubscriptions.value) return
        isSyncingPodcastSubscriptions.value = true
        try {
            // Whitelisted remote ids across BOTH surfaces - drives the cleanup, no second fetch.
            val remoteIds = mutableSetOf<String>()

            // Saved podcast shows (saved via likePlaylist) + subscribed podcast channels. The two
            // fetches are independent (merged below), so run them concurrently - the sync then costs
            // the slower round-trip, not the sum of both.
            val (savedShows, channelPage) = coroutineScope {
                val savedShowsAsync = async { YouTube.savedPodcastShows().getOrNull().orEmpty() }
                val channelPageAsync = async { YouTube.libraryPodcastChannels().getOrNull() }
                savedShowsAsync.await() to channelPageAsync.await()
            }
            val remotePodcasts = (savedShows + (channelPage?.items?.filterIsInstance<PodcastItem>().orEmpty()))
                .filterWhitelisted(database)
                .filterIsInstance<PodcastItem>()
                .distinctBy { it.id }

            remotePodcasts.forEach { podcast ->
                remoteIds.add(podcast.id)
                try {
                    val dbPodcast = database.podcast(podcast.id).firstOrNull()
                    when (PodcastSyncLogic.upsertAction(
                        existsLocally = dbPodcast != null,
                        bookmarkedLocally = dbPodcast?.bookmarkedAt != null,
                    )) {
                        PodcastSyncLogic.UpsertAction.INSERT ->
                            database.transaction {
                                upsertPodcast(
                                    PodcastEntity(
                                        id = podcast.id,
                                        title = podcast.title,
                                        author = podcast.author?.name,
                                        thumbnailUrl = podcast.thumbnail,
                                        channelId = podcast.channelId ?: podcast.author?.id,
                                        bookmarkedAt = LocalDateTime.now(),
                                    )
                                )
                            }
                        PodcastSyncLogic.UpsertAction.UPDATE_METADATA ->
                            database.transaction {
                                updatePodcast(
                                    dbPodcast!!.copy(
                                        title = podcast.title,
                                        author = podcast.author?.name,
                                        thumbnailUrl = podcast.thumbnail,
                                        channelId = podcast.channelId ?: podcast.author?.id ?: dbPodcast.channelId,
                                        lastUpdateTime = LocalDateTime.now(),
                                    )
                                )
                            }
                        // User removed it locally - respect that, the remote removal is likely pending.
                        PodcastSyncLogic.UpsertAction.SKIP_UNBOOKMARKED -> Unit
                    }
                } catch (e: Exception) {
                    Timber.e(e, "syncPodcastSubscriptions: failed to process ${podcast.id}")
                }
            }

            // Cleanup: local subscriptions no longer on (the whitelisted) YTM get unbookmarked.
            if (remoteIds.isNotEmpty()) {
                val localOnly = PodcastSyncLogic.localOnly(
                    local = database.subscribedPodcasts().first(),
                    remoteIds = remoteIds,
                    id = { it.id },
                )
                localOnly.forEach { podcast ->
                    try {
                        database.transaction { updatePodcast(podcast.copy(bookmarkedAt = null)) }
                    } catch (e: Exception) {
                        Timber.e(e, "syncPodcastSubscriptions: cleanup failed for ${podcast.id}")
                    }
                }
            }

            // Reconcile bookmarked CHANNELS (ArtistEntity isPodcastChannel) against the remote subscribed
            // channels — a channel unsubscribed on YouTube Music must disappear locally too. The remote
            // channels are ArtistItems (NOT PodcastItems, so they're absent from `remoteIds` above), so
            // this is the ONLY place the Channels tab reconciles. Runs only when the channel fetch
            // SUCCEEDED (channelPage != null): a failed fetch must never wipe local subscriptions; an
            // empty success is valid (the user unsubscribed from every channel).
            if (channelPage != null) {
                val remoteChannelIds = channelPage.items.filterIsInstance<ArtistItem>().map { it.id }.toSet()
                database.bookmarkedPodcastChannels().first()
                    .filterNot { it.id in remoteChannelIds }
                    .forEach { channel ->
                        try {
                            database.transaction { update(channel.copy(bookmarkedAt = null)) }
                        } catch (e: Exception) {
                            Timber.e(e, "syncPodcastSubscriptions: channel cleanup failed for ${channel.id}")
                        }
                    }
            }
        } catch (e: Exception) {
            Timber.e(e, "syncPodcastSubscriptions failed")
        } finally {
            isSyncingPodcastSubscriptions.value = false
        }
    }

    /**
     * Bidirectional "Episodes for Later" (VLSE) sync - the episode analogue of the liked/library song
     * sync. Saved episodes come back as `SongItem(isEpisode = true)` and are stored as songs with
     * `inLibrary` set + `isEpisode = true` (matching how the rest of the app models episodes), keeping
     * each episode's `setVideoId` so an un-save can call `removeEpisodeFromSavedEpisodes`. Episodes the
     * user removed from VLSE on YouTube are cleared locally.
     *
     * Whitelisted exactly like music (never sync non-whitelisted content, always allow whitelisted): the
     * SERVER is the podcast-whitelist authority, so an episode is kept only if its owning SHOW is served
     * by `/podcast` under the current flags — see the filter below.
     */
    suspend fun syncEpisodesForLater() {
        if (!isPersonalAccountSignedIn) return
        if (isSyncingEpisodes.value) return
        isSyncingEpisodes.value = true
        try {
            YouTube.episodesForLater().onSuccess { rawEpisodes ->
                // Kosher gate via the SERVER, not the local podcast table: keep an episode only if its
                // owning SHOW (MPSP…) is served by `/podcast` (whitelisted under the current flags). Never
                // adds non-whitelisted content, and — unlike a local-only show→channel resolve — DOES let a
                // whitelisted episode through even when its show isn't locally subscribed. Each unique show
                // is resolved ONCE, and the independent lookups run CONCURRENTLY (async/awaitAll) rather
                // than one sequential /podcast round-trip per show.
                //
                // A lookup that does not positively confirm the show drops the episode this run (never add
                // on doubt), but only a lookup that could NOT complete disables the cleanup below. A clean
                // 404 (`repo.podcast` returns null WITHOUT throwing — a reachable server saying "not
                // whitelisted") is a definitive negative, NOT a failure, so a user's non-kosher saves don't
                // permanently disable cleanup. Only a thrown error (a genuine outage with no snapshot, or a
                // non-network error) sets anyLookupFailed — mirroring syncPodcastSubscriptions' `channelPage
                // != null` guard: a transient/partial failure must never be read as "removed on YTM" and
                // wipe a genuine local save.
                val repo = context.zemerSearchRepository()
                val options = zemerSearchOptions(context)
                // Only an MPSP id can be looked up on /podcast — a UC/absent fallback id would 404
                // cleanly on a healthy server and read as "not whitelisted".
                val showIdOf: (SongItem) -> String? = { ep ->
                    ep.artists.mapNotNull { it.id }.firstOrNull { it.startsWith("MPSP") }
                }
                val distinctShowIds = rawEpisodes.mapNotNull(showIdOf).distinct()
                val lookups = coroutineScope {
                    distinctShowIds.map { showId ->
                        async {
                            // serverOrOffline returns null ONLY on a clean 404 (reachable server, show not
                            // whitelisted); an outage without a snapshot, or any non-network error, THROWS.
                            // So success(null) = definitively not whitelisted → drop the episode but NOT a
                            // failure; only isFailure = "couldn't verify" → disables the cleanup below.
                            val result = runCatching { repo.podcast(showId, 0, options) }
                            Triple(showId, result.getOrNull() != null, result.isFailure)
                        }
                    }.awaitAll()
                }
                val showAllowed = lookups.associate { (showId, allowed, _) -> showId to allowed }
                val anyLookupFailed = lookups.any { (_, _, failed) -> failed }
                // Whitelist resolution gates IMPORT; cleanup keys off the RAW remote set (an
                // unresolvable or flag-hidden show must not wipe its saved episode) — see
                // [PodcastSyncLogic.episodeSyncPlan].
                val plan = PodcastSyncLogic.episodeSyncPlan(rawEpisodes, { it.id }, showIdOf, showAllowed)
                val remoteEpisodes = plan.imported
                val remoteIds = plan.cleanupReference

                remoteEpisodes.forEach { episode ->
                    try {
                        val dbSong = database.song(episode.id).firstOrNull()
                        database.transaction {
                            if (dbSong == null) {
                                insert(episode.toMediaMetadata()) {
                                    it.copy(
                                        inLibrary = LocalDateTime.now(),
                                        isEpisode = true,
                                        libraryAddToken = episode.libraryAddToken,
                                        libraryRemoveToken = episode.libraryRemoveToken,
                                    )
                                }
                            } else {
                                update(
                                    dbSong.song.copy(
                                        isEpisode = true,
                                        inLibrary = dbSong.song.inLibrary ?: LocalDateTime.now(),
                                        libraryAddToken = episode.libraryAddToken ?: dbSong.song.libraryAddToken,
                                        libraryRemoveToken = episode.libraryRemoveToken ?: dbSong.song.libraryRemoveToken,
                                    )
                                )
                            }
                            // Keep the playlist-scoped setVideoId so un-save can remove it from VLSE.
                            episode.setVideoId?.let { svid ->
                                upsertSetVideoId(SetVideoIdEntity(videoId = episode.id, setVideoId = svid))
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "syncEpisodesForLater: failed to process ${episode.id}")
                    }
                }

                // Cleanup: local saved episodes no longer in (the whitelisted) VLSE are cleared. Guarded
                // against an empty remote (a successful-but-empty fetch) AND against any failed show lookup
                // (a transient server/network failure must never be read as "removed on YTM" and wipe a
                // genuine local save) - mirrors syncPodcastSubscriptions.
                if (remoteIds.isNotEmpty() && !anyLookupFailed) {
                    val localToRemove = PodcastSyncLogic.localOnly(
                        local = database.savedEpisodes().first(),
                        remoteIds = remoteIds,
                        id = { it.id },
                    )
                    localToRemove.forEach { song ->
                        try {
                            database.transaction { update(song.song.copy(inLibrary = null)) }
                        } catch (e: Exception) {
                            Timber.e(e, "syncEpisodesForLater: cleanup failed for ${song.id}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "syncEpisodesForLater failed")
        } finally {
            isSyncingEpisodes.value = false
        }
    }

    fun likeSong(s: SongEntity) {
        if (!isPersonalAccountSignedIn) return
        syncScope.launch {
            YouTube.likeVideo(s.id, s.liked)
        }
    }

    /**
     * THE heart WRITE chokepoint — the write-side twin of [SongEntity.isSavedForPlayer] (the read
     * side): an episode toggles save-for-later ([toggleSaveEpisode]: inLibrary + VLSE account sync),
     * a song toggles the music like (local `liked` flip + [likeSong]). Every heart affordance calls
     * this instead of hand-branching, so a new surface can never music-like an episode (writing a
     * `liked` the episode heart never reads). The insert-then-update handles a not-yet-persisted row
     * (insert is IGNORE — fills a missing row, never clobbers an existing one).
     */
    fun toggleSavedForPlayer(song: SongEntity) {
        if (song.isEpisode) {
            toggleSaveEpisode(song)
        } else {
            val toggled = song.toggleLike()
            database.query {
                insert(toggled)
                update(toggled)
            }
            likeSong(toggled)
        }
    }

    /**
     * Save/unsave a podcast episode - the episode analogue of [likeSong]. Flips `inLibrary` locally at
     * once (optimistic, drives the heart/notification; anonymous sessions stay local-only), then, for a
     * personal login, syncs the account and reverts the local flip + toasts on failure (including a
     * failed setVideoId lookup — success there left the remote entry alive and the next sync silently
     * re-saved it). The whole flow lives in one coroutine so the flip and its revert can never race.
     */
    fun toggleSaveEpisode(episode: SongEntity) {
        val wasSaved = episode.inLibrary != null
        syncScope.launch {
            // UPSERT in one transaction (insert-if-missing + full-row update) so saving a NOT-YET-persisted
            // episode (e.g. from the song menu) creates its row stamped isEpisode = true — otherwise the
            // save sets nothing (update no-ops on a missing row) and it never appears in Episodes-for-Later.
            saveEpisodeLocal(episode, inLibrary = if (wasSaved) null else LocalDateTime.now())
            // Anonymous (pooled) sessions never write the shared account (dataSyncId, not SAPISID).
            if (!isPersonalAccountSignedIn) return@launch

            val result = if (wasSaved) {
                // Removal needs the playlist-scoped setVideoId. We store it on the PULL sync, but an
                // episode saved in Zemer (pushed via addToPlaylist, which doesn't return it) has none until
                // then — so if it's missing, fetch it live from the SE playlist so the un-save reaches
                // YouTube Music instead of silently staying saved there. A FAILED lookup surfaces as
                // failure (revert + toast) — see [PodcastSyncLogic.unsaveAction].
                val stored = database.getSetVideoId(episode.id)?.setVideoId
                if (stored != null) {
                    YouTube.removeEpisodeFromSavedEpisodes(episode.id, stored)
                } else {
                    val lookup = YouTube.episodesForLater()
                    val live = lookup.getOrNull()?.firstOrNull { it.id == episode.id }?.setVideoId
                    when (PodcastSyncLogic.unsaveAction(lookup.isSuccess, live)) {
                        PodcastSyncLogic.UnsaveAction.REMOVE ->
                            YouTube.removeEpisodeFromSavedEpisodes(episode.id, live!!)
                        PodcastSyncLogic.UnsaveAction.NOTHING_TO_REMOVE -> Result.success(Unit)
                        PodcastSyncLogic.UnsaveAction.FAIL ->
                            Result.failure(lookup.exceptionOrNull() ?: Exception("episodesForLater lookup failed"))
                    }
                }
            } else {
                YouTube.addEpisodeToSavedEpisodes(episode.id)
            }
            result.onFailure { e ->
                Timber.e(e, "[EPISODE_SAVE] toggle failed for ${episode.id} - reverting")
                saveEpisodeLocal(episode, inLibrary = if (wasSaved) LocalDateTime.now() else null)
                withContext(Dispatchers.Main) {
                    context.toast(if (wasSaved) R.string.error_episode_remove else R.string.error_episode_save)
                }
            }
        }
    }

    /**
     * Upsert an episode's saved-for-later state, overwriting ONLY the save-owned columns (`inLibrary` +
     * `isEpisode`). Base the row on the EXISTING DB row (read first) — a full-row write of the caller's
     * possibly-stale [SongEntity] snapshot would clobber download/resume columns the user set elsewhere
     * (`isDownloaded`, `mediaStoreUri`, `lastPositionMs`, `totalPlayTime`), the same clobber
     * [MediaStoreDownloadManager.markSongAsDownloaded] is careful to avoid. Falling back to the caller's
     * entity when no row exists still lands a row for a not-yet-persisted episode, so it appears in
     * `savedEpisodes()` / Library → Podcasts → Episodes. The single upsert keeps it a one-statement
     * mutation (no split-mutation race).
     */
    private suspend fun saveEpisodeLocal(episode: SongEntity, inLibrary: LocalDateTime?) {
        val existing = database.song(episode.id).firstOrNull()?.song
        database.transaction {
            upsert((existing ?: episode).copy(inLibrary = inLibrary, isEpisode = true))
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
                val localRows = database.playlistSongs(playlistId).first().sortedBy { it.map.position }
                val localIds = localRows.map { it.song.id }

                // Compare (id, setVideoId) pairs, not just ids: setVideoId is what remote
                // remove/reorder need, so an unchanged-membership playlist still rebuilds once to
                // backfill entry ids that were never persisted.
                if (songs.map { it.id to it.setVideoId } == localRows.map { it.song.id to it.map.setVideoId }) return@onSuccess
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
                                    albumId = mediaMetadata.album?.id,
                                    albumName = mediaMetadata.album?.title
                                )
                            )
                        }
                    }

                    // Add playlist song mappings (setVideoId included — remote edits depend on it)
                    playlistSongMaps(songs, playlistId).forEach { insert(it) }
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

                // Thumbnails ride the whitelist fetch, so an install whose artist rows are largely
                // thumb-less (e.g. an app update while the whitelist version is unchanged) must do one
                // full fetch to bootstrap them — otherwise the version gate would defer the server
                // thumbnails indefinitely and every avatar would fall back to a per-artist browse.
                // A handful of artists legitimately have no server thumbnail, hence the threshold.
                val manyThumbsMissing = !localEmpty &&
                    database.getWhitelistedArtistIdsMissingThumb(MISSING_THUMB_BOOTSTRAP_THRESHOLD).size >= MISSING_THUMB_BOOTSTRAP_THRESHOLD

                // One-time backfill of the v35 displayName/altName columns (added NULL by the migration):
                // while unset, take the full-fetch path so an already-synced install picks up the split
                // names on its first sync after updating, instead of waiting for a server version bump.
                val displayNamesBackfilled = context.dataStore.get(DisplayNamesBackfilledKey, false)

                // Always fetch at least once per version (including version 1). Subsequent runs skip if already synced.
                if (whitelistFastPathEligible(
                        forceSync = forceSync,
                        remoteVersion = remoteVersion,
                        localVersion = localVersion,
                        localEmpty = localEmpty,
                        manyThumbsMissing = manyThumbsMissing,
                        displayNamesBackfilled = displayNamesBackfilled,
                    )
                ) {
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

                // Never apply an empty allow-set over a good local table: a successfully-parsed
                // empty response (mirror bug, empty snapshot) would otherwise diff EVERY artist as
                // removed and deleteRemovedArtists their songs/albums/history. Keep the last-good
                // table instead (same fail-safe as syncPodcastWhitelist).
                if (whitelistEntries.isEmpty()) {
                    runCatching { WhitelistCache.updateAll(database.getWhitelistEntriesSync()) }
                    refreshBlockedIds()
                    _whitelistSyncProgress.value = WhitelistSyncProgress(isComplete = true)
                    return@withContext
                }

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
                        .map { ArtistEntity(id = it.artistId, name = it.displayName ?: it.artistName, thumbnailUrl = it.thumbnailUrl) }
                    if (missingArtists.isNotEmpty()) {
                        insertArtists(missingArtists)
                    }
                    // Populate the per-artist thumbnail (carried in the synced whitelist) onto EXISTING artist
                    // rows too. Batched inside this transaction, so it's a single flow re-emit — not the old
                    // write-per-thumbnail recompose storm. The DAO query is fill-only: a null/blank thumbnail
                    // never wipes an existing one, and a device-resolved image is never overwritten.
                    artistThumbnailUpdates(whitelistEntries, existingArtistIds).forEach { (artistId, thumb) ->
                        updateArtistThumbnailUrl(artistId, thumb)
                    }
                    // Repair pre-split seeds: an existing artist row still carrying the legacy whitelist
                    // name (the dual dash form) is renamed to the clean displayName. Guarded in the DAO
                    // query (name must still equal the legacy value), so a YTM-resolved name never changes.
                    artistDisplayNameUpdates(whitelistEntries, existingArtistIds).forEach { (artistId, legacyName, displayName) ->
                        renameArtistFromWhitelist(artistId, legacyName, displayName)
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
                    remoteVersion?.let { settings[LastWhitelistVersionKey] = it }
                    // A full fetch just ran insertWhitelist with the split names, so the one-time
                    // backfill is done — subsequent syncs may take the version-gated fast path again.
                    settings[DisplayNamesBackfilledKey] = true
                }
            } catch (e: Exception) {
                _whitelistSyncProgress.value = WhitelistSyncProgress(isComplete = true)
            } finally {
                isSyncingWhitelist.value = false
            }
        }
    }

    /**
     * Sync the podcast whitelist (the Firestore `podcastsWhitelist` collection) into the local
     * `podcast_whitelist` table + in-memory [PodcastWhitelistCache], mirroring [syncArtistWhitelist]:
     * a version probe skips the fetch when unchanged, existing thumbnails are preserved across a
     * refresh, and a failure keeps the previous list (never empties the browse screen). Account-
     * independent (it powers content browsing, like the artist whitelist).
     */
    suspend fun syncPodcastWhitelist(forceSync: Boolean = false) {
        withContext(Dispatchers.IO) {
            if (isSyncingPodcastWhitelist.value) return@withContext
            isSyncingPodcastWhitelist.value = true
            _podcastWhitelistSyncProgress.value = WhitelistSyncProgress()
            try {
                // The allow-set + version gate + art all come from the content mirror (content.zemer.io/
                // podcastsWhitelist), MIRROR-FIRST with Firestore fallback — exactly like the artist
                // whitelist (WhitelistFetcher.fetch*): each doc now carries thumbnailUrl + channelId, so
                // the browse grid renders straight from the mirror with no per-device catalog fetch.
                val remoteVersion = WhitelistFetcher.fetchPodcastVersion().getOrNull()
                val localVersion = context.dataStore.get(LastPodcastWhitelistVersionKey, 0L)
                val localEmpty = database.getAllWhitelistedPodcastIdsSync().isEmpty()

                // Fetch at least once per version; later runs skip when already synced.
                if (!forceSync && remoteVersion != null && remoteVersion <= localVersion && !localEmpty) {
                    runCatching { PodcastWhitelistCache.updateAll(database.getPodcastWhitelistEntriesSync()) }
                    _podcastWhitelistSyncProgress.value = WhitelistSyncProgress(isComplete = true)
                    return@withContext
                }

                val whitelistEntries = WhitelistFetcher.fetchPodcastWhitelist { processed, total ->
                    _podcastWhitelistSyncProgress.value = WhitelistSyncProgress(current = processed, total = total)
                }.getOrThrow()

                // Never wipe a good local table with nothing: an empty allow-set must not unblock every
                // podcast. Keep the last-good table (same fail-safe as the artist sync).
                if (whitelistEntries.isEmpty()) {
                    runCatching { PodcastWhitelistCache.updateAll(database.getPodcastWhitelistEntriesSync()) }
                    _podcastWhitelistSyncProgress.value = WhitelistSyncProgress(isComplete = true)
                    return@withContext
                }

                // Preserve any existing channel avatar across the clear+reinsert as a guard for a row the
                // mirror ever omits art on (never regress a good local cover).
                val existingThumbnails = database.getPodcastWhitelistEntriesSync()
                    .associate { it.channelId to it.thumbnailUrl }
                val entriesWithThumbnails = whitelistEntries.map { entry ->
                    val existingThumb = existingThumbnails[entry.channelId]
                    if (existingThumb != null && entry.thumbnailUrl.isNullOrBlank()) entry.copy(thumbnailUrl = existingThumb)
                    else entry
                }

                database.transaction {
                    clearPodcastWhitelist()
                    insertPodcastWhitelist(entriesWithThumbnails)
                }
                PodcastWhitelistCache.updateAll(entriesWithThumbnails)

                _podcastWhitelistSyncProgress.value =
                    WhitelistSyncProgress(current = whitelistEntries.size, total = whitelistEntries.size, isComplete = true)
                context.dataStore.edit { settings ->
                    settings[LastPodcastWhitelistSyncTimeKey] = System.currentTimeMillis()
                    remoteVersion?.let { settings[LastPodcastWhitelistVersionKey] = it }
                }
                Timber.d("Podcast whitelist synced (mirror-first) with ${whitelistEntries.size} podcasts")
            } catch (e: Exception) {
                Timber.e(e, "Error syncing podcast whitelist")
                _podcastWhitelistSyncProgress.value = WhitelistSyncProgress(isComplete = true)
            } finally {
                isSyncingPodcastWhitelist.value = false
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
                // Keep-last-good on an EMPTY parse, like the whitelist syncs: a mirror bug serving
                // an empty body must not unhide every conditionally-blocked id (fail-open). An
                // intentional full clear of the collection should ride a versioned signal, not an
                // empty response.
                if (overrides.isEmpty()) return@onSuccess
                BlockedIdsCache.updateAll(overrides)
                context.dataStore.edit { it[BlockedContentIdsKey] = BlockedIdsCache.serialize(overrides) }
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

/**
 * If at least this many whitelisted artists lack a thumbnail, the version-gate is bypassed once so a
 * full whitelist fetch can bootstrap the server-carried thumbnails (app-update case). Well above the
 * handful of artists that legitimately have no server thumbnail, so a bootstrapped install skips again.
 */
internal const val MISSING_THUMB_BOOTSTRAP_THRESHOLD = 25

/**
 * Whether the whitelist sync may take the version-gated FAST PATH (skip the full fetch) instead of
 * fetching the whole allow-set. Pure so the gate is regression-tested. The fast path is eligible only
 * when a full fetch is genuinely unnecessary: the caller did not force a sync, the server version
 * probe succeeded and is not newer than the local version, the local table is non-empty, thumbnails
 * are not being bootstrapped ([MISSING_THUMB_BOOTSTRAP_THRESHOLD]), AND the one-time display-name
 * backfill has run. That last gate makes the v35 displayName/altName columns (added NULL by
 * MIGRATION_34_35) populate on the first sync after an update, so both-script Library search and the
 * legacy-name cleanup reach already-synced installs without a coordinated server version bump; once a
 * full fetch has run it is set and the fast path is available again.
 */
internal fun whitelistFastPathEligible(
    forceSync: Boolean,
    remoteVersion: Long?,
    localVersion: Long,
    localEmpty: Boolean,
    manyThumbsMissing: Boolean,
    displayNamesBackfilled: Boolean,
): Boolean =
    !forceSync &&
        remoteVersion != null &&
        remoteVersion <= localVersion &&
        !localEmpty &&
        !manyThumbsMissing &&
        displayNamesBackfilled

/**
 * Which (artistId, thumbnailUrl) pairs the whitelist sync writes onto EXISTING artist rows — pure so
 * the population rules are regression-tested: a null/blank server thumbnail is never written (never
 * wipes), and only rows already in the artist table are targeted (new rows carry the thumbnail via
 * their insert). The fill-only guard (never overwrite a device-resolved image) lives in the DAO query.
 */
internal fun artistThumbnailUpdates(
    entries: List<com.jtech.zemer.db.entities.ArtistWhitelistEntity>,
    existingArtistIds: Set<String>,
): List<Pair<String, String>> =
    entries.mapNotNull { entry ->
        val thumb = entry.thumbnailUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        if (entry.artistId in existingArtistIds) entry.artistId to thumb else null
    }

/**
 * Which (artistId, legacyName, displayName) renames the whitelist sync applies to EXISTING artist rows —
 * pure so the rules are regression-tested: only entries with a clean [displayName] that differs from the
 * legacy [artistName] qualify (new rows get the displayName via their insert), and only rows already in
 * the artist table are targeted. The never-clobber guard (the row's name must still equal the legacy
 * value, i.e. it was seeded from the whitelist and not since resolved from YTM) lives in the DAO query.
 */
internal fun artistDisplayNameUpdates(
    entries: List<com.jtech.zemer.db.entities.ArtistWhitelistEntity>,
    existingArtistIds: Set<String>,
): List<Triple<String, String, String>> =
    entries.mapNotNull { entry ->
        val display = entry.displayName?.takeIf { it.isNotBlank() && it != entry.artistName } ?: return@mapNotNull null
        if (entry.artistId in existingArtistIds) Triple(entry.artistId, entry.artistName, display) else null
    }
