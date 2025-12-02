package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.filterExplicit
import com.jtech.zemer.constants.HideExplicitKey
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.getSuspend
import com.jtech.zemer.utils.filterWhitelisted
import com.jtech.zemer.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NewReleaseViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
) : ViewModel() {
    private val _newReleaseAlbums = MutableStateFlow<List<AlbumItem>>(emptyList())
    val newReleaseAlbums = _newReleaseAlbums.asStateFlow()
    private val _newReleaseSongs = MutableStateFlow<List<SongItem>>(emptyList())
    val newReleaseSongs = _newReleaseSongs.asStateFlow()
    val isLoading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    private suspend fun loadInternal() {
        isLoading.value = true
        error.value = null
        val hideExplicit = context.dataStore.getSuspend(HideExplicitKey, false)
        runCatching {
            YouTube.browse(browseId = "FEmusic_new_releases", params = null).getOrNull()
        }.onSuccess { browseResult ->
            if (browseResult != null) {
                val filtered = browseResult.filterExplicit(hideExplicit)
                val allItems = filtered.items.flatMap { it.items }.filterWhitelisted(database)
                _newReleaseAlbums.value = allItems.filterIsInstance<AlbumItem>()
                _newReleaseSongs.value = allItems.filterIsInstance<SongItem>()
            } else {
                _newReleaseAlbums.value = emptyList()
                _newReleaseSongs.value = emptyList()
            }
        }.onFailure {
            error.value = it.message ?: "Failed to load new releases"
            reportException(it)
        }

        // Fallback to albums endpoint if mixed feed is empty
        if (_newReleaseAlbums.value.isEmpty()) {
            YouTube
                .newReleaseAlbums()
                .onSuccess { albums ->
                    val artists: MutableMap<Int, String> = mutableMapOf()
                    val favouriteArtists: MutableMap<Int, String> = mutableMapOf()
                    database.allArtistsByPlayTime().first().let { list ->
                        var favIndex = 0
                        for ((artistsIndex, artist) in list.withIndex()) {
                            artists[artistsIndex] = artist.id
                            if (artist.artist.bookmarkedAt != null) {
                                favouriteArtists[favIndex] = artist.id
                                favIndex++
                            }
                        }
                    }
                    _newReleaseAlbums.value =
                        albums
                            .sortedBy { album ->
                                val artistIds = album.artists.orEmpty().mapNotNull { it.id }
                                val firstArtistKey =
                                    artistIds.firstNotNullOfOrNull { artistId ->
                                        if (artistId in favouriteArtists.values) {
                                            favouriteArtists.entries.firstOrNull { it.value == artistId }?.key
                                        } else {
                                            artists.entries.firstOrNull { it.value == artistId }?.key
                                        }
                                    } ?: Int.MAX_VALUE
                                firstArtistKey
                            }
                            .filterExplicit(hideExplicit)
                            .filterWhitelisted(database)
                            .filterIsInstance<AlbumItem>()
                }.onFailure {
                    error.value = it.message ?: "Failed to load new releases"
                    reportException(it)
                }
        }
        isLoading.value = false
    }

    fun refresh() {
        viewModelScope.launch {
            loadInternal()
        }
    }

    init {
        refresh()
    }
}
