package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    // Albums load purely through the server's `/album` endpoint (whitelist-scoped, immune to on-device
    // InnerTube bot-gating) — no InnerTube fallback (north-star: no app-runtime InnerTube; a non-corpus
    // album is non-whitelisted and shouldn't open). The opener's playlistId rides along when it threaded
    // one (a search/artist card); otherwise the server's own `album.playlistId` is used.
    private val zemerPlaylistId = savedStateHandle.get<String>("playlistId")

    val playlistId = MutableStateFlow("")
    // True once the `/album` fetch 404s / fails (or returns no tracks) and there's nothing local to show —
    // the screen renders a "not available" state instead of an endless loading shimmer.
    val notFound = MutableStateFlow(false)
    val albumWithSongs =
        database
            .albumWithSongs(albumId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            val album = database.album(albumId).first()
            runCatching { zemerRepository.album(albumId, zemerPlaylistId, zemerSearchOptions(context)) }
                .onSuccess {
                    playlistId.value = it.album.playlistId
                    notFound.value = it.songs.isEmpty()
                    database.transaction {
                        if (album == null) {
                            insert(it)
                        } else {
                            update(album.album, it, album.artists)
                        }
                    }
                }.onFailure {
                    if (it is java.util.concurrent.CancellationException) throw it
                    notFound.value = true
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
