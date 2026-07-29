package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.search.ZemerSearchOptions
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

/**
 * Pure stale-row rule (unit-tested): a 404'd album's local copy is deleted only when the album is
 * gone for EVERYONE — i.e. the user's flags were already wide open (that 404 is unconditional), or
 * an open-flags re-probe also 404'd. [openFlags404] null = the probe itself failed (network) —
 * keep the row; a flag-hidden album must never be destroyed by its own filter.
 */
internal fun staleAlbumGoneForEveryone(flagsRestrictive: Boolean, openFlags404: Boolean?): Boolean =
    !flagsRestrictive || openFlags404 == true

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
            val options = zemerSearchOptions(context)
            runCatching { zemerRepository.album(albumId, zemerPlaylistId, options) }
                .onSuccess { page ->
                    if (page == null) {
                        // 404 — but the server also 404s an album that is merely FULLY BLOCKED under
                        // this user's content flags. Deleting the local row for a flag-hide would
                        // destroy library data the user gets back by flipping the filter, so the
                        // stale-row cleanup runs only when the album 404s with the filters wide open
                        // (an unfiltered request needs no probe). A failed probe keeps the row.
                        notFound.value = true
                        val flagsRestrictive = !options.allowFemale || options.blockVideos
                        val openFlags404 = if (!flagsRestrictive) true else runCatching {
                            zemerRepository.album(
                                albumId,
                                zemerPlaylistId,
                                ZemerSearchOptions(allowFemale = true, blockVideos = false, hideExplicit = false),
                            ) == null
                        }.getOrNull()
                        if (staleAlbumGoneForEveryone(flagsRestrictive, openFlags404)) {
                            database.query {
                                album?.album?.let(::delete)
                            }
                        }
                        return@onSuccess
                    }
                    playlistId.value = page.album.playlistId
                    notFound.value = page.songs.isEmpty()
                    database.transaction {
                        if (album == null) {
                            insert(page)
                        } else {
                            update(album.album, page, album.artists)
                        }
                    }
                }.onFailure {
                    if (it is java.util.concurrent.CancellationException) throw it
                    // Transient failure (network / server): show not-found but keep the local copy —
                    // only a definitive 404 above deletes it.
                    notFound.value = true
                    reportException(it)
                }
        }
    }
}
