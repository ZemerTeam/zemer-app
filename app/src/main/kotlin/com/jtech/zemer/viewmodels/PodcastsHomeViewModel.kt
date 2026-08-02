package com.jtech.zemer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.PodcastWhitelistEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The Home "Podcasts" row's own ViewModel (the isolated, fail-soft home-row pattern): the whitelisted
 * podcasts straight from the local DB (populated by [com.jtech.zemer.utils.SyncUtils.syncPodcastWhitelist]).
 * A DB error just yields an empty list, so the row hides and Home is never affected. No fetch to trigger -
 * the flow is live.
 */
@HiltViewModel
class PodcastsHomeViewModel @Inject constructor(
    database: MusicDatabase,
) : ViewModel() {
    val podcasts: StateFlow<List<PodcastWhitelistEntity>> =
        database.allWhitelistedPodcastsByName()
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
