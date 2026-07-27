package com.jtech.zemer.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.pages.ArtistPage
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.search.zemerSearchOptions
import com.jtech.zemer.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context
import com.jtech.zemer.constants.HideExplicitKey
import com.jtech.zemer.extensions.filterExplicit
import com.jtech.zemer.extensions.filterExplicitAlbums
import com.jtech.zemer.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ArtistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val zemerRepository: ZemerSearchRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val artistId = requireNotNull(savedStateHandle.get<String>("artistId")) {
        "artistId is required but was not provided in navigation arguments"
    }
    var artistPage by mutableStateOf<ArtistPage?>(null)
    var isLoading by mutableStateOf(true)
    val libraryArtist = database.artist(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val librarySongs = context.dataStore.data
        .map { it[HideExplicitKey] ?: false }
        .distinctUntilChanged()
        .flatMapLatest { hideExplicit ->
            database.artistSongsPreview(artistId).map { it.filterExplicit(hideExplicit) }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val libraryAlbums = context.dataStore.data
        .map { it[HideExplicitKey] ?: false }
        .distinctUntilChanged()
        .flatMapLatest { hideExplicit ->
            database.artistAlbumsPreview(artistId).map { albums ->
                timber.log.Timber.d("ArtistViewModel: artistId=$artistId, albums from query=${albums.size}, hideExplicit=$hideExplicit")
                albums.forEach { album ->
                    timber.log.Timber.d("ArtistViewModel: album=${album.album.title}, explicit=${album.album.explicit}")
                }
                albums.filterExplicitAlbums(hideExplicit)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        // Load artist page and reload when hide explicit setting changes
        viewModelScope.launch {
            context.dataStore.data
                .map { it[HideExplicitKey] ?: false }
                .distinctUntilChanged()
                .collect {
                    fetchArtistsFromYTM()
                }
        }
    }

    fun fetchArtistsFromYTM() {
        viewModelScope.launch {
            isLoading = true
            // Served purely from the Zemer `/artist` corpus (whitelist-pure, already content-filtered,
            // InnerTube-free). A 404 / failure leaves artistPage null — the screen then shows the local
            // library content (showLocal) or nothing. No InnerTube fallback by design: the north-star is
            // zero app-runtime InnerTube, and a non-corpus artist is non-whitelisted (shouldn't render).
            artistPage = runCatching { zemerRepository.artist(artistId, zemerSearchOptions(context)) }
                .onFailure {
                    if (it is java.util.concurrent.CancellationException) throw it
                    reportException(it)
                }
                .getOrNull()
            isLoading = false
        }
    }
}
