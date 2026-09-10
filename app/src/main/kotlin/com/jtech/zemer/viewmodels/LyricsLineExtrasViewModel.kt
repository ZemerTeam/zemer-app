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
    private data class Bound(val videoId: String, val language: LineExtrasLanguage, val lines: List<String>)

    private val bound = MutableStateFlow<Bound?>(null)

    val extras: StateFlow<LineExtras?> = bound
        .flatMapLatest { b -> if (b == null || b.language == LineExtrasLanguage.OFF) flowOf(null) else extrasStore.flow(b.videoId, b.language, b.lines) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Follow the pane: [lines] are the lyric lines exactly as displayed (the aligned reply pairs to them). */
    fun bind(mediaMetadata: MediaMetadata, language: LineExtrasLanguage, lines: List<String>) {
        bound.value = Bound(mediaMetadata.id, language, lines)
        if (language == LineExtrasLanguage.OFF || lines.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            lyricsStore.ensureResolveExtras(mediaMetadata)
            lyricsStore.ensureExtras(mediaMetadata, language, lines)
        }
    }
}
