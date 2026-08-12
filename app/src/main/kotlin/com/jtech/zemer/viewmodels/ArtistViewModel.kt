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
import com.jtech.zemer.constants.VideoDownloadsInMusicKey
import com.jtech.zemer.extensions.filterExplicit
import com.jtech.zemer.extensions.filterExplicitAlbums
import com.jtech.zemer.utils.ContentFilterState
import com.jtech.zemer.utils.OfflineModeState
import com.jtech.zemer.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
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
    // The local sections' inputs. Manual offline mode swaps the inLibrary-keyed previews (which
    // would miss pure downloads) for the downloaded-scoped queries — the offline page shows exactly
    // what this artist has on disk. Online behavior is byte-identical to before.
    private val librarySourceFlags = combine(
        context.dataStore.data.map { it[HideExplicitKey] ?: false }.distinctUntilChanged(),
        context.dataStore.data.map { it[VideoDownloadsInMusicKey] ?: true }.distinctUntilChanged(),
        OfflineModeState.state,
    ) { hideExplicit, includeVideos, offline -> Triple(hideExplicit, includeVideos, offline) }
        .distinctUntilChanged()
    val librarySongs = librarySourceFlags
        .flatMapLatest { (hideExplicit, includeVideos, offline) ->
            val source = if (offline) database.downloadedArtistSongs(artistId, includeVideos)
            else database.artistSongsPreview(artistId)
            source.map { it.filterExplicit(hideExplicit) }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val libraryAlbums = librarySourceFlags
        .flatMapLatest { (hideExplicit, includeVideos, offline) ->
            val source = if (offline) database.downloadedArtistAlbums(artistId, includeVideos)
            else database.artistAlbumsPreview(artistId)
            source.map { albums -> albums.filterExplicitAlbums(hideExplicit) }
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
        // Leaving offline mode with no loaded page: fetch the live page the screen returns to.
        reloadOnOfflineModeChange {
            if (!OfflineModeState.enabled && artistPage == null) fetchArtistsFromYTM()
        }
    }

    fun fetchArtistsFromYTM() {
        viewModelScope.launch {
            // Manual offline mode: no server fetch — the screen forces its local (downloaded-only)
            // rendering; a kept artistPage is harmless because showLocal wins while the mode is on.
            if (OfflineModeState.enabled) {
                isLoading = false
                return@launch
            }
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
        if (!isPodcastChannel || isLoadingMoreEpisodes || OfflineModeState.enabled) return
        isLoadingMoreEpisodes = true
        viewModelScope.launch {
            val options = zemerSearchOptions(context)
            runCatching {
                zemerRepository.podcastChannelEpisodes(artistId, offset, options)
            }.onSuccess { result ->
                // Only append onto the exact cursor this fetch started from: a full reload
                // (fetchArtistsFromYTM re-runs on a content-flag change and resets the page + cursor)
                // supersedes an in-flight page — applying it anyway would skip the reload's pages and
                // splice in rows fetched under stale flags (the ZemerGenreViewModel.loadMore guard).
                if (episodesNextOffset != offset || !zemerOptionsStillCurrent(options, ContentFilterState.current)) return@onSuccess
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
