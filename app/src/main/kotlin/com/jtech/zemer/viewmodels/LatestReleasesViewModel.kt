package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.latestreleases.LatestRelease
import com.jtech.zemer.latestreleases.LatestReleasesStore
import com.jtech.zemer.latestreleases.toAlbumItem
import com.jtech.zemer.utils.filterWhitelisted
import com.metrolist.innertube.models.AlbumItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Backs the "Latest Releases" Home section and its full screen, deliberately separate from
 * [HomeViewModel] so a failure fetching the external feed can never affect the rest of Home.
 *
 * On creation it shows the disk-cached releases immediately (if any) and then refreshes once; both
 * passes run the releases through the app's existing [filterWhitelisted] so the user's content
 * preferences (female / KidZone / Israeli, by artist id) apply exactly as everywhere else. The list
 * is newest-first, as the server produced it.
 */
@HiltViewModel
class LatestReleasesViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val database: MusicDatabase,
) : ViewModel() {
    private val _releases = MutableStateFlow<List<LatestRelease>>(emptyList())
    val releases: StateFlow<List<LatestRelease>> = _releases.asStateFlow()

    init {
        LatestReleasesStore.initialize(context)
        viewModelScope.launch(Dispatchers.IO) {
            val cached = LatestReleasesStore.cachedReleases()
            if (cached.isNotEmpty()) {
                val shown = filterReleases(cached)
                _releases.value = shown
                Timber.tag(TAG).d("Showing ${shown.size} cached releases (of ${cached.size} before whitelist filter)")
            }
            val fresh = LatestReleasesStore.refresh()
            val shown = filterReleases(fresh)
            _releases.value = shown
            Timber.tag(TAG).d("After refresh: ${shown.size} releases shown (of ${fresh.size} before whitelist filter)")
        }
    }

    /**
     * Keeps only releases whose artist passes the whitelist filter, preserving the feed order.
     * De-duplicates by [LatestRelease.browseId] first: the feed is external and may list one album
     * under more than one whitelisted artist, and browseId is the list key on both surfaces — a
     * duplicate would otherwise crash the Compose lists with a "key already used" error.
     */
    private suspend fun filterReleases(releases: List<LatestRelease>): List<LatestRelease> {
        if (releases.isEmpty()) return emptyList()
        val unique = releases.distinctBy { it.browseId }
        val byBrowseId = unique.associateBy { it.browseId }
        return unique.map { it.toAlbumItem() }
            .filterWhitelisted(database)
            .mapNotNull { byBrowseId[(it as? AlbumItem)?.browseId] }
    }

    private companion object {
        const val TAG = "Zemer_LatestReleases"
    }
}
