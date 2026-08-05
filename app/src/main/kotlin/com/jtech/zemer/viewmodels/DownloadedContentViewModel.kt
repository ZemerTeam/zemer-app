package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.constants.VideoDownloadsInMusicKey
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.statuses.StatusDownloadManager
import com.jtech.zemer.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DownloadedContentViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    statusDownloadManager: StatusDownloadManager,
) : ViewModel() {

    // Mirrors the pref the Music list itself honors, so the tile count always matches the opened
    // list (video-songs count in Music too while VideoDownloadsInMusicKey is on).
    val downloadedMusicCount = context.dataStore.data
        .map { it[VideoDownloadsInMusicKey] ?: true }
        .distinctUntilChanged()
        .flatMapLatest { database.downloadedSongsByCreateDateAsc(includeVideos = it) }
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val downloadedVideoCount = database.downloadedVideos()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val downloadedStatusCount = statusDownloadManager.downloads
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)
}
