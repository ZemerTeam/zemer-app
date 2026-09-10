package com.jtech.zemer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.lyrics.LineExtras
import com.jtech.zemer.lyrics.LineExtrasLanguage
import com.jtech.zemer.lyrics.LineExtrasStore
import com.jtech.zemer.lyrics.LyricsStore
import com.jtech.zemer.models.MediaMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The per-line extras (translation / romanization) for the song the lyrics pane shows. Shared by the pane
 * (renders them under each line) and the lyrics screen header (the machine-translation label) through one
 * Hilt instance. [bind] follows the playing song; the resolver is asked only once a language is picked and
 * the song has a lyrics body, so extras cost nothing until a user turns them on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LyricsLineExtrasViewModel @Inject constructor(
    private val lyricsStore: LyricsStore,
    private val extrasStore: LineExtrasStore,
) : ViewModel() {
    private val videoId = MutableStateFlow<String?>(null)

    val extras: StateFlow<LineExtras?> = videoId
        .flatMapLatest { id -> if (id == null) flowOf(null) else extrasStore.flow(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun bind(mediaMetadata: MediaMetadata, language: LineExtrasLanguage, hasLyrics: Boolean) {
        videoId.value = mediaMetadata.id
        if (language == LineExtrasLanguage.OFF || !hasLyrics) return
        viewModelScope.launch(Dispatchers.IO) { lyricsStore.ensureExtras(mediaMetadata) }
    }
}
