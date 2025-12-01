package com.jtech.zemer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.repositories.CachedSongsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class CachePlaylistViewModel @Inject constructor(
    private val cachedSongsRepository: CachedSongsRepository,
) : ViewModel() {

    val cachedSongs =
        cachedSongsRepository.cachedSongs.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    fun removeSongFromCache(songId: String) {
        cachedSongsRepository.removeSongFromCache(songId)
    }
}
