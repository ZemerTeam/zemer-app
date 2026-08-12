package com.jtech.zemer.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.pages.ArtistPage
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.playback.queues.Queue
import com.jtech.zemer.playback.queues.ZemerRadioQueue
import com.jtech.zemer.search.ZemerResultMapper
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.search.zemerSearchOptions
import com.jtech.zemer.tracking.PlaySource
import com.jtech.zemer.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context
import com.jtech.zemer.constants.HideExplicitKey
import com.jtech.zemer.extensions.filterExplicit
import com.jtech.zemer.extensions.filterExplicitAlbums
import com.jtech.zemer.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ArtistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val zemerRepository: ZemerSearchRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val artistId = requireNotNull(savedStateHandle.get<String>("artistId")) {
        "artistId is required but was not provided in navigation arguments"
    }
    // Podcast HOST channels are their own animal: served whitelist-pure by the Zemer server
    // (`/podcast-channel`, mapped to an ArtistPage), NOT InnerTube. Music artists use the corpus path.
    val isPodcastChannel = savedStateHandle.get<Boolean>("isPodcastChannel") ?: false
    var artistPage by mutableStateOf<ArtistPage?>(null)
    var isLoading by mutableStateOf(true)

    // Channel-wide episodes paging (`/podcast-channel?offset=`, podcast channels only): the next page
    // cursor from the last response (null = no more pages / pre-paging server / offline snapshot) and
    // a single-flight guard so the see-all's near-end trigger can't double-append a page.
    var episodesNextOffset by mutableStateOf<Int?>(null)
        private set
    private var isLoadingMoreEpisodes = false
    val libraryArtist = database.artist(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    // The bare artist row (no whitelist join) - the subscribe/bookmark state, which must work for
    // podcast host channels too (they are never whitelisted, so libraryArtist above is always null).
    val libraryArtistEntity = database.artistEntity(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val librarySongs = context.dataStore.data
        .map { it[HideExplicitKey] ?: false }
        .distinctUntilChanged()
        .flatMapLatest { hideExplicit ->
            database.artistSongsPreview(artistId).map { it.filterExplicit(hideExplicit) }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val libraryAlbums = context.dataStore.data
        .map { it[HideExplicitKey] ?: false }
        .distinctUntilChanged()
        .flatMapLatest { hideExplicit ->
            database.artistAlbumsPreview(artistId).map { albums ->
                timber.log.Timber.d("ArtistViewModel: artistId=$artistId, albums from query=${albums.size}, hideExplicit=$hideExplicit")
                albums.forEach { album ->
                    timber.log.Timber.d("ArtistViewModel: album=${album.album.title}, explicit=${album.album.explicit}")
                }
                albums.filterExplicitAlbums(hideExplicit)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        // Load artist page and reload when hide explicit setting changes
        viewModelScope.launch {
            context.dataStore.data
                .map { it[HideExplicitKey] ?: false }
                .distinctUntilChanged()
                .collect {
                    fetchArtistsFromYTM()
                }
        }
    }

    fun fetchArtistsFromYTM() {
        viewModelScope.launch {
            isLoading = true
            // Music artists: served purely from the Zemer `/artist` corpus (whitelist-pure, already
            // content-filtered, InnerTube-free). A 404 / failure leaves artistPage null — the screen
            // then shows the local library content (showLocal) or nothing. No InnerTube fallback by
            // design: the north-star is zero app-runtime InnerTube, and a non-corpus artist is
            // non-whitelisted (shouldn't render).
            // Podcast host channels are the exception: they are not in the corpus, so — like the whole
            artistPage = runCatching {
                if (isPodcastChannel) {
                    // Host channels are now served whitelist-pure by the Zemer server (`/podcast-channel`,
                    // mapped to an ArtistPage), not InnerTube `YouTube.artist`. A 404/null leaves the page
                    // empty → the channel's not-available state, same as a corpus artist. The response also
                    // carries the episodes paging cursor for the see-all screen.
                    zemerRepository.podcastChannel(artistId, zemerSearchOptions(context))
                        ?.also { episodesNextOffset = it.episodesNextOffset }
                        ?.artistPage
                } else {
                    zemerRepository.artist(artistId, zemerSearchOptions(context))
                }
            }
                .onFailure {
                    if (it is java.util.concurrent.CancellationException) throw it
                    reportException(it)
                }
                .getOrNull()
            isLoading = false
        }
    }

    /**
     * Appends the next page of the channel-wide episode list to the Episodes section (podcast
     * channels only; the see-all screen's near-end trigger). Single-flight; a fetch failure leaves
     * [episodesNextOffset] unchanged so the next trigger simply retries. The cursor advances only
     * on success, and a null/pre-paging response ends the paging.
     */
    fun loadMoreEpisodes() {
        val offset = episodesNextOffset ?: return
        if (!isPodcastChannel || isLoadingMoreEpisodes) return
        isLoadingMoreEpisodes = true
        viewModelScope.launch {
            runCatching {
                zemerRepository.podcastChannelEpisodes(artistId, offset, zemerSearchOptions(context))
            }.onSuccess { result ->
                if (result == null) {
                    episodesNextOffset = null
                } else {
                    val (episodes, next) = result
                    artistPage = artistPage?.let { appendChannelEpisodes(it, episodes) }
                    episodesNextOffset = next
                }
            }.onFailure {
                if (it is java.util.concurrent.CancellationException) throw it
                // Unreachable server mid-scroll: keep the cursor so a later near-end trigger retries.
            }
            isLoadingMoreEpisodes = false
        }
    }

    /** A corpus-native artist-seeded radio queue for the Radio button (Zemer `/radio`, no InnerTube). */
    fun radioQueue(): Queue =
        ZemerRadioQueue(
            kind = "artist",
            seed = artistId,
            context = context,
            playSource = PlaySource.artist(artistId),
        )
}

/**
 * The [page] with [more] appended to its Episodes section, de-duplicated by id (a serve-time
 * female/blocked drop can shift the server's DB-offset pages, so overlap is possible). Every other
 * section is untouched; a page without an Episodes section gains one only if [more] is non-empty.
 * Pure + top-level so the append/dedup rule is plain-JVM tested (ArtistChannelEpisodesTest).
 */
internal fun appendChannelEpisodes(
    page: ArtistPage,
    more: List<com.metrolist.innertube.models.EpisodeItem>,
): ArtistPage {
    if (more.isEmpty()) return page
    val sections = page.sections.toMutableList()
    val idx = sections.indexOfFirst { it.title == ZemerResultMapper.TITLE_EPISODES }
    if (idx < 0) {
        sections.add(com.metrolist.innertube.pages.ArtistSection(ZemerResultMapper.TITLE_EPISODES, more, null))
    } else {
        val existing = sections[idx]
        val seen = existing.items.mapTo(HashSet()) { it.id }
        val appended = existing.items + more.filter { seen.add(it.id) }
        sections[idx] = com.metrolist.innertube.pages.ArtistSection(existing.title, appended, existing.moreEndpoint)
    }
    return page.copy(sections = sections)
}
