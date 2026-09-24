package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.lyrics.LyricsStore
import com.jtech.zemer.lyrics.zemer.LyricsFeedback
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.playback.relay.RelayDeviceId
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LyricsMenuViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    private val lyricsStore: LyricsStore,
) : ViewModel() {
    /** Report / submit ride viewModelScope, which outlives the dismissed menu sheet (see [LyricsFeedback]). */
    val feedback = LyricsFeedback(viewModelScope, deviceId = { RelayDeviceId.get(context) })

    /** Refetch drops the cached row and stores a fresh chain answer; the pane reloads meanwhile (see LyricsStore.refetch). */
    fun refetchLyrics(mediaMetadata: MediaMetadata) {
        viewModelScope.launch(Dispatchers.IO) { lyricsStore.refetch(mediaMetadata) }
    }
}
