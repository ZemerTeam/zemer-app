package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.search.zemerSearchOptions
import com.jtech.zemer.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    database: MusicDatabase,
    private val zemerRepository: ZemerSearchRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val albumId = requireNotNull(savedStateHandle.get<String>("albumId")) {
        "albumId is required but was not provided in navigation arguments"
    }

    // A Zemer-search album loads through the server's `/album` endpoint (already whitelist-scoped,
    // fetched by a server immune to on-device InnerTube bot-gating) instead of `YouTube.album()`.
    // Both paths yield the same AlbumPage, so everything downstream is shared. The search card's
    // playlistId rides along because the server's album header doesn't return one.
    private val isZemer = savedStateHandle.get<Boolean>("zemer") == true
    private val zemerPlaylistId = savedStateHandle.get<String>("playlistId")

    val playlistId = MutableStateFlow("")
    val albumWithSongs =
        database
            .albumWithSongs(albumId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            val album = database.album(albumId).first()
            val result =
                if (isZemer) {
                    runCatching { zemerRepository.album(albumId, zemerPlaylistId, zemerSearchOptions(context)) }
                } else {
                    YouTube.album(albumId)
                }
            result
                .onSuccess {
                    playlistId.value = it.album.playlistId
                    database.transaction {
                        if (album == null) {
                            insert(it)
                        } else {
                            update(album.album, it, album.artists)
                        }
                    }
                }.onFailure {
                    reportException(it)
                    if (it.message?.contains("NOT_FOUND") == true) {
                        database.query {
                            album?.album?.let(::delete)
                        }
                    }
                }
        }
    }
}
