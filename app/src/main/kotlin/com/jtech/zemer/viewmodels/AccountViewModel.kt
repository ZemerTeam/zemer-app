package com.jtech.zemer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.utils.completed
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.utils.filterWhitelisted
import com.jtech.zemer.utils.reportException
import com.jtech.zemer.ui.utils.resize
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AccountContentType {
    PLAYLISTS, ALBUMS, ARTISTS
}

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val database: MusicDatabase,
) : ViewModel() {
    val playlists = MutableStateFlow<List<PlaylistItem>?>(null)
    val albums = MutableStateFlow<List<AlbumItem>?>(null)
    val artists = MutableStateFlow<List<ArtistItem>?>(null)
    
    // Selected content type for chips
    val selectedContentType = MutableStateFlow(AccountContentType.PLAYLISTS)

    init {
        viewModelScope.launch {
            runCatching {
                val likedPlaylists = YouTube.library("FEmusic_liked_playlists").completed().getOrThrow()
                likedPlaylists.items
                    .filterIsInstance<PlaylistItem>()
                    .filterNot { it -> it.id == "SE" }
                    .filterWhitelisted(database)
                    .filterIsInstance<PlaylistItem>()
            }.onSuccess {
                playlists.value = it
            }.onFailure {
                reportException(it)
            }
            runCatching {
                val likedAlbums = YouTube.library("FEmusic_liked_albums").completed().getOrThrow()
                likedAlbums.items
                    .filterIsInstance<AlbumItem>()
                    .filterWhitelisted(database)
                    .filterIsInstance<AlbumItem>()
            }.onSuccess {
                albums.value = it
            }.onFailure {
                reportException(it)
            }
            runCatching {
                val libraryArtists = YouTube.library("FEmusic_library_corpus_artists").completed().getOrThrow()
                libraryArtists.items
                    .filterIsInstance<ArtistItem>()
                    .filterWhitelisted(database)
                    .filterIsInstance<ArtistItem>()
                    .map { artist ->
                        artist.copy(
                            thumbnail = artist.thumbnail?.resize(544, 544)
                        )
                    }
            }.onSuccess {
                artists.value = it
            }.onFailure {
                reportException(it)
            }
        }
    }
    
    fun setSelectedContentType(contentType: AccountContentType) {
        selectedContentType.value = contentType
    }
}
