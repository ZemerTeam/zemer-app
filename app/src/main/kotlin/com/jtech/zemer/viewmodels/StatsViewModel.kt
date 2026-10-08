package com.jtech.zemer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.constants.MyTopFilter
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.stats.ListeningStats
import com.jtech.zemer.stats.buildListeningStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Local listening stats over a rolling [MyTopFilter] window, read from the play-event table.
 * Ranked and whitelist-scoped in SQL, totalled by [buildListeningStats]. Local Room only, no network.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    database: MusicDatabase,
) : ViewModel() {
    val period = MutableStateFlow(MyTopFilter.MONTH)

    val stats: StateFlow<ListeningStats?> = period
        .flatMapLatest { period ->
            // Re-derive the window start every minute so a rolling period slides while the screen stays
            // open; ALL_TIME's constant start re-queries nothing.
            flow<Long> {
                while (true) {
                    emit(period.toTimeMillis())
                    delay(WINDOW_REFRESH_MS)
                }
            }.distinctUntilChanged()
        }.flatMapLatest { from: Long ->
            combine(database.songPlayStats(from), database.artistPlayStats(from), ::buildListeningStats)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private companion object {
        const val WINDOW_REFRESH_MS = 60_000L
    }
}
