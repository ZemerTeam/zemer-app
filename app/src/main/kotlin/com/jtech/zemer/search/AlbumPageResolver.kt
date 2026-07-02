package com.jtech.zemer.search

import com.metrolist.innertube.pages.AlbumPage
import timber.log.Timber

/**
 * Resolve an [AlbumPage] for a search-result album. A Zemer-sourced result loads through the
 * server's `/album` endpoint first (whitelist-scoped, immune to on-device InnerTube bot-gating)
 * and falls back to the on-device InnerTube fetch when the server is unreachable/errors — unlike
 * the album *screen*, which surfaces the Zemer failure, a long-press menu has no error surface, so
 * degrading to today's InnerTube path beats a silently empty menu. A YouTube-sourced result keeps
 * the plain InnerTube fetch.
 *
 * The sources are lambdas so this decision logic stays pure and JVM-testable (the real InnerTube
 * client is a singleton `object` that can't be faked in a unit test).
 */
suspend fun resolveAlbumPage(
    zemer: Boolean,
    fromZemer: suspend () -> AlbumPage,
    fromInnerTube: suspend () -> Result<AlbumPage>,
): Result<AlbumPage> {
    if (!zemer) return fromInnerTube()
    return runCatching { fromZemer() }.recoverCatching { zemerError ->
        Timber.w(zemerError, "Zemer album fetch failed; falling back to InnerTube")
        fromInnerTube().getOrThrow()
    }
}
