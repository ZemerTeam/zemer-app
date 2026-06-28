package com.jtech.zemer.search

import com.metrolist.innertube.YouTube.SearchFilter
import com.metrolist.innertube.models.SearchSuggestions
import com.metrolist.innertube.pages.SearchResult
import com.metrolist.innertube.pages.SearchSummaryPage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Entry point for Zemer-engine search. It returns the same `YTItem`/page types the YouTube path does,
 * so the ViewModels can swap providers with a one-line branch and the UI is reused verbatim.
 *
 * Every query goes to [ZemerSearchClient] (search.zemer.io); there is no on-device fallback by design.
 * If the service is unreachable the call throws, the ViewModel shows the search-error state, and the
 * user can flip the toggle to YouTube Music search.
 *
 * Responses are memoized in a small LRU keyed by (k, filters, query): the five filter chips all request
 * the same k, so after the first they hit the cache instead of re-fetching the full payload five times;
 * the summary and as-you-type share the k=8 entry too.
 */
@Singleton
class ZemerSearchRepository @Inject constructor(
    private val client: ZemerSearchClient,
) {
    suspend fun summary(query: String, options: ZemerSearchOptions): SearchSummaryPage =
        ZemerResultMapper.summaryPage(fetch(query, options, K_SUMMARY), options.hideExplicit)

    suspend fun filtered(query: String, filter: SearchFilter, options: ZemerSearchOptions): SearchResult =
        ZemerResultMapper.filtered(fetch(query, options, K_FILTER), filter, options.hideExplicit)

    suspend fun suggestions(query: String, options: ZemerSearchOptions): SearchSuggestions =
        ZemerResultMapper.suggestions(fetch(query, options, K_SUGGEST), options.hideExplicit)

    private val cacheMutex = Mutex()
    private val cache = object : LinkedHashMap<String, ZemerSearchResponse>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ZemerSearchResponse>) = size > CACHE_SIZE
    }

    private suspend fun fetch(query: String, options: ZemerSearchOptions, k: Int): ZemerSearchResponse {
        val trimmed = query.trim()
        val key = "$k|${options.allowFemale}|${options.blockVideos}|$trimmed"
        cacheMutex.withLock { cache[key] }?.let { return it }
        val response = client.search(trimmed, options.allowFemale, options.blockVideos, k)
        cacheMutex.withLock { cache[key] = response }
        return response
    }

    companion object {
        private const val K_SUMMARY = 8
        private const val K_FILTER = 100
        private const val K_SUGGEST = 8
        private const val CACHE_SIZE = 12
    }
}
