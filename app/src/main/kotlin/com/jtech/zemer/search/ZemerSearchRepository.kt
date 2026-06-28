package com.jtech.zemer.search

import android.content.Context
import com.jtech.zemer.R
import com.metrolist.innertube.YouTube.SearchFilter
import com.metrolist.innertube.models.SearchSuggestions
import com.metrolist.innertube.pages.SearchResult
import com.metrolist.innertube.pages.SearchSummaryPage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Entry point for Zemer-engine search. It returns the same `YTItem`/page types the YouTube path does,
 * so the ViewModels can swap providers with a one-line branch and the UI is reused verbatim.
 *
 * Today every query goes to [ZemerSearchClient] (search.zemer.io). [fetch] is the single seam where
 * Phase 2 will add the on-device subset fallback (download + cache + pure-Kotlin matcher) without
 * touching callers — keeping the APK size unchanged.
 */
@Singleton
class ZemerSearchRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: ZemerSearchClient,
) {
    suspend fun summary(query: String, options: ZemerSearchOptions): SearchSummaryPage =
        ZemerResultMapper.summaryPage(fetch(query, options, K_SUMMARY), sectionTitles(), options.hideExplicit)

    suspend fun filtered(query: String, filter: SearchFilter, options: ZemerSearchOptions): SearchResult =
        ZemerResultMapper.filtered(fetch(query, options, K_FILTER), filter, options.hideExplicit)

    suspend fun suggestions(query: String, options: ZemerSearchOptions): SearchSuggestions =
        ZemerResultMapper.suggestions(fetch(query, options, K_SUGGEST), options.hideExplicit)

    private suspend fun fetch(query: String, options: ZemerSearchOptions, k: Int): ZemerSearchResponse =
        client.search(query.trim(), options.allowFemale, options.blockVideos, k)

    private fun sectionTitles() = SectionTitles(
        songs = context.getString(R.string.filter_songs),
        videos = context.getString(R.string.filter_videos),
        albums = context.getString(R.string.filter_albums),
        artists = context.getString(R.string.filter_artists),
        playlists = context.getString(R.string.filter_community_playlists),
    )

    companion object {
        private const val K_SUMMARY = 8
        private const val K_FILTER = 100
        private const val K_SUGGEST = 8
    }
}
