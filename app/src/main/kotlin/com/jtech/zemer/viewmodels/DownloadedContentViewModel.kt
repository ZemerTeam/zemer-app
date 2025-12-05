package com.jtech.zemer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.db.MusicDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DownloadedContentViewModel @Inject constructor(
    database: MusicDatabase,
) : ViewModel() {

    val downloadedMusicCount = database.downloadedSongsByCreateDateAsc()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val downloadedVideoCount = database.downloadedVideos()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)
}
