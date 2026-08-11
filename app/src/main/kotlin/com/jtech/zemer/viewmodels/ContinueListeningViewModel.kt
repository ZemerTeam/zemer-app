package com.jtech.zemer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.playback.EpisodeResume
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The Home "Continue Listening" row's own ViewModel (the isolated, fail-soft home-row pattern):
 * in-progress podcast episodes, most-recently-played first (via the play `event` table). A FINISHED
 * episode is filtered out here (it would restart from 0, not resume). A DB error yields an empty list,
 * so the row simply hides and Home is never affected.
 */
@HiltViewModel
class ContinueListeningViewModel @Inject constructor(
    database: MusicDatabase,
) : ViewModel() {
    val episodes: StateFlow<List<Song>> =
        database.continueListeningEpisodes()
            .map { list ->
                list.filter {
                    EpisodeResume.shouldResume(
                        it.song.lastPositionMs,
                        it.song.duration.takeIf { d -> d > 0 }?.times(1000L),
                    )
                }
            }
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
