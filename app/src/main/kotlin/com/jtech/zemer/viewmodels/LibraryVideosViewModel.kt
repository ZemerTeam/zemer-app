package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.constants.HideExplicitKey
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.extensions.filterExplicit
import com.jtech.zemer.utils.ContentFilterState
import com.jtech.zemer.utils.WhitelistCache
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.getSuspend
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryVideosViewModel @Inject constructor(
    private val database: MusicDatabase,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    val videos =
        database
            .videos()
            .map { list ->
                val hideExplicit = appContext.dataStore.getSuspend(HideExplicitKey, false)
                val filters = ContentFilterState.state.value
                val allowed = WhitelistCache.allowedEntries(database, filters).map { it.artistId }.toSet()
                list
                    .filterExplicit(hideExplicit)
                    .filter { video ->
                        val artistIds = video.artists.map { artist -> artist.id }
                        allowed.isEmpty() || artistIds.any { id -> id in allowed }
                    }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun refresh() {
        viewModelScope.launch {
            // placeholder for future sync logic
        }
    }
}
