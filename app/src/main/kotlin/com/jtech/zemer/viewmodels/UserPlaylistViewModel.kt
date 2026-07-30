package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.search.ZemerUserPlaylistPage
import com.jtech.zemer.search.zemerSearchOptions
import com.jtech.zemer.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A shared user playlist opened from its unguessable link (issue #176; deep link
 * `search.zemer.io/user_playlist/<id>`). One fetch per open — the snapshot is immutable, so there
 * is nothing to refresh; the receiver's content filters run server-side per request plus the local
 * blocked-ids pass in the mapper. Null page + [notFound] = 404 (mistyped or taken-down link).
 */
@HiltViewModel
class UserPlaylistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ZemerSearchRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val shareId = requireNotNull(savedStateHandle.get<String>("shareId")) {
        "shareId is required but was not provided in navigation arguments"
    }

    val page = MutableStateFlow<ZemerUserPlaylistPage?>(null)
    val isLoading = MutableStateFlow(true)
    val notFound = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            runCatching { repository.userPlaylist(shareId, zemerSearchOptions(context)) }
                .onSuccess { fetched ->
                    page.value = fetched
                    notFound.value = fetched == null
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    notFound.value = true
                    reportException(it)
                }
            isLoading.value = false
        }
    }
}
