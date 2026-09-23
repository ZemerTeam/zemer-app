package com.jtech.zemer.latestreleases

import com.jtech.zemer.utils.ContentFilterConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapLatest

/**
 * The releases a user sees: [feed] run through [filter] under the current [filters], re-run whenever
 * the feed, the artist [whitelist] or the content filters change. Filtering once at load left the row
 * empty for the whole session when the feed arrived before the whitelist (every artist was still
 * unverified and rejected). Unverified artists stay rejected while waiting; nothing is emitted until
 * the feed has loaded (a null [feed] value). A newer input cancels an in-flight filter pass.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun visibleLatestReleases(
    feed: Flow<List<LatestRelease>?>,
    whitelist: Flow<*>,
    filters: Flow<ContentFilterConfig>,
    filter: suspend (List<LatestRelease>, ContentFilterConfig) -> List<LatestRelease>,
): Flow<List<LatestRelease>> =
    combine(feed.filterNotNull(), whitelist, filters) { releases, _, config -> releases to config }
        .mapLatest { (releases, config) -> filter(releases, config) }
