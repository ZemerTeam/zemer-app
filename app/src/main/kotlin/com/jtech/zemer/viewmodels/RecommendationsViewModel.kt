package com.jtech.zemer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject

data class RecommendationsState(
    val recommendedSongs: List<Song> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class RecommendationsViewModel @Inject constructor(
    private val database: MusicDatabase,
) : ViewModel() {

    private val _state = MutableStateFlow(RecommendationsState())
    val state: StateFlow<RecommendationsState> = _state.asStateFlow()

    init {
        loadRecommendations()
    }

    fun refresh() {
        _state.value = _state.value.copy(loading = true)
        loadRecommendations()
    }

    private fun loadRecommendations() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val now = LocalDateTime.now()
                val nowEpoch = now.toEpochSecond(ZoneOffset.UTC) * 1000L
                val oneMonthAgo = now.minusMonths(1)
                val oneMonthAgoEpoch = oneMonthAgo.toEpochSecond(ZoneOffset.UTC) * 1000L

                val quickPickSongs = database.quickPicks(now = nowEpoch).firstOrNull().orEmpty()

                val topPlayedSongs = database.mostPlayedSongs(
                    fromTimeStamp = oneMonthAgoEpoch,
                    limit = 30,
                    offset = 0,
                ).firstOrNull().orEmpty()

                val seenIds = mutableSetOf<String>()
                val recommended = mutableListOf<Song>()

                for (song in quickPickSongs) {
                    if (seenIds.add(song.song.id)) recommended.add(song)
                }
                for (song in topPlayedSongs) {
                    if (seenIds.add(song.song.id)) recommended.add(song)
                }

                _state.value = RecommendationsState(
                    recommendedSongs = recommended.take(50),
                    loading = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Failed to load recommendations",
                )
            }
        }
    }
}
