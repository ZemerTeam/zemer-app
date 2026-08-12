package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.filterExplicit
import com.jtech.zemer.constants.HideExplicitKey
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.search.zemerSearchOptions
import com.jtech.zemer.tracking.PlaySource
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.getSuspend
import com.jtech.zemer.utils.filterWhitelisted
import com.jtech.zemer.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnlinePlaylistViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    private val zemerRepository: ZemerSearchRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val playlistId = requireNotNull(savedStateHandle.get<String>("playlistId")) {
        "playlistId is required but was not provided in navigation arguments"
    }

    // A Zemer-sourced playlist opens through the server's `/playlist` endpoint (already whitelist-scoped)
    // so its tracks/count/cover match the search card, instead of the InnerTube + local-whitelist path.
    // Exposed because a Zemer playlist has no `shuffleEndpoint` (the whole filtered list arrives up front),
    // so the screen gates its Shuffle button on a non-empty song list instead — matching the curated
    // "Zemer Playlists" screen — rather than on the InnerTube-only endpoint.
    val isZemer = savedStateHandle.get<Boolean>("zemer") == true

    // A discovery-sourced community playlist (home "Community playlists" row / search Community chip);
    // its plays are tagged `community:<id>` so the server can rank the community home row by real
    // engagement. Artist-owned playlists stay `playlist:<id>`. See [playSource].
    private val isCommunity = savedStateHandle.get<Boolean>("community") == true

    /** The tracking source for plays started from this screen (used by both Play and Shuffle). */
    val playSource: String =
        if (isCommunity) PlaySource.community(playlistId) else PlaySource.playlist(playlistId)

    val playlist = MutableStateFlow<PlaylistItem?>(null)
    val playlistSongs = MutableStateFlow<List<SongItem>>(emptyList())

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    val dbPlaylist = database.playlistByBrowseId(playlistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    var continuation: String? = null
        private set

    private var proactiveLoadJob: Job? = null

    init {
        fetchInitialPlaylistData()
    }

    private fun fetchInitialPlaylistData() {
        viewModelScope.launch(Dispatchers.IO) {
            // Manual offline mode: playlists are live-only - no fetch, show the error state instead
            // of an endless skeleton (library taps route to the local playlist screen offline).
            if (com.jtech.zemer.utils.OfflineModeState.enabled) {
                _error.value = context.getString(com.jtech.zemer.R.string.error_no_internet)
                _isLoading.value = false
                return@launch
            }
            _isLoading.value = true
            _error.value = null
            continuation = null
            proactiveLoadJob?.cancel() // Cancel any ongoing proactive load

            if (isZemer) {
                // Server returns the whole filtered list in one shot — no InnerTube fetch, no local
                // whitelist re-filter (that mismatch is the bug), and no pagination/proactive loading.
                runCatching { zemerRepository.playlist(playlistId, zemerSearchOptions(context)) }
                    .onSuccess { page ->
                        playlist.value = page.playlist
                        playlistSongs.value = page.songs
                        _isLoading.value = false
                    }.onFailure { throwable ->
                        _error.value = throwable.message ?: "Failed to load playlist"
                        _isLoading.value = false
                        reportException(throwable)
                    }
                return@launch
            }

            YouTube.playlist(playlistId)
                .onSuccess { playlistPage ->
                    val hideExplicit = context.dataStore.getSuspend(HideExplicitKey, false)
                    playlist.value = playlistPage.playlist
                    playlistSongs.value = playlistPage.songs
                        .distinctBy { it.id }
                        .filterExplicit(hideExplicit)
                        .filterWhitelisted(database)
                        .filterIsInstance<SongItem>()
                    continuation = playlistPage.songsContinuation
                    _isLoading.value = false
                    if (continuation != null) {
                        startProactiveBackgroundLoading()
                    }
                }.onFailure { throwable ->
                    _error.value = throwable.message ?: "Failed to load playlist"
                    _isLoading.value = false
                    reportException(throwable)
                }
        }
    }

    private fun startProactiveBackgroundLoading() {
        proactiveLoadJob?.cancel() // Cancel previous job if any
        proactiveLoadJob = viewModelScope.launch(Dispatchers.IO) {
            var currentProactiveToken = continuation
            while (currentProactiveToken != null && isActive) {
                // If a manual loadMore is happening, pause proactive loading
                if (_isLoadingMore.value) {
                    // Wait until manual load is finished, then re-evaluate
                    // This simple break and restart strategy from loadMoreSongs is preferred
                    break 
                }

                YouTube.playlistContinuation(currentProactiveToken)
                    .onSuccess { playlistContinuationPage ->
                        val hideExplicit = context.dataStore.getSuspend(HideExplicitKey, false)
                        val currentSongs = playlistSongs.value.toMutableList()
                        val filteredSongs = playlistContinuationPage.songs
                            .filterExplicit(hideExplicit)
                            .filterWhitelisted(database)
                            .filterIsInstance<SongItem>()
                        currentSongs.addAll(filteredSongs)
                        playlistSongs.value = currentSongs.distinctBy { it.id }
                        currentProactiveToken = playlistContinuationPage.continuation
                        // Update the class-level continuation for manual loadMore if needed
                        this@OnlinePlaylistViewModel.continuation = currentProactiveToken
                    }.onFailure { throwable ->
                        reportException(throwable)
                        currentProactiveToken = null // Stop proactive loading on error
                    }
            }
            // If loop finishes because currentProactiveToken is null, all songs are loaded proactively.
        }
    }

    fun loadMoreSongs() {
        if (_isLoadingMore.value) return // Already loading more (manually)
        
        val tokenForManualLoad = continuation ?: return // No more songs to load

        proactiveLoadJob?.cancel() // Cancel proactive loading to prioritize manual scroll
        _isLoadingMore.value = true

        viewModelScope.launch(Dispatchers.IO) {
            YouTube.playlistContinuation(tokenForManualLoad)
                .onSuccess { playlistContinuationPage ->
                    val hideExplicit = context.dataStore.getSuspend(HideExplicitKey, false)
                    val currentSongs = playlistSongs.value.toMutableList()
                    val filteredSongs = playlistContinuationPage.songs
                        .filterExplicit(hideExplicit)
                        .filterWhitelisted(database)
                        .filterIsInstance<SongItem>()
                    currentSongs.addAll(filteredSongs)
                    playlistSongs.value = currentSongs.distinctBy { it.id }
                    continuation = playlistContinuationPage.continuation
                }.onFailure { throwable ->
                    reportException(throwable)
                }.also {
                    _isLoadingMore.value = false
                    // Resume proactive loading if there's still a continuation
                    if (continuation != null && isActive) {
                        startProactiveBackgroundLoading()
                    }
                }
        }
    }

    fun retry() {
        proactiveLoadJob?.cancel()
        fetchInitialPlaylistData() // This will also restart proactive loading if applicable
    }

    override fun onCleared() {
        super.onCleared()
        proactiveLoadJob?.cancel()
    }
}
