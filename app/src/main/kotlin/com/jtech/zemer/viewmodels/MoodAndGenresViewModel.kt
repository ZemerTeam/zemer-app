package com.jtech.zemer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.pages.MoodAndGenres
import com.jtech.zemer.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoodAndGenresViewModel
@Inject
constructor() : ViewModel() {
    val moodAndGenres = MutableStateFlow<List<MoodAndGenres>?>(null)
    val isLoading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    private suspend fun loadInternal() {
        isLoading.value = true
        error.value = null
        YouTube
            .moodAndGenres()
            .onSuccess {
                moodAndGenres.value = it
            }.onFailure {
                error.value = it.message ?: "Failed to load moods & genres"
                reportException(it)
            }
        isLoading.value = false
    }

    fun refresh() {
        viewModelScope.launch {
            loadInternal()
        }
    }

    init {
        refresh()
    }
}
