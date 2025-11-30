package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import com.jtech.zemer.constants.HideExplicitKey
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.extensions.filterExplicit
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.get
import dagger.hilt.android.lifecycle.HiltViewModel
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
                val hideExplicit = appContext.dataStore.get(HideExplicitKey, false)
                list.filterExplicit(hideExplicit)
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun refresh() {
        viewModelScope.launch {
            // placeholder for future sync logic
        }
    }
}
