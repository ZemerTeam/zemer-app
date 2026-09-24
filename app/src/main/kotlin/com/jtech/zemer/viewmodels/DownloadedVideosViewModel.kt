package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.constants.SongSortDescendingKey
import com.jtech.zemer.constants.SongSortType
import com.jtech.zemer.constants.SongSortTypeKey
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.extensions.toEnum
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
class DownloadedVideosViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val database: MusicDatabase,
) : ViewModel() {

    val downloadedVideos =
        context.dataStore.data
            .map {
                it[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE) to (it[SongSortDescendingKey] ?: true)
            }
            .distinctUntilChanged()
            .flatMapLatest { (sortType, descending) ->
                database.downloadedVideosSorted(sortType, descending)
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
